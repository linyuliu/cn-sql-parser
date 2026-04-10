package io.github.cnsqlparser.druid

import com.alibaba.druid.sql.SQLUtils
import com.alibaba.druid.sql.ast.SQLStatement
import com.alibaba.druid.sql.ast.SQLName
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr
import com.alibaba.druid.sql.ast.statement.*
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement
import io.github.cnsqlparser.common.CompatibilityMode
import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.common.SqlSyntaxException
import io.github.cnsqlparser.model.ParseResult
import io.github.cnsqlparser.model.ParseWarning
import io.github.cnsqlparser.model.expression.ColumnRef
import io.github.cnsqlparser.model.expression.RawExpr
import io.github.cnsqlparser.model.query.QueryBlock
import io.github.cnsqlparser.model.query.TableFromItem
import io.github.cnsqlparser.model.statement.*
import org.slf4j.LoggerFactory

/**
 * Alibaba Druid SQL解析桥接层
 *
 * Druid SQL parser bridge for domestic Chinese database support.
 *
 * Druid原生支持的国产/特殊数据库：
 * - 达梦 DM (DbType.dm) - Oracle兼容
 * - 人大金仓 Kingbase (DbType.kingbase) - 多兼容模式
 * - GaussDB (DbType.gaussdb) - PG系
 * - HighGo (DbType.highgo) - PG系
 * - OceanBase MySQL (DbType.oceanbase)
 * - OceanBase Oracle (DbType.oceanbase_oracle)
 * - StarRocks (DbType.starrocks)
 * - TiDB (DbType.tidb)
 * - OSCAR神通 (DbType.oscar)
 * - PolarDB (DbType.polardb)
 * - ClickHouse (DbType.clickhouse)
 * - Hive (DbType.hive)
 *
 * 策略：
 * - 对国产数据库，Druid往往比ANTLR自定义grammar更完整地支持方言细节
 * - 先用Druid解析，再映射到统一SQL模型
 * - 对Druid不支持的特殊语法，标记为GenericStmt并记录警告
 * - 对极复杂的私有语法（乙方定制），接受GenericStmt降级
 */
class DruidParserBridge {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 解析SQL到统一SQL模型
     */
    fun parse(ctx: ParseContext): ParseResult {
        val dbType = DruidDbTypeMapper.forProduct(ctx.product, ctx.mode)
        return try {
            val druidStatements = SQLUtils.parseStatements(ctx.sql, dbType)
            val warnings = mutableListOf<ParseWarning>()
            val statements = druidStatements.mapNotNull { druidStmt ->
                try {
                    convertStatement(druidStmt, ctx)
                } catch (e: Exception) {
                    logger.warn("Druid statement conversion warning for ${ctx.product}(${ctx.mode}): ${e.message}")
                    warnings.add(ParseWarning("Statement conversion degraded to GenericStmt: ${e.message}"))
                    GenericStmt(
                        statementText = druidStmt.toString(),
                        dialect = ctx.product,
                        compatibilityMode = ctx.mode
                    )
                }
            }
            ParseResult(
                statements = statements,
                warnings = warnings,
                dialect = ctx.product
            )
        } catch (e: Exception) {
            throw SqlSyntaxException(
                message = "Druid failed to parse SQL for ${ctx.product}(${ctx.mode}): ${e.message}",
                cause = e
            )
        }
    }

    /**
     * 格式化SQL（利用Druid的SQL格式化能力）
     */
    fun format(sql: String, product: ProductDialect, mode: CompatibilityMode): String {
        val dbType = DruidDbTypeMapper.forProduct(product, mode)
        return try {
            SQLUtils.format(sql, dbType)
        } catch (e: Exception) {
            logger.warn("Druid SQL format failed: ${e.message}")
            sql
        }
    }

    /**
     * 提取表名（快速路径）
     */
    fun extractTables(sql: String, product: ProductDialect, mode: CompatibilityMode): List<String> {
        val dbType = DruidDbTypeMapper.forProduct(product, mode)
        return try {
            val statements = SQLUtils.parseStatements(sql, dbType)
            statements.flatMap { stmt ->
                val visitor = com.alibaba.druid.sql.visitor.SchemaStatVisitor()
                stmt.accept(visitor)
                visitor.tables.keys.map { it.name }
            }
        } catch (e: Exception) {
            logger.warn("Druid table extraction failed: ${e.message}")
            emptyList()
        }
    }

    private fun convertStatement(druidStmt: SQLStatement, ctx: ParseContext): SqlStatement {
        return when (druidStmt) {
            is SQLSelectStatement          -> convertSelect(druidStmt, ctx)
            is SQLInsertStatement          -> convertInsert(druidStmt, ctx)
            is SQLUpdateStatement          -> convertUpdate(druidStmt, ctx)
            is SQLDeleteStatement          -> convertDelete(druidStmt, ctx)
            is MySqlCreateTableStatement   -> convertMySqlCreateTable(druidStmt, ctx)
            is SQLCreateTableStatement     -> convertCreateTable(druidStmt, ctx)
            is SQLAlterTableStatement      -> convertAlterTable(druidStmt, ctx)
            is SQLDropTableStatement       -> convertDropTable(druidStmt, ctx)
            is SQLUseStatement             -> convertUse(druidStmt, ctx)
            is SQLMergeStatement           -> convertMerge(druidStmt, ctx)
            else -> GenericStmt(
                statementText = druidStmt.toString(),
                dialect = ctx.product,
                compatibilityMode = ctx.mode
            )
        }
    }

    private fun convertSelect(stmt: SQLSelectStatement, ctx: ParseContext): SelectStmt {
        return SelectStmt(
            query = QueryBlock(),
            sourceText = stmt.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertInsert(stmt: SQLInsertStatement, ctx: ParseContext): InsertStmt {
        val tableName = stmt.tableName
        val (name, schema) = parseTableName(tableName)
        val columns = stmt.columns?.map { col ->
            ColumnRef(name = col.toString())
        } ?: emptyList()
        return InsertStmt(
            table = TableRef(name = name, schema = schema),
            columns = columns,
            sourceText = stmt.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertUpdate(stmt: SQLUpdateStatement, ctx: ParseContext): UpdateStmt {
        val tableName = stmt.tableSource?.toString() ?: ""
        return UpdateStmt(
            table = TableRef(name = tableName),
            assignments = emptyList(),
            sourceText = stmt.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertDelete(stmt: SQLDeleteStatement, ctx: ParseContext): DeleteStmt {
        val tableName = stmt.tableName
        val (name, schema) = parseTableName(tableName)
        return DeleteStmt(
            table = TableRef(name = name, schema = schema),
            sourceText = stmt.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertCreateTable(stmt: SQLCreateTableStatement, ctx: ParseContext): CreateTableStmt {
        val (name, schema) = parseTableName(stmt.name)
        val columns = stmt.tableElementList
            .filterIsInstance<SQLColumnDefinition>()
            .map { col ->
                ColumnDefinition(
                    name = col.columnName ?: col.name?.simpleName ?: "",
                    dataType = col.dataType?.name ?: "UNKNOWN",
                    nullable = !col.containsNotNullConstraint()
                )
            }
        return CreateTableStmt(
            table = TableRef(name = name, schema = schema),
            columns = columns,
            sourceText = stmt.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertMySqlCreateTable(stmt: MySqlCreateTableStatement, ctx: ParseContext): CreateTableStmt {
        val (name, schema) = parseTableName(stmt.name)
        val columns = stmt.tableElementList
            .filterIsInstance<SQLColumnDefinition>()
            .map { col ->
                ColumnDefinition(
                    name = col.columnName ?: col.name?.simpleName ?: "",
                    dataType = col.dataType?.name ?: "UNKNOWN",
                    nullable = !col.containsNotNullConstraint(),
                    comment = col.comment?.toString()?.trim('\'', '"')
                )
            }
        return CreateTableStmt(
            table = TableRef(name = name, schema = schema),
            columns = columns,
            sourceText = stmt.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertAlterTable(stmt: SQLAlterTableStatement, ctx: ParseContext): AlterTableStmt {
        val (name, schema) = parseTableName(stmt.name)
        return AlterTableStmt(
            table = TableRef(name = name, schema = schema),
            actions = emptyList(),
            sourceText = stmt.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertDropTable(stmt: SQLDropTableStatement, ctx: ParseContext): DropStmt {
        val firstName = stmt.tableSources.firstOrNull()?.toString() ?: ""
        return DropStmt(
            objectType = DropObjectType.TABLE,
            objectName = firstName,
            ifExists = stmt.isIfExists,
            sourceText = stmt.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertUse(stmt: SQLUseStatement, ctx: ParseContext): UseStmt {
        return UseStmt(
            database = stmt.database?.simpleName ?: "",
            sourceText = stmt.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    private fun convertMerge(stmt: SQLMergeStatement, ctx: ParseContext): MergeStmt {
        val into = stmt.into
        val name = into?.toString() ?: ""
        return MergeStmt(
            targetTable = TableRef(name = name),
            sourceItem = TableFromItem(name = "source"),
            onCondition = RawExpr(stmt.on?.toString() ?: ""),
            whenClauses = emptyList(),
            sourceText = stmt.toString(),
            dialect = ctx.product,
            compatibilityMode = ctx.mode
        )
    }

    /**
     * 从 Druid SQLName 中提取 (name, schema) 对
     *
     * - SQLIdentifierExpr → (name, null)
     * - SQLPropertyExpr   → (name, ownerName)  e.g. `schema.table`
     */
    private fun parseTableName(sqlName: SQLName?): Pair<String, String?> {
        if (sqlName == null) return "" to null
        return when (sqlName) {
            is SQLPropertyExpr -> sqlName.name to sqlName.ownerName
            else -> sqlName.simpleName to null
        }
    }
}
