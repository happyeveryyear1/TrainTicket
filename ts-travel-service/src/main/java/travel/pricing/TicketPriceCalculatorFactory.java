package travel.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import travel.entity.Trip;

/**
 * 场景 2：工厂模式 + 动态创建对象
 *
 * 静态工厂方法根据火车类型名称（"GaoTie" / "DongChe"）返回不同的
 * TicketPriceCalculator 实现。
 *
 * CodeGraph 盲区：
 *   - 工厂方法的返回值类型是 TicketPriceCalculator（接口）
 *   - 运行时实际返回 StandardPriceCalculator 或 WeekendPriceCalculator
 *   - Tree-sitter 只能看到对 TicketPriceCalculator 接口方法的调用
 *   - 无法静态确定 createCalculator 返回的是哪个具体实现
 *   - 调用点 service.processOrder(order) 的 type_of 边指向接口，而非具体实现
 *
 * 本质上这是一个"静态分析不可判定"的问题 —— 工厂的返回值类型
 * 依赖于运行时参数，无论 CodeGraph 还是任何静态分析工具都无法
 * 在编译期确定。
 */
public final class TicketPriceCalculatorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketPriceCalculatorFactory.class);

    private TicketPriceCalculatorFactory() {
        // 工具类，禁止实例化
    }

    /**
     * 工厂方法：根据火车类型名称创建对应的票价计算器。
     *
     * 演示静态分析的硬边界：CodeGraph 无法知道 type="GaoTie" 时
     * 返回 StandardPriceCalculator，type="DongChe" 时返回
     * WeekendPriceCalculator。所有后续的方法调用都只能解析到接口级别。
     *
     * @param trainType 火车类型名称（"GaoTie" / "DongChe" / ...）
     * @return 对应的票价计算器实现
     */
    public static TicketPriceCalculator createCalculator(String trainType) {
        LOGGER.info("[TicketPriceCalculatorFactory][Creating calculator for train type: {}]", trainType);
        if ("GaoTie".equalsIgnoreCase(trainType) || "GaoTieOne".equalsIgnoreCase(trainType)
                || "GaoTieTwo".equalsIgnoreCase(trainType)) {
            return new StandardPriceCalculator();    // Gap: 实际类型在调用点不可见
        } else if ("DongChe".equalsIgnoreCase(trainType) || "DongCheOne".equalsIgnoreCase(trainType)) {
            return new WeekendPriceCalculator();     // Gap: 同上
        }
        // 默认回退
        LOGGER.warn("[TicketPriceCalculatorFactory][Unknown train type: {}, falling back to standard]", trainType);
        return new StandardPriceCalculator();
    }
}
