package travel.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import travel.entity.Trip;

/**
 * 场景 4：高铁车次处理器
 *
 * 此实例在 TripHandlerRegistry 初始化时被 put 到 Map 中：
 *   handlerMap.put("GaoTie", new GaoTieTripHandler());
 *   handlerMap.put("GaoTieOne", new GaoTieTripHandler());
 *   handlerMap.put("GaoTieTwo", new GaoTieTripHandler());
 *
 * 调用点 registry.getHandler("GaoTie").handle(trip) 的 dispatch key
 * 为运行时字符串，CodeGraph 无法从静态源码确定最终调用的 handler。
 */
public class GaoTieTripHandler implements TripTypeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GaoTieTripHandler.class);

    static final int SPEED_KMH = 300;

    @Override
    public String handle(Trip trip) {
        String result = String.format("高铁 %s (%s → %s) 最高时速 %d km/h",
                trip.getTrainTypeName(), trip.getStartStationName(),
                trip.getTerminalStationName(), SPEED_KMH);
        LOGGER.info("[GaoTieTripHandler][{}]", result);
        return result;
    }

    @Override
    public String supportedType() {
        return "GaoTie";
    }
}
