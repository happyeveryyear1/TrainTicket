package travel.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import travel.entity.Trip;

/**
 * 场景 4：动车车次处理器
 *
 * 此实例在 TripHandlerRegistry 初始化时被 put 到 Map 中：
 *   handlerMap.put("DongChe", new DongCheTripHandler());
 *   handlerMap.put("DongCheOne", new DongCheTripHandler());
 *
 * CodeGraph 无法从 Map<String, TripTypeHandler>.get(key) 调用中
 * 推断出此具体类型。
 */
public class DongCheTripHandler implements TripTypeHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DongCheTripHandler.class);

    static final int SPEED_KMH = 200;

    @Override
    public String handle(Trip trip) {
        String result = String.format("动车 %s (%s → %s) 最高时速 %d km/h",
                trip.getTrainTypeName(), trip.getStartStationName(),
                trip.getTerminalStationName(), SPEED_KMH);
        LOGGER.info("[DongCheTripHandler][{}]", result);
        return result;
    }

    @Override
    public String supportedType() {
        return "DongChe";
    }
}
