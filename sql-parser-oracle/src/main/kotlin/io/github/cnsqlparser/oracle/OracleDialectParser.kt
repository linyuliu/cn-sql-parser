package io.github.cnsqlparser.oracle

import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.druid.DruidParserBridge
import io.github.cnsqlparser.model.ParseResult

/**
 * Oracle方言解析器
 *
 * Oracle dialect parser backed by Alibaba Druid (DbType.oracle).
 *
 * 同时用于 Oracle 家族国产数据库：
 * - 达梦 DM（Oracle兼容模式，通过 DruidParserBridge 使用 DbType.dm）
 * - 人大金仓 Kingbase（Oracle兼容模式，通过 DruidParserBridge 使用 DbType.kingbase）
 * - OceanBase（Oracle兼容模式，通过 DruidParserBridge 使用 DbType.oceanbase_oracle）
 *
 * Druid对Oracle语法的支持已非常完整，包括：
 * - ROWNUM / ROWID（Oracle特有伪列）
 * - CONNECT BY 层次查询
 * - MERGE INTO
 * - PIVOT / UNPIVOT
 * - 分析函数
 * - sequence.NEXTVAL / .CURRVAL
 * - Oracle Hint: /*+ ... */
 * - TO_DATE, NVL, DECODE 等Oracle函数
 */
class OracleDialectParser {

    private val druidBridge = DruidParserBridge()

    fun parse(ctx: ParseContext): ParseResult {
        return druidBridge.parse(ctx)
    }
}
