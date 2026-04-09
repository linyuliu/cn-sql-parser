package io.github.cnsqlparser.model.statement

import io.github.cnsqlparser.common.CompatibilityMode
import io.github.cnsqlparser.common.ParsePosition
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.model.dialect.DialectAttribute
import io.github.cnsqlparser.model.expression.ColumnRef
import io.github.cnsqlparser.model.expression.SqlExpression
import io.github.cnsqlparser.model.query.FromItem
import io.github.cnsqlparser.model.query.LimitSpec
import io.github.cnsqlparser.model.query.OrderItem
import io.github.cnsqlparser.model.query.QueryBlock

/**
 * 统一SQL模型 - 第一层：Statement层
 *
 * Statement Layer of the Unified SQL Model (统一SQL模型, USM).
 *
 * 所有SQL语句类型继承此密封类（sealed class）。
 * Kotlin sealed class 确保所有子类型已知，便于 when 穷举处理。
 */
sealed class SqlStatement {
    /** 原始SQL文本（可选，用于调试和格式化） */
    open val sourceText: String? = null
    /** 语句在源文件中的位置 */
    open val position: ParsePosition? = null
    /** 解析该语句时使用的数据库产品方言 */
    open val dialect: ProductDialect? = null
    /** 解析该语句时使用的兼容模式（对多模式国产库尤为重要） */
    open val compatibilityMode: CompatibilityMode? = null
    /** 方言扩展属性（Hint、厂商特性、模式特定节点等） */
    open val dialectAttributes: List<DialectAttribute> = emptyList()
}

// ─── DML ─────────────────────────────────────────────────────────────────────

/** SELECT 语句 */
data class SelectStmt(
    val query: QueryBlock,
    override val sourceText: String? = null,
    override val position: ParsePosition? = null,
    override val dialect: ProductDialect? = null,
    override val compatibilityMode: CompatibilityMode? = null,
    override val dialectAttributes: List<DialectAttribute> = emptyList()
) : SqlStatement()

/** INSERT 语句 */
data class InsertStmt(
    val table: TableRef,
    val columns: List<ColumnRef> = emptyList(),
    val values: List<List<SqlExpression>> = emptyList(),
    val selectQuery: QueryBlock? = null,
    /** ON DUPLICATE KEY UPDATE (MySQL) / ON CONFLICT (PG) */
    val onConflict: OnConflictClause? = null,
    override val sourceText: String? = null,
    override val position: ParsePosition? = null,
    override val dialect: ProductDialect? = null,
    override val compatibilityMode: CompatibilityMode? = null,
    override val dialectAttributes: List<DialectAttribute> = emptyList()
) : SqlStatement()

/** UPDATE 语句 */
data class UpdateStmt(
    val table: TableRef,
    val assignments: List<Assignment>,
    val where: SqlExpression? = null,
    val fromItems: List<FromItem> = emptyList(),
    val orderBy: List<OrderItem> = emptyList(),
    val limit: LimitSpec? = null,
    override val sourceText: String? = null,
    override val position: ParsePosition? = null,
    override val dialect: ProductDialect? = null,
    override val compatibilityMode: CompatibilityMode? = null,
    override val dialectAttributes: List<DialectAttribute> = emptyList()
) : SqlStatement()

/** DELETE 语句 */
data class DeleteStmt(
    val table: TableRef,
    val where: SqlExpression? = null,
    val usingItems: List<FromItem> = emptyList(),
    val orderBy: List<OrderItem> = emptyList(),
    val limit: LimitSpec? = null,
    override val sourceText: String? = null,
    override val position: ParsePosition? = null,
    override val dialect: ProductDialect? = null,
    override val compatibilityMode: CompatibilityMode? = null,
    override val dialectAttributes: List<DialectAttribute> = emptyList()
) : SqlStatement()

/** MERGE 语句 (Oracle / SQL Server / PG 15+) */
data class MergeStmt(
    val targetTable: TableRef,
    val sourceItem: FromItem,
    val onCondition: SqlExpression,
    val whenClauses: List<MergeWhenClause>,
    override val sourceText: String? = null,
    override val position: ParsePosition? = null,
    override val dialect: ProductDialect? = null,
    override val compatibilityMode: CompatibilityMode? = null,
    override val dialectAttributes: List<DialectAttribute> = emptyList()
) : SqlStatement()

// ─── DDL ─────────────────────────────────────────────────────────────────────

/** CREATE TABLE 语句 */
data class CreateTableStmt(
    val table: TableRef,
    val columns: List<ColumnDefinition>,
    val constraints: List<TableConstraint> = emptyList(),
    val ifNotExists: Boolean = false,
    val asSelect: QueryBlock? = null,
    val tableOptions: Map<String, String> = emptyMap(),
    override val sourceText: String? = null,
    override val position: ParsePosition? = null,
    override val dialect: ProductDialect? = null,
    override val compatibilityMode: CompatibilityMode? = null,
    override val dialectAttributes: List<DialectAttribute> = emptyList()
) : SqlStatement()

/** ALTER TABLE 语句 */
data class AlterTableStmt(
    val table: TableRef,
    val actions: List<AlterTableAction>,
    override val sourceText: String? = null,
    override val position: ParsePosition? = null,
    override val dialect: ProductDialect? = null,
    override val compatibilityMode: CompatibilityMode? = null,
    override val dialectAttributes: List<DialectAttribute> = emptyList()
) : SqlStatement()

/** DROP 语句 */
data class DropStmt(
    val objectType: DropObjectType,
    val objectName: String,
    val schema: String? = null,
    val ifExists: Boolean = false,
    val cascade: Boolean = false,
    override val sourceText: String? = null,
    override val position: ParsePosition? = null,
    override val dialect: ProductDialect? = null,
    override val compatibilityMode: CompatibilityMode? = null,
    override val dialectAttributes: List<DialectAttribute> = emptyList()
) : SqlStatement()

/** USE / SET SCHEMA 语句（切换数据库/Schema） */
data class UseStmt(
    val database: String,
    override val sourceText: String? = null,
    override val position: ParsePosition? = null,
    override val dialect: ProductDialect? = null,
    override val compatibilityMode: CompatibilityMode? = null,
    override val dialectAttributes: List<DialectAttribute> = emptyList()
) : SqlStatement()

/** 通用/未识别语句（容错解析时使用） */
data class GenericStmt(
    val statementText: String,
    override val sourceText: String? = null,
    override val position: ParsePosition? = null,
    override val dialect: ProductDialect? = null,
    override val compatibilityMode: CompatibilityMode? = null,
    override val dialectAttributes: List<DialectAttribute> = emptyList()
) : SqlStatement()

// ─── Supporting types ─────────────────────────────────────────────────────────

/** 表引用 */
data class TableRef(
    val name: String,
    val schema: String? = null,
    val catalog: String? = null,
    val alias: String? = null
) {
    val qualifiedName: String
        get() = listOfNotNull(catalog, schema, name).joinToString(".")
}

/** SET赋值（UPDATE用） */
data class Assignment(
    val column: ColumnRef,
    val value: SqlExpression
)

/** ON CONFLICT / ON DUPLICATE KEY 子句 */
data class OnConflictClause(
    val conflictColumns: List<ColumnRef> = emptyList(),
    val action: OnConflictAction,
    val assignments: List<Assignment> = emptyList(),
    val whereCondition: SqlExpression? = null
)

enum class OnConflictAction { DO_NOTHING, DO_UPDATE }

/** MERGE WHEN 子句 */
sealed class MergeWhenClause
data class MergeWhenMatched(
    val condition: SqlExpression? = null,
    val action: MergeMatchedAction
) : MergeWhenClause()
data class MergeWhenNotMatched(
    val condition: SqlExpression? = null,
    val columns: List<ColumnRef> = emptyList(),
    val values: List<SqlExpression> = emptyList()
) : MergeWhenClause()

sealed class MergeMatchedAction
data class MergeUpdate(val assignments: List<Assignment>) : MergeMatchedAction()
object MergeDelete : MergeMatchedAction()

/** 列定义（CREATE TABLE 中使用） */
data class ColumnDefinition(
    val name: String,
    val dataType: String,
    val typeParams: List<String> = emptyList(),
    val nullable: Boolean = true,
    val defaultValue: SqlExpression? = null,
    val autoIncrement: Boolean = false,
    val primaryKey: Boolean = false,
    val unique: Boolean = false,
    val comment: String? = null,
    val position: ParsePosition? = null
)

/** 表约束 */
sealed class TableConstraint {
    abstract val name: String?
}
data class PrimaryKeyConstraint(
    override val name: String? = null,
    val columns: List<String>
) : TableConstraint()
data class UniqueConstraint(
    override val name: String? = null,
    val columns: List<String>
) : TableConstraint()
data class ForeignKeyConstraint(
    override val name: String? = null,
    val columns: List<String>,
    val referencedTable: String,
    val referencedColumns: List<String>
) : TableConstraint()
data class CheckConstraint(
    override val name: String? = null,
    val condition: SqlExpression
) : TableConstraint()

/** ALTER TABLE 操作 */
sealed class AlterTableAction
data class AddColumnAction(val column: ColumnDefinition) : AlterTableAction()
data class DropColumnAction(val columnName: String, val ifExists: Boolean = false) : AlterTableAction()
data class ModifyColumnAction(val column: ColumnDefinition) : AlterTableAction()
data class RenameColumnAction(val oldName: String, val newName: String) : AlterTableAction()
data class AddConstraintAction(val constraint: TableConstraint) : AlterTableAction()
data class DropConstraintAction(val constraintName: String) : AlterTableAction()
data class RenameTableAction(val newName: String) : AlterTableAction()

/** DROP 对象类型 */
enum class DropObjectType { TABLE, VIEW, INDEX, SEQUENCE, PROCEDURE, FUNCTION, TRIGGER, SCHEMA, DATABASE }
