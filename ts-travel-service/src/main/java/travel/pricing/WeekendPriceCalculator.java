package travel.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import travel.entity.Trip;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 场景 1：接口 + 多态 + 依赖注入 —— 周末溢价票价实现
 *
 * 此实现标注了 {@code @Component("weekend")}，通过 {@code @Qualifier("weekend")}
 * 显式注入。
 *
 * CodeGraph 的 Spring 框架解析器完全忽略 {@code @Qualifier} 值，
 * 因此无法区分此实现与 StandardPriceCalculator —— 两者都匹配
 * PascalCase 模式 4（0.70 置信度），解析器随机选择或回退到模糊匹配。
 */
@Component("weekend")
public class WeekendPriceCalculator implements TicketPriceCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeekendPriceCalculator.class);

    /** 标准每座票价（分） */
    private static final int BASE_PRICE_PER_SEAT = 5000;
    /** 周末溢价系数 */
    private static final double WEEKEND_SURCHARGE = 1.2;

    @Override
    public int calculatePrice(Trip trip, int seatCount) {
        int basePrice = BASE_PRICE_PER_SEAT * seatCount;
        boolean isWeekend = isWeekendTrip(trip);
        int price = isWeekend ? (int)(basePrice * WEEKEND_SURCHARGE) : basePrice;
        LOGGER.info("[WeekendPriceCalculator][Trip: {}, seats: {}, weekend: {}, total: {} cents]",
                trip.getTripId(), seatCount, isWeekend, price);
        return price;
    }

    /**
     * 判断车次出发时间是否为周末。存根实现 —— 始终返回 true 用于演示。
     */
    private boolean isWeekendTrip(Trip trip) {
        // 实际实现会解析 trip.getStartTime() 并判断星期几
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    @Override
    public String getStrategyName() {
        return "weekend";
    }
}
