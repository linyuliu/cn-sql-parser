package io.github.cnsqlparser.common

/**
 * 方言家族 - SQL语法家族（按语法血缘分类）
 *
 * Dialect family based on SQL language lineage.
 * Most domestic Chinese databases derive from one of these families.
 */
enum class DialectFamily(val displayName: String) {
    /** MySQL家族 - OceanBase-MySQL, StarRocks, TiDB, GoldenDB等 */
    MYSQL("MySQL Family"),
    /** PostgreSQL家族 - Kingbase-PG, MogDB, GaussDB, HighGo, Vastbase, Greenplum等 */
    POSTGRESQL("PostgreSQL Family"),
    /** Oracle家族 - DM达梦, Kingbase-Oracle, OceanBase-Oracle等 */
    ORACLE("Oracle Family"),
    /** SQL Server家族 - Kingbase-SQLServer mode等 */
    SQLSERVER("SQL Server Family"),
    /** Hive家族（大数据/数仓） */
    HIVE("Apache Hive Family"),
    /** Trino/Presto家族 */
    TRINO("Trino/Presto Family"),
    /** ClickHouse家族 */
    CLICKHOUSE("ClickHouse Family")
}
