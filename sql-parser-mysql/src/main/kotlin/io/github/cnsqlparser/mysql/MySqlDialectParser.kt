package io.github.cnsqlparser.mysql

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
 * MySQL方言解析器
 *
 * MySQL dialect parser backed by ANTLR4.
 * Also supports MySQL-family domestic databases:
 * - OceanBase (MySQL mode)
 * - TiDB
 * - GoldenDB
 * - StarRocks
 */
class MySqlDialectParser : AntlrParserAdapter() {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun createLexer(input: CharStream): Lexer =
        MySqlLexer(input)

    override fun createParser(tokens: CommonTokenStream): Parser =
        MySqlParser(tokens)

    override fun buildResult(ctx: ParseContext, parser: Parser, warnings: List<String>): ParseResult {
        val mysqlParser = parser as MySqlParser
        val rootCtx = mysqlParser.root()
        val visitor = MySqlAstVisitor(ctx)
        val statements = visitor.buildStatements(rootCtx)
        return ParseResult(
            statements = statements,
            warnings = warnings.map { ParseWarning(it) },
            dialect = ctx.product
        )
    }
}

/**
 * MySQL AST访问者 - 将ANTLR解析树映射到统一SQL模型
 *
 * Visits the ANTLR parse tree and maps it to the Unified SQL Model.
 */
internal class MySqlAstVisitor(private val ctx: ParseContext) : MySqlParserBaseVisitor<Any>() {

    fun buildStatements(rootCtx: MySqlParser.RootContext): List<io.github.cnsqlparser.model.statement.SqlStatement> {
        return rootCtx.sqlStatement().mapNotNull { stmtCtx ->
            try {
                visitSqlStatement(stmtCtx) as? io.github.cnsqlparser.model.statement.SqlStatement
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

    override fun visitSqlStatement(stmtCtx: MySqlParser.SqlStatementContext): Any? {
        return when {
            stmtCtx.selectStatement() != null -> visitSelectStatement(stmtCtx.selectStatement())
            stmtCtx.insertStatement() != null -> visitInsertStatement(stmtCtx.insertStatement())
            stmtCtx.updateStatement() != null -> visitUpdateStatement(stmtCtx.updateStatement())
            stmtCtx.deleteStatement() != null -> visitDeleteStatement(stmtCtx.deleteStatement())
            stmtCtx.createTableStatement() != null -> visitCreateTableStatement(stmtCtx.createTableStatement())
            stmtCtx.alterTableStatement() != null -> visitAlterTableStatement(stmtCtx.alterTableStatement())
            stmtCtx.dropStatement() != null -> visitDropStatement(stmtCtx.dropStatement())
            stmtCtx.useStatement() != null -> visitUseStatement(stmtCtx.useStatement())
            else -> GenericStmt(stmtCtx.text, dialect = ctx.product, compatibilityMode = ctx.mode)
        }
    }

    override fun visitSelectStatement(selectCtx: MySqlParser.SelectStatementContext): Any {
        val query = buildQueryBlock(selectCtx)
        return SelectStmt(
            query = query,
            sourceText = selectCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(
                selectCtx.start.line,
                selectCtx.start.charPositionInLine
            )
        )
    }

    private fun buildQueryBlock(selectCtx: MySqlParser.SelectStatementContext): io.github.cnsqlparser.model.query.QueryBlock {
        // Simplified: return a placeholder QueryBlock
        // Full implementation would walk the full expression tree
        return io.github.cnsqlparser.model.query.QueryBlock(
            selectItems = emptyList()
        )
    }

    override fun visitInsertStatement(insertCtx: MySqlParser.InsertStatementContext): Any {
        val tableName = insertCtx.tableName()
        val table = TableRef(
            name = tableName.id(0).text,
            schema = if (tableName.id().size > 1) tableName.id(0).text else null
        )
        return InsertStmt(
            table = table,
            sourceText = insertCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(insertCtx.start.line, insertCtx.start.charPositionInLine)
        )
    }

    override fun visitUpdateStatement(updateCtx: MySqlParser.UpdateStatementContext): Any {
        val tableName = updateCtx.tableName()
        val table = TableRef(
            name = tableName.id(0).text,
            schema = if (tableName.id().size > 1) tableName.id(0).text else null
        )
        return UpdateStmt(
            table = table,
            assignments = emptyList(),
            sourceText = updateCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(updateCtx.start.line, updateCtx.start.charPositionInLine)
        )
    }

    override fun visitDeleteStatement(deleteCtx: MySqlParser.DeleteStatementContext): Any {
        val tableName = deleteCtx.tableName()
        val table = TableRef(
            name = tableName.id(0).text,
            schema = if (tableName.id().size > 1) tableName.id(0).text else null
        )
        return DeleteStmt(
            table = table,
            sourceText = deleteCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(deleteCtx.start.line, deleteCtx.start.charPositionInLine)
        )
    }

    override fun visitCreateTableStatement(createCtx: MySqlParser.CreateTableStatementContext): Any {
        val tableName = createCtx.tableName()
        val table = TableRef(
            name = tableName.id(0).text,
            schema = if (tableName.id().size > 1) tableName.id(0).text else null
        )
        return CreateTableStmt(
            table = table,
            columns = emptyList(),
            sourceText = createCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(createCtx.start.line, createCtx.start.charPositionInLine)
        )
    }

    override fun visitAlterTableStatement(alterCtx: MySqlParser.AlterTableStatementContext): Any {
        val tableName = alterCtx.tableName()
        val table = TableRef(
            name = tableName.id(0).text,
            schema = if (tableName.id().size > 1) tableName.id(0).text else null
        )
        return AlterTableStmt(
            table = table,
            actions = emptyList(),
            sourceText = alterCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(alterCtx.start.line, alterCtx.start.charPositionInLine)
        )
    }

    override fun visitDropStatement(dropCtx: MySqlParser.DropStatementContext): Any {
        return DropStmt(
            objectType = DropObjectType.TABLE,
            objectName = dropCtx.text,
            sourceText = dropCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(dropCtx.start.line, dropCtx.start.charPositionInLine)
        )
    }

    override fun visitUseStatement(useCtx: MySqlParser.UseStatementContext): Any {
        return UseStmt(
            database = useCtx.id().text,
            sourceText = useCtx.text,
            dialect = ctx.product,
            compatibilityMode = ctx.mode,
            position = ParsePosition(useCtx.start.line, useCtx.start.charPositionInLine)
        )
    }
}
