package travel.handler;

import travel.entity.Trip;

/**
 * 场景 4：通过 Map 持有对象 + 运行时动态分发
 *
 * 车次类型处理器接口。不同火车类型（高铁/动车/普快）有不同的处理逻辑。
 * 处理器实例存储在 TripHandlerRegistry 的 Map 中，通过 key（火车类型名称）
 * 在运行时动态查找。
 *
 * CodeGraph 盲区：
 *   1. 处理器实例存储在 Map<String, TripTypeHandler> 中
 *   2. 调用点 registry.getHandler("GaoTie").handle(trip) 的 dispatch key
 *      是运行时字符串，静态分析无法确定调用的是哪个具体实现
 *   3. Map.get() 返回值类型是 TripTypeHandler（接口），后续调用都只能
 *      解析到接口级别
 *   4. 即使 Map 的 put() 调用可以被追踪（key → handler 实例的静态映射），
 *      CodeGraph 目前也不做这种数据流分析
 *
 * 这是策略模式 + 注册表模式的经典组合 —— 它非常灵活，但对静态分析
 * 完全不透明。
 */
public interface TripTypeHandler {

    /**
     * 处理指定车次。具体逻辑由实现类决定（如价格计算、座位分配等）。
     *
     * @param trip 车次信息
     * @return 处理结果描述
     */
    String handle(Trip trip);

    /**
     * 返回此处理器支持的车次类型名称（与 Map 的 key 一致）。
     */
    String supportedType();
}
