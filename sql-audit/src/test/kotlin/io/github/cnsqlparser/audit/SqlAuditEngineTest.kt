package io.github.cnsqlparser.audit

import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.model.ParseResult
import io.github.cnsqlparser.model.statement.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SqlAuditEngineTest {

    private val engine = SqlAuditEngine()

    private fun auditStmt(stmt: SqlStatement, dialect: ProductDialect = ProductDialect.MYSQL): List<AuditViolation> {
        val ctx = ParseContext(stmt.sourceText ?: "", dialect)
        return engine.audit(ParseResult(listOf(stmt), dialect = dialect), ctx)
    }

    @Test
    fun `DELETE without WHERE should trigger error`() {
        val stmt = DeleteStmt(
            table = TableRef("users"),
            where = null,
            sourceText = "DELETE FROM users"
        )
        val violations = auditStmt(stmt)
        assertThat(violations).anyMatch { it.ruleName == "NoWhereOnDelete" && it.severity == AuditSeverity.ERROR }
    }

    @Test
    fun `DELETE with WHERE should pass`() {
        val stmt = DeleteStmt(
            table = TableRef("users"),
            where = io.github.cnsqlparser.model.expression.LiteralExpr(true, io.github.cnsqlparser.model.expression.LiteralType.BOOLEAN),
            sourceText = "DELETE FROM users WHERE id = 1"
        )
        val violations = auditStmt(stmt)
        assertThat(violations.filter { it.ruleName == "NoWhereOnDelete" }).isEmpty()
    }

    @Test
    fun `UPDATE without WHERE should trigger error`() {
        val stmt = UpdateStmt(
            table = TableRef("users"),
            assignments = emptyList(),
            where = null,
            sourceText = "UPDATE users SET status = 'INACTIVE'"
        )
        val violations = auditStmt(stmt)
        assertThat(violations).anyMatch { it.ruleName == "NoWhereOnUpdate" && it.severity == AuditSeverity.ERROR }
    }

    @Test
    fun `DROP TABLE should trigger error`() {
        val stmt = DropStmt(
            objectType = DropObjectType.TABLE,
            objectName = "users",
            sourceText = "DROP TABLE users"
        )
        val violations = auditStmt(stmt)
        assertThat(violations).anyMatch { it.ruleName == "DangerousDrop" && it.severity == AuditSeverity.ERROR }
    }

    @Test
    fun `multiple rules triggered on risky SQL`() {
        val stmt = DeleteStmt(
            table = TableRef("orders"),
            where = null,
            sourceText = "DELETE FROM orders"
        )
        val violations = auditStmt(stmt)
        assertThat(violations).isNotEmpty()
        assertThat(violations.all { it.severity == AuditSeverity.ERROR || it.severity == AuditSeverity.WARNING }).isTrue()
    }
}
