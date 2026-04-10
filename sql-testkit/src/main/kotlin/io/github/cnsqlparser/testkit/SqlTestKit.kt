package io.github.cnsqlparser.testkit

import io.github.cnsqlparser.api.SqlParsers
import io.github.cnsqlparser.common.CompatibilityMode
import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.model.ParseResult
import io.github.cnsqlparser.model.statement.SelectStmt

/**
 * SQL测试工具包
 *
 * Test utilities for regression testing the SQL parser.
 * Deliberately uses no external test framework for main-scope use.
 */
object SqlTestKit {

    /**
     * 解析SQL，断言成功且返回指定数量的语句
     * @throws AssertionError if count doesn't match
     */
    fun assertParseSuccess(sql: String, dialect: ProductDialect, expectedStmtCount: Int = 1): ParseResult {
        val result = SqlParsers.parse(sql, dialect)
        check(result.statements.size == expectedStmtCount) {
            "Expected $expectedStmtCount statements but got ${result.statements.size} for SQL: $sql"
        }
        return result
    }

    /**
     * 断言SQL解析为SELECT语句
     */
    fun assertSelect(sql: String, dialect: ProductDialect): SelectStmt {
        val result = assertParseSuccess(sql, dialect)
        val stmt = result.firstStatement
        check(stmt is SelectStmt) {
            "Expected SelectStmt but got ${stmt?.javaClass?.simpleName}"
        }
        return stmt as SelectStmt
    }

    /**
     * 国产数据库解析断言
     */
    fun assertDomesticParse(
        sql: String,
        product: ProductDialect,
        mode: CompatibilityMode = CompatibilityMode.DEFAULT,
        expectedStmtCount: Int = 1
    ): ParseResult {
        check(product.domestic) { "$product is not a domestic database" }
        val result = SqlParsers.parse(sql, product, mode)
        check(result.statements.size == expectedStmtCount) {
            "Parse result stmt count mismatch for $product($mode): expected $expectedStmtCount got ${result.statements.size}"
        }
        return result
    }

    /**
     * 回归对比：JSqlParser vs Druid解析结果对比
     */
    fun regressionCompare(sql: String, dialect: ProductDialect): RegressionResult {
        val jsqlResult = runCatching {
            io.github.cnsqlparser.jsqlparser.JSqlParserBridge().parse(ParseContext(sql, dialect))
        }
        val druidResult = runCatching {
            io.github.cnsqlparser.druid.DruidParserBridge().parse(ParseContext(sql, dialect))
        }
        return RegressionResult(
            sql = sql,
            dialect = dialect,
            jsqlParserSuccess = jsqlResult.isSuccess,
            druidSuccess = druidResult.isSuccess,
            jsqlError = jsqlResult.exceptionOrNull()?.message,
            druidError = druidResult.exceptionOrNull()?.message,
            jsqlStmtCount = jsqlResult.getOrNull()?.statements?.size ?: 0,
            druidStmtCount = druidResult.getOrNull()?.statements?.size ?: 0
        )
    }
}

data class RegressionResult(
    val sql: String,
    val dialect: ProductDialect,
    val jsqlParserSuccess: Boolean,
    val druidSuccess: Boolean,
    val jsqlError: String?,
    val druidError: String?,
    val jsqlStmtCount: Int,
    val druidStmtCount: Int
) {
    val bothSucceeded: Boolean get() = jsqlParserSuccess && druidSuccess
    val stmtCountMatch: Boolean get() = jsqlStmtCount == druidStmtCount
}
