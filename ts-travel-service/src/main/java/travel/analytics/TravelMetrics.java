package travel.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 演示 Gap 3.1（DI 命名约定硬编码）：
 *
 * 此 {@code @Component} 的类名为 {@code TravelMetrics}，属于 PascalCase，
 * 因此 CodeGraph 的 springResolver 会命中模式 4（PascalCase → ENTITY_DIRS），
 * 仅获得 0.70 的置信度，并优先在 /entity/、/model/、/domain/ 目录中查找 ——
 * 但该类实际位于 /analytics/ 目录下，不在任何首选目录列表中。
 *
 * 运行时 Spring 能正常注入它，但 CodeGraph 的 DI 解析会将其当作一个
 * Entity/Model 处理，目录语义完全错配。
 */
@Component
public class TravelMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(TravelMetrics.class);

    private int tripCreateCount = 0;
    private int tripQueryCount = 0;

    public void recordTripCreated() {
        tripCreateCount++;
        LOGGER.info("[TravelMetrics][Trip created][total: {}]", tripCreateCount);
    }

    public void recordTripQueried() {
        tripQueryCount++;
        LOGGER.info("[TravelMetrics][Trip queried][total: {}]", tripQueryCount);
    }

    public int getTripCreateCount() {
        return tripCreateCount;
    }

    public int getTripQueryCount() {
        return tripQueryCount;
    }
}
