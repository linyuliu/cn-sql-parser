package io.github.cnsqlparser.common

/**
 * 解析请求上下文
 *
 * Parse request context: carries the SQL text, target product dialect, and compatibility mode.
 *
 * 这是解析入口的核心对象，"模式"是一等公民。
 * 在国产数据库场景中，单独指定 [product] 不够，必须同时指定 [mode]。
 *
 * 示例：
 * ```kotlin
 * // Kingbase Oracle兼容模式
 * ParseContext("SELECT ROWNUM FROM dual", ProductDialect.KINGBASE, CompatibilityMode.ORACLE)
 *
 * // OceanBase MySQL兼容模式（默认）
 * ParseContext("SELECT * FROM t LIMIT 10", ProductDialect.OCEANBASE)
 *
 * // OceanBase Oracle兼容模式
 * ParseContext("SELECT * FROM t WHERE ROWNUM <= 10", ProductDialect.OCEANBASE, CompatibilityMode.ORACLE)
 * ```
 */
data class ParseContext(
    /** 待解析的SQL文本 */
    val sql: String,
    /** 目标数据库产品 */
    val product: ProductDialect = ProductDialect.MYSQL,
    /** 兼容模式（适用于多模式数据库，如KingbaseES、OceanBase） */
    val mode: CompatibilityMode = CompatibilityMode.DEFAULT,
    /** 是否开启容错解析（遇到不能识别的语法时尽量继续） */
    val tolerant: Boolean = false,
    /** 额外参数（供各方言适配器使用） */
    val options: Map<String, String> = emptyMap()
) {
    /** 快捷构造：SQL文本 + 产品方言，使用默认兼容模式 */
    constructor(sql: String, product: ProductDialect) :
        this(sql, product, CompatibilityMode.DEFAULT)
}
