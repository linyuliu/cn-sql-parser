package io.github.cnsqlparser.converter.mapping

import io.github.cnsqlparser.common.DialectFamily

/**
 * 数据类型映射器
 *
 * Maps SQL data types across dialects. Each dialect may have its own
 * naming conventions for equivalent types (e.g. MySQL AUTO_INCREMENT ↔ Oracle SEQUENCE,
 * MySQL INT ↔ Oracle NUMBER(10), PG SERIAL ↔ MySQL INT AUTO_INCREMENT).
 */
object DataTypeMapper {

    /**
     * Map a data type name from one dialect to another.
     *
     * @param typeName source data type name (case-insensitive)
     * @param from source dialect family
     * @param to target dialect family
     * @param typeParams type parameters (e.g., ["10", "2"] for DECIMAL(10,2))
     * @return a Pair of (targetTypeName, targetTypeParams)
     */
    fun mapType(
        typeName: String,
        from: DialectFamily,
        to: DialectFamily,
        typeParams: List<String> = emptyList()
    ): Pair<String, List<String>> {
        if (from == to) return typeName to typeParams

        val normalizedType = typeName.uppercase().trim()
        val key = MappingKey(normalizedType, from, to)

        // Check exact mapping first
        val exactMapping = typeMappings[key]
        if (exactMapping != null) {
            return exactMapping.first to (exactMapping.second ?: typeParams)
        }

        // Try parameterized type mappings
        val paramMapping = parameterizedTypeMappings[key]
        if (paramMapping != null) {
            return paramMapping(typeParams)
        }

        // Fallback: return as-is
        return typeName to typeParams
    }

    private data class MappingKey(
        val typeName: String,
        val from: DialectFamily,
        val to: DialectFamily
    )

    // ─── MySQL → Oracle ────────────────────────────────────────────────────
    // ─── MySQL → PG ────────────────────────────────────────────────────────
    // ─── Oracle → MySQL ────────────────────────────────────────────────────
    // etc.

    private val typeMappings: Map<MappingKey, Pair<String, List<String>?>> = buildMap {
        // ─── MySQL → Oracle ───────────────────────────────────────
        put(MappingKey("INT", DialectFamily.MYSQL, DialectFamily.ORACLE), "NUMBER" to listOf("10"))
        put(MappingKey("INTEGER", DialectFamily.MYSQL, DialectFamily.ORACLE), "NUMBER" to listOf("10"))
        put(MappingKey("BIGINT", DialectFamily.MYSQL, DialectFamily.ORACLE), "NUMBER" to listOf("19"))
        put(MappingKey("SMALLINT", DialectFamily.MYSQL, DialectFamily.ORACLE), "NUMBER" to listOf("5"))
        put(MappingKey("TINYINT", DialectFamily.MYSQL, DialectFamily.ORACLE), "NUMBER" to listOf("3"))
        put(MappingKey("MEDIUMINT", DialectFamily.MYSQL, DialectFamily.ORACLE), "NUMBER" to listOf("7"))
        put(MappingKey("FLOAT", DialectFamily.MYSQL, DialectFamily.ORACLE), "BINARY_FLOAT" to null)
        put(MappingKey("DOUBLE", DialectFamily.MYSQL, DialectFamily.ORACLE), "BINARY_DOUBLE" to null)
        put(MappingKey("BOOLEAN", DialectFamily.MYSQL, DialectFamily.ORACLE), "NUMBER" to listOf("1"))
        put(MappingKey("DATETIME", DialectFamily.MYSQL, DialectFamily.ORACLE), "TIMESTAMP" to null)
        put(MappingKey("TEXT", DialectFamily.MYSQL, DialectFamily.ORACLE), "CLOB" to null)
        put(MappingKey("MEDIUMTEXT", DialectFamily.MYSQL, DialectFamily.ORACLE), "CLOB" to null)
        put(MappingKey("LONGTEXT", DialectFamily.MYSQL, DialectFamily.ORACLE), "CLOB" to null)
        put(MappingKey("TINYTEXT", DialectFamily.MYSQL, DialectFamily.ORACLE), "VARCHAR2" to listOf("255"))
        put(MappingKey("BLOB", DialectFamily.MYSQL, DialectFamily.ORACLE), "BLOB" to null)
        put(MappingKey("LONGBLOB", DialectFamily.MYSQL, DialectFamily.ORACLE), "BLOB" to null)
        put(MappingKey("MEDIUMBLOB", DialectFamily.MYSQL, DialectFamily.ORACLE), "BLOB" to null)
        put(MappingKey("TINYBLOB", DialectFamily.MYSQL, DialectFamily.ORACLE), "RAW" to listOf("255"))
        put(MappingKey("BIT", DialectFamily.MYSQL, DialectFamily.ORACLE), "NUMBER" to listOf("1"))
        put(MappingKey("ENUM", DialectFamily.MYSQL, DialectFamily.ORACLE), "VARCHAR2" to listOf("100"))
        put(MappingKey("SET", DialectFamily.MYSQL, DialectFamily.ORACLE), "VARCHAR2" to listOf("500"))
        put(MappingKey("JSON", DialectFamily.MYSQL, DialectFamily.ORACLE), "CLOB" to null)
        put(MappingKey("AUTO_INCREMENT", DialectFamily.MYSQL, DialectFamily.ORACLE), "NUMBER" to null)

        // ─── MySQL → PostgreSQL ──────────────────────────────────
        put(MappingKey("INT", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "INTEGER" to null)
        put(MappingKey("TINYINT", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "SMALLINT" to null)
        put(MappingKey("MEDIUMINT", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "INTEGER" to null)
        put(MappingKey("DOUBLE", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "DOUBLE PRECISION" to null)
        put(MappingKey("FLOAT", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "REAL" to null)
        put(MappingKey("DATETIME", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "TIMESTAMP" to null)
        put(MappingKey("TINYTEXT", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "TEXT" to null)
        put(MappingKey("MEDIUMTEXT", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "TEXT" to null)
        put(MappingKey("LONGTEXT", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "TEXT" to null)
        put(MappingKey("LONGBLOB", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "BYTEA" to null)
        put(MappingKey("MEDIUMBLOB", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "BYTEA" to null)
        put(MappingKey("TINYBLOB", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "BYTEA" to null)
        put(MappingKey("BLOB", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "BYTEA" to null)
        put(MappingKey("BIT", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "BOOLEAN" to null)
        put(MappingKey("ENUM", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "VARCHAR" to listOf("100"))
        put(MappingKey("SET", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "VARCHAR" to listOf("500"))
        put(MappingKey("JSON", DialectFamily.MYSQL, DialectFamily.POSTGRESQL), "JSONB" to null)

        // ─── MySQL → SQL Server ──────────────────────────────────
        put(MappingKey("INT", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "INT" to null)
        put(MappingKey("TINYINT", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "TINYINT" to null)
        put(MappingKey("MEDIUMINT", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "INT" to null)
        put(MappingKey("DOUBLE", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "FLOAT" to null)
        put(MappingKey("FLOAT", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "REAL" to null)
        put(MappingKey("BOOLEAN", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "BIT" to null)
        put(MappingKey("DATETIME", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "DATETIME2" to null)
        put(MappingKey("TEXT", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "NVARCHAR(MAX)" to null)
        put(MappingKey("MEDIUMTEXT", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "NVARCHAR(MAX)" to null)
        put(MappingKey("LONGTEXT", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "NVARCHAR(MAX)" to null)
        put(MappingKey("TINYTEXT", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "NVARCHAR" to listOf("255"))
        put(MappingKey("BLOB", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "VARBINARY(MAX)" to null)
        put(MappingKey("LONGBLOB", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "VARBINARY(MAX)" to null)
        put(MappingKey("MEDIUMBLOB", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "VARBINARY(MAX)" to null)
        put(MappingKey("JSON", DialectFamily.MYSQL, DialectFamily.SQLSERVER), "NVARCHAR(MAX)" to null)

        // ─── Oracle → MySQL ──────────────────────────────────────
        put(MappingKey("NUMBER", DialectFamily.ORACLE, DialectFamily.MYSQL), "DECIMAL" to null)
        put(MappingKey("VARCHAR2", DialectFamily.ORACLE, DialectFamily.MYSQL), "VARCHAR" to null)
        put(MappingKey("NVARCHAR2", DialectFamily.ORACLE, DialectFamily.MYSQL), "VARCHAR" to null)
        put(MappingKey("CHAR", DialectFamily.ORACLE, DialectFamily.MYSQL), "CHAR" to null)
        put(MappingKey("NCHAR", DialectFamily.ORACLE, DialectFamily.MYSQL), "CHAR" to null)
        put(MappingKey("CLOB", DialectFamily.ORACLE, DialectFamily.MYSQL), "LONGTEXT" to null)
        put(MappingKey("NCLOB", DialectFamily.ORACLE, DialectFamily.MYSQL), "LONGTEXT" to null)
        put(MappingKey("BLOB", DialectFamily.ORACLE, DialectFamily.MYSQL), "LONGBLOB" to null)
        put(MappingKey("RAW", DialectFamily.ORACLE, DialectFamily.MYSQL), "VARBINARY" to null)
        put(MappingKey("LONG RAW", DialectFamily.ORACLE, DialectFamily.MYSQL), "LONGBLOB" to null)
        put(MappingKey("LONG", DialectFamily.ORACLE, DialectFamily.MYSQL), "LONGTEXT" to null)
        put(MappingKey("BINARY_FLOAT", DialectFamily.ORACLE, DialectFamily.MYSQL), "FLOAT" to null)
        put(MappingKey("BINARY_DOUBLE", DialectFamily.ORACLE, DialectFamily.MYSQL), "DOUBLE" to null)
        put(MappingKey("DATE", DialectFamily.ORACLE, DialectFamily.MYSQL), "DATETIME" to null)
        put(MappingKey("TIMESTAMP", DialectFamily.ORACLE, DialectFamily.MYSQL), "DATETIME" to null)
        put(MappingKey("XMLTYPE", DialectFamily.ORACLE, DialectFamily.MYSQL), "TEXT" to null)

        // ─── Oracle → PostgreSQL ─────────────────────────────────
        put(MappingKey("NUMBER", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "NUMERIC" to null)
        put(MappingKey("VARCHAR2", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "VARCHAR" to null)
        put(MappingKey("NVARCHAR2", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "VARCHAR" to null)
        put(MappingKey("NCHAR", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "CHAR" to null)
        put(MappingKey("CLOB", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "TEXT" to null)
        put(MappingKey("NCLOB", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "TEXT" to null)
        put(MappingKey("BLOB", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "BYTEA" to null)
        put(MappingKey("RAW", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "BYTEA" to null)
        put(MappingKey("LONG RAW", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "BYTEA" to null)
        put(MappingKey("LONG", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "TEXT" to null)
        put(MappingKey("BINARY_FLOAT", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "REAL" to null)
        put(MappingKey("BINARY_DOUBLE", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "DOUBLE PRECISION" to null)
        put(MappingKey("DATE", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "TIMESTAMP" to null)
        put(MappingKey("XMLTYPE", DialectFamily.ORACLE, DialectFamily.POSTGRESQL), "XML" to null)

        // ─── Oracle → SQL Server ─────────────────────────────────
        put(MappingKey("NUMBER", DialectFamily.ORACLE, DialectFamily.SQLSERVER), "DECIMAL" to null)
        put(MappingKey("VARCHAR2", DialectFamily.ORACLE, DialectFamily.SQLSERVER), "NVARCHAR" to null)
        put(MappingKey("NVARCHAR2", DialectFamily.ORACLE, DialectFamily.SQLSERVER), "NVARCHAR" to null)
        put(MappingKey("CLOB", DialectFamily.ORACLE, DialectFamily.SQLSERVER), "NVARCHAR(MAX)" to null)
        put(MappingKey("NCLOB", DialectFamily.ORACLE, DialectFamily.SQLSERVER), "NVARCHAR(MAX)" to null)
        put(MappingKey("BLOB", DialectFamily.ORACLE, DialectFamily.SQLSERVER), "VARBINARY(MAX)" to null)
        put(MappingKey("RAW", DialectFamily.ORACLE, DialectFamily.SQLSERVER), "VARBINARY" to null)
        put(MappingKey("BINARY_FLOAT", DialectFamily.ORACLE, DialectFamily.SQLSERVER), "REAL" to null)
        put(MappingKey("BINARY_DOUBLE", DialectFamily.ORACLE, DialectFamily.SQLSERVER), "FLOAT" to null)
        put(MappingKey("DATE", DialectFamily.ORACLE, DialectFamily.SQLSERVER), "DATETIME2" to null)
        put(MappingKey("XMLTYPE", DialectFamily.ORACLE, DialectFamily.SQLSERVER), "XML" to null)

        // ─── PostgreSQL → MySQL ──────────────────────────────────
        put(MappingKey("SERIAL", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "INT" to null)
        put(MappingKey("BIGSERIAL", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "BIGINT" to null)
        put(MappingKey("SMALLSERIAL", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "SMALLINT" to null)
        put(MappingKey("TEXT", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "LONGTEXT" to null)
        put(MappingKey("BYTEA", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "LONGBLOB" to null)
        put(MappingKey("BOOLEAN", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "TINYINT" to listOf("1"))
        put(MappingKey("DOUBLE PRECISION", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "DOUBLE" to null)
        put(MappingKey("REAL", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "FLOAT" to null)
        put(MappingKey("NUMERIC", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "DECIMAL" to null)
        put(MappingKey("JSONB", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "JSON" to null)
        put(MappingKey("JSON", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "JSON" to null)
        put(MappingKey("UUID", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "VARCHAR" to listOf("36"))
        put(MappingKey("INTERVAL", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "VARCHAR" to listOf("100"))
        put(MappingKey("TIMESTAMPTZ", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "DATETIME" to null)
        put(MappingKey("XML", DialectFamily.POSTGRESQL, DialectFamily.MYSQL), "TEXT" to null)

        // ─── PostgreSQL → Oracle ─────────────────────────────────
        put(MappingKey("SERIAL", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "NUMBER" to listOf("10"))
        put(MappingKey("BIGSERIAL", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "NUMBER" to listOf("19"))
        put(MappingKey("SMALLSERIAL", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "NUMBER" to listOf("5"))
        put(MappingKey("TEXT", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "CLOB" to null)
        put(MappingKey("BYTEA", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "BLOB" to null)
        put(MappingKey("BOOLEAN", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "NUMBER" to listOf("1"))
        put(MappingKey("DOUBLE PRECISION", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "BINARY_DOUBLE" to null)
        put(MappingKey("REAL", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "BINARY_FLOAT" to null)
        put(MappingKey("NUMERIC", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "NUMBER" to null)
        put(MappingKey("VARCHAR", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "VARCHAR2" to null)
        put(MappingKey("JSONB", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "CLOB" to null)
        put(MappingKey("JSON", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "CLOB" to null)
        put(MappingKey("UUID", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "VARCHAR2" to listOf("36"))
        put(MappingKey("TIMESTAMPTZ", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "TIMESTAMP WITH TIME ZONE" to null)
        put(MappingKey("XML", DialectFamily.POSTGRESQL, DialectFamily.ORACLE), "XMLTYPE" to null)

        // ─── PostgreSQL → SQL Server ─────────────────────────────
        put(MappingKey("SERIAL", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), "INT" to null)
        put(MappingKey("BIGSERIAL", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), "BIGINT" to null)
        put(MappingKey("TEXT", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), "NVARCHAR(MAX)" to null)
        put(MappingKey("BYTEA", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), "VARBINARY(MAX)" to null)
        put(MappingKey("BOOLEAN", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), "BIT" to null)
        put(MappingKey("DOUBLE PRECISION", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), "FLOAT" to null)
        put(MappingKey("REAL", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), "REAL" to null)
        put(MappingKey("NUMERIC", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), "DECIMAL" to null)
        put(MappingKey("JSONB", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), "NVARCHAR(MAX)" to null)
        put(MappingKey("UUID", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), "UNIQUEIDENTIFIER" to null)
        put(MappingKey("TIMESTAMPTZ", DialectFamily.POSTGRESQL, DialectFamily.SQLSERVER), "DATETIMEOFFSET" to null)

        // ─── SQL Server → MySQL ──────────────────────────────────
        put(MappingKey("NVARCHAR", DialectFamily.SQLSERVER, DialectFamily.MYSQL), "VARCHAR" to null)
        put(MappingKey("NCHAR", DialectFamily.SQLSERVER, DialectFamily.MYSQL), "CHAR" to null)
        put(MappingKey("NTEXT", DialectFamily.SQLSERVER, DialectFamily.MYSQL), "LONGTEXT" to null)
        put(MappingKey("IMAGE", DialectFamily.SQLSERVER, DialectFamily.MYSQL), "LONGBLOB" to null)
        put(MappingKey("BIT", DialectFamily.SQLSERVER, DialectFamily.MYSQL), "TINYINT" to listOf("1"))
        put(MappingKey("MONEY", DialectFamily.SQLSERVER, DialectFamily.MYSQL), "DECIMAL" to listOf("19", "4"))
        put(MappingKey("SMALLMONEY", DialectFamily.SQLSERVER, DialectFamily.MYSQL), "DECIMAL" to listOf("10", "4"))
        put(MappingKey("DATETIME2", DialectFamily.SQLSERVER, DialectFamily.MYSQL), "DATETIME" to null)
        put(MappingKey("DATETIMEOFFSET", DialectFamily.SQLSERVER, DialectFamily.MYSQL), "DATETIME" to null)
        put(MappingKey("UNIQUEIDENTIFIER", DialectFamily.SQLSERVER, DialectFamily.MYSQL), "VARCHAR" to listOf("36"))
        put(MappingKey("XML", DialectFamily.SQLSERVER, DialectFamily.MYSQL), "TEXT" to null)

        // ─── SQL Server → Oracle ─────────────────────────────────
        put(MappingKey("NVARCHAR", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "NVARCHAR2" to null)
        put(MappingKey("NCHAR", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "NCHAR" to null)
        put(MappingKey("NTEXT", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "NCLOB" to null)
        put(MappingKey("IMAGE", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "BLOB" to null)
        put(MappingKey("BIT", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "NUMBER" to listOf("1"))
        put(MappingKey("MONEY", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "NUMBER" to listOf("19", "4"))
        put(MappingKey("SMALLMONEY", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "NUMBER" to listOf("10", "4"))
        put(MappingKey("DATETIME2", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "TIMESTAMP" to null)
        put(MappingKey("DATETIMEOFFSET", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "TIMESTAMP WITH TIME ZONE" to null)
        put(MappingKey("UNIQUEIDENTIFIER", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "VARCHAR2" to listOf("36"))
        put(MappingKey("INT", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "NUMBER" to listOf("10"))
        put(MappingKey("BIGINT", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "NUMBER" to listOf("19"))
        put(MappingKey("SMALLINT", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "NUMBER" to listOf("5"))
        put(MappingKey("TINYINT", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "NUMBER" to listOf("3"))
        put(MappingKey("FLOAT", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "BINARY_DOUBLE" to null)
        put(MappingKey("REAL", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "BINARY_FLOAT" to null)
        put(MappingKey("XML", DialectFamily.SQLSERVER, DialectFamily.ORACLE), "XMLTYPE" to null)

        // ─── SQL Server → PostgreSQL ─────────────────────────────
        put(MappingKey("NVARCHAR", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), "VARCHAR" to null)
        put(MappingKey("NCHAR", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), "CHAR" to null)
        put(MappingKey("NTEXT", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), "TEXT" to null)
        put(MappingKey("IMAGE", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), "BYTEA" to null)
        put(MappingKey("BIT", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), "BOOLEAN" to null)
        put(MappingKey("MONEY", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), "NUMERIC" to listOf("19", "4"))
        put(MappingKey("SMALLMONEY", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), "NUMERIC" to listOf("10", "4"))
        put(MappingKey("DATETIME2", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), "TIMESTAMP" to null)
        put(MappingKey("DATETIMEOFFSET", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), "TIMESTAMPTZ" to null)
        put(MappingKey("UNIQUEIDENTIFIER", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), "UUID" to null)
        put(MappingKey("FLOAT", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL), "DOUBLE PRECISION" to null)
    }

    private val parameterizedTypeMappings: Map<MappingKey, (List<String>) -> Pair<String, List<String>>> = buildMap {
        // Oracle NUMBER with specific precision → MySQL
        put(MappingKey("NUMBER", DialectFamily.ORACLE, DialectFamily.MYSQL)) { params ->
            when {
                params.isEmpty() -> "DECIMAL" to emptyList()
                params.size == 1 -> {
                    val precision = params[0].toIntOrNull() ?: 10
                    when {
                        precision <= 3 -> "TINYINT" to emptyList()
                        precision <= 5 -> "SMALLINT" to emptyList()
                        precision <= 9 -> "INT" to emptyList()
                        precision <= 18 -> "BIGINT" to emptyList()
                        else -> "DECIMAL" to params
                    }
                }
                else -> "DECIMAL" to params
            }
        }
    }
}
