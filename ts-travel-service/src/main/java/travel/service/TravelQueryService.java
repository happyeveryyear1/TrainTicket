package travel.service;

import edu.fudan.common.entity.TravelInfo;
import edu.fudan.common.util.Response;
import org.springframework.http.HttpHeaders;

/**
 * 演示：Interface-to-interface inheritance
 *
 * {@code TravelQueryService extends TravelService} —— 这是一个接口继承接口的场景。
 * 它继承了 TravelService 的全部 12 个方法声明，并额外声明了 queryByRouteName 和
 * findAvailableTrips 两个方法。
 *
 * CodeGraph 对 interface-to-interface extends 的处理流程：
 * 1. tree-sitter-java 将 {@code extends} 解析为 {@code extends_interfaces} 节点
 * 2. {@code extractInheritance()} 遍历 extends_interfaces → type_list 下的所有
 *    type_identifier，为每个父接口生成 {@code extends} 边
 * 3. {@code extends_interfaces} 明确包含在 {@code extractInheritance()} 的子类型
 *    匹配列表中（与 class 的 {@code superclass} 并列）
 * 4. 解析器侧的 SUPERTYPE_BEARING_KINDS 包含 interface，所以超类型链可以被完整遍历
 *
 * 调用链验证：Controller 调用 {@code travelService.findByRoute("G1234")} 时，
 * CodeGraph 需要：
 *   TravelController.findByRoute
 *     → TravelService (作为字段类型，import 解析到接口)
 *     → 但 findByRoute 不在 TravelService 中！它在 TravelQueryService 中。
 *     → 如果引用的是 TravelQueryService 类型，可以通过 implements 边找到 TravelServiceImpl
 *     → 如果引用的是 TravelService 类型，则 findByRoute 不在 TravelService 的方法列表中，
 *       需要 GraphTraverser 沿着 implements 边下钻到 TravelServiceImpl，再检查
 *       父接口 TravelQueryService 的方法列表才能找到。
 *
 * 这是一个多层级接口链：TravelServiceImpl → TravelQueryService → TravelService
 */
public interface TravelQueryService extends TravelService {

    /**
     * 根据 route 名称（非 ID）查询 trip 列表。
     * 此方法只定义在 TravelQueryService 中，不在 TravelService 中。
     */
    Response findByRoute(String routeName, HttpHeaders headers);

    /**
     * 查询所有可预订的车次（出发时间 > 当前时间）。
     * 同样只在 TravelQueryService 中，不在 TravelService 中。
     */
    Response findAvailableTrips(HttpHeaders headers);

    // =========================================================================
    // 四个场景演示方法（TravelServiceImpl 实现）
    // =========================================================================

    /** 场景 1：接口注入的票价计算器 */
    Response calculateTripPrice(String tripId, int seatCount, HttpHeaders headers);

    /** 场景 2：工厂模式创建计算器 */
    Response calculatePriceByFactory(String tripId, int seatCount, String trainType, HttpHeaders headers);

    /** 场景 3：泛型 BaseRepository<T> 查找 */
    Response findTripByGenericRepo(String tripId, HttpHeaders headers);

    /** 场景 4：Map<String, Handler> 动态分发 */
    Response processTripByType(String tripId, HttpHeaders headers);
}
