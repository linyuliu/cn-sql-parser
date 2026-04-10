package io.github.cnsqlparser.audit

import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.model.ParseResult
import io.github.cnsqlparser.model.statement.*

/**
 * SQL审核规则SPI
 *
 * SPI interface for SQL audit rules.
 * Each rule evaluates a SQL statement and returns zero or more violations.
 */
interface SqlAuditRule {
    val name: String
    val description: String
    val severity: AuditSeverity

    fun check(stmt: SqlStatement, ctx: ParseContext): List<AuditViolation>
}

enum class AuditSeverity { ERROR, WARNING, INFO }

data class AuditViolation(
    val ruleName: String,
    val severity: AuditSeverity,
    val message: String,
    val sql: String? = null
)

/**
 * SQL审核引擎
 */
class SqlAuditEngine(private val rules: List<SqlAuditRule> = defaultRules()) {

    fun audit(result: ParseResult, ctx: ParseContext): List<AuditViolation> {
        return result.statements.flatMap { stmt ->
            rules.flatMap { rule -> rule.check(stmt, ctx) }
        }
    }

    companion object {
        fun defaultRules(): List<SqlAuditRule> = listOf(
            NoWhereOnDeleteRule(),
            NoWhereOnUpdateRule(),
            SelectStarRule(),
            DangerousDropRule()
        )
    }
}

/**
 * DELETE无WHERE条件检查
 */
class NoWhereOnDeleteRule : SqlAuditRule {
    override val name = "NoWhereOnDelete"
    override val description = "DELETE statement without WHERE clause affects all rows"
    override val severity = AuditSeverity.ERROR

    override fun check(stmt: SqlStatement, ctx: ParseContext): List<AuditViolation> {
        if (stmt is DeleteStmt && stmt.where == null) {
            return listOf(AuditViolation(name, severity,
                "DELETE statement has no WHERE clause - this will delete all rows from ${stmt.table.qualifiedName}",
                stmt.sourceText
            ))
        }
        return emptyList()
    }
}

/**
 * UPDATE无WHERE条件检查
 */
class NoWhereOnUpdateRule : SqlAuditRule {
    override val name = "NoWhereOnUpdate"
    override val description = "UPDATE statement without WHERE clause affects all rows"
    override val severity = AuditSeverity.ERROR

    override fun check(stmt: SqlStatement, ctx: ParseContext): List<AuditViolation> {
        if (stmt is UpdateStmt && stmt.where == null) {
            return listOf(AuditViolation(name, severity,
                "UPDATE statement has no WHERE clause - this will update all rows in ${stmt.table.qualifiedName}",
                stmt.sourceText
            ))
        }
        return emptyList()
    }
}

/**
 * SELECT * 检查
 */
class SelectStarRule : SqlAuditRule {
    override val name = "SelectStar"
    override val description = "SELECT * is discouraged in production SQL"
    override val severity = AuditSeverity.WARNING

    override fun check(stmt: SqlStatement, ctx: ParseContext): List<AuditViolation> {
        if (stmt is SelectStmt) {
            val hasStar = stmt.query.selectItems.any { item ->
                item.expression is io.github.cnsqlparser.model.expression.StarExpr
            }
            if (hasStar) {
                return listOf(AuditViolation(name, severity,
                    "SELECT * is discouraged - explicitly list columns instead",
                    stmt.sourceText
                ))
            }
        }
        return emptyList()
    }
}

/**
 * DROP语句检查
 */
class DangerousDropRule : SqlAuditRule {
    override val name = "DangerousDrop"
    override val description = "DROP statements are irreversible"
    override val severity = AuditSeverity.ERROR

    override fun check(stmt: SqlStatement, ctx: ParseContext): List<AuditViolation> {
        if (stmt is DropStmt) {
            return listOf(AuditViolation(name, severity,
                "DROP ${stmt.objectType} ${stmt.objectName} is irreversible",
                stmt.sourceText
            ))
        }
        return emptyList()
    }
}
