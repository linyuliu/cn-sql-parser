package io.github.cnsqlparser.common

/**
 * 兼容模式 - 多模式数据库的兼容模式
 *
 * Compatibility mode for multi-mode databases such as KingbaseES and OceanBase.
 *
 * 典型场景：
 * - 人大金仓（Kingbase）支持四种兼容模式：Oracle / MySQL / SQL Server / PostgreSQL
 * - OceanBase 支持 MySQL 兼容模式（默认）和 Oracle 兼容模式
 * - 达梦 支持 Oracle 兼容为主
 *
 * 使用示例:
 * ```kotlin
 * val ctx = ParseContext(
 *     sql = "SELECT ROWNUM FROM dual",
 *     product = ProductDialect.KINGBASE,
 *     mode = CompatibilityMode.ORACLE
 * )
 * ```
 */
enum class CompatibilityMode(val displayName: String) {
    /** 默认模式（数据库原生默认模式） */
    DEFAULT("Default"),
    /** Oracle兼容模式 */
    ORACLE("Oracle Compatible"),
    /** MySQL兼容模式 */
    MYSQL("MySQL Compatible"),
    /** PostgreSQL兼容模式 */
    POSTGRESQL("PostgreSQL Compatible"),
    /** SQL Server兼容模式 */
    SQLSERVER("SQL Server Compatible")
}
