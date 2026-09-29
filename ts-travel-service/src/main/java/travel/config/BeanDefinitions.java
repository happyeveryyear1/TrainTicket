package travel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 演示 Gap 3.4（{@code @Bean} 方法返回类型引用不被支持）：
 *
 * 此 {@code @Configuration} 类通过 {@code @Bean} 工厂方法声明了两个 bean：
 * {@code DiscountCalculator} 和 {@code RouteCache}。
 *
 * CodeGraph 的 springResolver 的 {@code resolve()} 方法完全不了解
 * {@code @Bean} 方法的语义 —— 它不知道一个 {@code @Autowired DiscountCalculator}
 * 字段是由 {@code discountCalculator()} 工厂方法生成的。
 *
 * 解析器仅尝试将 DI 引用名称与五种硬编码模式匹配（*Service、*Repository 等），
 * 没有模式检查 @Bean 方法的返回类型。因此：
 * - 不存在从 {@code @Bean} 方法到其返回类型类的 "produces" 边
 * - 不存在从 {@code @Autowired DiscountCalculator} 使用点回溯到对应 @Bean 方法的边
 * - {@code @Bean} 方法节点上没有任何特殊标记指示 "这是一个 bean 定义"
 *
 * 运行时 Spring 能正常实例化并注入这些 bean，但 CodeGraph 的图无法表达
 * "@Bean 方法 → 被注入 bean" 这一关联。
 */
@Configuration
public class BeanDefinitions {

    private static final Logger LOGGER = LoggerFactory.getLogger(BeanDefinitions.class);

    // ---- @Bean 工厂方法 ----

    @Bean
    public DiscountCalculator discountCalculator() {
        LOGGER.info("[BeanDefinitions][Creating DiscountCalculator bean]");
        return new DiscountCalculator();
    }

    @Bean
    public RouteCache routeCache() {
        LOGGER.info("[BeanDefinitions][Creating RouteCache bean]");
        return new RouteCache();
    }

    // ---- 内部 POJO 类：非 Spring 原型，仅通过 @Bean 方法暴露为 bean ----

    /**
     * 折扣计算器 —— 纯 POJO，不包含 @Component/@Service 等 Spring 原型注解。
     * 仅通过 {@link #discountCalculator()} 上的 {@code @Bean} 注册到 IoC 容器。
     */
    public static class DiscountCalculator {

        /**
         * 根据 tripId 计算折扣百分比。
         *
         * @param tripId 车次 ID
         * @return 折扣百分比（0-100，0 表示无折扣）
         */
        public int calculateDiscount(String tripId) {
            // 存根：所有车次统一 10% 折扣
            return 10;
        }

        /**
         * 计算折后价格。
         *
         * @param originalPrice 原价
         * @param discountPercent 折扣百分比
         * @return 折后价格
         */
        public int applyDiscount(int originalPrice, int discountPercent) {
            return originalPrice * (100 - discountPercent) / 100;
        }
    }

    /**
     * 路由缓存 —— 纯 POJO，不包含 Spring 原型注解。
     * 仅通过 {@link #routeCache()} 上的 {@code @Bean} 注册到 IoC 容器。
     */
    public static class RouteCache {

        private final Map<String, String> cache = new HashMap<>();

        /**
         * 缓存 routeId → routeName 的映射。
         */
        public void put(String routeId, String routeName) {
            cache.put(routeId, routeName);
        }

        /**
         * 根据 routeId 查询缓存的 routeName。
         *
         * @return 缓存的名称，未命中则返回 null
         */
        public String get(String routeId) {
            return cache.get(routeId);
        }

        /** 清空全部缓存条目。 */
        public void clear() {
            cache.clear();
        }

        /** @return 当前缓存条目数 */
        public int size() {
            return cache.size();
        }
    }
}
