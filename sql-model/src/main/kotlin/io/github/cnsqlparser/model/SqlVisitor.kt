package io.github.cnsqlparser.model

import io.github.cnsqlparser.model.statement.*

/**
 * 统一SQL模型访问者接口
 *
 * Visitor pattern interface for traversing the Unified SQL Model.
 * Use [DefaultSqlVisitor] if you only need to override specific visit methods.
 */
interface SqlVisitor<R> {
    fun visitSelect(stmt: SelectStmt): R
    fun visitInsert(stmt: InsertStmt): R
    fun visitUpdate(stmt: UpdateStmt): R
    fun visitDelete(stmt: DeleteStmt): R
    fun visitMerge(stmt: MergeStmt): R
    fun visitCreateTable(stmt: CreateTableStmt): R
    fun visitAlterTable(stmt: AlterTableStmt): R
    fun visitDrop(stmt: DropStmt): R
    fun visitUse(stmt: UseStmt): R
    fun visitGeneric(stmt: GenericStmt): R
}

/**
 * 默认（空操作）访问者 - 返回Unit
 *
 * Default no-op visitor. Override only the methods you need.
 */
abstract class DefaultSqlVisitor : SqlVisitor<Unit> {
    override fun visitSelect(stmt: SelectStmt) {}
    override fun visitInsert(stmt: InsertStmt) {}
    override fun visitUpdate(stmt: UpdateStmt) {}
    override fun visitDelete(stmt: DeleteStmt) {}
    override fun visitMerge(stmt: MergeStmt) {}
    override fun visitCreateTable(stmt: CreateTableStmt) {}
    override fun visitAlterTable(stmt: AlterTableStmt) {}
    override fun visitDrop(stmt: DropStmt) {}
    override fun visitUse(stmt: UseStmt) {}
    override fun visitGeneric(stmt: GenericStmt) {}
}

/** 根据 sealed class 类型派发到正确的 visitor 方法 */
fun <R> SqlStatement.accept(visitor: SqlVisitor<R>): R = when (this) {
    is SelectStmt      -> visitor.visitSelect(this)
    is InsertStmt      -> visitor.visitInsert(this)
    is UpdateStmt      -> visitor.visitUpdate(this)
    is DeleteStmt      -> visitor.visitDelete(this)
    is MergeStmt       -> visitor.visitMerge(this)
    is CreateTableStmt -> visitor.visitCreateTable(this)
    is AlterTableStmt  -> visitor.visitAlterTable(this)
    is DropStmt        -> visitor.visitDrop(this)
    is UseStmt         -> visitor.visitUse(this)
    is GenericStmt     -> visitor.visitGeneric(this)
}
