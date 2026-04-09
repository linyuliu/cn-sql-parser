package io.github.cnsqlparser.postgresql

import io.github.cnsqlparser.antlr.AntlrParserAdapter
import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.common.ParsePosition
import io.github.cnsqlparser.model.ParseResult
import io.github.cnsqlparser.model.ParseWarning
import io.github.cnsqlparser.model.statement.*
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.Parser
import org.slf4j.LoggerFactory

/**
 * PostgreSQL方言解析器
 *
 * PostgreSQL dialect parser backed by ANTLR4.
 *
 * 同时用于 PG 家族国产数据库（默认/PG模式下）：
 * - 人大金仓 Kingbase (PG兼容模式)
 * - MogDB
 * - GaussDB / openGauss
 * - HighGo DB 瀚高
 * - Vastbase G100 海量数据
 * - Greenplum
 *
 * 对于 Kingbase Oracle模式，使用 OracleDialectParser（配合 DruidBridge）。
 * 对于 Kingbase MySQL模式，使用 MySqlDialectParser。
 */
class PostgreSqlDialectParser : AntlrParserAdapter() {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun createLexer(input: CharStream): Lexer =
        PostgreSqlLexer(input)

    override fun createParser(tokens: CommonTokenStream): Parser =
        PostgreSqlParser(tokens)

    override fun buildResult(ctx: ParseContext, parser: Parser, warnings: List<String>): ParseResult {
        val pgParser = parser as PostgreSqlParser
        val rootCtx = pgParser.root()
        val visitor = PostgreSqlAstVisitor(ctx)
        val statements = visitor.buildStatements(rootCtx)
        return ParseResult(
            statements = statements,
            warnings = warnings.map { ParseWarning(it) },
            dialect = ctx.product
        )
    }
}

/**
 * PostgreSQL AST访问者 - 将ANTLR解析树映射到统一SQL模型
 */
internal class PostgreSqlAstVisitor(private val ctx: ParseContext) : PostgreSqlParserBaseVisitor<Any>() {

    fun buildStatements(rootCtx: PostgreSqlParser.RootContext): List<SqlStatement> {
        return rootCtx.sqlStatement().mapNotNull { stmtCtx ->
            try {
                visitSqlStatement(stmtCtx) as? SqlStatement
            } catch (e: Exception) {
                GenericStmt(
                    statementText = stmtCtx.text,
                    sourceText = stmtCtx.text,
                    dialect = ctx.product,
                    compatibilityMode = ctx.mode
                )
            }
        }
    }

    override fun visitSqlStatement(stmtCtx: PostgreSqlParser.SqlStatementContext): Any? {
        return when {
            stmtCtx.selectStatement() != null -> visitSelectStatement(stmtCtx.selectStatement())
            stmtCtx.insertStatement() != null -> visitInsertStatement(stmtCtx.insertStatement())
            stmtCtx.updateStatement() != null -> visitUpdateStatement(stmtCtx.updateStatement())
            stmtCtx.deleteStatement() != null -> visitDeleteStatement(stmtCtx.deleteStatement())
            stmtCtx.createTableStatement() != null -> visitCreateTableStatement(stmtCtx.createTableStatement())
            stmtCtx.alterTableStatement() != null -> visitAlterTableStatement(stmtCtx.alterTableStatement())
            stmtCtx.dropStatement() != null -> visitDropStatement(stmtCtx.dropStatement())
            else -> GenericStmt(stmtCtx.text, dialect = ctx.product, compatibilityMode = ctx.mode)
        }
    }

    override fun visitSelectStatement(selectCtx: PostgreSqlParser.SelectStatementContext): Any {
        return SelectStmt(
            query = io.github.cnsqlparser.model.query.QueryBlock(),
            sourceText = selectCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(selectCtx.start.line, selectCtx.start.charPositionInLine)
        )
    }

    override fun visitInsertStatement(insertCtx: PostgreSqlParser.InsertStatementContext): Any {
        val tableName = insertCtx.tableName()
        val table = tableRefFrom(tableName)
        return InsertStmt(
            table = table,
            sourceText = insertCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(insertCtx.start.line, insertCtx.start.charPositionInLine)
        )
    }

    override fun visitUpdateStatement(updateCtx: PostgreSqlParser.UpdateStatementContext): Any {
        val tableName = updateCtx.tableName()
        val table = tableRefFrom(tableName)
        return UpdateStmt(
            table = table,
            assignments = emptyList(),
            sourceText = updateCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(updateCtx.start.line, updateCtx.start.charPositionInLine)
        )
    }

    override fun visitDeleteStatement(deleteCtx: PostgreSqlParser.DeleteStatementContext): Any {
        val tableName = deleteCtx.tableName()
        val table = tableRefFrom(tableName)
        return DeleteStmt(
            table = table,
            sourceText = deleteCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(deleteCtx.start.line, deleteCtx.start.charPositionInLine)
        )
    }

    override fun visitCreateTableStatement(createCtx: PostgreSqlParser.CreateTableStatementContext): Any {
        val tableName = createCtx.tableName()
        val table = tableRefFrom(tableName)
        return CreateTableStmt(
            table = table,
            columns = emptyList(),
            sourceText = createCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(createCtx.start.line, createCtx.start.charPositionInLine)
        )
    }

    override fun visitAlterTableStatement(alterCtx: PostgreSqlParser.AlterTableStatementContext): Any {
        val tableName = alterCtx.tableName()
        val table = tableRefFrom(tableName)
        return AlterTableStmt(
            table = table,
            actions = emptyList(),
            sourceText = alterCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(alterCtx.start.line, alterCtx.start.charPositionInLine)
        )
    }

    override fun visitDropStatement(dropCtx: PostgreSqlParser.DropStatementContext): Any {
        return DropStmt(
            objectType = DropObjectType.TABLE,
            objectName = dropCtx.text,
            sourceText = dropCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(dropCtx.start.line, dropCtx.start.charPositionInLine)
        )
    }

    /**
     * tableName : id DOT id | id
     * id(0) = schema (when 2 ids), id(1) = name; or id(0) = name (when 1 id)
     */
    private fun tableRefFrom(tableNameCtx: PostgreSqlParser.TableNameContext): TableRef {
        val ids = tableNameCtx.id()
        return if (ids.size >= 2) {
            TableRef(name = ids[1].text, schema = ids[0].text)
        } else {
            TableRef(name = ids[0].text)
        }
    }
}
