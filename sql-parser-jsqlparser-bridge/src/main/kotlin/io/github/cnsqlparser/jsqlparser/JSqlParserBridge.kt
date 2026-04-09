package io.github.cnsqlparser.jsqlparser

import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.common.SqlSyntaxException
import io.github.cnsqlparser.model.ParseResult
import io.github.cnsqlparser.model.ParseWarning
import io.github.cnsqlparser.model.statement.*
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.insert.Insert
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.update.Update
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.alter.Alter
import net.sf.jsqlparser.statement.drop.Drop
import net.sf.jsqlparser.statement.merge.Merge
import org.slf4j.LoggerFactory

/**
 * JSqlParser桥接层
 *
 * Provides SQL parsing backed by JSqlParser as:
 * 1. Fallback for unsupported dialects
 * 2. Regression comparison baseline
 * 3. Quick support for mainstream SQL (MySQL/PG/Oracle/MSSQL)
 *
 * JSqlParser是辅助，不是终局。主要用于：
 * - 主流SQL快速兜底
 * - 回归对比（与ANTLR解析结果比对）
 * - 不支持方言的临时fallback
 *
 * 支持的方言：MySQL, PostgreSQL, Oracle, SQL Server, H2, MariaDB等
 */
class JSqlParserBridge {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 解析SQL
     */
    fun parse(ctx: ParseContext): ParseResult {
        return try {
            val jsqlStatements = CCJSqlParserUtil.parseStatements(ctx.sql)
            val statements = jsqlStatements.statements.mapNotNull { jsqlStmt ->
                try {
                    convertStatement(jsqlStmt, ctx)
                } catch (e: Exception) {
                    logger.warn("JSqlParser statement conversion failed: ${e.message}", e)
                    GenericStmt(
                        statementText = jsqlStmt.toString(),
                        dialect = ctx.product,
                        compatibilityMode = ctx.mode
                    )
                }
            }
            ParseResult(
                statements = statements,
                dialect = ctx.product
            )
        } catch (e: Exception) {
            throw SqlSyntaxException(
                message = "JSqlParser failed to parse SQL: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * 仅提取表引用（快速路径，无需完整解析）
     */
    fun extractTables(sql: String): List<String> {
        return try {
            val tables = net.sf.jsqlparser.util.TablesNamesFinder()
                .getTableList(CCJSqlParserUtil.parse(sql))
            tables?.toList() ?: emptyList()
        } catch (e: Exception) {
            logger.warn("JSqlParser table extraction failed: ${e.message}")
            emptyList()
        }
    }

    private fun convertStatement(
        jsqlStmt: net.sf.jsqlparser.statement.Statement,
        ctx: ParseContext
    ): SqlStatement {
        return when (jsqlStmt) {
            is Select -> convertSelect(jsqlStmt, ctx)
            is Insert -> convertInsert(jsqlStmt, ctx)
            is Update -> convertUpdate(jsqlStmt, ctx)
            is Delete -> convertDelete(jsqlStmt, ctx)
            is CreateTable -> convertCreateTable(jsqlStmt, ctx)
            is Alter -> convertAlter(jsqlStmt, ctx)
            is Drop -> convertDrop(jsqlStmt, ctx)
            is Merge -> convertMerge(jsqlStmt, ctx)
            else -> GenericStmt(
                statementText = jsqlStmt.toString(),
                dialect = ctx.product,
                compatibilityMode = ctx.mode
            )
        }
    }

    private fun convertSelect(select: Select, ctx: ParseContext): SelectStmt {
        return SelectStmt(
            query = io.github.cnsqlparser.model.query.QueryBlock(),
            sourceText = select.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertInsert(insert: Insert, ctx: ParseContext): InsertStmt {
        val table = insert.table
        return InsertStmt(
            table = TableRef(
                name = table.name,
                schema = table.schemaName
            ),
            sourceText = insert.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertUpdate(update: Update, ctx: ParseContext): UpdateStmt {
        val table = update.table
        return UpdateStmt(
            table = TableRef(
                name = table.name,
                schema = table.schemaName
            ),
            assignments = emptyList(),
            sourceText = update.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertDelete(delete: Delete, ctx: ParseContext): DeleteStmt {
        val table = delete.table
        return DeleteStmt(
            table = TableRef(
                name = table.name,
                schema = table.schemaName
            ),
            sourceText = delete.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertCreateTable(create: CreateTable, ctx: ParseContext): CreateTableStmt {
        val table = create.table
        val columns = create.columnDefinitions?.map { col ->
            ColumnDefinition(
                name = col.columnName,
                dataType = col.colDataType.dataType,
                nullable = true
            )
        } ?: emptyList()
        return CreateTableStmt(
            table = TableRef(
                name = table.name,
                schema = table.schemaName
            ),
            columns = columns,
            sourceText = create.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertAlter(alter: Alter, ctx: ParseContext): AlterTableStmt {
        val table = alter.table
        return AlterTableStmt(
            table = TableRef(
                name = table.name,
                schema = table.schemaName
            ),
            actions = emptyList(),
            sourceText = alter.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertDrop(drop: Drop, ctx: ParseContext): DropStmt {
        val objectType = when (drop.type?.uppercase()) {
            "TABLE" -> DropObjectType.TABLE
            "VIEW" -> DropObjectType.VIEW
            "INDEX" -> DropObjectType.INDEX
            "SCHEMA" -> DropObjectType.SCHEMA
            else -> DropObjectType.TABLE
        }
        return DropStmt(
            objectType = objectType,
            objectName = drop.name?.name ?: "",
            schema = drop.name?.schemaName,
            ifExists = drop.isIfExists,
            sourceText = drop.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertMerge(merge: Merge, ctx: ParseContext): MergeStmt {
        val table = merge.table
        return MergeStmt(
            targetTable = TableRef(name = table.name, schema = table.schemaName),
            sourceItem = io.github.cnsqlparser.model.query.TableFromItem(
                name = "source",
                alias = "src"
            ),
            onCondition = io.github.cnsqlparser.model.expression.RawExpr(
                merge.onCondition?.toString() ?: ""
            ),
            whenClauses = emptyList(),
            sourceText = merge.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }
}
