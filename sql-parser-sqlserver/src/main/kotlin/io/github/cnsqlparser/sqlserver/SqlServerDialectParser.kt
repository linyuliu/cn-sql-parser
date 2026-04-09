package io.github.cnsqlparser.sqlserver

import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.jsqlparser.JSqlParserBridge
import io.github.cnsqlparser.model.ParseResult

/**
 * SQL Server方言解析器
 *
 * SQL Server dialect parser backed by JSqlParser (which supports T-SQL well).
 *
 * 同时用于 SQL Server 兼容模式的国产数据库：
 * - 人大金仓 Kingbase（SQL Server兼容模式）
 *
 * JSqlParser支持的T-SQL特性：
 * - TOP N
 * - NOLOCK / WITH (NOLOCK)
 * - OUTPUT clause
 * - MERGE statement
 * - DATEADD, DATEDIFF 等函数
 */
class SqlServerDialectParser {

    private val jsqlParser = JSqlParserBridge()

    fun parse(ctx: ParseContext): ParseResult {
        return jsqlParser.parse(ctx)
    }
}
