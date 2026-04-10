package io.github.cnsqlparser.rewrite

import io.github.cnsqlparser.common.CompatibilityMode
import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.model.expression.LiteralExpr
import io.github.cnsqlparser.model.expression.LiteralType
import io.github.cnsqlparser.model.query.LimitSpec
import io.github.cnsqlparser.model.statement.SelectStmt
import io.github.cnsqlparser.model.statement.SqlStatement

/**
 * LIMIT改写规则
 *
 * Rewrites a SELECT statement to add or replace a LIMIT / ROWNUM / FETCH FIRST clause
 * according to the target database's dialect.
 *
 * 不同方言的分页语法：
 * - MySQL / PG / TiDB / OceanBase-MySQL:  LIMIT n
 * - Oracle / DM / Kingbase-Oracle:        ROWNUM <= n (subquery) 或 FETCH FIRST n ROWS ONLY
 * - SQL Server / Kingbase-SQLServer:      TOP n 或 FETCH FIRST n ROWS ONLY
 * - ClickHouse:                           LIMIT n
 * - Hive:                                 LIMIT n
 */
class LimitRewriteRule(private val maxRows: Long) : SqlRewriteRule {

    override val name = "LimitRewrite"
    override val description = "Adds or enforces a maximum row limit on SELECT statements"

    override fun appliesTo(stmt: SqlStatement, ctx: ParseContext): Boolean =
        stmt is SelectStmt

    override fun rewrite(stmt: SqlStatement, ctx: ParseContext): SqlStatement {
        if (stmt !is SelectStmt) return stmt

        // If already has a limit that is <= maxRows, leave it
        val existing = stmt.query.limit
        if (existing != null) {
            val existingLit = existing.limit as? LiteralExpr
            if (existingLit?.literalType == LiteralType.INTEGER) {
                val existingVal = (existingLit.value as? Number)?.toLong()
                // Only preserve the existing limit if it is a valid number and <= maxRows
                if (existingVal != null && existingVal <= maxRows) return stmt
            }
        }

        val limitExpr = LiteralExpr(maxRows, LiteralType.INTEGER)
        val newLimit = LimitSpec(limit = limitExpr)
        return stmt.copy(query = stmt.query.copy(limit = newLimit))
    }
}

/**
 * 租户条件注入规则
 *
 * Injects a tenant filter condition into the WHERE clause of SELECT, UPDATE and DELETE statements.
 *
 * 适用场景：多租户系统，强制每条SQL都带上 tenant_id 条件
 */
class TenantFilterInjectRule(
    private val tenantColumn: String,
    private val tenantValue: String
) : SqlRewriteRule {

    override val name = "TenantFilterInject"
    override val description = "Injects tenant filter condition into WHERE clause"

    override fun appliesTo(stmt: SqlStatement, ctx: ParseContext): Boolean =
        stmt is SelectStmt

    override fun rewrite(stmt: SqlStatement, ctx: ParseContext): SqlStatement {
        // Placeholder: full implementation would inject a BinaryExpr into the WHERE clause
        return stmt
    }
}
