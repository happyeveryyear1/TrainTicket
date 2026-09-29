package travel.controller;

import edu.fudan.common.entity.TravelInfo;
import edu.fudan.common.entity.TripAllDetailInfo;
import edu.fudan.common.entity.TripInfo;
import edu.fudan.common.entity.TripResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import edu.fudan.common.entity.TravelInfo;
import travel.entity.*;
import travel.service.TravelAnalyticsService;
import travel.service.TravelQueryService;
import travel.service.TravelService;

import java.util.ArrayList;

import static org.springframework.http.ResponseEntity.ok;

/**
 * @author fdse
 */
@RestController
@RequestMapping("/api/v1/travelservice")

public class TravelController {

    /**
     * 三层依赖注入演示：
     *
     * - TravelService: 字段类型是顶层父接口。所有已有端点通过此字段调用。
     *   它是接口继承链的根节点。
     * - TravelQueryService extends TravelService: 新增 findByRoute/findAvailableTrips。
     *   CodeGraph 必须沿着 extends (接口→接口) + implements (类→接口) 双层边
     *   才能将调用点连接到 TravelServiceImpl 中的实现。
     * - TravelAnalyticsService (独立接口): 新增 getAnalytics/getPriceStats。
     *   TravelServiceImpl 同时实现此接口和 TravelQueryService，形成多接口实现。
     *
     * Spring 在运行时会将同一个 TravelServiceImpl 实例注入到全部三个字段中，
     * 因为 TravelServiceImpl 实现了所有三个接口。
     *
     * CodeGraph 的分析链路：
     *   Controller.findByRoute  --calls--> TravelQueryService.findByRoute
     *     --implements--> TravelServiceImpl.findByRoute
     *     其中 TravelQueryService --extends--> TravelService
     */
    private final TravelService travelService;
    private final TravelQueryService travelQueryService;
    private final TravelAnalyticsService travelAnalyticsService;

    /**
     * 构造器注入。
     * TravelService / TravelQueryService / TravelAnalyticsService 三个字段都
     * 由同一个 TravelServiceImpl 实例注入 —— Spring 自动识别。
     */
    public TravelController(
            TravelService travelService,
            TravelQueryService travelQueryService,
            TravelAnalyticsService travelAnalyticsService) {
        this.travelService = travelService;
        this.travelQueryService = travelQueryService;
        this.travelAnalyticsService = travelAnalyticsService;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(TravelController.class);

    @GetMapping(path = "/welcome")
    public String home(@RequestHeader HttpHeaders headers) {
        return "Welcome to [ Travel Service ] !";
    }

    @GetMapping(value = "/train_types/{tripId}")
    public HttpEntity getTrainTypeByTripId(@PathVariable String tripId,
                                           @RequestHeader HttpHeaders headers) {
        // TrainType
        TravelController.LOGGER.info("[getTrainTypeByTripId][Get train Type by Trip id][TripId: {}]", tripId);
        return ok(travelService.getTrainTypeByTripId(tripId, headers));
    }

    @GetMapping(value = "/routes/{tripId}")
    public HttpEntity getRouteByTripId(@PathVariable String tripId,
                                       @RequestHeader HttpHeaders headers) {
        TravelController.LOGGER.info("[getRouteByTripId][Get Route By Trip ID][TripId: {}]", tripId);
        //Route
        return ok(travelService.getRouteByTripId(tripId, headers));
    }

    @PostMapping(value = "/trips/routes")
    public HttpEntity getTripsByRouteId(@RequestBody ArrayList<String> routeIds,
                                        @RequestHeader HttpHeaders headers) {
        // ArrayList<ArrayList<Trip>>
        TravelController.LOGGER.info("[getTripByRoute][Get Trips by Route ids][RouteIds: {}]", routeIds.size());
        return ok(travelService.getTripByRoute(routeIds, headers));
    }

    @CrossOrigin(origins = "*")
    @PostMapping(value = "/trips")
    public HttpEntity<?> createTrip(@RequestBody TravelInfo routeIds, @RequestHeader HttpHeaders headers) {
        // null
        TravelController.LOGGER.info("[create][Create trip][TripId: {}]", routeIds.getTripId());
        return new ResponseEntity<>(travelService.create(routeIds, headers), HttpStatus.CREATED);
    }

    /**
     * Return Trip only, no left ticket information
     *
     * @param tripId  trip id
     * @param headers headers
     * @return HttpEntity
     */
    @CrossOrigin(origins = "*")
    @GetMapping(value = "/trips/{tripId}")
    public HttpEntity retrieve(@PathVariable String tripId, @RequestHeader HttpHeaders headers) {
        // Trip
        TravelController.LOGGER.info("[retrieve][Retrieve trip][TripId: {}]", tripId);
        return ok(travelService.retrieve(tripId, headers));
    }

    @CrossOrigin(origins = "*")
    @PutMapping(value = "/trips")
    public HttpEntity updateTrip(@RequestBody TravelInfo info, @RequestHeader HttpHeaders headers) {
        // Trip
        TravelController.LOGGER.info("[update][Update trip][TripId: {}]", info.getTripId());
        return ok(travelService.update(info, headers));
    }

    @CrossOrigin(origins = "*")
    @DeleteMapping(value = "/trips/{tripId}")
    public HttpEntity deleteTrip(@PathVariable String tripId, @RequestHeader HttpHeaders headers) {
        // string
        TravelController.LOGGER.info("[delete][Delete trip][TripId: {}]", tripId);
        return ok(travelService.delete(tripId, headers));
    }

    /**
     * Return Trips and the remaining tickets
     *
     * @param info    trip info
     * @param headers headers
     * @return HttpEntity
     */
    @CrossOrigin(origins = "*")
    @PostMapping(value = "/trips/left")
    public HttpEntity queryInfo(@RequestBody TripInfo info, @RequestHeader HttpHeaders headers) {
        if (info.getStartPlace() == null || info.getStartPlace().length() == 0 ||
                info.getEndPlace() == null || info.getEndPlace().length() == 0 ||
                info.getDepartureTime() == null) {
            TravelController.LOGGER.info("[query][Travel Query Fail][Something null]");
            ArrayList<TripResponse> errorList = new ArrayList<>();
            return ok(errorList);
        }
        TravelController.LOGGER.info("[query][Query TripResponse]");
        return ok(travelService.queryByBatch(info, headers));
    }

    /**
     * Return Trips and the remaining tickets
     *
     * @param info    trip info
     * @param headers headers
     * @return HttpEntity
     */
    @CrossOrigin(origins = "*")
    @PostMapping(value = "/trips/left_parallel")
    public HttpEntity queryInfoInparallel(@RequestBody TripInfo info, @RequestHeader HttpHeaders headers) {
        if (info.getStartPlace() == null || info.getStartPlace().length() == 0 ||
                info.getEndPlace() == null || info.getEndPlace().length() == 0 ||
                info.getDepartureTime() == null) {
            TravelController.LOGGER.info("[queryInParallel][Travel Query Fail][Something null]");
            ArrayList<TripResponse> errorList = new ArrayList<>();
            return ok(errorList);
        }
        TravelController.LOGGER.info("[queryInParallel][Query TripResponse]");
        return ok(travelService.queryInParallel(info, headers));
    }

    /**
     * Return a Trip and the remaining
     *
     * @param gtdi    trip all detail info
     * @param headers headers
     * @return HttpEntity
     */
    @CrossOrigin(origins = "*")
    @PostMapping(value = "/trip_detail")
    public HttpEntity getTripAllDetailInfo(@RequestBody TripAllDetailInfo gtdi, @RequestHeader HttpHeaders headers) {
        // TripAllDetailInfo
        // TripAllDetail tripAllDetail
        TravelController.LOGGER.info("[getTripAllDetailInfo][Get trip detail][TripId: {}]", gtdi.getTripId());
        return ok(travelService.getTripAllDetailInfo(gtdi, headers));
    }

    @CrossOrigin(origins = "*")
    @GetMapping(value = "/trips")
    public HttpEntity queryAll(@RequestHeader HttpHeaders headers) {
        // List<Trip>
        TravelController.LOGGER.info("[queryAll][Query all trips]");
        return ok(travelService.queryAll(headers));
    }

    @CrossOrigin(origins = "*")
    @GetMapping(value = "/admin_trip")
    public HttpEntity adminQueryAll(@RequestHeader HttpHeaders headers) {
        // ArrayList<AdminTrip>
        TravelController.LOGGER.info("[adminQueryAll][Admin query all trips]");
        return ok(travelService.adminQueryAll(headers));
    }

    // =========================================================================
    // 以下新增端点用于演示 interface-to-interface inheritance 和
    // multi-interface implementation 的 CodeGraph 调用链分析
    // =========================================================================

    /**
     * 演示接口继承链：调用 TravelQueryService 中的方法。
     *
     * 分析链路（CodeGraph 需要完成的三跳）：
     *   ① Controller.findByRouteName  --calls-->  TravelQueryService.findByRoute
     *   ② TravelQueryService          --extends-->  TravelService (根接口)
     *   ③ TravelServiceImpl           --implements--> TravelQueryService
     *
     * 步骤 ② 是关键：tree-sitter 的 extractInheritance() 已从 extends_interfaces
     * 节点中提取，并生成了 extends 边。GraphTraverser 在做 getCallees 时应能
     * 沿着 extends + implements 边找到 TravelServiceImpl.findByRoute 的具体实现。
     */
    @CrossOrigin(origins = "*")
    @GetMapping(value = "/trips/by_route/{routeName}")
    public HttpEntity findByRouteName(@PathVariable String routeName,
                                      @RequestHeader HttpHeaders headers) {
        TravelController.LOGGER.info("[findByRouteName][Searching by route: {}]", routeName);
        return ok(travelQueryService.findByRoute(routeName, headers));
    }

    /**
     * 演示多接口实现：调用 TravelAnalyticsService（独立接口）中的方法。
     *
     * 分析链路：
     *   ① Controller.getAnalytics --calls--> TravelAnalyticsService.getAnalytics
     *   ② TravelServiceImpl       --implements--> TravelAnalyticsService
     *
     * TravelServiceImpl 同时 implements TravelQueryService（继承自 TravelService）
     * 和 TravelAnalyticsService。CodeGraph 需要为 TravelServiceImpl 生成两条
     * implements 边，分别指向两个接口。
     */
    @CrossOrigin(origins = "*")
    @GetMapping(value = "/analytics")
    public HttpEntity getAnalytics(@RequestHeader HttpHeaders headers) {
        TravelController.LOGGER.info("[getAnalytics][Fetching analytics]");
        return ok(travelAnalyticsService.getAnalytics(headers));
    }

    @CrossOrigin(origins = "*")
    @GetMapping(value = "/analytics/price/{tripId}")
    public HttpEntity getPriceStats(@PathVariable String tripId,
                                    @RequestHeader HttpHeaders headers) {
        TravelController.LOGGER.info("[getPriceStats][Computing stats for trip: {}]", tripId);
        return ok(travelAnalyticsService.getPriceStats(tripId, headers));
    }

    @CrossOrigin(origins = "*")
    @GetMapping(value = "/trips/available")
    public HttpEntity findAvailableTrips(@RequestHeader HttpHeaders headers) {
        TravelController.LOGGER.info("[findAvailableTrips][Querying available trips]");
        return ok(travelQueryService.findAvailableTrips(headers));
    }

    // =========================================================================
    // 场景 1：接口 + 多态 + 依赖注入
    // 字段 travelQueryService 为接口类型，运行时注入 TravelServiceImpl
    // CodeGraph: Controller → TravelQueryService.calculateTripPrice
    //   → TravelServiceImpl.calculateTripPrice → priceCalculator.calculatePrice
    //   → TicketPriceCalculator.calculatePrice (接口)
    //   → 需沿 implements 边找到 StandardPriceCalculator 或 WeekendPriceCalculator
    // =========================================================================
    @CrossOrigin(origins = "*")
    @GetMapping(value = "/price/{tripId}")
    public HttpEntity calculatePrice(@PathVariable String tripId,
                                     @RequestParam(defaultValue = "1") int seats,
                                     @RequestHeader HttpHeaders headers) {
        TravelController.LOGGER.info("[calculatePrice][tripId: {}, seats: {}]", tripId, seats);
        return ok(travelQueryService.calculateTripPrice(tripId, seats, headers));
    }

    // =========================================================================
    // 场景 2：工厂模式 + 动态创建对象
    // 工厂方法根据 trainType 参数返回不同的计算器实现
    // CodeGraph: Controller → TravelServiceImpl.calculatePriceByFactory
    //   → TicketPriceCalculatorFactory.createCalculator(trainType)
    //     → new StandardPriceCalculator() 或 new WeekendPriceCalculator()
    //   → calculator.calculatePrice(trip, seats)
    // 返回值为接口类型，CodeGraph 无法确定具体实现
    // =========================================================================
    @CrossOrigin(origins = "*")
    @GetMapping(value = "/price/factory/{tripId}")
    public HttpEntity calculatePriceByFactory(@PathVariable String tripId,
                                              @RequestParam(defaultValue = "1") int seats,
                                              @RequestParam(defaultValue = "GaoTie") String trainType,
                                              @RequestHeader HttpHeaders headers) {
        TravelController.LOGGER.info("[calculatePriceByFactory][tripId: {}, seats: {}, type: {}]",
                tripId, seats, trainType);
        return ok(travelQueryService.calculatePriceByFactory(tripId, seats, trainType, headers));
    }

    // =========================================================================
    // 场景 3：泛型 + 泛型擦除 + 多态
    // TripRepository2 extends BaseRepository<Trip>，泛型实参 Trip 不生成边
    // CodeGraph: Controller → TravelServiceImpl.findTripByGenericRepo
    //   → tripRepository2.findById(id)  ← 方法在 BaseRepository<T> 中定义
    //     泛型 T 在调用点擦除，CodeGraph 无法关联到 Trip 类型
    // =========================================================================
    @CrossOrigin(origins = "*")
    @GetMapping(value = "/trips/generic/{tripId}")
    public HttpEntity findTripByGenericRepo(@PathVariable String tripId,
                                            @RequestHeader HttpHeaders headers) {
        TravelController.LOGGER.info("[findTripByGenericRepo][tripId: {}]", tripId);
        return ok(travelQueryService.findTripByGenericRepo(tripId, headers));
    }

    // =========================================================================
    // 场景 4：通过 Map 持有对象 + 运行时动态分发
    // TripHandlerRegistry 内部 Map<String, TripTypeHandler> 按 key 分发
    // CodeGraph: Controller → TravelServiceImpl.processTripByType
    //   → handlerRegistry.processTrip(trip)
    //     → handlerMap.get(trainType).handle(trip)
    // dispatch key 为运行时字符串，静态分析无法确定调用了哪个 handler 实现
    // =========================================================================
    @CrossOrigin(origins = "*")
    @GetMapping(value = "/trips/handle/{tripId}")
    public HttpEntity processTripByType(@PathVariable String tripId,
                                        @RequestHeader HttpHeaders headers) {
        TravelController.LOGGER.info("[processTripByType][tripId: {}]", tripId);
        return ok(travelQueryService.processTripByType(tripId, headers));
    }

}
