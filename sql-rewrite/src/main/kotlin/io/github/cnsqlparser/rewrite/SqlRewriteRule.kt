package io.github.cnsqlparser.rewrite

import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.model.statement.SqlStatement

/**
 * SQL改写规则SPI
 *
 * SPI interface for SQL rewrite rules.
 * Implement this interface to add custom rewrite logic.
 */
interface SqlRewriteRule {
    /** 规则名称 */
    val name: String
    /** 规则描述 */
    val description: String

    /** 判断是否适用于当前语句 */
    fun appliesTo(stmt: SqlStatement, ctx: ParseContext): Boolean

    /** 执行改写，返回改写后的语句 */
    fun rewrite(stmt: SqlStatement, ctx: ParseContext): SqlStatement
}

/**
 * SQL改写管道
 *
 * Pipeline that applies a sequence of rewrite rules to a SQL statement.
 */
class SqlRewritePipeline(private val rules: List<SqlRewriteRule> = emptyList()) {

    fun rewrite(stmt: SqlStatement, ctx: ParseContext): SqlStatement {
        var current = stmt
        for (rule in rules) {
            if (rule.appliesTo(current, ctx)) {
                current = rule.rewrite(current, ctx)
            }
        }
        return current
    }

    fun withRule(rule: SqlRewriteRule): SqlRewritePipeline =
        SqlRewritePipeline(rules + rule)
}
