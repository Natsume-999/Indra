// ============================================================================
//  离线校验用 TabooLib 桩（仅 tools/verify.sh 使用，不参与 Gradle 构建）
//  ---------------------------------------------------------------------------
//  ⚠️ 已通过 javap 对真实 taboolib database-6.3.0-a1d3953.jar 逐项核实：
//    · HostSQL(String, String, String, String, String)   ← 端口是 String 不是 Int
//    · HostSQLite(File)
//    · Host.createDataSource(): javax.sql.DataSource
//    · Table(String, Host<E>, Function1<Table<T,E>, Unit>)
//    · Table.select/update/delete/insert 首参均为 DataSource
//    · ColumnTypeSQL / ColumnTypeSQLite / ColumnOptionSQL / ColumnOptionSQLite 都是 enum
// ============================================================================

package taboolib.module.database

import java.io.File
import java.sql.ResultSet
import javax.sql.DataSource

// ── 主机 ────────────────────────────────────────────────────────────────────

open class Host<E>

class HostSQL(
    val host: String,
    val port: String,
    val user: String,
    val password: String,
    val database: String
) : Host<SQL>() {
    fun createDataSource(): DataSource = throw NotImplementedError("stub")
}

class HostSQLite(val file: File) : Host<SQLite>() {
    fun createDataSource(): DataSource = throw NotImplementedError("stub")
}

class SQL : ColumnBuilder()
class SQLite : ColumnBuilder()

// ── 列类型 / 列选项 ─────────────────────────────────────────────────────────

enum class ColumnTypeSQL {
    TINYINT, SMALLINT, MEDIUMINT, INT, BIGINT, FLOAT, DOUBLE, DECIMAL, BIT, SERIAL,
    BOOL, BOOLEAN, FIXED, CHAR, VARCHAR, TINYTEXT, TEXT, MEDIUMTEXT, LONGTEXT,
    TINYBLOB, BLOB, MEDIUMBLOB, LONGBLOB, BINARY, VARBINARY, DATE, TIME, DATETIME, TIMESTAMP
}

enum class ColumnTypeSQLite { NULL, INTEGER, REAL, TEXT, BLOB, NUMERIC }

enum class ColumnOptionSQL { AUTO_INCREMENT, ZEROFILL, UNSIGNED, NOTNULL, PRIMARY_KEY, UNIQUE_KEY, KEY }

enum class ColumnOptionSQLite { NOTNULL, UNIQUE, PRIMARY_KEY, AUTOINCREMENT }

// ── 列构建器（DSL 内部） ────────────────────────────────────────────────────

open class ColumnBuilder {
    /** DSL：`type(ColumnTypeSQL.VARCHAR, 64) { options(...); def(...) }` */
    fun type(type: Any, length: Int = 0, block: Column.() -> Unit = {}): Column = Column()
}

open class Column {
    fun options(vararg option: Any) {}
    fun def(value: Any) {}
}

class ColumnSQLite : Column()

// 让 enum 常量可像函数一样调用：ColumnTypeSQLite.TEXT { ... }
operator fun ColumnTypeSQLite.invoke(length: Int = 0, block: (ColumnSQLite) -> Unit = {}): ColumnSQLite {
    val c = ColumnSQLite()
    block(c)
    return c
}

operator fun ColumnTypeSQL.invoke(length: Int = 0, block: (Column) -> Unit = {}): Column {
    val c = Column()
    block(c)
    return c
}

// ── 表 ─────────────────────────────────────────────────────────────────────

class Table<T : Host<E>, E>(val name: String, val host: Host<E>, block: Table<T, E>.() -> Unit = {}) {

    fun add(name: String, block: E.() -> Unit): Table<T, E> = this
    fun createTable(dataSource: DataSource, check: Boolean = true) {}
    fun select(dataSource: DataSource, block: ActionSelect.() -> Unit): ResultProcessorList = ResultProcessorList()
    fun update(dataSource: DataSource, block: ActionUpdate.() -> Unit): Int = 0
    fun delete(dataSource: DataSource, block: ActionDelete.() -> Unit): Int = 0
    fun insert(dataSource: DataSource, vararg columns: String, block: ActionInsert.() -> Unit): Int = 0
}

// ── 动作 DSL ───────────────────────────────────────────────────────────────

open class ActionFilterable {
    fun where(block: Where.() -> Unit) {}
}

class Where {
    infix fun String.eq(value: Any) {}
}

class ActionSelect : ActionFilterable() {
    fun rows(vararg columns: String) {}
    fun distinct(vararg columns: String) {}
    /** @deprecated 已弃用，改用 [orderBy] */
    fun order(column: String, desc: Boolean = false) {}
    fun orderBy(column: String, type: Order.Type = Order.Type.ASC) {}

    /** `sum("money", "total") { }` */
    fun sum(column: String, alias: String, block: Any.() -> Unit = {}) {}
    fun limit(count: Int) {}
    fun offset(count: Int) {}
}

/** 排序方向（对应真实 TabooLib 的 `Order.Type`） */
object Order {
    enum class Type { ASC, DESC }
}

class ActionUpdate(val table: String) : ActionFilterable() {
    fun set(column: String, value: Any?) {}
}

class ActionDelete : ActionFilterable()

class ActionInsert {
    fun value(vararg values: Any?) {}
}

// ── 结果集 ─────────────────────────────────────────────────────────────────

/**
 * 查询结果列表。
 *
 * ⚠️ 真实 ResultProcessorList 的 `map` **带 ResultProcessor 接收者**
 *    （`fun <R> map(transform: ResultProcessor.() -> R): List<R>`），
 *    所以源码里写的是 `map { getInt("x") }` 而不是 `map { it.getInt("x") }`。
 *
 * ⚠️ 这里**刻意不实现 List/Iterable**：否则 Kotlin 标准库的
 *    `Iterable.map { it -> }` 会与带接收者的版本形成重载竞争，
 *    编译器优先选标准库版本，导致 `getInt` 无法解析（实测踩过）。
 */
class ResultProcessorList {

    fun <R> map(transform: ResultProcessor.() -> R): List<R> = emptyList()

    fun <R : Any> firstOrNull(): R? = null

    /** 带接收者的 firstOrNull —— 源码里写 `firstOrNull { getInt("x") }` */
    fun <R : Any> firstOrNull(transform: ResultProcessor.() -> R): R? = null

    /** `rows("MAX(id)")` 场景：`map { getLong(1) }.firstOrNull()` */
    fun count(): Long = 0L
}

@Suppress("unused")
fun <R> ResultProcessorList.flatMap(transform: ResultProcessor.() -> List<R>): List<R> = emptyList()

class ResultProcessor {
    fun getString(column: String): String? = null
    fun getString(index: Int): String? = null
    fun getInt(column: String): Int = 0
    fun getInt(index: Int): Int = 0
    fun getLong(column: String): Long = 0L
    fun getLong(index: Int): Long = 0L
    fun getDouble(column: String): Double = 0.0
    fun getDouble(index: Int): Double = 0.0
}

// ── 扩展：File / ConfigurationSection → Host ────────────────────────────────

fun java.io.File.getHost(): HostSQLite = HostSQLite(this)
