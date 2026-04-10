package io.github.cnsqlparser.common

/**
 * SQL源码中的位置信息
 */
data class ParsePosition(
    /** 行号（从1开始） */
    val line: Int,
    /** 列号（从0开始） */
    val column: Int,
    /** 字节偏移量（从0开始，-1表示未知） */
    val offset: Int = -1
) {
    override fun toString(): String = "line $line, column $column"
}
