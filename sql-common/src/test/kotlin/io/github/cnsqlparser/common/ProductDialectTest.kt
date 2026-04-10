package io.github.cnsqlparser.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProductDialectTest {

    @Test
    fun `domestic databases should be correctly identified`() {
        val domestic = ProductDialect.domesticDatabases()
        assertThat(domestic).contains(
            ProductDialect.KINGBASE,
            ProductDialect.DM,
            ProductDialect.MOGDB,
            ProductDialect.GAUSSDB,
            ProductDialect.HIGHGO,
            ProductDialect.VASTBASE,
            ProductDialect.OCEANBASE,
            ProductDialect.GOLDENDB,
            ProductDialect.TIDB
        )
    }

    @Test
    fun `PostgreSQL family should include PG-based domestic databases`() {
        val pgFamily = ProductDialect.byFamily(DialectFamily.POSTGRESQL)
        assertThat(pgFamily).contains(
            ProductDialect.POSTGRESQL,
            ProductDialect.KINGBASE,  // default PG-compatible
            ProductDialect.MOGDB,
            ProductDialect.GAUSSDB,
            ProductDialect.HIGHGO,
            ProductDialect.VASTBASE,
            ProductDialect.GREENPLUM
        )
    }

    @Test
    fun `Oracle family should include Oracle-compatible domestic databases`() {
        val oracleFamily = ProductDialect.byFamily(DialectFamily.ORACLE)
        assertThat(oracleFamily).contains(
            ProductDialect.ORACLE,
            ProductDialect.DM
        )
    }

    @Test
    fun `MySQL family should include MySQL-compatible domestic databases`() {
        val mysqlFamily = ProductDialect.byFamily(DialectFamily.MYSQL)
        assertThat(mysqlFamily).contains(
            ProductDialect.MYSQL,
            ProductDialect.OCEANBASE,
            ProductDialect.GOLDENDB,
            ProductDialect.TIDB,
            ProductDialect.STARROCKS
        )
    }

    @Test
    fun `ParseContext should default to MySQL dialect and DEFAULT mode`() {
        val ctx = ParseContext("SELECT 1")
        assertThat(ctx.product).isEqualTo(ProductDialect.MYSQL)
        assertThat(ctx.mode).isEqualTo(CompatibilityMode.DEFAULT)
        assertThat(ctx.tolerant).isFalse()
    }

    @Test
    fun `ParseContext for Kingbase Oracle mode`() {
        val ctx = ParseContext(
            sql = "SELECT ROWNUM FROM dual",
            product = ProductDialect.KINGBASE,
            mode = CompatibilityMode.ORACLE
        )
        assertThat(ctx.product).isEqualTo(ProductDialect.KINGBASE)
        assertThat(ctx.mode).isEqualTo(CompatibilityMode.ORACLE)
    }

    @Test
    fun `findByName should work case-insensitively`() {
        assertThat(ProductDialect.findByName("mysql")).isEqualTo(ProductDialect.MYSQL)
        assertThat(ProductDialect.findByName("KINGBASE")).isEqualTo(ProductDialect.KINGBASE)
        assertThat(ProductDialect.findByName("unknown")).isNull()
    }
}
