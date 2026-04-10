package io.github.cnsqlparser.converter.generator

import io.github.cnsqlparser.common.DialectFamily
import io.github.cnsqlparser.model.expression.LiteralExpr
import io.github.cnsqlparser.model.expression.LiteralType
import io.github.cnsqlparser.model.query.QueryBlock
import io.github.cnsqlparser.model.statement.*

/**
 * SQL Server方言SQL生成器
 *
 * SQL Server-specific SQL generator.
 * - Square bracket quoting for identifiers
 * - TOP / OFFSET-FETCH pagination
 * - IDENTITY for auto-increment
 * - NVARCHAR/NCHAR types
 * - No LIMIT keyword
 */
class SqlServerGenerator(sourceFamily: DialectFamily? = null) :
    BaseSqlGenerator(sourceFamily, DialectFamily.SQLSERVER) {

    override fun quoteIdentifier(name: String): String {
        if (name == "*" || (name.startsWith("[") && name.endsWith("]"))) return name
        return "[$name]"
    }

    override fun supportsLimitOffset(): Boolean = false
    override fun supportsFetchFirst(): Boolean = true
    override fun supportsTop(): Boolean = true

    override fun generateAutoIncrement(): String = "IDENTITY(1,1)"

    override fun generateIfNotExists(): String {
        // SQL Server doesn't support IF NOT EXISTS in CREATE TABLE directly
        return ""
    }

    override fun generateColumnComment(comment: String): String {
        // SQL Server uses sp_addextendedproperty; skip inline
        return ""
    }

    override fun appendPagination(sb: StringBuilder, query: QueryBlock) {
        // SQL Server uses OFFSET-FETCH or TOP
        if (query.limit != null || query.fetch != null) {
            val limitExpr = query.limit?.limit ?: query.fetch?.count
            val offsetExpr = query.limit?.offset ?: query.offset

            if (limitExpr != null && query.orderBy.isNotEmpty()) {
                // OFFSET-FETCH requires ORDER BY in SQL Server
                if (offsetExpr != null) {
                    sb.append(" OFFSET ")
                    sb.append(generateExpression(offsetExpr))
                    sb.append(" ROWS")
                } else {
                    sb.append(" OFFSET 0 ROWS")
                }
                sb.append(" FETCH NEXT ")
                sb.append(generateExpression(limitExpr))
                sb.append(" ROWS ONLY")
            }
            // If no ORDER BY, TOP was already injected in generateSelect
        }
    }

    override fun generateSelect(stmt: SelectStmt): String {
        val query = stmt.query
        // If there's a LIMIT but no ORDER BY, use TOP
        if ((query.limit != null || query.fetch != null) && query.orderBy.isEmpty()) {
            val limitExpr = query.limit?.limit ?: query.fetch?.count
            if (limitExpr != null) {
                return generateQueryWithTop(query, limitExpr)
            }
        }
        return generateQuery(query)
    }

    private fun generateQueryWithTop(query: QueryBlock, topExpr: io.github.cnsqlparser.model.expression.SqlExpression): String {
        val sb = StringBuilder()

        // WITH (CTEs)
        if (query.ctes.isNotEmpty()) {
            sb.append("WITH ")
            sb.append(query.ctes.joinToString(", ") { generateCte(it) })
            sb.append(" ")
        }

        sb.append("SELECT ")
        if (query.distinct) sb.append("DISTINCT ")
        sb.append("TOP ")
        sb.append(generateExpression(topExpr))
        sb.append(" ")

        // SELECT items
        if (query.selectItems.isEmpty()) {
            sb.append("*")
        } else {
            sb.append(query.selectItems.joinToString(", ") { generateSelectItem(it) })
        }

        // FROM
        if (query.fromItems.isNotEmpty()) {
            sb.append(" FROM ")
            sb.append(query.fromItems.joinToString(", ") { generateFromItem(it) })
        }

        // WHERE
        val where = query.where
        if (where != null) {
            sb.append(" WHERE ")
            sb.append(generateExpression(where))
        }

        // GROUP BY
        if (query.groupBy.isNotEmpty()) {
            sb.append(" GROUP BY ")
            sb.append(query.groupBy.joinToString(", ") { generateExpression(it) })
        }

        // HAVING
        val having = query.having
        if (having != null) {
            sb.append(" HAVING ")
            sb.append(generateExpression(having))
        }

        // SET operations
        val setOp = query.setOp
        if (setOp != null) {
            sb.append(" ")
            sb.append(generateSetOp(setOp))
        }

        return sb.toString()
    }

    override fun generateOnConflict(clause: OnConflictClause): String {
        // SQL Server doesn't have ON CONFLICT; use MERGE for upsert
        return ""
    }

    override fun generateAlterAction(action: AlterTableAction): String = when (action) {
        is ModifyColumnAction -> "ALTER COLUMN ${generateColumnDef(action.column)}"
        else -> super.generateAlterAction(action)
    }

    override fun generateLiteral(expr: LiteralExpr): String = when (expr.literalType) {
        LiteralType.BOOLEAN -> if (expr.value == true) "1" else "0"
        LiteralType.STRING -> "N'${escapeString(expr.value?.toString() ?: "")}'"
        else -> super.generateLiteral(expr)
    }

    override fun generateUse(stmt: UseStmt): String = "USE [${stmt.database}]"
}
