// ============================================================================
//  离线校验用 TabooLib 桩（仅 tools/verify.sh 使用，不参与 Gradle 构建）
//  ---------------------------------------------------------------------------
//  ⚠️ PTC Object 注解在真实 TabooLib 6.3 里位于 `taboolib.expansion` 包，
//     **不是** taboolib.module.database。这里保持一致，否则源码里的
//     `import taboolib.expansion.Id` 在离线校验时会解析失败。
// ============================================================================

package taboolib.expansion

import taboolib.module.database.ColumnTypeSQL

/** 主键（自增） */
annotation class Id

/** 普通索引 */
annotation class Key

/** 唯一键 */
annotation class UniqueKey

/** 非空 */
annotation class NotNull

/** 字段长度 */
annotation class Length(val value: Int)

/** 列名别名 */
annotation class Alias(val value: String)

/** 表名 */
annotation class TableName(val value: String, val schema: String = "")

/** 列类型覆盖：`@ColumnType(sql = ColumnTypeSQL.BIGINT)` */
annotation class ColumnType(
    val sql: ColumnTypeSQL = ColumnTypeSQL.VARCHAR,
    val sqlite: ColumnTypeSQL = ColumnTypeSQL.VARCHAR,
    val length: Int = 0
)

/** 忽略该字段（不建列） */
annotation class Transient
