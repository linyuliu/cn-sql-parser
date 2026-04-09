package io.github.cnsqlparser.common

/**
 * SQL解析异常基类
 */
open class SqlParseException(
    message: String,
    val position: ParsePosition? = null,
    cause: Throwable? = null
) : RuntimeException(if (position != null) "$message at $position" else message, cause)

/**
 * 语法错误异常
 */
class SqlSyntaxException(
    message: String,
    position: ParsePosition? = null,
    val offendingText: String? = null,
    cause: Throwable? = null
) : SqlParseException(message, position, cause)

/**
 * 不支持的方言特性异常
 */
class UnsupportedDialectFeatureException(
    feature: String,
    val dialect: ProductDialect,
    val mode: CompatibilityMode = CompatibilityMode.DEFAULT
) : SqlParseException(
    "Unsupported feature '$feature' for ${dialect.displayName} (mode: ${mode.displayName})"
)

/**
 * SQL改写异常
 */
class SqlRewriteException(
    message: String,
    cause: Throwable? = null
) : SqlParseException(message, null, cause)

/**
 * SQL审核异常
 */
class SqlAuditException(
    message: String,
    cause: Throwable? = null
) : SqlParseException(message, null, cause)
