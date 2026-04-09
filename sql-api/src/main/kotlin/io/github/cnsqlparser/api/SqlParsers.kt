package io.github.cnsqlparser.api

import io.github.cnsqlparser.audit.SqlAuditEngine
import io.github.cnsqlparser.audit.AuditViolation
import io.github.cnsqlparser.common.CompatibilityMode
import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.druid.DruidParserBridge
import io.github.cnsqlparser.druid.domestic.DomesticDatabaseSupport
import io.github.cnsqlparser.jsqlparser.JSqlParserBridge
import io.github.cnsqlparser.model.ParseResult
import io.github.cnsqlparser.rewrite.LimitRewriteRule
import io.github.cnsqlparser.rewrite.SqlRewritePipeline
import io.github.cnsqlparser.semantic.SqlMetadataExtractor
import io.github.cnsqlparser.semantic.TableInfo

/**
 * SQL解析门面 - Java友好API
 *
 * Java-friendly SQL parsing facade.
 *
 * Java使用示例：
 * ```java
 * // 解析MySQL SQL
 * ParseResult result = SqlParsers.parse("SELECT * FROM orders WHERE id = 1", ProductDialect.MYSQL);
 *
 * // 解析Kingbase Oracle兼容模式
 * ParseResult result = SqlParsers.parse(
 *     "SELECT ROWNUM, name FROM users WHERE ROWNUM <= 10",
 *     ProductDialect.KINGBASE,
 *     CompatibilityMode.ORACLE
 * );
 *
 * // 提取表名
 * List<TableInfo> tables = SqlParsers.extractTables("SELECT a.id FROM a JOIN b ON a.id=b.id", ProductDialect.MYSQL);
 *
 * // 添加LIMIT
 * String rewritten = SqlParsers.addLimit("SELECT * FROM users", ProductDialect.MYSQL, 100);
 *
 * // 审核SQL
 * List<AuditViolation> violations = SqlParsers.audit("DELETE FROM users", ProductDialect.MYSQL);
 * ```
 */
object SqlParsers {

    private val jsqlBridge = JSqlParserBridge()
    private val druidBridge = DruidParserBridge()
    private val metadataExtractor = SqlMetadataExtractor()
    private val auditEngine = SqlAuditEngine()

    // ─── Parse ───────────────────────────────────────────────────────────────

    /**
     * 解析SQL（使用默认MySQL方言）
     */
    @JvmStatic
    fun parse(sql: String): ParseResult =
        parse(sql, ProductDialect.MYSQL)

    /**
     * 解析SQL（指定方言）
     */
    @JvmStatic
    fun parse(sql: String, dialect: ProductDialect): ParseResult =
        parse(sql, dialect, CompatibilityMode.DEFAULT)

    /**
     * 解析SQL（指定方言和兼容模式）
     *
     * 自动选择最合适的解析引擎：
     * - 国产数据库 → DomesticDatabaseSupport (Druid为主)
     * - Oracle / DM → Druid
     * - MySQL家族 → Druid (JSqlParser fallback)
     * - PG家族 → Druid (JSqlParser fallback)
     * - SQL Server → JSqlParser
     */
    @JvmStatic
    fun parse(sql: String, dialect: ProductDialect, mode: CompatibilityMode): ParseResult {
        val ctx = ParseContext(sql, dialect, mode)
        return when {
            dialect.domestic -> DomesticDatabaseSupport.parse(ctx)
            dialect == ProductDialect.ORACLE -> druidBridge.parse(ctx)
            dialect == ProductDialect.SQLSERVER -> jsqlBridge.parse(ctx)
            else -> druidBridge.parse(ctx)
        }
    }

    /**
     * 解析SQL（完整上下文，支持容错模式）
     */
    @JvmStatic
    fun parse(ctx: ParseContext): ParseResult = parse(ctx.sql, ctx.product, ctx.mode)

    // ─── Metadata ────────────────────────────────────────────────────────────

    /**
     * 提取SQL中引用的所有表名
     */
    @JvmStatic
    fun extractTables(sql: String, dialect: ProductDialect): List<TableInfo> {
        val result = parse(sql, dialect)
        return metadataExtractor.extractTables(result)
    }

    /**
     * 快速提取表名（通过JSqlParser，适合简单场景）
     */
    @JvmStatic
    fun extractTablesFast(sql: String): List<String> =
        jsqlBridge.extractTables(sql)

    // ─── Rewrite ──────────────────────────────────────────────────────────────

    /**
     * 为SELECT语句添加LIMIT（仅处理单条SQL语句）
     *
     * 根据方言和兼容模式自动选择正确的分页语法。
     * 注意：仅返回改写后的第一条语句的文本；如需处理多条语句，请使用 rewriteAll。
     */
    @JvmStatic
    fun addLimit(sql: String, dialect: ProductDialect, maxRows: Long): String {
        val ctx = ParseContext(sql, dialect)
        val result = parse(ctx)
        val pipeline = SqlRewritePipeline().withRule(LimitRewriteRule(maxRows))
        return result.statements
            .map { stmt -> pipeline.rewrite(stmt, ctx) }
            .joinToString("; ") { it.sourceText ?: sql }
            .ifBlank { sql }
    }

    /**
     * 对所有语句应用改写管道，返回改写后的SQL文本列表
     */
    @JvmStatic
    fun rewriteAll(sql: String, dialect: ProductDialect, pipeline: SqlRewritePipeline): List<String> {
        val ctx = ParseContext(sql, dialect)
        val result = parse(ctx)
        return result.statements.map { stmt ->
            pipeline.rewrite(stmt, ctx).sourceText ?: stmt.sourceText ?: ""
        }
    }

    /**
     * 格式化SQL
     */
    @JvmStatic
    fun format(sql: String, dialect: ProductDialect): String =
        druidBridge.format(sql, dialect, CompatibilityMode.DEFAULT)

    // ─── Audit ────────────────────────────────────────────────────────────────

    /**
     * 审核SQL，返回违规列表
     */
    @JvmStatic
    fun audit(sql: String, dialect: ProductDialect): List<AuditViolation> {
        val ctx = ParseContext(sql, dialect)
        val result = parse(ctx)
        return auditEngine.audit(result, ctx)
    }
}
