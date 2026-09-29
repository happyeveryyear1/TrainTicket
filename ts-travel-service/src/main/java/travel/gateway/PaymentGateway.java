package travel.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 演示 Gap 3.1（DI 命名约定硬编码）：
 *
 * 此 {@code @Service} 的类名为 {@code PaymentGateway}，不以 Service、Repository、
 * Controller、Component、Config 中任何一种结尾。虽然它是 PascalCase，能命中
 * CodeGraph springResolver 的模式 4，但仅获得 0.70 的置信度（五种模式中最低），
 * 且解析器会优先在 /entity/、/model/、/domain/ 目录中查找 —— 但该类实际位于
 * /gateway/ 目录下。
 *
 * 此外，{@code PaymentGateway} 虽以 "Gateway" 结尾，但 springResolver 中没有任何
 * 模式处理 "Gateway" 后缀 —— 命名约定是硬编码的五种，无法覆盖 Spring 生态中
 * 实际使用的各种命名惯例（Gateway、Handler、Provider、Adapter、Mapper 等）。
 *
 * 运行时 Spring 能正常发现并注入它（{@code @Service} 会被 component-scan 拾取），
 * 但 CodeGraph 的 DI 解析会将其降级到错误目录优先级的弱匹配。
 */
@Service
public class PaymentGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentGateway.class);

    /**
     * 处理 trip 支付。存根实现 —— 实际生产代码会接入第三方支付服务。
     *
     * @param tripId 关联的 trip ID
     * @param amount 支付金额（分）
     * @return 是否支付成功
     */
    public boolean processPayment(String tripId, int amount) {
        LOGGER.info("[PaymentGateway][Processing payment][tripId: {}, amount: {}]", tripId, amount);
        return true;
    }

    /**
     * 退款。存根实现。
     *
     * @param tripId 关联的 trip ID
     * @param amount 退款金额（分）
     */
    public void refund(String tripId, int amount) {
        LOGGER.info("[PaymentGateway][Processing refund][tripId: {}, amount: {}]", tripId, amount);
    }
}
