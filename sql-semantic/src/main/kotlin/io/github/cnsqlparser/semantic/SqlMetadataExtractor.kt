package io.github.cnsqlparser.semantic

import io.github.cnsqlparser.model.ParseResult
import io.github.cnsqlparser.model.statement.*
import io.github.cnsqlparser.model.query.*

/**
 * SQL元数据提取器
 *
 * Extracts metadata (tables, columns, aliases, CTEs) from the Unified SQL Model.
 */
class SqlMetadataExtractor {

    /**
     * 提取所有表引用（含子查询、CTE）
     */
    fun extractTables(result: ParseResult): List<TableInfo> {
        return result.statements.flatMap { stmt ->
            extractTablesFromStatement(stmt)
        }
    }

    /**
     * 提取所有列引用
     */
    fun extractColumns(result: ParseResult): List<ColumnInfo> {
        return result.statements.flatMap { stmt ->
            extractColumnsFromStatement(stmt)
        }
    }

    /**
     * 提取CTE名称列表
     */
    fun extractCteNames(result: ParseResult): List<String> {
        return result.statements.flatMap { stmt ->
            when (stmt) {
                is SelectStmt -> stmt.query.ctes.map { it.name }
                else -> emptyList()
            }
        }
    }

    private fun extractTablesFromStatement(stmt: SqlStatement): List<TableInfo> {
        return when (stmt) {
            is SelectStmt -> extractTablesFromQuery(stmt.query)
            is InsertStmt -> listOf(TableInfo(stmt.table.name, stmt.table.schema, TableUsage.TARGET)) +
                (stmt.selectQuery?.let { extractTablesFromQuery(it) } ?: emptyList())
            is UpdateStmt -> listOf(TableInfo(stmt.table.name, stmt.table.schema, TableUsage.TARGET)) +
                stmt.fromItems.flatMap { extractTablesFromFromItem(it) }
            is DeleteStmt -> listOf(TableInfo(stmt.table.name, stmt.table.schema, TableUsage.TARGET)) +
                stmt.usingItems.flatMap { extractTablesFromFromItem(it) }
            is MergeStmt -> listOf(TableInfo(stmt.targetTable.name, stmt.targetTable.schema, TableUsage.TARGET)) +
                extractTablesFromFromItem(stmt.sourceItem)
            is CreateTableStmt -> listOf(TableInfo(stmt.table.name, stmt.table.schema, TableUsage.DEFINITION))
            is AlterTableStmt -> listOf(TableInfo(stmt.table.name, stmt.table.schema, TableUsage.DEFINITION))
            is DropStmt -> listOf(TableInfo(stmt.objectName, stmt.schema, TableUsage.DEFINITION))
            is UseStmt -> emptyList()
            is GenericStmt -> emptyList()
        }
    }

    private fun extractTablesFromQuery(query: QueryBlock): List<TableInfo> {
        val fromTables = query.fromItems.flatMap { extractTablesFromFromItem(it) }
        val cteTables = query.ctes.flatMap { extractTablesFromQuery(it.query) }
        val subOpTables = query.setOp?.let { extractTablesFromQuery(it.right) } ?: emptyList()
        return fromTables + cteTables + subOpTables
    }

    private fun extractTablesFromFromItem(fromItem: FromItem): List<TableInfo> {
        return when (fromItem) {
            is TableFromItem -> listOf(TableInfo(fromItem.name, fromItem.schema, TableUsage.SOURCE, fromItem.alias))
            is SubqueryFromItem -> extractTablesFromQuery(fromItem.query)
            is JoinFromItem -> extractTablesFromFromItem(fromItem.left) + extractTablesFromFromItem(fromItem.right)
        }
    }

    private fun extractColumnsFromStatement(stmt: SqlStatement): List<ColumnInfo> = emptyList()
}

/** 表信息 */
data class TableInfo(
    val name: String,
    val schema: String? = null,
    val usage: TableUsage = TableUsage.SOURCE,
    val alias: String? = null
) {
    val qualifiedName: String get() = if (schema != null) "$schema.$name" else name
}

enum class TableUsage { SOURCE, TARGET, DEFINITION }

/** 列信息 */
data class ColumnInfo(
    val name: String,
    val table: String? = null,
    val schema: String? = null,
    val alias: String? = null
)
