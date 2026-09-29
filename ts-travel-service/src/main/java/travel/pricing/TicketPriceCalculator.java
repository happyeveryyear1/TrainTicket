package travel.pricing;

import travel.entity.Trip;

/**
 * 场景 1：接口 + 多态 + 依赖注入
 *
 * 票价计算器接口。Controller 以接口类型注入，运行时 Spring 根据
 * {@code @Qualifier} 选择具体实现。CodeGraph 需要追踪：
 *
 *   Controller.calculatePrice  --calls-->  TicketPriceCalculator.calculatePrice
 *     --implements-->  StandardPriceCalculator.calculatePrice
 *       或           WeekendPriceCalculator.calculatePrice
 *
 * 由于 Controller 字段声明为接口类型，Tree-sitter 只能提取到对接口方法的
 * 调用。CodeGraph 的 GraphTraverser 需要沿 implements 边找到具体实现类
 * 中的方法体，才能构建完整的调用链。
 */
public interface TicketPriceCalculator {

    /**
     * 根据车次信息计算票价（单位：分）。
     *
     * @param trip      车次信息
     * @param seatCount 购票张数
     * @return 总票价（分）
     */
    int calculatePrice(Trip trip, int seatCount);

    /**
     * 返回此计算器的策略名称（用于日志/注册表）。
     */
    String getStrategyName();
}
