package io.github.cnsqlparser.converter

import io.github.cnsqlparser.common.DialectFamily
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.converter.generator.*
import io.github.cnsqlparser.model.ParseResult
import io.github.cnsqlparser.model.statement.GenericStmt
import io.github.cnsqlparser.model.statement.SqlStatement
import org.slf4j.LoggerFactory

/**
 * SQL方言转换器
 *
 * Orchestrates the dialect conversion pipeline:
 * 1. Accept a [ParseResult] (already parsed AST from any source dialect)
 * 2. Generate SQL text in the target dialect using the appropriate [SqlGenerator]
 *
 * Usage:
 * ```kotlin
 * val result = SqlParsers.parse("SELECT * FROM users LIMIT 10", ProductDialect.MYSQL)
 * val converted = DialectConverter.convert(result, ProductDialect.ORACLE)
 * println(converted.sql) // SELECT * FROM "users" FETCH FIRST 10 ROWS ONLY
 * ```
 */
object DialectConverter {

    private val logger = LoggerFactory.getLogger(DialectConverter::class.java)

    /**
     * Convert parsed SQL statements to a target dialect.
     *
     * @param parseResult the parsed SQL result (from any source dialect)
     * @param targetDialect the target dialect to convert to
     * @return a [ConversionResult] with the converted SQL and any warnings
     */
    fun convert(parseResult: ParseResult, targetDialect: ProductDialect): ConversionResult {
        val sourceDialect = parseResult.dialect ?: ProductDialect.MYSQL
        val sourceFamily = sourceDialect.family
        val targetFamily = targetDialect.family

        val generator = createGenerator(sourceFamily, targetFamily)
        val warnings = mutableListOf<ConversionWarning>()

        // Convert parse warnings
        for (w in parseResult.warnings) {
            warnings.add(ConversionWarning(
                "Parse warning: ${w.message}",
                WarningCategory.GENERAL
            ))
        }

        val convertedStatements = parseResult.statements.map { stmt ->
            convertStatement(stmt, generator, warnings)
        }

        val sql = convertedStatements.joinToString(";\n")

        return ConversionResult(
            sql = sql,
            sourceDialect = sourceDialect,
            targetDialect = targetDialect,
            warnings = warnings
        )
    }

    /**
     * Convert a single SQL statement to a target dialect.
     *
     * @param stmt the SQL statement AST
     * @param sourceDialect the source dialect
     * @param targetDialect the target dialect
     * @return a [ConversionResult] with the converted SQL and any warnings
     */
    fun convertStatement(
        stmt: SqlStatement,
        sourceDialect: ProductDialect,
        targetDialect: ProductDialect
    ): ConversionResult {
        val generator = createGenerator(sourceDialect.family, targetDialect.family)
        val warnings = mutableListOf<ConversionWarning>()
        val sql = convertStatement(stmt, generator, warnings)

        return ConversionResult(
            sql = sql,
            sourceDialect = sourceDialect,
            targetDialect = targetDialect,
            warnings = warnings
        )
    }

    private fun convertStatement(
        stmt: SqlStatement,
        generator: SqlGenerator,
        warnings: MutableList<ConversionWarning>
    ): String {
        return try {
            if (stmt is GenericStmt) {
                warnings.add(ConversionWarning(
                    "Statement could not be fully parsed; returning original text: ${stmt.statementText.take(80)}...",
                    WarningCategory.UNSUPPORTED_FEATURE
                ))
                stmt.statementText
            } else {
                generator.generate(stmt)
            }
        } catch (e: Exception) {
            logger.warn("Failed to convert statement: ${e.message}", e)
            warnings.add(ConversionWarning(
                "Conversion failed: ${e.message}; returning original text",
                WarningCategory.GENERAL
            ))
            stmt.sourceText ?: ""
        }
    }

    /**
     * Create the appropriate SQL generator for a source→target conversion.
     */
    fun createGenerator(sourceFamily: DialectFamily, targetFamily: DialectFamily): SqlGenerator {
        return when (targetFamily) {
            DialectFamily.MYSQL -> MySqlGenerator(sourceFamily)
            DialectFamily.ORACLE -> OracleSqlGenerator(sourceFamily)
            DialectFamily.POSTGRESQL -> PostgreSqlGenerator(sourceFamily)
            DialectFamily.SQLSERVER -> SqlServerGenerator(sourceFamily)
            else -> MySqlGenerator(sourceFamily) // Fallback to MySQL-compatible output
        }
    }
}
