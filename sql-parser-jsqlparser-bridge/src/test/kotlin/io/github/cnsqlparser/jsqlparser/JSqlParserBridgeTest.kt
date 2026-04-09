package io.github.cnsqlparser.jsqlparser

import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.model.statement.SelectStmt
import io.github.cnsqlparser.model.statement.InsertStmt
import io.github.cnsqlparser.model.statement.DeleteStmt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JSqlParserBridgeTest {

    private val bridge = JSqlParserBridge()

    @Test
    fun `parse simple SELECT`() {
        val ctx = ParseContext("SELECT id, name FROM users", ProductDialect.MYSQL)
        val result = bridge.parse(ctx)
        assertThat(result.statements).hasSize(1)
        assertThat(result.statements[0]).isInstanceOf(SelectStmt::class.java)
    }

    @Test
    fun `parse INSERT statement`() {
        val ctx = ParseContext("INSERT INTO users (name) VALUES ('test')", ProductDialect.MYSQL)
        val result = bridge.parse(ctx)
        assertThat(result.statements).hasSize(1)
        val stmt = result.statements[0] as InsertStmt
        assertThat(stmt.table.name).isEqualToIgnoringCase("users")
    }

    @Test
    fun `extract tables from simple query`() {
        val tables = bridge.extractTables("SELECT a.id FROM orders a JOIN users b ON a.user_id = b.id")
        assertThat(tables).containsExactlyInAnyOrder("orders", "users")
    }

    @Test
    fun `extract tables from subquery`() {
        val tables = bridge.extractTables("SELECT * FROM (SELECT id FROM employees) t")
        assertThat(tables).contains("employees")
    }

    @Test
    fun `parse DELETE statement`() {
        val ctx = ParseContext("DELETE FROM logs WHERE created_at < '2024-01-01'", ProductDialect.MYSQL)
        val result = bridge.parse(ctx)
        assertThat(result.statements).hasSize(1)
        assertThat(result.statements[0]).isInstanceOf(DeleteStmt::class.java)
    }
}
