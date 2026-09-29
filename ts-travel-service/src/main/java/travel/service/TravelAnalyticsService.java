package travel.service;

import edu.fudan.common.util.Response;
import org.springframework.http.HttpHeaders;

/**
 * 演示：多接口实现（multi-interface implementation）
 *
 * 此接口是一个独立接口，不继承任何其他接口。
 * TravelServiceImpl 将同时实现 TravelQueryService（继承自 TravelService）和
 * 此接口 —— 形成菱形依赖的变体：
 *
 *          TravelService
 *               ↑ extends
 *       TravelQueryService    TravelAnalyticsService
 *               ↑ implements  ↗ implements
 *          TravelServiceImpl
 *
 * CodeGraph 对此的处理验证点：
 * 1. tree-sitter 是否为 TravelServiceImpl 生成两条 {@code implements} 边？
 *    分别指向 TravelQueryService 和 TravelAnalyticsService
 * 2. GraphTraverser 在查找 callee 时，能正确在 TravelAnalyticsService 的方法
 *    列表中匹配 {@code getAnalytics()}，即使 TravelServiceImpl 的 field 类型
 *    在 Controller 中被声明为 TravelService
 */
public interface TravelAnalyticsService {

    /**
     * 获取分析数据（JSON 字符串）。
     * 此方法仅在 TravelAnalyticsService 中声明。
     */
    Response getAnalytics(HttpHeaders headers);

    /**
     * 获取指定车次的价格统计。
     * 此方法仅在 TravelAnalyticsService 中声明。
     */
    Response getPriceStats(String tripId, HttpHeaders headers);
}
