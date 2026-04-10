package io.github.cnsqlparser.model.dialect

import io.github.cnsqlparser.common.ParsePosition
import io.github.cnsqlparser.common.ProductDialect

/**
 * 统一SQL模型 - 第四层：方言扩展层
 *
 * Dialect Extension Layer of the Unified SQL Model.
 * This layer is a first-class citizen — every dialect-specific construct (hints,
 * vendor types, mode-specific syntax, etc.) must have a home here.
 *
 * 为什么需要这一层：
 * - Oracle hint: `/*+ INDEX(t idx) */`
 * - MySQL index hint: `USE INDEX (idx)`
 * - SQL Server TOP: `SELECT TOP 10 *`
 * - ClickHouse FINAL/SAMPLE
 * - Kingbase / OceanBase mode-specific syntax
 * 如果没有这层，这些都无处安放。
 */
sealed class DialectAttribute {
    abstract val position: ParsePosition?
}

/**
 * 通用Hint节点（Oracle `/*+ ... */`, MySQL `/*+ ... */`）
 */
data class HintNode(
    val hintText: String,
    val dialect: ProductDialect? = null,
    override val position: ParsePosition? = null
) : DialectAttribute()

/**
 * 厂商专属类型引用（如 Oracle NUMBER, MySQL TINYINT UNSIGNED, PG SERIAL）
 */
data class VendorTypeRef(
    val typeName: String,
    val typeParams: List<String> = emptyList(),
    val dialect: ProductDialect? = null,
    override val position: ParsePosition? = null
) : DialectAttribute()

/**
 * 模式特定节点（存放无法归入通用模型的方言特性）
 *
 * e.g.:
 * - Kingbase ROWNUM (Oracle-mode)
 * - OceanBase PARALLEL hint
 * - ClickHouse SAMPLE BY
 */
data class ModeSpecificNode(
    val feature: String,
    val rawText: String,
    val dialect: ProductDialect? = null,
    override val position: ParsePosition? = null
) : DialectAttribute()

/**
 * SQL Server TOP 子句
 */
data class SqlServerTopClause(
    val count: Int,
    val percent: Boolean = false,
    val withTies: Boolean = false,
    override val position: ParsePosition? = null
) : DialectAttribute()

/**
 * MySQL / PG 索引 Hint（USE INDEX / FORCE INDEX / IGNORE INDEX）
 */
data class IndexHintNode(
    val hintType: IndexHintType,
    val indexNames: List<String>,
    override val position: ParsePosition? = null
) : DialectAttribute()

enum class IndexHintType { USE, FORCE, IGNORE }
