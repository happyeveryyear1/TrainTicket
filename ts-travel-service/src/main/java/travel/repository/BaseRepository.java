package travel.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 场景 3：泛型 + 泛型擦除 + 多态
 *
 * 泛型仓库基类 —— 封装了 findById / findAll / save 三个通用操作。
 * 子类继承时指定具体实体类型：{@code TripRepository2 extends BaseRepository<Trip>}。
 *
 * CodeGraph 的泛型处理限制：
 *   1. Tree-sitter 提取的是擦除后的类型签名 —— {@code BaseRepository<T>} 中的
 *      T 在 AST 中只是 type_identifier "T"，没有与 Trip 的关联边
 *   2. 调用 {@code repo.findById(id)} 时，返回值类型是 T（擦除后为 Object），
 *      CodeGraph 无法将其精确关联到 Trip 类型
 *   3. 子类 {@code TripRepository2 extends BaseRepository<Trip>} 的 extends 边
 *      指向 BaseRepository，但 T → Trip 的类型实参关系不生成边
 *   4. 调用链 {@code service → TripRepository2.findById} 穿越泛型基类时，
 *      中间层 BaseRepository.findById 的方法签名带泛型参数，GraphTraverser
 *      不会将其视为 TripRepository2.findById 的调用者
 *
 * 这是 Java 泛型擦除的根本性限制 —— 运行时不存在类型实参信息。
 * CodeGraph 基于 AST/源码做静态分析，同样面临此限制。
 */
public abstract class BaseRepository<T> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /** 内部存储（模拟数据库，实际为 HashMap） */
    protected final java.util.Map<String, T> store = new java.util.HashMap<>();

    /**
     * 根据 ID 查找实体。泛型返回值 —— CodeGraph 不知道 T 在此上下文中是 Trip。
     *
     * @param id 实体 ID
     * @return 包含实体的 Optional
     */
    public Optional<T> findById(String id) {
        T entity = store.get(id);
        logger.info("[BaseRepository][findById][id: {}, found: {}]", id, entity != null);
        return Optional.ofNullable(entity);
    }

    /**
     * 查找全部实体。
     * @return 实体列表（泛型 T）
     */
    public List<T> findAll() {
        List<T> result = new ArrayList<>(store.values());
        logger.info("[BaseRepository][findAll][count: {}]", result.size());
        return result;
    }

    /**
     * 保存实体。
     * @param id     实体 ID
     * @param entity 实体对象（泛型 T）
     */
    public void save(String id, T entity) {
        store.put(id, entity);
        logger.info("[BaseRepository][save][id: {}, entity: {}]", id, entity);
    }
}
