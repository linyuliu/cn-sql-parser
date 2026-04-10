package io.github.cnsqlparser.model.expression

import io.github.cnsqlparser.common.ParsePosition

/**
 * 统一SQL模型 - 第三层：表达式层
 *
 * Expression Layer of the Unified SQL Model.
 * Covers all expression types encountered in SQL predicates, projections,
 * function arguments, CASE expressions, and sub-queries.
 */
sealed class SqlExpression {
    abstract val position: ParsePosition?
}

/** 列引用 e.g. `t.col`, `schema.table.col` */
data class ColumnRef(
    val name: String,
    val table: String? = null,
    val schema: String? = null,
    override val position: ParsePosition? = null
) : SqlExpression()

/** 字面量（字符串、数字、布尔、NULL） */
data class LiteralExpr(
    val value: Any?,
    val literalType: LiteralType,
    override val position: ParsePosition? = null
) : SqlExpression()

enum class LiteralType { STRING, INTEGER, DECIMAL, BOOLEAN, NULL, DATE, TIMESTAMP }

/** 二元表达式 e.g. `a + b`, `x = y`, `a AND b` */
data class BinaryExpr(
    val left: SqlExpression,
    val operator: String,
    val right: SqlExpression,
    override val position: ParsePosition? = null
) : SqlExpression()

/** 一元表达式 e.g. `NOT x`, `-1`, `IS NULL` */
data class UnaryExpr(
    val operator: String,
    val operand: SqlExpression,
    val prefix: Boolean = true,
    override val position: ParsePosition? = null
) : SqlExpression()

/** 函数调用 e.g. `COUNT(*)`, `SUBSTR(col, 1, 3)` */
data class FunctionCall(
    val name: String,
    val schema: String? = null,
    val arguments: List<SqlExpression> = emptyList(),
    val distinct: Boolean = false,
    val star: Boolean = false,
    val orderBy: List<OrderByItem> = emptyList(),
    val filter: SqlExpression? = null,
    override val position: ParsePosition? = null
) : SqlExpression()

/** ORDER BY 项（用于窗口函数、聚合ORDER BY） */
data class OrderByItem(
    val expr: SqlExpression,
    val ascending: Boolean = true,
    val nullsFirst: Boolean? = null
)

/** CASE 表达式 */
data class CaseExpr(
    val operand: SqlExpression? = null,
    val whenClauses: List<WhenClause>,
    val elseExpr: SqlExpression? = null,
    override val position: ParsePosition? = null
) : SqlExpression()

data class WhenClause(val condition: SqlExpression, val result: SqlExpression)

/** CAST 表达式 e.g. `CAST(col AS VARCHAR(100))` */
data class CastExpr(
    val expression: SqlExpression,
    val targetType: String,
    val typeParams: List<String> = emptyList(),
    override val position: ParsePosition? = null
) : SqlExpression()

/** BETWEEN 表达式 e.g. `x BETWEEN 1 AND 10` */
data class BetweenExpr(
    val expression: SqlExpression,
    val lower: SqlExpression,
    val upper: SqlExpression,
    val not: Boolean = false,
    override val position: ParsePosition? = null
) : SqlExpression()

/** IN 表达式 e.g. `x IN (1, 2, 3)` or `x IN (subquery)` */
data class InExpr(
    val expression: SqlExpression,
    val values: List<SqlExpression> = emptyList(),
    val subquery: SubqueryExpr? = null,
    val not: Boolean = false,
    override val position: ParsePosition? = null
) : SqlExpression()

/** EXISTS 表达式 e.g. `EXISTS (SELECT ...)` */
data class ExistsExpr(
    val subquery: SubqueryExpr,
    val not: Boolean = false,
    override val position: ParsePosition? = null
) : SqlExpression()

/** 子查询表达式 e.g. `(SELECT ...)` */
data class SubqueryExpr(
    val query: io.github.cnsqlparser.model.query.QueryBlock,
    override val position: ParsePosition? = null
) : SqlExpression()

/** 窗口函数表达式 e.g. `ROW_NUMBER() OVER (PARTITION BY ...)` */
data class WindowFunctionExpr(
    val function: FunctionCall,
    val windowSpec: io.github.cnsqlparser.model.query.WindowSpec,
    override val position: ParsePosition? = null
) : SqlExpression()

/** 星号 * */
data class StarExpr(
    val table: String? = null,
    override val position: ParsePosition? = null
) : SqlExpression()

/** 带括号的表达式 */
data class ParenExpr(
    val expression: SqlExpression,
    override val position: ParsePosition? = null
) : SqlExpression()

/** 原始/未解析的表达式片段（容错用） */
data class RawExpr(
    val rawText: String,
    override val position: ParsePosition? = null
) : SqlExpression()
