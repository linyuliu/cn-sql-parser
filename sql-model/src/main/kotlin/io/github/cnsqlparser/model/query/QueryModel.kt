package io.github.cnsqlparser.model.query

import io.github.cnsqlparser.common.ParsePosition
import io.github.cnsqlparser.model.expression.SqlExpression

/**
 * 统一SQL模型 - 第二层：Query层
 *
 * Query Layer of the Unified SQL Model.
 * Covers all clauses and sub-structures of a SELECT query.
 */

/**
 * 查询块 - 表示一个完整的SELECT查询（含子查询/CTE/UNION）
 */
data class QueryBlock(
    /** WITH (CTE) 子句 */
    val ctes: List<CteSpec> = emptyList(),
    /** SELECT项目列表 */
    val selectItems: List<SelectItem> = emptyList(),
    /** FROM 子句 */
    val fromItems: List<FromItem> = emptyList(),
    /** WHERE 条件 */
    val where: SqlExpression? = null,
    /** GROUP BY */
    val groupBy: List<SqlExpression> = emptyList(),
    /** HAVING */
    val having: SqlExpression? = null,
    /** ORDER BY */
    val orderBy: List<OrderItem> = emptyList(),
    /** LIMIT / FETCH FIRST (MySQL/PG/标准SQL) */
    val limit: LimitSpec? = null,
    /** OFFSET */
    val offset: SqlExpression? = null,
    /** FETCH FIRST (标准SQL, Oracle 12c+, DB2) */
    val fetch: FetchSpec? = null,
    /** UNION / INTERSECT / EXCEPT */
    val setOp: SetOperation? = null,
    /** DISTINCT */
    val distinct: Boolean = false,
    val position: ParsePosition? = null
)

/** SELECT 项 */
data class SelectItem(
    val expression: SqlExpression,
    val alias: String? = null,
    val position: ParsePosition? = null
)

/**
 * FROM 子句中的数据源
 */
sealed class FromItem {
    abstract val alias: String?
    abstract val position: ParsePosition?
}

/** 表引用 */
data class TableFromItem(
    val name: String,
    val schema: String? = null,
    val catalog: String? = null,
    override val alias: String? = null,
    override val position: ParsePosition? = null
) : FromItem() {
    val qualifiedName: String
        get() = listOfNotNull(catalog, schema, name).joinToString(".")
}

/** 子查询 FROM (SELECT ...) AS t */
data class SubqueryFromItem(
    val query: QueryBlock,
    override val alias: String? = null,
    override val position: ParsePosition? = null
) : FromItem()

/** JOIN */
data class JoinFromItem(
    val left: FromItem,
    val right: FromItem,
    val joinType: JoinType,
    val condition: JoinSpec? = null,
    override val alias: String? = null,
    override val position: ParsePosition? = null
) : FromItem()

enum class JoinType {
    INNER, LEFT, RIGHT, FULL, CROSS, NATURAL,
    LEFT_SEMI, RIGHT_SEMI, LEFT_ANTI, RIGHT_ANTI  // Hive/大数据扩展
}

/** JOIN 条件（ON / USING） */
sealed class JoinSpec
data class OnJoinSpec(val condition: SqlExpression) : JoinSpec()
data class UsingJoinSpec(val columns: List<String>) : JoinSpec()

/** CTE (Common Table Expression) WITH子句 */
data class CteSpec(
    val name: String,
    val columns: List<String> = emptyList(),
    val query: QueryBlock,
    val materialized: Boolean? = null,
    val position: ParsePosition? = null
)

/** ORDER BY 项 */
data class OrderItem(
    val expression: SqlExpression,
    val ascending: Boolean = true,
    val nullsFirst: Boolean? = null,
    val position: ParsePosition? = null
)

/** LIMIT 子句 (MySQL/PG/SQLite风格) */
data class LimitSpec(
    val limit: SqlExpression,
    val offset: SqlExpression? = null,
    val position: ParsePosition? = null
)

/** FETCH FIRST 子句 (标准SQL / Oracle 12c+) */
data class FetchSpec(
    val count: SqlExpression,
    val percent: Boolean = false,
    val withTies: Boolean = false,
    val position: ParsePosition? = null
)

/** WINDOW 规范（窗口函数） */
data class WindowSpec(
    val name: String? = null,
    val partitionBy: List<SqlExpression> = emptyList(),
    val orderBy: List<OrderItem> = emptyList(),
    val frame: WindowFrame? = null,
    val position: ParsePosition? = null
)

/** 窗口帧（ROWS/RANGE BETWEEN ...） */
data class WindowFrame(
    val type: WindowFrameType,
    val start: WindowFrameBound,
    val end: WindowFrameBound? = null
)

enum class WindowFrameType { ROWS, RANGE, GROUPS }

sealed class WindowFrameBound
object UnboundedPreceding : WindowFrameBound()
object CurrentRow : WindowFrameBound()
object UnboundedFollowing : WindowFrameBound()
data class PrecedingOffset(val offset: SqlExpression) : WindowFrameBound()
data class FollowingOffset(val offset: SqlExpression) : WindowFrameBound()

/** UNION / INTERSECT / EXCEPT */
data class SetOperation(
    val type: SetOpType,
    val all: Boolean = false,
    val right: QueryBlock,
    val position: ParsePosition? = null
)

enum class SetOpType { UNION, INTERSECT, EXCEPT }
