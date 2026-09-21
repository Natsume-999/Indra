package com.indra.rpg.common.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 时长与日期工具（封禁时长解析、解封时间换算）。
 *
 * 移植自 Phoenix 的 common/TimeUtil.kt（v1.16.0），仅改包名。
 *
 * 单位符可由配置指定，默认：
 *   s 秒 / m 分 / H 时 / d 天 / w 周 / M 月 / y 年
 * 例："7d" → 604800，"1y" → 31536000
 */
object TimeUtil {

    /**
     * 时长字符串解析为秒。
     * 只接受「数字 + 单个单位」的形式；空串 / 格式不符一律返回 0（= 永久）。
     */
    fun String?.parseTime(
        second: String = "s",
        minute: String = "m",
        hour: String = "H",
        day: String = "d",
        week: String = "w",
        month: String = "M",
        year: String = "y"
    ): Long {
        return this?.takeIf { it.isNotBlank() }
            ?.let {
                val (value, unit) = Regex("^(\\d+)($second|$minute|$hour|$day|$week|$month|$year)$")
                    .matchEntire(it)?.destructured
                    ?: return 0
                val unitMultiplier = when (unit) {
                    second -> 1
                    minute -> 60
                    hour -> 3600
                    day -> 86400
                    week -> 86400 * 7
                    month -> 86400 * 30
                    year -> 86400 * 365
                    else -> throw IllegalArgumentException("无效的时间单位: $unit")
                }
                value.toLong() * unitMultiplier
            } ?: 0
    }

    fun LocalDate?.formatToString(pattern: String = "yyyy-MM-dd"): String =
        this?.format(DateTimeFormatter.ofPattern(pattern)) ?: ""

    fun LocalDateTime?.formatToString(pattern: String = "yyyy-MM-dd HH:mm:ss"): String =
        this?.format(DateTimeFormatter.ofPattern(pattern)) ?: ""

    fun String?.formatToLocalDate(pattern: String = "yyyy-MM-dd"): LocalDate? =
        this?.takeIf { it.isNotBlank() }
            ?.runCatching { LocalDate.parse(this, DateTimeFormatter.ofPattern(pattern)) }
            ?.getOrNull()

    fun String?.formatToLocalDateTime(pattern: String = "yyyy-MM-dd HH:mm:ss"): LocalDateTime? =
        this?.takeIf { it.isNotBlank() }
            ?.runCatching { LocalDateTime.parse(this, DateTimeFormatter.ofPattern(pattern)) }
            ?.getOrNull()
}
