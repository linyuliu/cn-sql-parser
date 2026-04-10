package io.github.cnsqlparser.converter.generator

import io.github.cnsqlparser.common.DialectFamily
import io.github.cnsqlparser.converter.mapping.DataTypeMapper
import io.github.cnsqlparser.converter.mapping.FunctionMapper
import io.github.cnsqlparser.model.expression.*
import io.github.cnsqlparser.model.query.*
import io.github.cnsqlparser.model.statement.*

/**
 * 基础SQL生成器
 *
 * Base SQL generator with common generation logic shared across dialects.
 * Subclasses override specific methods for dialect-aware output
 * (identifier quoting, pagination, type names, function names, etc.).
 */
abstract class BaseSqlGenerator(
    /** The source dialect family (where the AST came from) */
    private val sourceFamily: DialectFamily?,
    /** The target dialect family for this generator */
    val targetFamily: DialectFamily
) : SqlGenerator {

    // ─── Abstract / dialect-specific hooks ────────────────────────────────────

    /** Quote an identifier (table/column name) according to the dialect */
    abstract fun quoteIdentifier(name: String): String

    /** Whether the dialect supports LIMIT/OFFSET syntax */
    open fun supportsLimitOffset(): Boolean = true

    /** Whether the dialect supports FETCH FIRST syntax */
    open fun supportsFetchFirst(): Boolean = false

    /** Whether the dialect uses TOP for row limiting */
    open fun supportsTop(): Boolean = false

    /** Generate the auto-increment clause for a column (dialect-specific) */
    open fun generateAutoIncrement(): String = ""

    /** Generate IF NOT EXISTS clause for CREATE TABLE (may differ by dialect) */
    open fun generateIfNotExists(): String = "IF NOT EXISTS"

    // ─── Statement generation ──────────────────────────────────────────────

    override fun generate(stmt: SqlStatement): String = when (stmt) {
        is SelectStmt      -> generateSelect(stmt)
        is InsertStmt      -> generateInsert(stmt)
        is UpdateStmt      -> generateUpdate(stmt)
        is DeleteStmt      -> generateDelete(stmt)
        is MergeStmt       -> generateMerge(stmt)
        is CreateTableStmt -> generateCreateTable(stmt)
        is AlterTableStmt  -> generateAlterTable(stmt)
        is DropStmt        -> generateDrop(stmt)
        is UseStmt         -> generateUse(stmt)
        is GenericStmt     -> stmt.statementText
    }

    // ─── SELECT ──────────────────────────────────────────────────────────────

    protected open fun generateSelect(stmt: SelectStmt): String {
        return generateQuery(stmt.query)
    }

    override fun generateQuery(query: QueryBlock): String {
        val sb = StringBuilder()

        // WITH (CTEs)
        if (query.ctes.isNotEmpty()) {
            sb.append("WITH ")
            sb.append(query.ctes.joinToString(", ") { generateCte(it) })
            sb.append(" ")
        }

        sb.append("SELECT ")
        if (query.distinct) sb.append("DISTINCT ")

        // SELECT items
        if (query.selectItems.isEmpty()) {
            sb.append("*")
        } else {
            sb.append(query.selectItems.joinToString(", ") { generateSelectItem(it) })
        }

        // FROM
        if (query.fromItems.isNotEmpty()) {
            sb.append(" FROM ")
            sb.append(query.fromItems.joinToString(", ") { generateFromItem(it) })
        }

        // WHERE
        val where = query.where
        if (where != null) {
            sb.append(" WHERE ")
            sb.append(generateExpression(where))
        }

        // GROUP BY
        if (query.groupBy.isNotEmpty()) {
            sb.append(" GROUP BY ")
            sb.append(query.groupBy.joinToString(", ") { generateExpression(it) })
        }

        // HAVING
        val having = query.having
        if (having != null) {
            sb.append(" HAVING ")
            sb.append(generateExpression(having))
        }

        // SET operations (UNION / INTERSECT / EXCEPT)
        val setOp = query.setOp
        if (setOp != null) {
            sb.append(" ")
            sb.append(generateSetOp(setOp))
        }

        // ORDER BY
        if (query.orderBy.isNotEmpty()) {
            sb.append(" ORDER BY ")
            sb.append(query.orderBy.joinToString(", ") { generateOrderItem(it) })
        }

        // Pagination: LIMIT/OFFSET or FETCH FIRST
        appendPagination(sb, query)

        return sb.toString()
    }

    protected open fun appendPagination(sb: StringBuilder, query: QueryBlock) {
        val limit = query.limit
        val fetch = query.fetch
        val offset = query.offset

        if (limit != null && supportsLimitOffset()) {
            sb.append(" LIMIT ")
            sb.append(generateExpression(limit.limit))
            val effectiveOffset = limit.offset ?: offset
            if (effectiveOffset != null) {
                sb.append(" OFFSET ")
                sb.append(generateExpression(effectiveOffset))
            }
        } else if (fetch != null && supportsFetchFirst()) {
            if (offset != null) {
                sb.append(" OFFSET ")
                sb.append(generateExpression(offset))
                sb.append(" ROWS")
            }
            sb.append(" FETCH FIRST ")
            sb.append(generateExpression(fetch.count))
            sb.append(" ROWS")
            if (fetch.withTies) {
                sb.append(" WITH TIES")
            } else {
                sb.append(" ONLY")
            }
        } else if (limit != null && supportsFetchFirst()) {
            // Convert LIMIT to FETCH FIRST
            val effectiveOffset = limit.offset ?: offset
            if (effectiveOffset != null) {
                sb.append(" OFFSET ")
                sb.append(generateExpression(effectiveOffset))
                sb.append(" ROWS")
            }
            sb.append(" FETCH FIRST ")
            sb.append(generateExpression(limit.limit))
            sb.append(" ROWS ONLY")
        }
    }

    // ─── INSERT ──────────────────────────────────────────────────────────────

    protected open fun generateInsert(stmt: InsertStmt): String {
        val sb = StringBuilder("INSERT INTO ")
        sb.append(generateTableRef(stmt.table))

        if (stmt.columns.isNotEmpty()) {
            sb.append(" (")
            sb.append(stmt.columns.joinToString(", ") { quoteIdentifier(it.name) })
            sb.append(")")
        }

        val selectQuery = stmt.selectQuery
        if (selectQuery != null) {
            sb.append(" ")
            sb.append(generateQuery(selectQuery))
        } else if (stmt.values.isNotEmpty()) {
            sb.append(" VALUES ")
            sb.append(stmt.values.joinToString(", ") { row ->
                "(" + row.joinToString(", ") { generateExpression(it) } + ")"
            })
        }

        val onConflict = stmt.onConflict
        if (onConflict != null) {
            sb.append(generateOnConflict(onConflict))
        }

        return sb.toString()
    }

    protected open fun generateOnConflict(clause: OnConflictClause): String {
        // Default: PostgreSQL-style ON CONFLICT
        val sb = StringBuilder(" ON CONFLICT")
        if (clause.conflictColumns.isNotEmpty()) {
            sb.append(" (")
            sb.append(clause.conflictColumns.joinToString(", ") { quoteIdentifier(it.name) })
            sb.append(")")
        }
        when (clause.action) {
            OnConflictAction.DO_NOTHING -> sb.append(" DO NOTHING")
            OnConflictAction.DO_UPDATE -> {
                sb.append(" DO UPDATE SET ")
                sb.append(clause.assignments.joinToString(", ") { generateAssignment(it) })
                val whereCondition = clause.whereCondition
                if (whereCondition != null) {
                    sb.append(" WHERE ")
                    sb.append(generateExpression(whereCondition))
                }
            }
        }
        return sb.toString()
    }

    // ─── UPDATE ──────────────────────────────────────────────────────────────

    protected open fun generateUpdate(stmt: UpdateStmt): String {
        val sb = StringBuilder("UPDATE ")
        sb.append(generateTableRef(stmt.table))
        sb.append(" SET ")
        sb.append(stmt.assignments.joinToString(", ") { generateAssignment(it) })

        if (stmt.fromItems.isNotEmpty()) {
            sb.append(" FROM ")
            sb.append(stmt.fromItems.joinToString(", ") { generateFromItem(it) })
        }

        val updateWhere = stmt.where
        if (updateWhere != null) {
            sb.append(" WHERE ")
            sb.append(generateExpression(updateWhere))
        }

        if (stmt.orderBy.isNotEmpty()) {
            sb.append(" ORDER BY ")
            sb.append(stmt.orderBy.joinToString(", ") { generateOrderItem(it) })
        }

        val updateLimit = stmt.limit
        if (updateLimit != null && supportsLimitOffset()) {
            sb.append(" LIMIT ")
            sb.append(generateExpression(updateLimit.limit))
        }

        return sb.toString()
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    protected open fun generateDelete(stmt: DeleteStmt): String {
        val sb = StringBuilder("DELETE FROM ")
        sb.append(generateTableRef(stmt.table))

        if (stmt.usingItems.isNotEmpty()) {
            sb.append(" USING ")
            sb.append(stmt.usingItems.joinToString(", ") { generateFromItem(it) })
        }

        val deleteWhere = stmt.where
        if (deleteWhere != null) {
            sb.append(" WHERE ")
            sb.append(generateExpression(deleteWhere))
        }

        if (stmt.orderBy.isNotEmpty()) {
            sb.append(" ORDER BY ")
            sb.append(stmt.orderBy.joinToString(", ") { generateOrderItem(it) })
        }

        val deleteLimit = stmt.limit
        if (deleteLimit != null && supportsLimitOffset()) {
            sb.append(" LIMIT ")
            sb.append(generateExpression(deleteLimit.limit))
        }

        return sb.toString()
    }

    // ─── MERGE ───────────────────────────────────────────────────────────────

    protected open fun generateMerge(stmt: MergeStmt): String {
        val sb = StringBuilder("MERGE INTO ")
        sb.append(generateTableRef(stmt.targetTable))
        sb.append(" USING ")
        sb.append(generateFromItem(stmt.sourceItem))
        sb.append(" ON ")
        sb.append(generateExpression(stmt.onCondition))

        for (clause in stmt.whenClauses) {
            when (clause) {
                is MergeWhenMatched -> {
                    sb.append(" WHEN MATCHED")
                    val matchedCond = clause.condition
                    if (matchedCond != null) {
                        sb.append(" AND ")
                        sb.append(generateExpression(matchedCond))
                    }
                    sb.append(" THEN ")
                    when (val action = clause.action) {
                        is MergeUpdate -> {
                            sb.append("UPDATE SET ")
                            sb.append(action.assignments.joinToString(", ") { generateAssignment(it) })
                        }
                        is MergeDelete -> sb.append("DELETE")
                    }
                }
                is MergeWhenNotMatched -> {
                    sb.append(" WHEN NOT MATCHED")
                    val notMatchedCond = clause.condition
                    if (notMatchedCond != null) {
                        sb.append(" AND ")
                        sb.append(generateExpression(notMatchedCond))
                    }
                    sb.append(" THEN INSERT")
                    if (clause.columns.isNotEmpty()) {
                        sb.append(" (")
                        sb.append(clause.columns.joinToString(", ") { quoteIdentifier(it.name) })
                        sb.append(")")
                    }
                    if (clause.values.isNotEmpty()) {
                        sb.append(" VALUES (")
                        sb.append(clause.values.joinToString(", ") { generateExpression(it) })
                        sb.append(")")
                    }
                }
            }
        }

        return sb.toString()
    }

    // ─── DDL: CREATE TABLE ───────────────────────────────────────────────────

    protected open fun generateCreateTable(stmt: CreateTableStmt): String {
        val sb = StringBuilder("CREATE TABLE ")

        if (stmt.ifNotExists) {
            sb.append(generateIfNotExists())
            sb.append(" ")
        }

        sb.append(generateTableRef(stmt.table))

        val asSelect = stmt.asSelect
        if (asSelect != null) {
            sb.append(" AS ")
            sb.append(generateQuery(asSelect))
            return sb.toString()
        }

        sb.append(" (\n")

        val columnDefs = stmt.columns.map { "  ${generateColumnDef(it)}" }
        val constraintDefs = stmt.constraints.map { "  ${generateConstraint(it)}" }
        val allDefs = columnDefs + constraintDefs
        sb.append(allDefs.joinToString(",\n"))

        sb.append("\n)")

        val tableOptions = generateTableOptions(stmt.tableOptions)
        if (tableOptions.isNotBlank()) {
            sb.append(" ")
            sb.append(tableOptions)
        }

        return sb.toString()
    }

    protected open fun generateColumnDef(col: ColumnDefinition): String {
        val sb = StringBuilder()
        sb.append(quoteIdentifier(col.name))
        sb.append(" ")

        // Map data type if converting between dialects
        val (mappedType, mappedParams) = mapDataType(col.dataType, col.typeParams)
        sb.append(mappedType)
        if (mappedParams.isNotEmpty()) {
            sb.append("(")
            sb.append(mappedParams.joinToString(", "))
            sb.append(")")
        }

        if (!col.nullable) sb.append(" NOT NULL")
        val defaultValue = col.defaultValue
        if (defaultValue != null) {
            sb.append(" DEFAULT ")
            sb.append(generateExpression(defaultValue))
        }
        if (col.autoIncrement) {
            val autoInc = generateAutoIncrement()
            if (autoInc.isNotBlank()) {
                sb.append(" ")
                sb.append(autoInc)
            }
        }
        if (col.primaryKey) sb.append(" PRIMARY KEY")
        if (col.unique) sb.append(" UNIQUE")
        val comment = col.comment
        if (comment != null) {
            val commentSql = generateColumnComment(comment)
            if (commentSql.isNotBlank()) {
                sb.append(" ")
                sb.append(commentSql)
            }
        }

        return sb.toString()
    }

    protected open fun generateColumnComment(comment: String): String = ""

    protected open fun generateConstraint(constraint: TableConstraint): String = when (constraint) {
        is PrimaryKeyConstraint -> {
            val cName = constraint.name
            val prefix = if (cName != null) "CONSTRAINT ${quoteIdentifier(cName)} " else ""
            "${prefix}PRIMARY KEY (${constraint.columns.joinToString(", ") { quoteIdentifier(it) }})"
        }
        is UniqueConstraint -> {
            val cName = constraint.name
            val prefix = if (cName != null) "CONSTRAINT ${quoteIdentifier(cName)} " else ""
            "${prefix}UNIQUE (${constraint.columns.joinToString(", ") { quoteIdentifier(it) }})"
        }
        is ForeignKeyConstraint -> {
            val cName = constraint.name
            val prefix = if (cName != null) "CONSTRAINT ${quoteIdentifier(cName)} " else ""
            "${prefix}FOREIGN KEY (${constraint.columns.joinToString(", ") { quoteIdentifier(it) }}) " +
                "REFERENCES ${quoteIdentifier(constraint.referencedTable)} " +
                "(${constraint.referencedColumns.joinToString(", ") { quoteIdentifier(it) }})"
        }
        is CheckConstraint -> {
            val cName = constraint.name
            val prefix = if (cName != null) "CONSTRAINT ${quoteIdentifier(cName)} " else ""
            "${prefix}CHECK (${generateExpression(constraint.condition)})"
        }
    }

    protected open fun generateTableOptions(options: Map<String, String>): String = ""

    protected open fun mapDataType(typeName: String, typeParams: List<String>): Pair<String, List<String>> {
        val from = sourceFamily ?: return typeName to typeParams
        return DataTypeMapper.mapType(typeName, from, targetFamily, typeParams)
    }

    // ─── DDL: ALTER TABLE ────────────────────────────────────────────────────

    protected open fun generateAlterTable(stmt: AlterTableStmt): String {
        val sb = StringBuilder("ALTER TABLE ")
        sb.append(generateTableRef(stmt.table))
        sb.append(" ")

        sb.append(stmt.actions.joinToString(", ") { generateAlterAction(it) })

        return sb.toString()
    }

    protected open fun generateAlterAction(action: AlterTableAction): String = when (action) {
        is AddColumnAction -> "ADD COLUMN ${generateColumnDef(action.column)}"
        is DropColumnAction -> {
            val ifExists = if (action.ifExists) " IF EXISTS" else ""
            "DROP COLUMN${ifExists} ${quoteIdentifier(action.columnName)}"
        }
        is ModifyColumnAction -> "MODIFY COLUMN ${generateColumnDef(action.column)}"
        is RenameColumnAction -> "RENAME COLUMN ${quoteIdentifier(action.oldName)} TO ${quoteIdentifier(action.newName)}"
        is AddConstraintAction -> "ADD ${generateConstraint(action.constraint)}"
        is DropConstraintAction -> "DROP CONSTRAINT ${quoteIdentifier(action.constraintName)}"
        is RenameTableAction -> "RENAME TO ${quoteIdentifier(action.newName)}"
    }

    // ─── DDL: DROP ───────────────────────────────────────────────────────────

    protected open fun generateDrop(stmt: DropStmt): String {
        val sb = StringBuilder("DROP ")
        sb.append(stmt.objectType.name.replace("_", " "))
        sb.append(" ")
        if (stmt.ifExists) sb.append("IF EXISTS ")
        val dropSchema = stmt.schema
        if (dropSchema != null) {
            sb.append(quoteIdentifier(dropSchema))
            sb.append(".")
        }
        sb.append(quoteIdentifier(stmt.objectName))
        if (stmt.cascade) sb.append(" CASCADE")
        return sb.toString()
    }

    // ─── USE ─────────────────────────────────────────────────────────────────

    protected open fun generateUse(stmt: UseStmt): String = "USE ${quoteIdentifier(stmt.database)}"

    // ─── Helpers ─────────────────────────────────────────────────────────────

    protected fun generateTableRef(ref: TableRef): String {
        val parts = mutableListOf<String>()
        val catalog = ref.catalog
        val schema = ref.schema
        if (catalog != null) parts.add(quoteIdentifier(catalog))
        if (schema != null) parts.add(quoteIdentifier(schema))
        parts.add(quoteIdentifier(ref.name))
        val qualifiedName = parts.joinToString(".")
        val alias = ref.alias
        return if (alias != null) "$qualifiedName ${quoteIdentifier(alias)}" else qualifiedName
    }

    protected fun generateSelectItem(item: SelectItem): String {
        val exprSql = generateExpression(item.expression)
        val alias = item.alias
        return if (alias != null) "$exprSql AS ${quoteIdentifier(alias)}" else exprSql
    }

    protected fun generateFromItem(item: FromItem): String = when (item) {
        is TableFromItem -> {
            val parts = mutableListOf<String>()
            val catalog = item.catalog
            val schema = item.schema
            if (catalog != null) parts.add(quoteIdentifier(catalog))
            if (schema != null) parts.add(quoteIdentifier(schema))
            parts.add(quoteIdentifier(item.name))
            val qualifiedName = parts.joinToString(".")
            val alias = item.alias
            if (alias != null) "$qualifiedName ${quoteIdentifier(alias)}" else qualifiedName
        }
        is SubqueryFromItem -> {
            val subSql = generateQuery(item.query)
            val alias = item.alias
            if (alias != null) "($subSql) ${quoteIdentifier(alias)}" else "($subSql)"
        }
        is JoinFromItem -> {
            val leftSql = generateFromItem(item.left)
            val rightSql = generateFromItem(item.right)
            val joinTypeSql = when (item.joinType) {
                JoinType.INNER -> "INNER JOIN"
                JoinType.LEFT -> "LEFT JOIN"
                JoinType.RIGHT -> "RIGHT JOIN"
                JoinType.FULL -> "FULL JOIN"
                JoinType.CROSS -> "CROSS JOIN"
                JoinType.NATURAL -> "NATURAL JOIN"
                JoinType.LEFT_SEMI -> "LEFT SEMI JOIN"
                JoinType.RIGHT_SEMI -> "RIGHT SEMI JOIN"
                JoinType.LEFT_ANTI -> "LEFT ANTI JOIN"
                JoinType.RIGHT_ANTI -> "RIGHT ANTI JOIN"
            }
            val cond = item.condition
            val conditionSql = when (cond) {
                is OnJoinSpec -> " ON ${generateExpression(cond.condition)}"
                is UsingJoinSpec -> " USING (${cond.columns.joinToString(", ") { quoteIdentifier(it) }})"
                null -> ""
            }
            "$leftSql $joinTypeSql $rightSql$conditionSql"
        }
    }

    protected fun generateAssignment(assignment: Assignment): String =
        "${quoteIdentifier(assignment.column.name)} = ${generateExpression(assignment.value)}"

    protected fun generateOrderItem(item: OrderItem): String {
        val exprSql = generateExpression(item.expression)
        val direction = if (item.ascending) "ASC" else "DESC"
        val nulls = when (item.nullsFirst) {
            true -> " NULLS FIRST"
            false -> " NULLS LAST"
            null -> ""
        }
        return "$exprSql $direction$nulls"
    }

    protected fun generateCte(cte: CteSpec): String {
        val sb = StringBuilder()
        sb.append(quoteIdentifier(cte.name))
        if (cte.columns.isNotEmpty()) {
            sb.append(" (")
            sb.append(cte.columns.joinToString(", ") { quoteIdentifier(it) })
            sb.append(")")
        }
        sb.append(" AS ")
        if (cte.materialized == true) sb.append("MATERIALIZED ")
        else if (cte.materialized == false) sb.append("NOT MATERIALIZED ")
        sb.append("(")
        sb.append(generateQuery(cte.query))
        sb.append(")")
        return sb.toString()
    }

    protected fun generateSetOp(setOp: SetOperation): String {
        val typeName = when (setOp.type) {
            SetOpType.UNION -> "UNION"
            SetOpType.INTERSECT -> "INTERSECT"
            SetOpType.EXCEPT -> "EXCEPT"
        }
        val all = if (setOp.all) " ALL" else ""
        return "$typeName$all ${generateQuery(setOp.right)}"
    }

    // ─── Expression generation ───────────────────────────────────────────────

    override fun generateExpression(expr: SqlExpression): String = when (expr) {
        is ColumnRef -> generateColumnRef(expr)
        is LiteralExpr -> generateLiteral(expr)
        is BinaryExpr -> generateBinary(expr)
        is UnaryExpr -> generateUnary(expr)
        is FunctionCall -> generateFunctionCall(expr)
        is CaseExpr -> generateCase(expr)
        is CastExpr -> generateCast(expr)
        is BetweenExpr -> generateBetween(expr)
        is InExpr -> generateIn(expr)
        is ExistsExpr -> generateExists(expr)
        is SubqueryExpr -> "(${generateQuery(expr.query)})"
        is WindowFunctionExpr -> generateWindowFunction(expr)
        is StarExpr -> { val t = expr.table; if (t != null) "${quoteIdentifier(t)}.*" else "*" }
        is ParenExpr -> "(${generateExpression(expr.expression)})"
        is RawExpr -> expr.rawText
    }

    protected fun generateColumnRef(ref: ColumnRef): String {
        val parts = mutableListOf<String>()
        val schema = ref.schema
        val table = ref.table
        if (schema != null) parts.add(quoteIdentifier(schema))
        if (table != null) parts.add(quoteIdentifier(table))
        parts.add(quoteIdentifier(ref.name))
        return parts.joinToString(".")
    }

    protected open fun generateLiteral(expr: LiteralExpr): String = when (expr.literalType) {
        LiteralType.STRING -> "'${escapeString(expr.value?.toString() ?: "")}'"
        LiteralType.NULL -> "NULL"
        LiteralType.BOOLEAN -> if (expr.value == true) "TRUE" else "FALSE"
        LiteralType.INTEGER, LiteralType.DECIMAL -> expr.value?.toString() ?: "NULL"
        LiteralType.DATE -> "DATE '${expr.value}'"
        LiteralType.TIMESTAMP -> "TIMESTAMP '${expr.value}'"
    }

    protected open fun escapeString(value: String): String =
        value.replace("'", "''")

    protected fun generateBinary(expr: BinaryExpr): String {
        val leftSql = generateExpression(expr.left)
        val rightSql = generateExpression(expr.right)
        return "$leftSql ${expr.operator} $rightSql"
    }

    protected fun generateUnary(expr: UnaryExpr): String {
        val operandSql = generateExpression(expr.operand)
        return if (expr.prefix) "${expr.operator} $operandSql" else "$operandSql ${expr.operator}"
    }

    protected open fun generateFunctionCall(expr: FunctionCall): String {
        // Check for function mapping
        val mapping = if (sourceFamily != null) {
            FunctionMapper.mapFunction(expr.name, sourceFamily, targetFamily)
        } else null

        val funcName = mapping?.targetName ?: expr.name

        val sb = StringBuilder(funcName)
        sb.append("(")

        if (expr.star) {
            sb.append("*")
        } else {
            if (expr.distinct) sb.append("DISTINCT ")
            sb.append(expr.arguments.joinToString(", ") { generateExpression(it) })
        }

        if (expr.orderBy.isNotEmpty()) {
            sb.append(" ORDER BY ")
            sb.append(expr.orderBy.joinToString(", ") {
                val dir = if (it.ascending) "ASC" else "DESC"
                "${generateExpression(it.expr)} $dir"
            })
        }

        sb.append(")")

        val filter = expr.filter
        if (filter != null) {
            sb.append(" FILTER (WHERE ")
            sb.append(generateExpression(filter))
            sb.append(")")
        }

        return sb.toString()
    }

    protected fun generateCase(expr: CaseExpr): String {
        val sb = StringBuilder("CASE")
        val operand = expr.operand
        if (operand != null) {
            sb.append(" ")
            sb.append(generateExpression(operand))
        }
        for (clause in expr.whenClauses) {
            sb.append(" WHEN ")
            sb.append(generateExpression(clause.condition))
            sb.append(" THEN ")
            sb.append(generateExpression(clause.result))
        }
        val elseExpr = expr.elseExpr
        if (elseExpr != null) {
            sb.append(" ELSE ")
            sb.append(generateExpression(elseExpr))
        }
        sb.append(" END")
        return sb.toString()
    }

    protected open fun generateCast(expr: CastExpr): String {
        val (mappedType, mappedParams) = mapDataType(expr.targetType, expr.typeParams)
        val typeStr = if (mappedParams.isNotEmpty()) {
            "$mappedType(${mappedParams.joinToString(", ")})"
        } else {
            mappedType
        }
        return "CAST(${generateExpression(expr.expression)} AS $typeStr)"
    }

    protected fun generateBetween(expr: BetweenExpr): String {
        val not = if (expr.not) "NOT " else ""
        return "${generateExpression(expr.expression)} ${not}BETWEEN ${generateExpression(expr.lower)} AND ${generateExpression(expr.upper)}"
    }

    protected fun generateIn(expr: InExpr): String {
        val not = if (expr.not) "NOT " else ""
        val exprSql = generateExpression(expr.expression)
        val sub = expr.subquery
        return if (sub != null) {
            "$exprSql ${not}IN (${generateQuery(sub.query)})"
        } else {
            "$exprSql ${not}IN (${expr.values.joinToString(", ") { generateExpression(it) }})"
        }
    }

    protected fun generateExists(expr: ExistsExpr): String {
        val not = if (expr.not) "NOT " else ""
        return "${not}EXISTS (${generateQuery(expr.subquery.query)})"
    }

    protected fun generateWindowFunction(expr: WindowFunctionExpr): String {
        val funcSql = generateFunctionCall(expr.function)
        val sb = StringBuilder(funcSql)
        sb.append(" OVER (")
        sb.append(generateWindowSpec(expr.windowSpec))
        sb.append(")")
        return sb.toString()
    }

    protected fun generateWindowSpec(spec: WindowSpec): String {
        val parts = mutableListOf<String>()
        val name = spec.name
        if (name != null) parts.add(name)
        if (spec.partitionBy.isNotEmpty()) {
            parts.add("PARTITION BY ${spec.partitionBy.joinToString(", ") { generateExpression(it) }}")
        }
        if (spec.orderBy.isNotEmpty()) {
            parts.add("ORDER BY ${spec.orderBy.joinToString(", ") { generateOrderItem(it) }}")
        }
        val frame = spec.frame
        if (frame != null) {
            parts.add(generateWindowFrame(frame))
        }
        return parts.joinToString(" ")
    }

    protected fun generateWindowFrame(frame: WindowFrame): String {
        val typeName = when (frame.type) {
            WindowFrameType.ROWS -> "ROWS"
            WindowFrameType.RANGE -> "RANGE"
            WindowFrameType.GROUPS -> "GROUPS"
        }
        val startBound = generateFrameBound(frame.start)
        val end = frame.end
        return if (end != null) {
            "$typeName BETWEEN $startBound AND ${generateFrameBound(end)}"
        } else {
            "$typeName $startBound"
        }
    }

    protected fun generateFrameBound(bound: WindowFrameBound): String = when (bound) {
        is UnboundedPreceding -> "UNBOUNDED PRECEDING"
        is CurrentRow -> "CURRENT ROW"
        is UnboundedFollowing -> "UNBOUNDED FOLLOWING"
        is PrecedingOffset -> "${generateExpression(bound.offset)} PRECEDING"
        is FollowingOffset -> "${generateExpression(bound.offset)} FOLLOWING"
    }
}
