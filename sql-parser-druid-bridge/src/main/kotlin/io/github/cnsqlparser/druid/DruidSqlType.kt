package io.github.cnsqlparser.druid

/**
 * Alibaba Druid支持的数据库类型映射
 *
 * Druid supports a wide range of database types, including many
 * Oracle-compatible and MySQL-compatible domestic Chinese databases.
 *
 * 支持的国产数据库（通过Druid）：
 * - 达梦DM: DbType.dm
 * - PolarDB: DbType.polardb
 * - GaussDB: DbType.gaussdb
 * - OceanBase (MySQL): DbType.oceanbase
 * - OceanBase (Oracle): DbType.oceanbase_oracle
 * - HighGo: DbType.highgo
 * - StarRocks: DbType.starrocks
 * - TiDB: DbType.tidb
 * - OSCAR (神通): DbType.oscar
 * - Kingbase: DbType.kingbase
 * - DB2: DbType.db2
 * - Hive: DbType.hive
 * - ClickHouse: DbType.clickhouse
 */
object DruidDbTypeMapper {
    // Maps our ProductDialect + CompatibilityMode to Druid DbType string
    private val mapping = mapOf(
        "MYSQL"         to "mysql",
        "POSTGRESQL"    to "postgresql",
        "ORACLE"        to "oracle",
        "SQLSERVER"     to "sqlserver",
        "HIVE"          to "hive",
        "CLICKHOUSE"    to "clickhouse",
        "STARROCKS"     to "starrocks",
        "TIDB"          to "tidb",
        "DM"            to "dm",
        "KINGBASE"      to "kingbase",
        "GAUSSDB"       to "gaussdb",
        "MOGDB"         to "postgresql",   // PG-compatible
        "HIGHGO"        to "highgo",
        "VASTBASE"      to "postgresql",   // PG-compatible
        "OCEANBASE"     to "oceanbase",
        "OCEANBASE_ORACLE" to "oceanbase_oracle",
        "GOLDENDB"      to "mysql",        // MySQL-compatible
        "GREENPLUM"     to "postgresql"    // PG-compatible
    )

    fun forProduct(
        product: io.github.cnsqlparser.common.ProductDialect,
        mode: io.github.cnsqlparser.common.CompatibilityMode
    ): String {
        // Multi-mode databases need special handling
        if (product == io.github.cnsqlparser.common.ProductDialect.OCEANBASE &&
            mode == io.github.cnsqlparser.common.CompatibilityMode.ORACLE
        ) {
            return "oceanbase_oracle"
        }
        return mapping[product.name] ?: product.family.name.lowercase()
    }
}
