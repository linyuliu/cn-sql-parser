package io.github.cnsqlparser.converter.generator

import io.github.cnsqlparser.common.DialectFamily
import io.github.cnsqlparser.model.statement.*

/**
 * MySQL方言SQL生成器
 *
 * MySQL-specific SQL generator.
 * - Backtick quoting for identifiers
 * - LIMIT/OFFSET pagination
 * - AUTO_INCREMENT
 * - ENGINE/CHARSET table options
 * - ON DUPLICATE KEY UPDATE
 * - COMMENT on columns
 */
class MySqlGenerator(sourceFamily: DialectFamily? = null) :
    BaseSqlGenerator(sourceFamily, DialectFamily.MYSQL) {

    override fun quoteIdentifier(name: String): String {
        // Don't re-quote if already quoted, and don't quote *
        if (name == "*" || (name.startsWith("`") && name.endsWith("`"))) return name
        return "`$name`"
    }

    override fun supportsLimitOffset(): Boolean = true
    override fun supportsFetchFirst(): Boolean = false
    override fun supportsTop(): Boolean = false

    override fun generateAutoIncrement(): String = "AUTO_INCREMENT"

    override fun generateColumnComment(comment: String): String =
        "COMMENT '${escapeString(comment)}'"

    override fun generateOnConflict(clause: OnConflictClause): String {
        // MySQL uses ON DUPLICATE KEY UPDATE
        return when (clause.action) {
            OnConflictAction.DO_NOTHING -> "" // MySQL doesn't have a direct equivalent; use INSERT IGNORE
            OnConflictAction.DO_UPDATE -> {
                val sb = StringBuilder(" ON DUPLICATE KEY UPDATE ")
                sb.append(clause.assignments.joinToString(", ") { generateAssignment(it) })
                sb.toString()
            }
        }
    }

    override fun generateTableOptions(options: Map<String, String>): String {
        if (options.isEmpty()) return ""
        return options.entries.joinToString(" ") { "${it.key}=${it.value}" }
    }

    override fun generateAlterAction(action: AlterTableAction): String = when (action) {
        is ModifyColumnAction -> "MODIFY COLUMN ${generateColumnDef(action.column)}"
        else -> super.generateAlterAction(action)
    }

    override fun generateUse(stmt: UseStmt): String = "USE `${stmt.database}`"
}
