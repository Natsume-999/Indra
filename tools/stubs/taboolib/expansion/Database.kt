// ============================================================================
//  离线校验用 TabooLib 桩（仅 tools/verify.sh 使用，不参与 Gradle 构建）
//  ---------------------------------------------------------------------------
//  ⚠️ 本文件刻意与 **真实 TabooLib 6.3 API 对齐**（已通过 javap 反编译核实）。
//     与源码不同步会导致 verify.sh 报假错，所以改动源码前先改这里。
//
//  真实 API 关键事实：
//    · PTC 注解全部在 `taboolib.expansion` 包，**不是** taboolib.module.database
//    · `findOneByKey<T>(key)` 支持「显式类型参数 + 位置参数」两种调用形式
//    · `sortDescendingPage<T>(field, page, size) { }` 是独立函数，返回 List<T>
//    · `upsert(listOf(x as Any))` / `insert(...)` 形参是 List<Any>
//    · Container.init() 会自动建表，无需手写 DDL
// ============================================================================

package taboolib.expansion

// ── 主机构建 ────────────────────────────────────────────────────────────────
// ⚠️ HostSQL / HostSQLite / getHost 的桩已移到 taboolib/module/database/DatabaseDsl.kt
//    （真实包是 taboolib.module.database，之前放在 expansion 是错的）

/**
 * 数据源描述。
 * `db(name, node, file)` 读 `<node>.enable`：
 *   enable=true  → ConfigurationSection（MySQL）
 *   enable=false → File（SQLite）
 */
fun db(name: String, node: String, file: String): Any = java.io.File(file)

// ── 容器 ────────────────────────────────────────────────────────────────────

class PersistentContainer {
    /** 注册表映射；`new<T>()` / `new<T>("name")` */
    fun <T : Any> new(): Unit = Unit
    fun <T : Any> new(name: String): Unit = Unit
    fun <T : Any> get(): ContainerOperator<T> = ContainerOperator()
    fun <T : Any> operator(name: String): ContainerOperator<T> = ContainerOperator()
    fun transaction(block: TransactionContext.() -> Unit): Unit = Unit
    fun close(): Unit = Unit
}

fun persistentContainer(source: Any, block: PersistentContainer.() -> Unit): PersistentContainer =
    PersistentContainer().apply(block)

class ContainerOperator<T : Any> {
    // 两种调用形式都要支持：findOneByKey<T>(key) 和 findOneByKey(key)
    fun <R : Any> findOneByKey(key: Any): R? = null
    fun <R : Any> findByKey(data: Any, usePrimaryKey: Boolean = false): R? = null

    /** `upsert(listOf(x as Any))` */
    fun upsert(data: List<Any>): Unit = Unit
    /** `insert(listOf(x as Any))` */
    fun insert(data: List<Any>): Unit = Unit
    fun delete(data: Any): Unit = Unit

    fun <R : Any> selectAll(): List<R> = emptyList()
    fun <R : Any> sortDescendingPage(field: String, page: Int, size: Int, block: () -> Unit = {}): List<R> =
        emptyList()

    fun count(): Long = 0L
    fun close(): Unit = Unit
}

class TransactionContext {
    fun <T : Any> get(): ContainerOperator<T> = ContainerOperator()
    fun <T : Any> operator(name: String): ContainerOperator<T> = ContainerOperator()
    fun rollback(): Unit = Unit
    fun rollbackNow(message: String): Unit = Unit
    fun join(block: () -> Unit): Unit = Unit
}
