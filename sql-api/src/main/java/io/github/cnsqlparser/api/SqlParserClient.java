package io.github.cnsqlparser.api;

import io.github.cnsqlparser.audit.AuditViolation;
import io.github.cnsqlparser.common.CompatibilityMode;
import io.github.cnsqlparser.common.ProductDialect;
import io.github.cnsqlparser.model.ParseResult;
import io.github.cnsqlparser.semantic.TableInfo;

import java.util.List;

/**
 * Java-friendly SQL Parser client.
 *
 * Wraps {@link SqlParsers} with explicit Java types for easy integration.
 *
 * <p>Usage example:
 * <pre>{@code
 * SqlParserClient client = new SqlParserClient();
 *
 * // Parse MySQL SQL
 * ParseResult result = client.parse("SELECT id, name FROM users WHERE age > 18", ProductDialect.MYSQL);
 *
 * // Parse Kingbase in Oracle-compatible mode
 * ParseResult kbResult = client.parse(
 *     "SELECT ROWNUM, name FROM users WHERE ROWNUM <= 10",
 *     ProductDialect.KINGBASE,
 *     CompatibilityMode.ORACLE
 * );
 *
 * // Extract table names
 * List<TableInfo> tables = client.extractTables("SELECT a.id FROM orders a JOIN items b ON a.id=b.order_id", ProductDialect.MYSQL);
 *
 * // Add limit
 * String limited = client.addLimit("SELECT * FROM big_table", ProductDialect.POSTGRESQL, 1000);
 *
 * // Audit SQL
 * List<AuditViolation> violations = client.audit("DELETE FROM users", ProductDialect.MYSQL);
 * }</pre>
 */
public class SqlParserClient {

    /**
     * Parses SQL using the default MySQL dialect.
     */
    public ParseResult parse(String sql) {
        return SqlParsers.parse(sql);
    }

    /**
     * Parses SQL with the specified dialect.
     */
    public ParseResult parse(String sql, ProductDialect dialect) {
        return SqlParsers.parse(sql, dialect);
    }

    /**
     * Parses SQL with the specified dialect and compatibility mode.
     * Use this for multi-mode databases like KingbaseES and OceanBase.
     *
     * @param sql     the SQL text to parse
     * @param dialect the target database product
     * @param mode    the compatibility mode (e.g. ORACLE for Kingbase Oracle mode)
     */
    public ParseResult parse(String sql, ProductDialect dialect, CompatibilityMode mode) {
        return SqlParsers.parse(sql, dialect, mode);
    }

    /**
     * Extracts all table references from the SQL.
     */
    public List<TableInfo> extractTables(String sql, ProductDialect dialect) {
        return SqlParsers.extractTables(sql, dialect);
    }

    /**
     * Quickly extracts table names using JSqlParser (best-effort).
     */
    public List<String> extractTablesFast(String sql) {
        return SqlParsers.extractTablesFast(sql);
    }

    /**
     * Adds a LIMIT clause to a SELECT statement.
     * Automatically uses the correct syntax for the target dialect.
     *
     * @param sql     the SELECT SQL
     * @param dialect the target database product
     * @param maxRows the maximum number of rows
     */
    public String addLimit(String sql, ProductDialect dialect, long maxRows) {
        return SqlParsers.addLimit(sql, dialect, maxRows);
    }

    /**
     * Formats SQL using the Druid SQL formatter.
     */
    public String format(String sql, ProductDialect dialect) {
        return SqlParsers.format(sql, dialect);
    }

    /**
     * Audits SQL and returns a list of violations.
     */
    public List<AuditViolation> audit(String sql, ProductDialect dialect) {
        return SqlParsers.audit(sql, dialect);
    }
}
