package io.github.cnsqlparser.druid

import io.github.cnsqlparser.common.CompatibilityMode
import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.model.statement.SelectStmt
import io.github.cnsqlparser.model.statement.InsertStmt
import io.github.cnsqlparser.model.statement.DeleteStmt
import io.github.cnsqlparser.model.statement.CreateTableStmt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DruidParserBridgeTest {

    private val bridge = DruidParserBridge()

    @Test
    fun `parse simple MySQL SELECT`() {
        val ctx = ParseContext("SELECT id, name FROM users WHERE age > 18", ProductDialect.MYSQL)
        val result = bridge.parse(ctx)
        assertThat(result.statements).hasSize(1)
        assertThat(result.statements[0]).isInstanceOf(SelectStmt::class.java)
    }

    @Test
    fun `parse MySQL INSERT`() {
        val ctx = ParseContext(
            "INSERT INTO users (name, email) VALUES ('张三', 'test@example.com')",
            ProductDialect.MYSQL
        )
        val result = bridge.parse(ctx)
        assertThat(result.statements).hasSize(1)
        val stmt = result.statements[0] as InsertStmt
        assertThat(stmt.table.name).isEqualToIgnoringCase("users")
    }

    @Test
    fun `parse MySQL CREATE TABLE`() {
        val sql = """
            CREATE TABLE employees (
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(100) NOT NULL,
                salary DECIMAL(10,2)
            )
        """.trimIndent()
        val ctx = ParseContext(sql, ProductDialect.MYSQL)
        val result = bridge.parse(ctx)
        assertThat(result.statements).hasSize(1)
        val stmt = result.statements[0] as CreateTableStmt
        assertThat(stmt.table.name).isEqualToIgnoringCase("employees")
        assertThat(stmt.columns).isNotEmpty()
    }

    @Test
    fun `parse Oracle SELECT with ROWNUM`() {
        val sql = "SELECT * FROM (SELECT ROWNUM rn, t.* FROM employees t WHERE ROWNUM <= 10) WHERE rn > 0"
        val ctx = ParseContext(sql, ProductDialect.ORACLE)
        val result = bridge.parse(ctx)
        assertThat(result.statements).hasSize(1)
        assertThat(result.statements[0]).isInstanceOf(SelectStmt::class.java)
    }

    @Test
    fun `parse DM Oracle-compatible SQL`() {
        val sql = "SELECT NVL(commission, 0) FROM employees"
        val ctx = ParseContext(sql, ProductDialect.DM)
        val result = bridge.parse(ctx)
        assertThat(result.statements).hasSize(1)
    }

    @Test
    fun `parse OceanBase MySQL mode`() {
        val sql = "SELECT /*+ PARALLEL(4) */ COUNT(*) FROM big_table WHERE date_col > '2024-01-01'"
        val ctx = ParseContext(sql, ProductDialect.OCEANBASE, CompatibilityMode.MYSQL)
        val result = bridge.parse(ctx)
        assertThat(result.statements).hasSize(1)
    }

    @Test
    fun `extract tables from JOIN query`() {
        val sql = "SELECT a.id, b.name FROM orders a JOIN users b ON a.user_id = b.id"
        val tables = bridge.extractTables(sql, ProductDialect.MYSQL, CompatibilityMode.DEFAULT)
        assertThat(tables).isNotEmpty()
    }

    @Test
    fun `format SQL`() {
        val sql = "select id,name from users where age>18 order by name"
        val formatted = bridge.format(sql, ProductDialect.MYSQL, CompatibilityMode.DEFAULT)
        assertThat(formatted).isNotBlank()
        assertThat(formatted).contains("SELECT")
    }
}
