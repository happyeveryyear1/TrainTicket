package travel.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import travel.entity.Trip;

/**
 * 场景 1：接口 + 多态 + 依赖注入 —— 标准票价实现
 *
 * 此实现标注了 {@code @Primary}，当没有 {@code @Qualifier} 指定时，
 * Spring 默认注入此实现。
 *
 * CodeGraph 分析链路验证：
 *   Controller --calls--> TicketPriceCalculator.calculatePrice (接口方法)
 *              --implements--> StandardPriceCalculator.calculatePrice
 *
 * 关键问题：Controller 字段类型是 TicketPriceCalculator（接口），
 * CodeGraph 能否正确穿越 implements 边找到 StandardPriceCalculator 的方法体？
 */
@Component
@Primary
public class StandardPriceCalculator implements TicketPriceCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(StandardPriceCalculator.class);

    /** 标准每座票价（分） */
    private static final int BASE_PRICE_PER_SEAT = 5000;

    @Override
    public int calculatePrice(Trip trip, int seatCount) {
        int price = BASE_PRICE_PER_SEAT * seatCount;
        LOGGER.info("[StandardPriceCalculator][Trip: {}, seats: {}, total: {} cents]",
                trip.getTripId(), seatCount, price);
        return price;
    }

    @Override
    public String getStrategyName() {
        return "standard";
    }
}
