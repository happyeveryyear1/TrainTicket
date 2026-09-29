package travel.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import travel.entity.Trip;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 场景 4：通过 Map 持有对象的处理器注册表
 *
 * 初始化时将所有 TripTypeHandler 实现放入 Map：
 *   "GaoTie"     → GaoTieTripHandler
 *   "GaoTieOne"  → GaoTieTripHandler
 *   "GaoTieTwo"  → GaoTieTripHandler
 *   "DongChe"    → DongCheTripHandler
 *   "DongCheOne" → DongCheTripHandler
 *
 * 使用方通过 {@code registry.getHandler(trip.getTrainTypeName()).handle(trip)}
 * 动态选择处理器。
 *
 * CodeGraph 盲区（三层叠加）：
 *   1. Map.get(key) 返回值类型是 TripTypeHandler（接口），无法确定具体实现
 *   2. dispatch key 是运行时字符串 trip.getTrainTypeName()，静态分析不可判定
 *   3. handle() 的调用 point-to 分析在 CodeGraph 中不做（数据流分析边界）
 *
 * 即使所有 put() 调用都在同一文件中可见（静态映射），CodeGraph 也不做
 * 跨调用的数据流追踪来连接 put → get → 方法调用。
 */
@Component
public class TripHandlerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(TripHandlerRegistry.class);

    private final Map<String, TripTypeHandler> handlerMap = new HashMap<>();

    @PostConstruct
    public void init() {
        GaoTieTripHandler gaoTie = new GaoTieTripHandler();
        handlerMap.put("GaoTie", gaoTie);     // Gap: put → get 不被 CodeGraph 追踪
        handlerMap.put("GaoTieOne", gaoTie);
        handlerMap.put("GaoTieTwo", gaoTie);

        DongCheTripHandler dongChe = new DongCheTripHandler();
        handlerMap.put("DongChe", dongChe);   // Gap: 同上
        handlerMap.put("DongCheOne", dongChe);

        LOGGER.info("[TripHandlerRegistry][Initialized with {} handlers]", handlerMap.size());
    }

    /**
     * 根据火车类型获取处理器。
     * 返回类型是 TripTypeHandler（接口）—— 调用方无法静态确定具体实现。
     */
    public TripTypeHandler getHandler(String trainType) {
        TripTypeHandler handler = handlerMap.getOrDefault(trainType, new GaoTieTripHandler());
        LOGGER.info("[TripHandlerRegistry][getHandler][type: {}, handler: {}]",
                trainType, handler.getClass().getSimpleName());
        return handler;
    }

    /**
     * 直接处理车次 —— 封装了 get + handle 两步。
     * CodeGraph 在此只能看到调用了 TripTypeHandler.handle()（接口方法），
     * 无法追踪到 GaoTieTripHandler.handle() 或 DongCheTripHandler.handle()。
     */
    public String processTrip(Trip trip) {
        TripTypeHandler handler = getHandler(trip.getTrainTypeName());
        return handler.handle(trip);          // Gap: 动态分发, 静态分析不可判定
    }
}
