package io.github.cnsqlparser.converter

import io.github.cnsqlparser.common.DialectFamily
import io.github.cnsqlparser.common.ProductDialect

/**
 * 方言转换结果
 *
 * Result of a dialect conversion operation.
 *
 * @param sql the converted SQL text in the target dialect
 * @param sourceDialect the source dialect the SQL was parsed from
 * @param targetDialect the target dialect the SQL was converted to
 * @param warnings any warnings generated during conversion (e.g., unsupported features, lossy conversions)
 */
data class ConversionResult(
    val sql: String,
    val sourceDialect: ProductDialect,
    val targetDialect: ProductDialect,
    val warnings: List<ConversionWarning> = emptyList()
) {
    val hasWarnings: Boolean get() = warnings.isNotEmpty()
}

/**
 * 方言转换警告
 *
 * Warning generated during dialect conversion.
 */
data class ConversionWarning(
    val message: String,
    val category: WarningCategory = WarningCategory.GENERAL
)

/**
 * 警告分类
 */
enum class WarningCategory {
    /** 通用警告 */
    GENERAL,
    /** 不支持的特性（目标方言不支持） */
    UNSUPPORTED_FEATURE,
    /** 有损转换（精度可能丢失） */
    LOSSY_CONVERSION,
    /** 函数映射不精确 */
    APPROXIMATE_FUNCTION_MAPPING,
    /** 类型映射不精确 */
    APPROXIMATE_TYPE_MAPPING
}
