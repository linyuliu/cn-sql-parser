package io.github.cnsqlparser.converter.generator

import io.github.cnsqlparser.common.DialectFamily
import io.github.cnsqlparser.model.expression.CastExpr
import io.github.cnsqlparser.model.expression.SqlExpression
import io.github.cnsqlparser.model.statement.*

/**
 * PostgreSQL方言SQL生成器
 *
 * PostgreSQL-specific SQL generator.
 * - Double-quote quoting for identifiers
 * - LIMIT/OFFSET pagination (also supports FETCH FIRST)
 * - SERIAL / BIGSERIAL for auto-increment
 * - :: casting syntax
 * - TEXT type
 * - RETURNING clauses
 */
class PostgreSqlGenerator(sourceFamily: DialectFamily? = null) :
    BaseSqlGenerator(sourceFamily, DialectFamily.POSTGRESQL) {

    override fun quoteIdentifier(name: String): String {
        if (name == "*" || (name.startsWith("\"") && name.endsWith("\""))) return name
        return "\"$name\""
    }

    override fun supportsLimitOffset(): Boolean = true
    override fun supportsFetchFirst(): Boolean = true
    override fun supportsTop(): Boolean = false

    override fun generateAutoIncrement(): String {
        // PostgreSQL uses SERIAL type instead of AUTO_INCREMENT.
        // The type mapping should already handle INT → SERIAL.
        // No extra keyword needed.
        return ""
    }

    override fun generateAlterAction(action: AlterTableAction): String = when (action) {
        is ModifyColumnAction -> {
            // PostgreSQL uses ALTER COLUMN ... TYPE ... for type changes
            val (mappedType, mappedParams) = mapDataType(action.column.dataType, action.column.typeParams)
            val typeStr = if (mappedParams.isNotEmpty()) {
                "$mappedType(${mappedParams.joinToString(", ")})"
            } else {
                mappedType
            }
            "ALTER COLUMN ${quoteIdentifier(action.column.name)} TYPE $typeStr"
        }
        else -> super.generateAlterAction(action)
    }

    override fun generateColumnComment(comment: String): String {
        // PostgreSQL uses separate COMMENT ON COLUMN statements; skip inline
        return ""
    }

    override fun generateUse(stmt: UseStmt): String {
        // PostgreSQL uses SET search_path
        return "SET search_path TO \"${stmt.database}\""
    }

    override fun generateTableOptions(options: Map<String, String>): String {
        // PostgreSQL doesn't use ENGINE/CHARSET table options
        return ""
    }
}
