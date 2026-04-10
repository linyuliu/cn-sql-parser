package io.github.cnsqlparser.converter.generator

import io.github.cnsqlparser.common.DialectFamily
import io.github.cnsqlparser.model.expression.CastExpr
import io.github.cnsqlparser.model.expression.LiteralExpr
import io.github.cnsqlparser.model.expression.LiteralType
import io.github.cnsqlparser.model.query.QueryBlock
import io.github.cnsqlparser.model.statement.*

/**
 * Oracle方言SQL生成器
 *
 * Oracle-specific SQL generator.
 * - Double-quote quoting for identifiers
 * - FETCH FIRST / ROWNUM pagination (Oracle 12c+ style)
 * - SEQUENCE for auto-increment (no inline AUTO_INCREMENT)
 * - VARCHAR2 instead of VARCHAR
 * - SYSDATE / SYSTIMESTAMP
 * - No IF NOT EXISTS in DDL
 */
class OracleSqlGenerator(sourceFamily: DialectFamily? = null) :
    BaseSqlGenerator(sourceFamily, DialectFamily.ORACLE) {

    override fun quoteIdentifier(name: String): String {
        if (name == "*" || (name.startsWith("\"") && name.endsWith("\""))) return name
        // Oracle convention: uppercase unquoted identifiers.
        // We quote with double quotes to preserve case.
        return "\"$name\""
    }

    override fun supportsLimitOffset(): Boolean = false
    override fun supportsFetchFirst(): Boolean = true
    override fun supportsTop(): Boolean = false

    override fun generateAutoIncrement(): String {
        // Oracle 12c+ supports GENERATED ALWAYS AS IDENTITY
        return "GENERATED ALWAYS AS IDENTITY"
    }

    override fun generateIfNotExists(): String {
        // Oracle doesn't support IF NOT EXISTS in CREATE TABLE
        // Returning empty; callers should handle this separately if needed
        return ""
    }

    override fun generateColumnComment(comment: String): String {
        // Oracle uses separate COMMENT ON COLUMN statements; skip inline
        return ""
    }

    override fun generateOnConflict(clause: OnConflictClause): String {
        // Oracle uses MERGE for upsert; return empty for ON CONFLICT
        return ""
    }

    override fun generateAlterAction(action: AlterTableAction): String = when (action) {
        is ModifyColumnAction -> "MODIFY ${generateColumnDef(action.column)}"
        is AddColumnAction -> "ADD ${generateColumnDef(action.column)}"
        else -> super.generateAlterAction(action)
    }

    override fun generateLiteral(expr: LiteralExpr): String = when (expr.literalType) {
        LiteralType.BOOLEAN -> if (expr.value == true) "1" else "0"
        else -> super.generateLiteral(expr)
    }

    override fun generateCast(expr: CastExpr): String {
        val (mappedType, mappedParams) = mapDataType(expr.targetType, expr.typeParams)
        val typeStr = if (mappedParams.isNotEmpty()) {
            "$mappedType(${mappedParams.joinToString(", ")})"
        } else {
            mappedType
        }
        return "CAST(${generateExpression(expr.expression)} AS $typeStr)"
    }

    override fun generateUse(stmt: UseStmt): String {
        // Oracle uses ALTER SESSION SET CURRENT_SCHEMA
        return "ALTER SESSION SET CURRENT_SCHEMA = \"${stmt.database}\""
    }

    override fun generateTableOptions(options: Map<String, String>): String {
        // Oracle doesn't use ENGINE/CHARSET table options
        return ""
    }
}
