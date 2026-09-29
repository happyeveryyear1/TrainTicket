package travel.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import travel.entity.Trip;

/**
 * 场景 3：泛型 + 泛型擦除 + 多态 —— Trip 仓库实现
 *
 * 继承 {@code BaseRepository<Trip>}，将泛型参数 T 绑定为 Trip。
 *
 * CodeGraph 分析链路验证：
 *   1. tree-sitter 提取 extends 边：TripRepository2 → BaseRepository ✅
 *   2. 但 T → Trip 的类型实参绑定 不生成边 ❌
 *   3. 调用 repo.findById(id) 时：
 *      - 方法定义在 BaseRepository 中，签名返回 Optional<T>
 *      - 调用点无法得知 T 在此上下文中是 Trip
 *      - 后续对返回值的 .get() 调用无法关联到 Trip 类型的方法
 *
 * 与 Spring Data CrudRepository 的对比：
 *   现有的 TripRepository extends CrudRepository<Trip, TripId> 面临同样问题。
 *   这是所有基于泛型的 DAO 模式的共同限制。
 */
@Repository
public class TripRepository2 extends BaseRepository<Trip> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TripRepository2.class);

    /**
     * 根据 routeId 查找车次列表 —— TripRepository2 特有的方法。
     * 此方法不在泛型基类 BaseRepository 中，调用方需要持有 TripRepository2
     * 类型的引用才能调用。
     */
    public java.util.List<Trip> findByRouteId(String routeId) {
        java.util.List<Trip> result = new java.util.ArrayList<>();
        for (Trip trip : store.values()) {
            if (trip.getRouteId() != null && trip.getRouteId().equals(routeId)) {
                result.add(trip);
            }
        }
        LOGGER.info("[TripRepository2][findByRouteId][routeId: {}, found: {}]", routeId, result.size());
        return result;
    }
}
