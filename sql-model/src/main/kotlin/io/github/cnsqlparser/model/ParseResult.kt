package io.github.cnsqlparser.model

import io.github.cnsqlparser.common.ParsePosition
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.model.statement.SqlStatement

/**
 * 解析结果
 *
 * Holds one or more parsed SQL statements along with parse warnings.
 */
data class ParseResult(
    /** 解析出的语句列表 */
    val statements: List<SqlStatement>,
    /** 解析警告（非致命错误） */
    val warnings: List<ParseWarning> = emptyList(),
    /** 源方言 */
    val dialect: ProductDialect? = null
) {
    /** 是否只有一条语句 */
    val isSingleStatement: Boolean get() = statements.size == 1

    /** 第一条语句（便捷方法） */
    val firstStatement: SqlStatement? get() = statements.firstOrNull()

    /** 是否有警告 */
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
}

/** 解析警告（非致命的不支持语法或降级信息） */
data class ParseWarning(
    val message: String,
    val position: ParsePosition? = null
)
