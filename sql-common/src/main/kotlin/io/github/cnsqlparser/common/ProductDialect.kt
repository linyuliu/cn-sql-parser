package io.github.cnsqlparser.common

/**
 * 数据库产品方言 - 具体数据库产品枚举
 *
 * Specific database product dialects.
 * Covers mainstream databases and domestic Chinese databases (国产数据库).
 *
 * 国产数据库策略：按"语法家族 + 兼容模式 + overlay"设计
 * - PG家族：Kingbase-PG, MogDB, GaussDB, HighGo, Vastbase, Greenplum
 * - Oracle家族：DM达梦, Kingbase-Oracle, OceanBase-Oracle
 * - MySQL家族：OceanBase-MySQL, StarRocks, TiDB, GoldenDB
 */
enum class ProductDialect(
    /** 所属语法家族 */
    val family: DialectFamily,
    /** 显示名称 */
    val displayName: String,
    /** 是否为国产数据库 */
    val domestic: Boolean = false,
    /** 说明 */
    val description: String = ""
) {
    // ─── 主流数据库 Mainstream ─────────────────────────────────────────────────
    MYSQL(DialectFamily.MYSQL, "MySQL"),
    POSTGRESQL(DialectFamily.POSTGRESQL, "PostgreSQL"),
    ORACLE(DialectFamily.ORACLE, "Oracle"),
    SQLSERVER(DialectFamily.SQLSERVER, "Microsoft SQL Server"),
    HIVE(DialectFamily.HIVE, "Apache Hive"),
    TRINO(DialectFamily.TRINO, "Trino"),
    CLICKHOUSE(DialectFamily.CLICKHOUSE, "ClickHouse"),
    STARROCKS(DialectFamily.MYSQL, "StarRocks", description = "MySQL-compatible OLAP engine"),
    GREENPLUM(DialectFamily.POSTGRESQL, "Greenplum", description = "MPP data warehouse based on PostgreSQL"),

    // ─── 国产数据库 Domestic Chinese Databases ─────────────────────────────────

    /**
     * 人大金仓 KingbaseES
     * 支持四种兼容模式：Oracle / MySQL / SQL Server / PostgreSQL
     * 默认模式为 PostgreSQL 兼容
     * 使用 ParseContext.mode 指定具体兼容模式
     */
    KINGBASE(
        DialectFamily.POSTGRESQL,
        "KingbaseES（人大金仓）",
        domestic = true,
        description = "四种兼容模式: Oracle/MySQL/SQLServer/PostgreSQL。默认PG兼容，用 CompatibilityMode 切换"
    ),

    /**
     * 达梦 DM Database
     * 以Oracle兼容语法为主，部分支持MySQL/PG语法
     */
    DM(
        DialectFamily.ORACLE,
        "DM（达梦数据库）",
        domestic = true,
        description = "Oracle兼容为主，支持部分MySQL/PG语法"
    ),

    /**
     * MogDB
     * 基于openGauss的PG系数据库，华为云赋能
     */
    MOGDB(
        DialectFamily.POSTGRESQL,
        "MogDB",
        domestic = true,
        description = "基于openGauss的PG系数据库"
    ),

    /**
     * GaussDB / openGauss
     * 华为自研，基于PostgreSQL，部分Oracle兼容
     */
    GAUSSDB(
        DialectFamily.POSTGRESQL,
        "GaussDB/openGauss（华为）",
        domestic = true,
        description = "基于PostgreSQL，部分Oracle兼容语法"
    ),

    /**
     * HighGo DB 瀚高数据库
     * 基于PostgreSQL
     */
    HIGHGO(
        DialectFamily.POSTGRESQL,
        "HighGo DB（瀚高）",
        domestic = true,
        description = "基于PostgreSQL的国产数据库"
    ),

    /**
     * Vastbase G100 海量数据
     * 基于openGauss/PostgreSQL
     */
    VASTBASE(
        DialectFamily.POSTGRESQL,
        "Vastbase G100（海量数据）",
        domestic = true,
        description = "基于openGauss/PostgreSQL"
    ),

    /**
     * OceanBase 蚂蚁集团
     * 支持 MySQL 兼容模式（默认）和 Oracle 兼容模式
     * 使用 ParseContext.mode 指定
     */
    OCEANBASE(
        DialectFamily.MYSQL,
        "OceanBase（蚂蚁集团）",
        domestic = true,
        description = "支持MySQL兼容模式（默认）和Oracle兼容模式，用 CompatibilityMode 切换"
    ),

    /**
     * GoldenDB 中兴通讯
     * MySQL兼容为主
     */
    GOLDENDB(
        DialectFamily.MYSQL,
        "GoldenDB（中兴通讯）",
        domestic = true,
        description = "MySQL兼容为主"
    ),

    /**
     * TiDB PingCAP
     * MySQL兼容协议
     */
    TIDB(
        DialectFamily.MYSQL,
        "TiDB（PingCAP）",
        domestic = true,
        description = "高度MySQL兼容的分布式数据库"
    );

    companion object {
        /** 获取所有国产数据库 */
        fun domesticDatabases(): List<ProductDialect> = entries.filter { it.domestic }

        /** 根据语法家族获取所有方言 */
        fun byFamily(family: DialectFamily): List<ProductDialect> =
            entries.filter { it.family == family }

        /** 根据名称查找（忽略大小写） */
        fun findByName(name: String): ProductDialect? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
