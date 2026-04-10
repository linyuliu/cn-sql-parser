package io.github.cnsqlparser.converter.generator

import io.github.cnsqlparser.model.expression.*
import io.github.cnsqlparser.model.query.*
import io.github.cnsqlparser.model.statement.*

/**
 * SQL生成器接口
 *
 * Converts a Unified SQL Model AST back into dialect-specific SQL text.
 * Each target dialect implements this interface with its own quoting, pagination,
 * type naming, and function syntax rules.
 */
interface SqlGenerator {

    /** Generate SQL text for a statement */
    fun generate(stmt: SqlStatement): String

    /** Generate SQL text for a query block */
    fun generateQuery(query: QueryBlock): String

    /** Generate SQL text for an expression */
    fun generateExpression(expr: SqlExpression): String
}
