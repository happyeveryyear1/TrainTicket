package travel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fudan.common.entity.*;
import edu.fudan.common.util.JsonUtils;
import edu.fudan.common.util.Response;
import edu.fudan.common.util.StringUtils;
import org.apache.skywalking.apm.toolkit.trace.TraceCrossThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.RouteMatcher.Route;
import org.springframework.web.client.RestTemplate;
import travel.analytics.TravelMetrics;
import travel.config.BeanDefinitions.DiscountCalculator;
import travel.config.BeanDefinitions.RouteCache;
import travel.entity.AdminTrip;
import travel.entity.Travel;
import travel.entity.Trip;
import travel.entity.TripAllDetail;
import travel.gateway.PaymentGateway;
import travel.handler.TripHandlerRegistry;
import travel.pricing.TicketPriceCalculator;
import travel.pricing.TicketPriceCalculatorFactory;
import travel.repository.BaseRepository;
import travel.repository.TripRepository;
import travel.repository.TripRepository2;

import javax.transaction.Transactional;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author fdse
 */
/**
 * 构造器链演示：
 *
 * TravelServiceImpl 继承链：
 *   TravelServiceImpl
 *     ├── extends AbstractTravelService        (类继承)
 *     │     └── super(metrics) → explicit_constructor_invocation ❌ CodeGraph 不捕获
 *     ├── implements TravelQueryService        (接口实现)
 *     │     └── extends TravelService          (接口继承接口)
 *     └── implements TravelAnalyticsService    (接口实现)
 *
 * 接口继承链（CodeGraph 应能处理）：
 *   TravelServiceImpl implements TravelQueryService extends TravelService
 *   TravelServiceImpl implements TravelAnalyticsService (独立接口)
 *
 * 构造器调用链（CodeGraph 无法处理）：
 *   TravelServiceImpl() --super()--> AbstractTravelService(TravelMetrics)
 *     --this()--> AbstractTravelService(TravelMetrics, String)
 *   两端 super()/this() 都是 explicit_constructor_invocation，
 *   CodeGraph 的 callTypes 仅含 'method_invocation'，不处理此节点类型。
 */
@Service
public class TravelServiceImpl extends AbstractTravelService
        implements TravelQueryService, TravelAnalyticsService {

    private final TripRepository repository;
    private final RestTemplate restTemplate;
    private final DiscoveryClient discoveryClient;

    // ---- 场景演示依赖 ----
    private final PaymentGateway paymentGateway;
    private final DiscountCalculator discountCalculator;
    private final RouteCache routeCache;

    /** 场景 1：接口类型注入 —— CodeGraph 需沿 implements 边找到具体实现 */
    private final TicketPriceCalculator priceCalculator;

    /** 场景 3：泛型仓库 —— BaseRepository<T> 的 T 在调用点擦除 */
    private final TripRepository2 tripRepository2;

    /** 场景 4：Map 持有的处理器 —— dispatch key 是运行时字符串 */
    private final TripHandlerRegistry handlerRegistry;

    public TravelServiceImpl(
            TripRepository repository,
            RestTemplate restTemplate,
            DiscoveryClient discoveryClient,
            TravelMetrics travelMetrics,          // 传给 super()
            PaymentGateway paymentGateway,
            DiscountCalculator discountCalculator,
            RouteCache routeCache,
            TicketPriceCalculator priceCalculator,   // 场景 1
            TripRepository2 tripRepository2,         // 场景 3
            TripHandlerRegistry handlerRegistry) {   // 场景 4
        super(travelMetrics);                    // Gap: super() 不受 CodeGraph 捕获
        this.repository = repository;
        this.restTemplate = restTemplate;
        this.discoveryClient = discoveryClient;
        this.paymentGateway = paymentGateway;
        this.discountCalculator = discountCalculator;
        this.routeCache = routeCache;
        this.priceCalculator = priceCalculator;
        this.tripRepository2 = tripRepository2;
        this.handlerRegistry = handlerRegistry;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(TravelServiceImpl.class);

    private static final ExecutorService executorService = Executors.newFixedThreadPool(20, new CustomizableThreadFactory("HttpClientThreadPool-"));

    private String getServiceUrl(String serviceName) {
        return "http://" + serviceName;
    }

    String success = "Success";
    String noContent = "No Content";

    @Override
    public Response create(TravelInfo info, HttpHeaders headers) {
        TripId ti = new TripId(info.getTripId());
        if (repository.findByTripId(ti) == null) {
            Trip trip = new Trip(ti, info.getTrainTypeName(), info.getStartStationName(),
                    info.getStationsName(), info.getTerminalStationName(), info.getStartTime(), info.getEndTime());
            trip.setRouteId(info.getRouteId());
            repository.save(trip);
            return new Response<>(1, "Create trip:" + ti.toString() + ".", null);
        } else {
            TravelServiceImpl.LOGGER.error("[create][Create trip error][Trip already exists][TripId: {}]", info.getTripId());
            return new Response<>(1, "Trip " + info.getTripId().toString() + " already exists", null);
        }
    }

    @Override
    public Response getRouteByTripId(String tripId, HttpHeaders headers) {
        Route route = null;
        if (null != tripId && tripId.length() >= 2) {
            TripId tripId1 = new TripId(tripId);
            Trip trip = repository.findByTripId(tripId1);
            if (trip != null) {
                route = getRouteByRouteId(trip.getRouteId(), headers);
            } else {
                TravelServiceImpl.LOGGER.error("[getRouteByTripId][Get route by Trip id error][Trip not found][TripId: {}]", tripId);
            }
        }
        if (route != null) {
            return new Response<>(1, success, route);
        } else {
            TravelServiceImpl.LOGGER.error("[getRouteByTripId][Get route by Trip id error][Route not found][TripId: {}]", tripId);
            return new Response<>(0, noContent, null);
        }
    }

    @Override
    public Response getTrainTypeByTripId(String tripId, HttpHeaders headers) {
        TripId tripId1 = new TripId(tripId);
        TrainType trainType = null;
        Trip trip = repository.findByTripId(tripId1);
        if (trip != null) {
            trainType = getTrainTypeByName(trip.getTrainTypeName(), headers);
        } else {
            TravelServiceImpl.LOGGER.error("[getTrainTypeByTripId][Get Train Type by Trip id error][Trip not found][TripId: {}]", tripId);
        }
        if (trainType != null) {
            return new Response<>(1, success, trainType);
        } else {
            TravelServiceImpl.LOGGER.error("[getTrainTypeByTripId][Get Train Type by Trip id error][Train Type not found][TripId: {}]", tripId);
            return new Response<>(0, noContent, null);
        }
    }

    @Override
    public Response getTripByRoute(ArrayList<String> routeIds, HttpHeaders headers) {
        ArrayList<ArrayList<Trip>> tripList = new ArrayList<>();
        for (String routeId : routeIds) {
            ArrayList<Trip> tempTripList = repository.findByRouteId(routeId);
            if (tempTripList == null) {
                tempTripList = new ArrayList<>();
            }
            tripList.add(tempTripList);
        }
        if (!tripList.isEmpty()) {
            return new Response<>(1, success, tripList);
        } else {
            TravelServiceImpl.LOGGER.warn("[getTripByRoute][Get trips by routes warn][Trip list][{}]", "No content");
            return new Response<>(0, noContent, null);
        }
    }


    @Override
    public Response retrieve(String tripId, HttpHeaders headers) {
        TripId ti = new TripId(tripId);
        Trip trip = repository.findByTripId(ti);
        if (trip != null) {
            return new Response<>(1, "Search Trip Success by Trip Id " + tripId, trip);
        } else {
            TravelServiceImpl.LOGGER.error("[retrieve][Retrieve trip error][Trip not found][TripId: {}]", tripId);
            return new Response<>(0, "No Content according to tripId" + tripId, null);
        }
    }

    @Override
    public Response update(TravelInfo info, HttpHeaders headers) {
        TripId ti = new TripId(info.getTripId());
        Trip t = repository.findByTripId(ti);
        if (t != null) {
            t.setStartStationName(info.getTrainTypeName());
            t.setStartStationName( info.getStartStationName());
            t.setStationsName(info.getStationsName());
            t.setTerminalStationName(info.getTerminalStationName());
            t.setStartTime(info.getStartTime());
            t.setEndTime(info.getEndTime());
            t.setRouteId(info.getRouteId());
            repository.save(t);
            return new Response<>(1, "Update trip:" + ti.toString(), t);
        } else {
            TravelServiceImpl.LOGGER.error("[update][Update trip error][Trip not found][TripId: {}]", info.getTripId());
            return new Response<>(1, "Trip" + info.getTripId().toString() + "doesn 't exists", null);
        }
        
        //Gets the start and arrival stations of the train number to query. The originating and arriving stations received here are both station names, so two requests need to be sent to convert to station ids
        String startPlaceName = info.getStartPlace();
        String endPlaceName = info.getEndPlace();

        //This is the final result
        List<TripResponse> list = new ArrayList<>();

        //Check all train info
        List<Trip> allTripList = repository.findAll();
        list = getTicketsByBatch(allTripList, startPlaceName, endPlaceName, info.getDepartureTime(), headers);
    }

    @Override
    @Transactional
    public Response delete(String tripId, HttpHeaders headers) {
        TripId ti = new TripId(tripId);
        if (repository.findByTripId(ti) != null) {
            repository.deleteByTripId(ti);
            return new Response<>(1, "Delete trip:" + tripId + ".", tripId);
        } else {
            TravelServiceImpl.LOGGER.error("[delete][Delete trip error][Trip not found][TripId: {}]", tripId);
            return new Response<>(0, "Trip " + tripId + " doesn't exist.", null);
        }
    }

    @Override
    public Response query(TripInfo info, HttpHeaders headers) {

        //Gets the start and arrival stations of the train number to query. The originating and arriving stations received here are both station names, so two requests need to be sent to convert to station ids
        String startPlaceName = info.getStartPlace();
        String endPlaceName = info.getEndPlace();

        //This is the final result
        List<TripResponse> list = new ArrayList<>();

        //Check all train info
        List<Trip> allTripList = repository.findAll();
        if(allTripList != null) {
            for (Trip tempTrip : allTripList) {
                //Get the detailed route list of this train
                TripResponse response = getTickets(tempTrip, null, startPlaceName, endPlaceName, info.getDepartureTime(), headers);
                if (response == null) {
                    TravelServiceImpl.LOGGER.warn("[query][Query trip error][Tickets not found][start: {},end: {},time: {}]", startPlaceName, endPlaceName, info.getDepartureTime());
                }else{
                    list.add(response);
                }
            }
        }
        return new Response<>(1, success, list);
    }



    @Override
    public Response queryByBatch(TripInfo info, HttpHeaders headers) {

        //Gets the start and arrival stations of the train number to query. The originating and arriving stations received here are both station names, so two requests need to be sent to convert to station ids
        String startPlaceName = info.getStartPlace();
        String endPlaceName = info.getEndPlace();

        //This is the final result
        List<TripResponse> list = new ArrayList<>();

        //Check all train info
        List<Trip> allTripList = repository.findAll();
        list = getTicketsByBatch(allTripList, startPlaceName, endPlaceName, info.getDepartureTime(), headers);
        return new Response<>(1, success, list);
    }

    @TraceCrossThread
    class MyCallable implements Callable<TripResponse> {
        private TripInfo info;
        private Trip tempTrip;
        private HttpHeaders headers;
        private String startPlaceName;
        private String endPlaceName;

        MyCallable(TripInfo info, String startPlaceName, String endPlaceName, Trip tempTrip, HttpHeaders headers) {
            this.info = info;
            this.tempTrip = tempTrip;
            this.headers = headers;
            this.startPlaceName = startPlaceName;
            this.endPlaceName = endPlaceName;
        }

        @Override
        public TripResponse call() throws Exception {
            TravelServiceImpl.LOGGER.debug("[call][Start to query][tripId: {}, routeId: {}] ", tempTrip.getTripId().toString(), tempTrip.getRouteId());

            String startPlaceName = info.getStartPlace();
            String endPlaceName = info.getEndPlace();
            //Route tempRoute = getRouteByRouteId(tempTrip.getRouteId(), headers);

            TripResponse response = null;

            response = getTickets(tempTrip, null, startPlaceName, endPlaceName, info.getDepartureTime(), headers);

            if (response == null) {
                TravelServiceImpl.LOGGER.warn("[call][Query trip error][Tickets not found][tripId: {}, routeId: {}, start: {}, end: {},time: {}]", tempTrip.getTripId().toString(), tempTrip.getRouteId(), startPlaceName, endPlaceName, info.getDepartureTime());
            } else {
                TravelServiceImpl.LOGGER.info("[call][Query trip success][tripId: {}, routeId: {}] ", tempTrip.getTripId().toString(), tempTrip.getRouteId());
            }
            return response;
        }
    }

    @Override
    public Response queryInParallel(TripInfo info, HttpHeaders headers) {
        //Gets the start and arrival stations of the train number to query. The originating and arriving stations received here are both station names, so two requests need to be sent to convert to station ids
        String startPlaceName = info.getStartPlace();
        String endPlaceName = info.getEndPlace();

        //This is the final result
        List<TripResponse> list = new ArrayList<>();

        //Check all train info
        List<Trip> allTripList = repository.findAll();
        List<Future<TripResponse>> futureList = new ArrayList<>();

        if(allTripList != null ){
            for (Trip tempTrip : allTripList) {
                MyCallable callable = new MyCallable(info, startPlaceName, endPlaceName, tempTrip, headers);
                Future<TripResponse> future = executorService.submit(callable);
                futureList.add(future);
            }
        }

        for (Future<TripResponse> future : futureList) {
            try {
                TripResponse response = future.get();
                if (response != null) {
                    list.add(response);
                }
            } catch (Exception e) {
                TravelServiceImpl.LOGGER.error("[queryInParallel][Query error]"+e.toString());
            }
        }

        if (list.isEmpty()) {
            return new Response<>(0, "No Trip info content", null);
        } else {
            return new Response<>(1, success, list);
        }
    }

    @Override
    public Response getTripAllDetailInfo(TripAllDetailInfo gtdi, HttpHeaders headers) {
        TripAllDetail gtdr = new TripAllDetail();
        TravelServiceImpl.LOGGER.debug("[getTripAllDetailInfo][TripId: {}]", gtdi.getTripId());
        Trip trip = repository.findByTripId(new TripId(gtdi.getTripId()));
        if (trip == null) {
            gtdr.setTripResponse(null);
            gtdr.setTrip(null);
            TravelServiceImpl.LOGGER.error("[getTripAllDetailInfo][Get trip detail error][Trip not found][TripId: {}]", gtdi.getTripId());
            return new Response<>(0, "Trip not found", gtdr);
        } else {
            String startPlaceName = gtdi.getFrom();
            String endPlaceName = gtdi.getTo();
            TripResponse tripResponse = getTickets(trip, null, startPlaceName, endPlaceName, gtdi.getTravelDate(), headers);
            if (tripResponse == null) {
                gtdr.setTripResponse(null);
                gtdr.setTrip(null);
                TravelServiceImpl.LOGGER.warn("[getTripAllDetailInfo][Get trip detail error][Tickets not found][start: {},end: {},time: {}]", startPlaceName, endPlaceName, gtdi.getTravelDate());
                return new Response<>(0, "getTickets failed", gtdr);
            } else {
                gtdr.setTripResponse(tripResponse);
                gtdr.setTrip(repository.findByTripId(new TripId(gtdi.getTripId())));
            }
        }
        return new Response<>(1, success, gtdr);
    }

    private List<TripResponse> getTicketsByBatch(List<Trip> trips, String startPlaceName, String endPlaceName, String departureTime, HttpHeaders headers) {
        List<TripResponse> responses = new ArrayList<>();
        //Determine if the date checked is the same day and after
        if (!afterToday(departureTime)) {
            TravelServiceImpl.LOGGER.info("[getTickets][depaturetime not vailid][departuretime: {}]", departureTime);
            return responses;
        }

        List<Travel> infos = new ArrayList<>();
        Map<String, Trip> tripMap = new HashMap<>();
        for(Trip trip: trips){
            Travel query = new Travel();
            query.setTrip(trip);
            query.setStartPlace(startPlaceName);
            query.setEndPlace(endPlaceName);
            query.setDepartureTime(departureTime);

            infos.add(query);
            tripMap.put(trip.getTripId().toString(), trip);
        }

        TravelServiceImpl.LOGGER.info("[getTicketsByBatch][before get basic][trips: {}]", trips);

        HttpEntity requestEntity = new HttpEntity(infos, null);
        String basic_service_url = getServiceUrl("ts-basic-service");
        ResponseEntity<Response> re = restTemplate.exchange(
                basic_service_url + "/api/v1/basicservice/basic/travels",
                HttpMethod.POST,
                requestEntity,
                Response.class);

        Response r = re.getBody();
        if(r.getStatus() == 0){
            TravelServiceImpl.LOGGER.info("[getTicketsByBatch][Ts-basic-service response status is 0][response is: {}]", r);
            return responses;
        }
        Map<String, TravelResult> trMap;
        ObjectMapper mapper = new ObjectMapper();
        try{
            trMap = mapper.readValue(JsonUtils.object2Json(r.getData()), new TypeReference<Map<String, TravelResult>>(){});
        }catch(Exception e) {
            TravelServiceImpl.LOGGER.warn("[getTicketsByBatch][Ts-basic-service convert data failed][Fail msg: {}]", e.getMessage());
            return responses;
        }

        for(Map.Entry<String, TravelResult> trEntry: trMap.entrySet()){
            //Set the returned ticket information
            String tripNumber = trEntry.getKey();
            TravelResult tr = trEntry.getValue();
            Trip trip = tripMap.get(tripNumber);

            TripResponse response = setResponse(trip, tr, startPlaceName, endPlaceName, departureTime, headers);
            responses.add(response);
        }
        return responses;
    }

    private TripResponse getTickets(Trip trip, Route route1, String startPlaceName, String endPlaceName, String departureTime, HttpHeaders headers) {

        //Determine if the date checked is the same day and after
        if (!afterToday(departureTime)) {
            TravelServiceImpl.LOGGER.info("[getTickets][depaturetime not vailid][departuretime: {}]", departureTime);
            return null;
        }

        Travel query = new Travel();
        query.setTrip(trip);
        query.setStartPlace(startPlaceName);
        query.setEndPlace(endPlaceName);
        query.setDepartureTime(departureTime);
        TravelServiceImpl.LOGGER.info("[getTickets][before get basic][trip: {}]", trip);

        HttpEntity requestEntity = new HttpEntity(query, null);
        String basic_service_url = getServiceUrl("ts-basic-service");
        ResponseEntity<Response> re = restTemplate.exchange(
                basic_service_url + "/api/v1/basicservice/basic/travel",
                HttpMethod.POST,
                requestEntity,
                Response.class);

        Response r = re.getBody();
        if(r.getStatus() == 0){
            TravelServiceImpl.LOGGER.info("[getTickets][Ts-basic-service response status is 0][response is: {}]", r);
            return null;
        }

        TravelResult resultForTravel = JsonUtils.conveterObject(re.getBody().getData(), TravelResult.class);

        //Set the returned ticket information
        return setResponse(trip, resultForTravel, startPlaceName, endPlaceName, departureTime, headers);
    }

    private TripResponse setResponse(Trip trip, TravelResult tr, String startPlaceName, String endPlaceName, String departureTime, HttpHeaders headers){
        //Set the returned ticket information
        TripResponse response = new TripResponse();
        response.setConfortClass(50);
        response.setEconomyClass(50);

        Route route = tr.getRoute();
        List<String> stationList = route.getStations();

        TravelServiceImpl.LOGGER.info("[departrue date][y: {}][m:{}][d: {}]",calDateB.get(Calendar.YEAR), calDateB.get(Calendar.MONTH), calDateB.get(Calendar.DATE));


        int firstClassTotalNum = tr.getTrainType().getConfortClass();
        int secondClassTotalNum = tr.getTrainType().getEconomyClass();

        int first = getRestTicketNumber(departureTime, trip.getTripId().toString(),
                startPlaceName, endPlaceName, SeatClass.FIRSTCLASS.getCode(), firstClassTotalNum, stationList, headers);

        int second = getRestTicketNumber(departureTime, trip.getTripId().toString(),
                startPlaceName, endPlaceName, SeatClass.SECONDCLASS.getCode(), secondClassTotalNum, stationList, headers);
        response.setConfortClass(first);
        response.setEconomyClass(second);

        response.setStartStation(startPlaceName);
        response.setTerminalStation(endPlaceName);

        //Calculate the distance from the starting point
        int indexStart = route.getStations().indexOf(startPlaceName);
        int indexEnd = route.getStations().indexOf(endPlaceName);
        int distanceStart = route.getDistances().get(indexStart) - route.getDistances().get(0);
        int distanceEnd = route.getDistances().get(indexEnd) - route.getDistances().get(0);  
        Calendar calDateB = Calendar.getInstance();
        calDateB.setTime(StringUtils.String2Date(date));

        TravelServiceImpl.LOGGER.info("[today date][y: {}][m:{}][d: {}]",calDateA.get(Calendar.YEAR), calDateA.get(Calendar.MONTH), calDateA.get(Calendar.DATE));
        TravelServiceImpl.LOGGER.info("[departrue date][y: {}][m:{}][d: {}]",calDateB.get(Calendar.YEAR), calDateB.get(Calendar.MONTH), calDateB.get(Calendar.DATE));

        if (calDateA.get(Calendar.YEAR) > calDateB.get(Calendar.YEAR)) {
            return false;
        } else if (calDateA.get(Calendar.YEAR) == calDateB.get(Calendar.YEAR)) {
            if (calDateA.get(Calendar.MONTH) > calDateB.get(Calendar.MONTH)) {
                return false;
            } else if (calDateA.get(Calendar.MONTH) == calDateB.get(Calendar.MONTH)) {
                return calDateA.get(Calendar.DAY_OF_MONTH) <= calDateB.get(Calendar.DAY_OF_MONTH);
            } else {
                return true;
            }
        } else {
            return true;
        }
    }

    private TrainType getTrainTypeByName(String trainTypeName, HttpHeaders headers) {
        HttpEntity requestEntity = new HttpEntity(null);
        String train_service_url = getServiceUrl("ts-train-service");
        ResponseEntity<Response<TrainType>> re = restTemplate.exchange(
                train_service_url + "/api/v1/trainservice/trains/byName/" + trainTypeName,
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<Response<TrainType>>() {
                });

        return re.getBody().getData();
    }

    private Route getRouteByRouteId(String routeId, HttpHeaders headers) {
        TravelServiceImpl.LOGGER.info("[getRouteByRouteId][Get Route By Id][Route ID：{}]", routeId);
        HttpEntity requestEntity = new HttpEntity(null);
        String route_service_url = getServiceUrl("ts-route-service");
        ResponseEntity<Response> re = restTemplate.exchange(
                route_service_url + "/api/v1/routeservice/routes/" + routeId,
                HttpMethod.GET,
                requestEntity,
                Response.class);
        Response routeRes = re.getBody();

        Route route1 = new Route();
        TravelServiceImpl.LOGGER.info("[getRouteByRouteId][Get Route By Id][Routes Response is : {}]", routeRes.toString());
        if (routeRes.getStatus() == 1) {
            route1 = JsonUtils.conveterObject(routeRes.getData(), Route.class);
            TravelServiceImpl.LOGGER.info("[getRouteByRouteId][Get Route By Id][Route is: {}]", route1.toString());
        }
        return route1;
    }

    private int getRestTicketNumber(String travelDate, String trainNumber, String startStationName, String endStationName, int seatType, int totalNum, List<String> stationList, HttpHeaders headers) {
        Seat seatRequest = new Seat();

        seatRequest.setDestStation(endStationName);
        seatRequest.setStartStation(startStationName);
        seatRequest.setTrainNumber(trainNumber);
        seatRequest.setTravelDate(travelDate);
        seatRequest.setSeatType(seatType);
        seatRequest.setTotalNum(totalNum);
        seatRequest.setStations(stationList);

        TravelServiceImpl.LOGGER.info("[getRestTicketNumber][Seat request][request: {}]", seatRequest.toString());

        HttpEntity requestEntity = new HttpEntity(seatRequest, null);
        String seat_service_url = getServiceUrl("ts-seat-service");
        ResponseEntity<Response<Integer>> re = restTemplate.exchange(
                seat_service_url + "/api/v1/seatservice/seats/left_tickets",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Response<Integer>>() {
                });
        TravelServiceImpl.LOGGER.info("[getRestTicketNumber][Get Rest tickets num][num is: {}]", re.getBody().toString());

        return re.getBody().getData();
    }

    @Override
    public Response adminQueryAll(HttpHeaders headers) {
        List<Trip> trips = repository.findAll();
        ArrayList<AdminTrip> adminTrips = new ArrayList<>();
        if(trips != null){
            for (Trip trip : trips) {
                AdminTrip adminTrip = new AdminTrip();
                adminTrip.setTrip(trip);
                adminTrip.setRoute(getRouteByRouteId(trip.getRouteId(), headers));
                adminTrip.setTrainType(getTrainTypeByName(trip.getTrainTypeName(), headers));
                adminTrips.add(adminTrip);
            }
        }

        if (!adminTrips.isEmpty()) {
            return new Response<>(1, success, adminTrips);
        } else {
            TravelServiceImpl.LOGGER.warn("[adminQueryAll][Admin query all trips warn][{}]", "No Content");
            return new Response<>(0, noContent, null);
        }
    }

    // =========================================================================
    // TravelQueryService 新增方法（接口继承接口场景）
    // =========================================================================

    /**
     * 根据 route 名称查找 trip。
     * 此方法来自 TravelQueryService，而 TravelQueryService extends TravelService。
     * CodeGraph 需要沿 extends 边从 TravelQueryService → TravelService 回溯，
     * 同时沿 implements 边从 TravelServiceImpl → TravelQueryService 下钻，
     * 才能完整构建此方法的调用者/被调用者关系。
     */
    @Override
    public Response findByRoute(String routeName, HttpHeaders headers) {
        TravelServiceImpl.LOGGER.info("[findByRoute][Searching trips by route name: {}]", routeName);
        List<Trip> allTrips = repository.findAll();
        List<TripResponse> result = new ArrayList<>();
        if (allTrips != null) {
            for (Trip trip : allTrips) {
                if (trip.getRouteId() != null) {
                    Route route = getRouteByRouteId(trip.getRouteId(), headers);
                    if (route != null && route.getStations() != null
                            && route.getStations().stream().anyMatch(s -> s.contains(routeName))) {
                        TripResponse resp = new TripResponse();
                        resp.setTripId(trip.getTripId());
                        resp.setTrainTypeName(trip.getTrainTypeName());
                        resp.setStartStation(trip.getStartStationName());
                        resp.setTerminalStation(trip.getTerminalStationName());
                        result.add(resp);
                    }
                }
            }
        }
        return new Response<>(1, success, result);
    }

    @Override
    public Response findAvailableTrips(HttpHeaders headers) {
        TravelServiceImpl.LOGGER.info("[findAvailableTrips][Querying all available trips]");
        List<Trip> allTrips = repository.findAll();
        if (allTrips != null && !allTrips.isEmpty()) {
            return new Response<>(1, success, allTrips);
        }
        return new Response<>(0, noContent, null);
    }

    // =========================================================================
    // TravelAnalyticsService 新增方法（多接口实现场景）
    // =========================================================================

    @Override
    public Response getAnalytics(HttpHeaders headers) {
        TravelServiceImpl.LOGGER.info("[getAnalytics][Retrieving travel analytics]");
        metrics.recordTripQueried();
        int discount = discountCalculator.calculateDiscount("all");
        int tripCount = metrics.getTripCreateCount();
        String json = String.format(
                "{\"tripCreateCount\":%d,\"tripQueryCount\":%d,\"defaultDiscount\":%d}",
                tripCount, metrics.getTripQueryCount(), discount);
        return new Response<>(1, success, json);
    }

    @Override
    public Response getPriceStats(String tripId, HttpHeaders headers) {
        TravelServiceImpl.LOGGER.info("[getPriceStats][Computing price stats for trip: {}]", tripId);
        Trip trip = repository.findByTripId(new TripId(tripId));
        if (trip == null) {
            return new Response<>(0, "Trip not found: " + tripId, null);
        }
        String stats = String.format(
                "{\"tripId\":\"%s\",\"trainType\":\"%s\",\"startStation\":\"%s\",\"terminalStation\":\"%s\"}",
                tripId, trip.getTrainTypeName(), trip.getStartStationName(), trip.getTerminalStationName());
        return new Response<>(1, success, stats);
    }

    // =========================================================================
    // 场景 1：接口 + 多态 + 依赖注入
    // Controller → TicketPriceCalculator接口 → StandardPriceCalculator实现
    // CodeGraph 需沿 implements 边找到具体实现类的方法体
    // =========================================================================

    /**
     * 使用接口注入的票价计算器计算票价。
     * priceCalculator 字段类型是接口，运行时实际类型由 Spring DI 决定。
     */
    public Response calculateTripPrice(String tripId, int seatCount, HttpHeaders headers) {
        Trip trip = repository.findByTripId(new TripId(tripId));
        if (trip == null) {
            return new Response<>(0, "Trip not found: " + tripId, null);
        }
        int price = priceCalculator.calculatePrice(trip, seatCount);
        String result = String.format("{\"tripId\":\"%s\",\"seats\":%d,\"price\":%d,\"strategy\":\"%s\"}",
                tripId, seatCount, price, priceCalculator.getStrategyName());
        return new Response<>(1, success, result);
    }

    // =========================================================================
    // 场景 2：工厂模式 + 动态创建对象
    // 工厂返回接口类型，运行时实际类型取决于 trainType 参数
    // CodeGraph 无法静态确定 createCalculator 返回的具体实现
    // =========================================================================

    /**
     * 通过工厂方法获取票价计算器并根据火车类型计算票价。
     * 工厂返回值类型是接口，CodeGraph 无法确定实际返回的 StandardPriceCalculator
     * 还是 WeekendPriceCalculator。
     */
    public Response calculatePriceByFactory(String tripId, int seatCount,
                                            String trainType, HttpHeaders headers) {
        Trip trip = repository.findByTripId(new TripId(tripId));
        if (trip == null) {
            return new Response<>(0, "Trip not found: " + tripId, null);
        }
        // Gap: 工厂返回接口类型，后续 calculatePrice() 调用只能解析到接口级别
        TicketPriceCalculator calculator = TicketPriceCalculatorFactory.createCalculator(trainType);
        int price = calculator.calculatePrice(trip, seatCount);
        String result = String.format(
                "{\"tripId\":\"%s\",\"seats\":%d,\"price\":%d,\"factoryType\":\"%s\",\"strategy\":\"%s\"}",
                tripId, seatCount, price, trainType, calculator.getStrategyName());
        return new Response<>(1, success, result);
    }

    // =========================================================================
    // 场景 3：泛型 + 泛型擦除 + 多态
    // tripRepository2 继承 BaseRepository<Trip>，但泛型实参 Trip 不生成边
    // 对 findById() 的调用穿越泛型基类，CodeGraph 无法精确定位
    // =========================================================================

    /**
     * 通过泛型 BaseRepository 的 findById 查询车次。
     * 调用链：service → TripRepository2 → BaseRepository<Trip>.findById
     * CodeGraph 可追踪 extends 边，但 T→Trip 的泛型绑定不可见。
     */
    @SuppressWarnings("unchecked")
    public Response findTripByGenericRepo(String tripId, HttpHeaders headers) {
        // Gap: findById 定义在 BaseRepository<T> 中，返回 Optional<T>
        // CodeGraph 无法在调用点确定 T==Trip
        java.util.Optional<Trip> opt = tripRepository2.findById(tripId);
        if (opt.isPresent()) {
            Trip trip = opt.get();
            return new Response<>(1, success, trip);
        }
        // 如果泛型仓库中没有，尝试用 BaseRepository.save 写入后再查
        Trip fallback = repository.findByTripId(new TripId(tripId));
        if (fallback != null) {
            tripRepository2.save(tripId, fallback);
            return new Response<>(1, "Saved to generic repo", fallback);
        }
        return new Response<>(0, "Trip not found: " + tripId, null);
    }

    // =========================================================================
    // 场景 4：通过 Map 持有对象 + 运行时动态分发
    // handlerRegistry 内部 Map<String, TripTypeHandler>，通过 key 动态选择
    // CodeGraph 无法从 runtime key 确定具体调用的 handler 实现
    // =========================================================================

    /**
     * 根据车次的火车类型动态选择处理器。
     * dispatch key = trip.getTrainTypeName()，完全运行时决定。
     * CodeGraph 只能看到调用了 TripTypeHandler.handle() 接口方法，
     * 无法追踪到 GaoTieTripHandler 或 DongCheTripHandler 的具体实现。
     */
    public Response processTripByType(String tripId, HttpHeaders headers) {
        Trip trip = repository.findByTripId(new TripId(tripId));
        if (trip == null) {
            return new Response<>(0, "Trip not found: " + tripId, null);
        }
        // Gap: registry.getHandler(key).handle(trip)
        //   ① Map.get() 返回 TripTypeHandler（接口）
        //   ② dispatch key 是 trip.getTrainTypeName()（运行时字符串）
        //   ③ handle() 的实际目标实现静态不可判定
        String handleResult = handlerRegistry.processTrip(trip);
        return new Response<>(1, success, handleResult);
    }
}
