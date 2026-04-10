package io.github.cnsqlparser.converter.mapping

import io.github.cnsqlparser.common.DialectFamily

/**
 * 函数映射器
 *
 * Maps SQL function names and calling conventions across dialects.
 * Handles cases like NVL→IFNULL, SYSDATE→NOW(), DECODE→CASE, etc.
 */
object FunctionMapper {

    /**
     * Map a function call from one dialect to another.
     *
     * @param functionName source function name (case-insensitive)
     * @param from source dialect family
     * @param to target dialect family
     * @return a [FunctionMapping] describing the target function, or null if no mapping needed
     */
    fun mapFunction(
        functionName: String,
        from: DialectFamily,
        to: DialectFamily
    ): FunctionMapping? {
        if (from == to) return null
        val key = MappingKey(functionName.uppercase().trim(), from, to)
        return functionMappings[key]
    }

    private data class MappingKey(
        val functionName: String,
        val from: DialectFamily,
        val to: DialectFamily
    )

    /**
     * Describes how a function should be mapped.
     *
     * @param targetName the function name in the target dialect
     * @param rewriteStrategy how the arguments should be rewritten
     */
    data class FunctionMapping(
        val targetName: String,
        val rewriteStrategy: RewriteStrategy = RewriteStrategy.RENAME_ONLY
    )

    enum class RewriteStrategy {
        /** Simply rename the function, keep arguments as-is */
        RENAME_ONLY,
        /** Function has special rewrite logic (handled case-by-case in generator) */
        CUSTOM
    }

    private val functionMappings: Map<MappingKey, FunctionMapping> = buildMap {
        // ─── Oracle → MySQL ──────────────────────────────────────
        put(MappingKey("NVL", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("IFNULL"))
        put(MappingKey("NVL2", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("IF", RewriteStrategy.CUSTOM))
        put(MappingKey("SYSDATE", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("NOW"))
        put(MappingKey("SYSTIMESTAMP", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("NOW"))
        put(MappingKey("LENGTH", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("CHAR_LENGTH"))
        put(MappingKey("LENGTHB", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("LENGTH"))
        put(MappingKey("SUBSTR", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("SUBSTRING"))
        put(MappingKey("TO_CHAR", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("DATE_FORMAT", RewriteStrategy.CUSTOM))
        put(MappingKey("TO_DATE", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("STR_TO_DATE", RewriteStrategy.CUSTOM))
        put(MappingKey("TO_NUMBER", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("CAST", RewriteStrategy.CUSTOM))
        put(MappingKey("DECODE", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("CASE", RewriteStrategy.CUSTOM))
        put(MappingKey("TRUNC", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("TRUNCATE"))
        put(MappingKey("MONTHS_BETWEEN", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("TIMESTAMPDIFF", RewriteStrategy.CUSTOM))
        put(MappingKey("ROWNUM", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("LIMIT", RewriteStrategy.CUSTOM))
        put(MappingKey("LISTAGG", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("GROUP_CONCAT", RewriteStrategy.CUSTOM))
        put(MappingKey("INSTR", DialectFamily.ORACLE, DialectFamily.MYSQL), FunctionMapping("LOCATE", RewriteStrategy.CUSTOM))

        // ─── Oracle → PostgreSQL ─────────────────────────────────
        put(MappingKey("NVL", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), FunctionMapping("COALESCE"))
        put(MappingKey("SYSDATE", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), FunctionMapping("CURRENT_TIMESTAMP"))
        put(MappingKey("SYSTIMESTAMP", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), FunctionMapping("CURRENT_TIMESTAMP"))
        put(MappingKey("SUBSTR", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), FunctionMapping("SUBSTRING"))
        put(MappingKey("TO_CHAR", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), FunctionMapping("TO_CHAR"))
        put(MappingKey("TO_DATE", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), FunctionMapping("TO_DATE"))
        put(MappingKey("TO_NUMBER", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), FunctionMapping("CAST", RewriteStrategy.CUSTOM))
        put(MappingKey("DECODE", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), FunctionMapping("CASE", RewriteStrategy.CUSTOM))
        put(MappingKey("TRUNC", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), FunctionMapping("TRUNC"))
        put(MappingKey("LISTAGG", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), FunctionMapping("STRING_AGG"))
        put(MappingKey("LENGTHB", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), FunctionMapping("OCTET_LENGTH"))

        // ─── Oracle → SQL Server ─────────────────────────────────
        put(MappingKey("NVL", DialectFamily.ORACLE, DialectFamily.SQLSERVER), FunctionMapping("ISNULL"))
        put(MappingKey("SYSDATE", DialectFamily.ORACLE, DialectFamily.SQLSERVER), FunctionMapping("GETDATE"))
        put(MappingKey("SYSTIMESTAMP", DialectFamily.ORACLE, DialectFamily.SQLSERVER), FunctionMapping("SYSDATETIME"))
        put(MappingKey("SUBSTR", DialectFamily.ORACLE, DialectFamily.SQLSERVER), FunctionMapping("SUBSTRING"))
        put(MappingKey("LENGTH", DialectFamily.ORACLE, DialectFamily.SQLSERVER), FunctionMapping("LEN"))
        put(MappingKey("TO_CHAR", DialectFamily.ORACLE, DialectFamily.SQLSERVER), FunctionMapping("FORMAT", RewriteStrategy.CUSTOM))
        put(MappingKey("TO_DATE", DialectFamily.ORACLE, DialectFamily.SQLSERVER), FunctionMapping("CONVERT", RewriteStrategy.CUSTOM))
        put(MappingKey("DECODE", DialectFamily.ORACLE, DialectFamily.SQLSERVER), FunctionMapping("CASE", RewriteStrategy.CUSTOM))
        put(MappingKey("TRUNC", DialectFamily.ORACLE, DialectFamily.SQLSERVER), FunctionMapping("ROUND", RewriteStrategy.CUSTOM))
        put(MappingKey("LISTAGG", DialectFamily.ORACLE, DialectFamily.SQLSERVER), FunctionMapping("STRING_AGG"))

        // ─── MySQL → Oracle ──────────────────────────────────────
        put(MappingKey("IFNULL", DialectFamily.MYSQL, DialectFamily.ORACLE), FunctionMapping("NVL"))
        put(MappingKey("NOW", DialectFamily.MYSQL, DialectFamily.ORACLE), FunctionMapping("SYSDATE"))
        put(MappingKey("CURDATE", DialectFamily.MYSQL, DialectFamily.ORACLE), FunctionMapping("TRUNC", RewriteStrategy.CUSTOM))
        put(MappingKey("CHAR_LENGTH", DialectFamily.MYSQL, DialectFamily.ORACLE), FunctionMapping("LENGTH"))
        put(MappingKey("CHARACTER_LENGTH", DialectFamily.MYSQL, DialectFamily.ORACLE), FunctionMapping("LENGTH"))
        put(MappingKey("SUBSTRING", DialectFamily.MYSQL, DialectFamily.ORACLE), FunctionMapping("SUBSTR"))
        put(MappingKey("DATE_FORMAT", DialectFamily.MYSQL, DialectFamily.ORACLE), FunctionMapping("TO_CHAR", RewriteStrategy.CUSTOM))
        put(MappingKey("STR_TO_DATE", DialectFamily.MYSQL, DialectFamily.ORACLE), FunctionMapping("TO_DATE", RewriteStrategy.CUSTOM))
        put(MappingKey("GROUP_CONCAT", DialectFamily.MYSQL, DialectFamily.ORACLE), FunctionMapping("LISTAGG", RewriteStrategy.CUSTOM))
        put(MappingKey("IF", DialectFamily.MYSQL, DialectFamily.ORACLE), FunctionMapping("CASE", RewriteStrategy.CUSTOM))
        put(MappingKey("TRUNCATE", DialectFamily.MYSQL, DialectFamily.ORACLE), FunctionMapping("TRUNC"))
        put(MappingKey("LOCATE", DialectFamily.MYSQL, DialectFamily.ORACLE), FunctionMapping("INSTR", RewriteStrategy.CUSTOM))
        put(MappingKey("CONCAT_WS", DialectFamily.MYSQL, DialectFamily.ORACLE), FunctionMapping("CONCAT", RewriteStrategy.CUSTOM))

        // ─── MySQL → PostgreSQL ──────────────────────────────────
        put(MappingKey("IFNULL", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), FunctionMapping("COALESCE"))
        put(MappingKey("NOW", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), FunctionMapping("NOW"))
        put(MappingKey("CURDATE", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), FunctionMapping("CURRENT_DATE"))
        put(MappingKey("SUBSTRING", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), FunctionMapping("SUBSTRING"))
        put(MappingKey("GROUP_CONCAT", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), FunctionMapping("STRING_AGG", RewriteStrategy.CUSTOM))
        put(MappingKey("IF", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), FunctionMapping("CASE", RewriteStrategy.CUSTOM))
        put(MappingKey("TRUNCATE", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), FunctionMapping("TRUNC"))
        put(MappingKey("DATE_FORMAT", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), FunctionMapping("TO_CHAR", RewriteStrategy.CUSTOM))
        put(MappingKey("STR_TO_DATE", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), FunctionMapping("TO_DATE", RewriteStrategy.CUSTOM))

        // ─── MySQL → SQL Server ──────────────────────────────────
        put(MappingKey("IFNULL", DialectFamily.MYSQL, DialectFamily.SQLSERVER), FunctionMapping("ISNULL"))
        put(MappingKey("NOW", DialectFamily.MYSQL, DialectFamily.SQLSERVER), FunctionMapping("GETDATE"))
        put(MappingKey("CHAR_LENGTH", DialectFamily.MYSQL, DialectFamily.SQLSERVER), FunctionMapping("LEN"))
        put(MappingKey("CHARACTER_LENGTH", DialectFamily.MYSQL, DialectFamily.SQLSERVER), FunctionMapping("LEN"))
        put(MappingKey("GROUP_CONCAT", DialectFamily.MYSQL, DialectFamily.SQLSERVER), FunctionMapping("STRING_AGG", RewriteStrategy.CUSTOM))
        put(MappingKey("IF", DialectFamily.MYSQL, DialectFamily.SQLSERVER), FunctionMapping("IIF"))
        put(MappingKey("TRUNCATE", DialectFamily.MYSQL, DialectFamily.SQLSERVER), FunctionMapping("ROUND", RewriteStrategy.CUSTOM))

        // ─── PostgreSQL → MySQL ──────────────────────────────────
        put(MappingKey("COALESCE", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), FunctionMapping("COALESCE"))
        put(MappingKey("STRING_AGG", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), FunctionMapping("GROUP_CONCAT", RewriteStrategy.CUSTOM))
        put(MappingKey("TO_CHAR", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), FunctionMapping("DATE_FORMAT", RewriteStrategy.CUSTOM))
        put(MappingKey("TO_DATE", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), FunctionMapping("STR_TO_DATE", RewriteStrategy.CUSTOM))
        put(MappingKey("CURRENT_TIMESTAMP", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), FunctionMapping("NOW"))
        put(MappingKey("CURRENT_DATE", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), FunctionMapping("CURDATE"))
        put(MappingKey("OCTET_LENGTH", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), FunctionMapping("LENGTH"))

        // ─── PostgreSQL → Oracle ─────────────────────────────────
        put(MappingKey("STRING_AGG", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), FunctionMapping("LISTAGG"))
        put(MappingKey("CURRENT_TIMESTAMP", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), FunctionMapping("SYSTIMESTAMP"))
        put(MappingKey("CURRENT_DATE", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), FunctionMapping("TRUNC", RewriteStrategy.CUSTOM))

        // ─── PostgreSQL → SQL Server ─────────────────────────────
        put(MappingKey("COALESCE", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), FunctionMapping("COALESCE"))
        put(MappingKey("STRING_AGG", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), FunctionMapping("STRING_AGG"))
        put(MappingKey("CURRENT_TIMESTAMP", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), FunctionMapping("GETDATE"))
        put(MappingKey("CURRENT_DATE", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), FunctionMapping("CAST", RewriteStrategy.CUSTOM))

        // ─── SQL Server → MySQL ──────────────────────────────────
        put(MappingKey("ISNULL", DialectFamily.SQLSERVER, DialectFamily.MYSQL), FunctionMapping("IFNULL"))
        put(MappingKey("GETDATE", DialectFamily.SQLSERVER, DialectFamily.MYSQL), FunctionMapping("NOW"))
        put(MappingKey("SYSDATETIME", DialectFamily.SQLSERVER, DialectFamily.MYSQL), FunctionMapping("NOW"))
        put(MappingKey("LEN", DialectFamily.SQLSERVER, DialectFamily.MYSQL), FunctionMapping("CHAR_LENGTH"))
        put(MappingKey("IIF", DialectFamily.SQLSERVER, DialectFamily.MYSQL), FunctionMapping("IF"))
        put(MappingKey("STRING_AGG", DialectFamily.SQLSERVER, DialectFamily.MYSQL), FunctionMapping("GROUP_CONCAT", RewriteStrategy.CUSTOM))

        // ─── SQL Server → Oracle ─────────────────────────────────
        put(MappingKey("ISNULL", DialectFamily.SQLSERVER, DialectFamily.ORACLE), FunctionMapping("NVL"))
        put(MappingKey("GETDATE", DialectFamily.SQLSERVER, DialectFamily.ORACLE), FunctionMapping("SYSDATE"))
        put(MappingKey("SYSDATETIME", DialectFamily.SQLSERVER, DialectFamily.ORACLE), FunctionMapping("SYSTIMESTAMP"))
        put(MappingKey("LEN", DialectFamily.SQLSERVER, DialectFamily.ORACLE), FunctionMapping("LENGTH"))
        put(MappingKey("IIF", DialectFamily.SQLSERVER, DialectFamily.ORACLE), FunctionMapping("CASE", RewriteStrategy.CUSTOM))
        put(MappingKey("STRING_AGG", DialectFamily.SQLSERVER, DialectFamily.ORACLE), FunctionMapping("LISTAGG"))

        // ─── SQL Server → PostgreSQL ─────────────────────────────
        put(MappingKey("ISNULL", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), FunctionMapping("COALESCE"))
        put(MappingKey("GETDATE", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), FunctionMapping("NOW"))
        put(MappingKey("SYSDATETIME", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), FunctionMapping("NOW"))
        put(MappingKey("LEN", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), FunctionMapping("LENGTH"))
        put(MappingKey("IIF", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), FunctionMapping("CASE", RewriteStrategy.CUSTOM))
        put(MappingKey("STRING_AGG", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), FunctionMapping("STRING_AGG"))
    }
}
