package travel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import travel.analytics.TravelMetrics;

/**
 * 演示：{@code super()} 和 {@code this()} 显式构造器调用的 CodeGraph 盲区
 *
 * 此类包含三个构造器，形成一条构造器链：
 *
 *  ① TravelServiceImpl()                          → 隐式 super()
 *       ↓ super(metrics)
 *  ② AbstractTravelService(TravelMetrics)          → this(metrics, "default")
 *       ↓ this(metrics, name)
 *  ③ AbstractTravelService(TravelMetrics, String)  → 实际的初始化逻辑
 *
 * tree-sitter-java 将 {@code super(metrics)} 和 {@code this(metrics, "default")}
 * 解析为 {@code explicit_constructor_invocation} 节点，但 CodeGraph 的
 * {@code callTypes} 中仅包含 {@code method_invocation}，完全不处理
 * {@code explicit_constructor_invocation}。
 *
 * 这意味着：
 * - TravelServiceImpl 构造器 → AbstractTravelService(TravelMetrics) 的 super() 边 丢失
 * - AbstractTravelService(TravelMetrics) → AbstractTravelService(TravelMetrics, String)
 *   的 this() 边 丢失
 * - 构造器链在图中完全不可见，getCallers/getCallees 都无法穿越构造器边界
 *
 * 这对于 Spring DI 的构造器注入场景尤其关键：Spring 在实例化 bean 时沿着构造器链
 * 依次调用的路径，在 CodeGraph 中不存在任何图边。
 */
public abstract class AbstractTravelService {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /** 所有子类共享的指标收集器 */
    protected final TravelMetrics metrics;

    /** 服务实例名称 */
    protected final String serviceName;

    /*
     * 构造器链的入口：子类通过 super(metrics) 调用此构造器。
     * 此构造器再通过 this(metrics, "default") 委托给第三个构造器。
     *
     * CodeGraph 盲区：this(metrics, "default") 是 explicit_constructor_invocation，
     * CodeGraph 不生成任何边指向第三个构造器。
     */
    public AbstractTravelService(TravelMetrics metrics) {
        this(metrics, "default");  // Gap: this() 调用不被 CodeGraph 捕获
        logger.info("[AbstractTravelService][Constructor chaining][this(metrics, \"default\") called]");
    }

    /*
     * 构造器链的终点：实际初始化逻辑所在。
     * 子类的 super(metrics) 调用应通过构造器链最终到达这里，
     * 但 CodeGraph 无法表示这一调用路径。
     */
    public AbstractTravelService(TravelMetrics metrics, String serviceName) {
        this.metrics = metrics;
        this.serviceName = serviceName;
        logger.info("[AbstractTravelService][Root constructor][serviceName: {}]", serviceName);
    }

    /** 子类可覆盖的初始化钩子。空实现，供子类选择性覆盖。 */
    protected void init() {
        // 钩子：子类可覆盖以添加额外的初始化逻辑
    }
}
