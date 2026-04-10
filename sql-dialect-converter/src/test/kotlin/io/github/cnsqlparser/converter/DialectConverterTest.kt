package io.github.cnsqlparser.converter

import io.github.cnsqlparser.common.DialectFamily
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.converter.generator.*
import io.github.cnsqlparser.converter.mapping.DataTypeMapper
import io.github.cnsqlparser.converter.mapping.FunctionMapper
import io.github.cnsqlparser.model.ParseResult
import io.github.cnsqlparser.model.expression.*
import io.github.cnsqlparser.model.query.*
import io.github.cnsqlparser.model.statement.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DialectConverterTest {

    // ─── Helper: build a simple SELECT AST by hand ───────────────────────────

    private fun simpleSelect(
        columns: List<SelectItem> = listOf(SelectItem(StarExpr())),
        table: String = "users",
        schema: String? = null,
        alias: String? = null,
        where: SqlExpression? = null,
        limit: LimitSpec? = null,
        fetch: FetchSpec? = null,
        orderBy: List<OrderItem> = emptyList(),
        dialect: ProductDialect? = null
    ): SelectStmt {
        val fromItem = TableFromItem(name = table, schema = schema, alias = alias)
        val query = QueryBlock(
            selectItems = columns,
            fromItems = listOf(fromItem),
            where = where,
            limit = limit,
            fetch = fetch,
            orderBy = orderBy
        )
        return SelectStmt(query = query, dialect = dialect)
    }

    // ─── MySQL Generator Tests ───────────────────────────────────────────────

    @Nested
    inner class MySqlGeneratorTests {
        private val generator = MySqlGenerator()

        @Test
        fun `generates simple SELECT star`() {
            val stmt = simpleSelect()
            val sql = generator.generate(stmt)
            assertThat(sql).contains("SELECT *")
            assertThat(sql).contains("FROM `users`")
        }

        @Test
        fun `generates SELECT with columns`() {
            val columns = listOf(
                SelectItem(ColumnRef("id")),
                SelectItem(ColumnRef("name"), alias = "user_name")
            )
            val stmt = simpleSelect(columns = columns)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("`id`")
            assertThat(sql).contains("`name` AS `user_name`")
        }

        @Test
        fun `generates SELECT with WHERE clause`() {
            val where = BinaryExpr(ColumnRef("age"), ">", LiteralExpr(18, LiteralType.INTEGER))
            val stmt = simpleSelect(where = where)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("WHERE `age` > 18")
        }

        @Test
        fun `generates SELECT with LIMIT`() {
            val limit = LimitSpec(limit = LiteralExpr(10, LiteralType.INTEGER))
            val stmt = simpleSelect(limit = limit)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("LIMIT 10")
        }

        @Test
        fun `generates INSERT with values`() {
            val stmt = InsertStmt(
                table = TableRef("users"),
                columns = listOf(ColumnRef("name"), ColumnRef("email")),
                values = listOf(
                    listOf(LiteralExpr("John", LiteralType.STRING), LiteralExpr("john@test.com", LiteralType.STRING))
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("INSERT INTO `users`")
            assertThat(sql).contains("(`name`, `email`)")
            assertThat(sql).contains("VALUES ('John', 'john@test.com')")
        }

        @Test
        fun `generates UPDATE with SET and WHERE`() {
            val stmt = UpdateStmt(
                table = TableRef("users"),
                assignments = listOf(Assignment(ColumnRef("name"), LiteralExpr("Jane", LiteralType.STRING))),
                where = BinaryExpr(ColumnRef("id"), "=", LiteralExpr(1, LiteralType.INTEGER))
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("UPDATE `users` SET `name` = 'Jane' WHERE `id` = 1")
        }

        @Test
        fun `generates DELETE with WHERE`() {
            val stmt = DeleteStmt(
                table = TableRef("users"),
                where = BinaryExpr(ColumnRef("id"), "=", LiteralExpr(1, LiteralType.INTEGER))
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("DELETE FROM `users` WHERE `id` = 1")
        }

        @Test
        fun `generates CREATE TABLE with columns`() {
            val stmt = CreateTableStmt(
                table = TableRef("employees"),
                columns = listOf(
                    ColumnDefinition("id", "INT", autoIncrement = true, primaryKey = true, nullable = false),
                    ColumnDefinition("name", "VARCHAR", typeParams = listOf("100"), nullable = false),
                    ColumnDefinition("salary", "DECIMAL", typeParams = listOf("10", "2"))
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("CREATE TABLE `employees`")
            assertThat(sql).contains("`id` INT NOT NULL AUTO_INCREMENT PRIMARY KEY")
            assertThat(sql).contains("`name` VARCHAR(100) NOT NULL")
            assertThat(sql).contains("`salary` DECIMAL(10, 2)")
        }

        @Test
        fun `generates DROP TABLE IF EXISTS`() {
            val stmt = DropStmt(DropObjectType.TABLE, "users", ifExists = true)
            val sql = generator.generate(stmt)
            assertThat(sql).isEqualTo("DROP TABLE IF EXISTS `users`")
        }

        @Test
        fun `generates USE statement`() {
            val stmt = UseStmt("mydb")
            val sql = generator.generate(stmt)
            assertThat(sql).isEqualTo("USE `mydb`")
        }

        @Test
        fun `generates SELECT with JOIN`() {
            val fromItem = JoinFromItem(
                left = TableFromItem("users", alias = "u"),
                right = TableFromItem("orders", alias = "o"),
                joinType = JoinType.LEFT,
                condition = OnJoinSpec(BinaryExpr(ColumnRef("id", "u"), "=", ColumnRef("user_id", "o")))
            )
            val query = QueryBlock(
                selectItems = listOf(SelectItem(StarExpr())),
                fromItems = listOf(fromItem)
            )
            val stmt = SelectStmt(query = query)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("LEFT JOIN")
            assertThat(sql).contains("ON `u`.`id` = `o`.`user_id`")
        }

        @Test
        fun `generates window function`() {
            val windowFunc = WindowFunctionExpr(
                function = FunctionCall("ROW_NUMBER"),
                windowSpec = WindowSpec(
                    partitionBy = listOf(ColumnRef("dept_id")),
                    orderBy = listOf(OrderItem(ColumnRef("salary"), ascending = false))
                )
            )
            val columns = listOf(
                SelectItem(ColumnRef("name")),
                SelectItem(windowFunc, alias = "rn")
            )
            val stmt = simpleSelect(columns = columns, table = "employees")
            val sql = generator.generate(stmt)
            assertThat(sql).contains("ROW_NUMBER() OVER (PARTITION BY `dept_id` ORDER BY `salary` DESC)")
        }

        @Test
        fun `generates CASE expression`() {
            val caseExpr = CaseExpr(
                whenClauses = listOf(
                    WhenClause(
                        BinaryExpr(ColumnRef("status"), "=", LiteralExpr("A", LiteralType.STRING)),
                        LiteralExpr("Active", LiteralType.STRING)
                    ),
                    WhenClause(
                        BinaryExpr(ColumnRef("status"), "=", LiteralExpr("I", LiteralType.STRING)),
                        LiteralExpr("Inactive", LiteralType.STRING)
                    )
                ),
                elseExpr = LiteralExpr("Unknown", LiteralType.STRING)
            )
            val columns = listOf(SelectItem(caseExpr, alias = "status_desc"))
            val stmt = simpleSelect(columns = columns)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("CASE WHEN")
            assertThat(sql).contains("THEN 'Active'")
            assertThat(sql).contains("ELSE 'Unknown' END")
        }
    }

    // ─── Oracle Generator Tests ──────────────────────────────────────────────

    @Nested
    inner class OracleGeneratorTests {
        private val generator = OracleSqlGenerator()

        @Test
        fun `uses double-quote for identifiers`() {
            val stmt = simpleSelect()
            val sql = generator.generate(stmt)
            assertThat(sql).contains("\"users\"")
        }

        @Test
        fun `converts LIMIT to FETCH FIRST`() {
            val limit = LimitSpec(limit = LiteralExpr(10, LiteralType.INTEGER))
            val stmt = simpleSelect(limit = limit)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("FETCH FIRST 10 ROWS ONLY")
            assertThat(sql).doesNotContain("LIMIT")
        }

        @Test
        fun `generates IDENTITY for auto-increment`() {
            val stmt = CreateTableStmt(
                table = TableRef("employees"),
                columns = listOf(
                    ColumnDefinition("id", "NUMBER", typeParams = listOf("10"), autoIncrement = true, primaryKey = true, nullable = false)
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("GENERATED ALWAYS AS IDENTITY")
        }

        @Test
        fun `converts boolean to 1 and 0`() {
            val where = BinaryExpr(ColumnRef("active"), "=", LiteralExpr(true, LiteralType.BOOLEAN))
            val stmt = simpleSelect(where = where)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("= 1")
        }

        @Test
        fun `generates ALTER SESSION for USE`() {
            val stmt = UseStmt("myschema")
            val sql = generator.generate(stmt)
            assertThat(sql).contains("ALTER SESSION SET CURRENT_SCHEMA")
        }
    }

    // ─── PostgreSQL Generator Tests ──────────────────────────────────────────

    @Nested
    inner class PostgreSqlGeneratorTests {
        private val generator = PostgreSqlGenerator()

        @Test
        fun `uses double-quote for identifiers`() {
            val stmt = simpleSelect()
            val sql = generator.generate(stmt)
            assertThat(sql).contains("\"users\"")
        }

        @Test
        fun `supports LIMIT`() {
            val limit = LimitSpec(limit = LiteralExpr(10, LiteralType.INTEGER))
            val stmt = simpleSelect(limit = limit)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("LIMIT 10")
        }

        @Test
        fun `generates SET search_path for USE`() {
            val stmt = UseStmt("myschema")
            val sql = generator.generate(stmt)
            assertThat(sql).contains("SET search_path TO")
        }

        @Test
        fun `generates ALTER COLUMN TYPE for modify`() {
            val stmt = AlterTableStmt(
                table = TableRef("users"),
                actions = listOf(
                    ModifyColumnAction(ColumnDefinition("name", "VARCHAR", typeParams = listOf("200")))
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("ALTER COLUMN \"name\" TYPE VARCHAR(200)")
        }
    }

    // ─── SQL Server Generator Tests ──────────────────────────────────────────

    @Nested
    inner class SqlServerGeneratorTests {
        private val generator = SqlServerGenerator()

        @Test
        fun `uses square brackets for identifiers`() {
            val stmt = simpleSelect()
            val sql = generator.generate(stmt)
            assertThat(sql).contains("[users]")
        }

        @Test
        fun `generates TOP when no ORDER BY`() {
            val limit = LimitSpec(limit = LiteralExpr(10, LiteralType.INTEGER))
            val stmt = simpleSelect(limit = limit)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("TOP 10")
            assertThat(sql).doesNotContain("LIMIT")
        }

        @Test
        fun `generates OFFSET-FETCH when ORDER BY present`() {
            val limit = LimitSpec(limit = LiteralExpr(10, LiteralType.INTEGER))
            val orderBy = listOf(OrderItem(ColumnRef("id")))
            val stmt = simpleSelect(limit = limit, orderBy = orderBy)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("OFFSET 0 ROWS FETCH NEXT 10 ROWS ONLY")
        }

        @Test
        fun `generates IDENTITY for auto-increment`() {
            val stmt = CreateTableStmt(
                table = TableRef("employees"),
                columns = listOf(
                    ColumnDefinition("id", "INT", autoIncrement = true, primaryKey = true, nullable = false)
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("IDENTITY(1,1)")
        }

        @Test
        fun `generates N-prefix for strings`() {
            val columns = listOf(SelectItem(LiteralExpr("Hello", LiteralType.STRING)))
            val stmt = simpleSelect(columns = columns)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("N'Hello'")
        }

        @Test
        fun `converts boolean to 1 and 0`() {
            val where = BinaryExpr(ColumnRef("active"), "=", LiteralExpr(true, LiteralType.BOOLEAN))
            val stmt = simpleSelect(where = where)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("= 1")
        }
    }

    // ─── Cross-Dialect Conversion Tests ──────────────────────────────────────

    @Nested
    inner class CrossDialectConversionTests {

        @Test
        fun `MySQL to Oracle - LIMIT converts to FETCH FIRST`() {
            val limit = LimitSpec(limit = LiteralExpr(10, LiteralType.INTEGER))
            val stmt = simpleSelect(limit = limit, dialect = ProductDialect.MYSQL)
            val result = ParseResult(listOf(stmt), dialect = ProductDialect.MYSQL)

            val converted = DialectConverter.convert(result, ProductDialect.ORACLE)
            assertThat(converted.sql).contains("FETCH FIRST 10 ROWS ONLY")
            assertThat(converted.sql).doesNotContain("LIMIT")
        }

        @Test
        fun `MySQL to SQL Server - LIMIT converts to TOP`() {
            val limit = LimitSpec(limit = LiteralExpr(5, LiteralType.INTEGER))
            val stmt = simpleSelect(limit = limit, dialect = ProductDialect.MYSQL)
            val result = ParseResult(listOf(stmt), dialect = ProductDialect.MYSQL)

            val converted = DialectConverter.convert(result, ProductDialect.SQLSERVER)
            assertThat(converted.sql).contains("TOP 5")
            assertThat(converted.sql).doesNotContain("LIMIT")
        }

        @Test
        fun `Oracle to MySQL - FetchSpec not supported in MySQL produces output without pagination`() {
            val fetch = FetchSpec(count = LiteralExpr(20, LiteralType.INTEGER))
            val fromItem = TableFromItem(name = "employees")
            val query = QueryBlock(
                selectItems = listOf(SelectItem(StarExpr())),
                fromItems = listOf(fromItem),
                fetch = fetch
            )
            val stmt = SelectStmt(query = query, dialect = ProductDialect.ORACLE)
            val result = ParseResult(listOf(stmt), dialect = ProductDialect.ORACLE)

            // MySQL doesn't support FETCH FIRST natively.
            // When FetchSpec is on the AST but the generator doesn't support it,
            // the query is generated without pagination.
            val converted = DialectConverter.convert(result, ProductDialect.MYSQL)
            assertThat(converted.sql).contains("SELECT * FROM `employees`")
            assertThat(converted.sql).doesNotContain("FETCH FIRST")
        }

        @Test
        fun `MySQL to PostgreSQL - basic SELECT`() {
            val columns = listOf(
                SelectItem(ColumnRef("id")),
                SelectItem(ColumnRef("name"))
            )
            val where = BinaryExpr(ColumnRef("age"), ">", LiteralExpr(18, LiteralType.INTEGER))
            val stmt = simpleSelect(columns = columns, where = where, dialect = ProductDialect.MYSQL)
            val result = ParseResult(listOf(stmt), dialect = ProductDialect.MYSQL)

            val converted = DialectConverter.convert(result, ProductDialect.POSTGRESQL)
            assertThat(converted.sql).contains("SELECT")
            assertThat(converted.sql).contains("\"id\"")
            assertThat(converted.sql).contains("\"name\"")
            assertThat(converted.sql).contains("WHERE")
        }

        @Test
        fun `function mapping - IFNULL to NVL (MySQL to Oracle)`() {
            val funcCall = FunctionCall(
                "IFNULL",
                arguments = listOf(ColumnRef("name"), LiteralExpr("unknown", LiteralType.STRING))
            )
            val columns = listOf(SelectItem(funcCall, alias = "real_name"))
            val stmt = simpleSelect(columns = columns, dialect = ProductDialect.MYSQL)
            val result = ParseResult(listOf(stmt), dialect = ProductDialect.MYSQL)

            val converted = DialectConverter.convert(result, ProductDialect.ORACLE)
            assertThat(converted.sql).contains("NVL")
            assertThat(converted.sql).doesNotContain("IFNULL")
        }

        @Test
        fun `function mapping - NVL to COALESCE (Oracle to PostgreSQL)`() {
            val funcCall = FunctionCall(
                "NVL",
                arguments = listOf(ColumnRef("name"), LiteralExpr("unknown", LiteralType.STRING))
            )
            val columns = listOf(SelectItem(funcCall))
            val stmt = simpleSelect(columns = columns, dialect = ProductDialect.ORACLE)
            val result = ParseResult(listOf(stmt), dialect = ProductDialect.ORACLE)

            val converted = DialectConverter.convert(result, ProductDialect.POSTGRESQL)
            assertThat(converted.sql).contains("COALESCE")
        }

        @Test
        fun `function mapping - NVL to ISNULL (Oracle to SQL Server)`() {
            val funcCall = FunctionCall(
                "NVL",
                arguments = listOf(ColumnRef("name"), LiteralExpr("unknown", LiteralType.STRING))
            )
            val columns = listOf(SelectItem(funcCall))
            val stmt = simpleSelect(columns = columns, dialect = ProductDialect.ORACLE)
            val result = ParseResult(listOf(stmt), dialect = ProductDialect.ORACLE)

            val converted = DialectConverter.convert(result, ProductDialect.SQLSERVER)
            assertThat(converted.sql).contains("ISNULL")
        }

        @Test
        fun `function mapping - NOW to SYSDATE (MySQL to Oracle)`() {
            val funcCall = FunctionCall("NOW")
            val columns = listOf(SelectItem(funcCall, alias = "current_time"))
            val stmt = simpleSelect(columns = columns, dialect = ProductDialect.MYSQL)
            val result = ParseResult(listOf(stmt), dialect = ProductDialect.MYSQL)

            val converted = DialectConverter.convert(result, ProductDialect.ORACLE)
            assertThat(converted.sql).contains("SYSDATE")
        }

        @Test
        fun `function mapping - GETDATE to NOW (SQL Server to MySQL)`() {
            val funcCall = FunctionCall("GETDATE")
            val columns = listOf(SelectItem(funcCall, alias = "now"))
            val stmt = simpleSelect(columns = columns, dialect = ProductDialect.SQLSERVER)
            val result = ParseResult(listOf(stmt), dialect = ProductDialect.SQLSERVER)

            val converted = DialectConverter.convert(result, ProductDialect.MYSQL)
            assertThat(converted.sql).contains("NOW")
        }
    }

    // ─── DDL Type Mapping Tests ──────────────────────────────────────────────

    @Nested
    inner class DdlTypeConversionTests {

        @Test
        fun `MySQL INT maps to Oracle NUMBER(10)`() {
            val generator = OracleSqlGenerator(DialectFamily.MYSQL)
            val stmt = CreateTableStmt(
                table = TableRef("test"),
                columns = listOf(
                    ColumnDefinition("id", "INT", nullable = false)
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("NUMBER(10)")
        }

        @Test
        fun `MySQL DATETIME maps to Oracle TIMESTAMP`() {
            val generator = OracleSqlGenerator(DialectFamily.MYSQL)
            val stmt = CreateTableStmt(
                table = TableRef("test"),
                columns = listOf(
                    ColumnDefinition("created_at", "DATETIME")
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("TIMESTAMP")
        }

        @Test
        fun `MySQL TEXT maps to Oracle CLOB`() {
            val generator = OracleSqlGenerator(DialectFamily.MYSQL)
            val stmt = CreateTableStmt(
                table = TableRef("test"),
                columns = listOf(
                    ColumnDefinition("content", "TEXT")
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("CLOB")
        }

        @Test
        fun `MySQL DOUBLE maps to PG DOUBLE PRECISION`() {
            val generator = PostgreSqlGenerator(DialectFamily.MYSQL)
            val stmt = CreateTableStmt(
                table = TableRef("test"),
                columns = listOf(
                    ColumnDefinition("amount", "DOUBLE")
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("DOUBLE PRECISION")
        }

        @Test
        fun `MySQL JSON maps to PG JSONB`() {
            val generator = PostgreSqlGenerator(DialectFamily.MYSQL)
            val stmt = CreateTableStmt(
                table = TableRef("test"),
                columns = listOf(
                    ColumnDefinition("data", "JSON")
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("JSONB")
        }

        @Test
        fun `Oracle VARCHAR2 maps to MySQL VARCHAR`() {
            val generator = MySqlGenerator(DialectFamily.ORACLE)
            val stmt = CreateTableStmt(
                table = TableRef("test"),
                columns = listOf(
                    ColumnDefinition("name", "VARCHAR2", typeParams = listOf("100"))
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("VARCHAR(100)")
            assertThat(sql).doesNotContain("VARCHAR2")
        }

        @Test
        fun `PG SERIAL maps to MySQL INT`() {
            val generator = MySqlGenerator(DialectFamily.POSTGRESQL)
            val stmt = CreateTableStmt(
                table = TableRef("test"),
                columns = listOf(
                    ColumnDefinition("id", "SERIAL")
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("INT")
        }

        @Test
        fun `SQL Server NVARCHAR maps to MySQL VARCHAR`() {
            val generator = MySqlGenerator(DialectFamily.SQLSERVER)
            val stmt = CreateTableStmt(
                table = TableRef("test"),
                columns = listOf(
                    ColumnDefinition("name", "NVARCHAR", typeParams = listOf("200"))
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("VARCHAR(200)")
        }

        @Test
        fun `SQL Server UNIQUEIDENTIFIER maps to PG UUID`() {
            val generator = PostgreSqlGenerator(DialectFamily.SQLSERVER)
            val stmt = CreateTableStmt(
                table = TableRef("test"),
                columns = listOf(
                    ColumnDefinition("id", "UNIQUEIDENTIFIER")
                )
            )
            val sql = generator.generate(stmt)
            assertThat(sql).contains("UUID")
        }
    }

    // ─── DataTypeMapper Direct Tests ─────────────────────────────────────────

    @Nested
    inner class DataTypeMapperTests {

        @Test
        fun `same dialect returns unchanged`() {
            val (type, params) = DataTypeMapper.mapType("INT", DialectFamily.MYSQL, DialectFamily.MYSQL)
            assertThat(type).isEqualTo("INT")
        }

        @Test
        fun `MySQL INT to Oracle`() {
            val (type, params) = DataTypeMapper.mapType("INT", DialectFamily.MYSQL, DialectFamily.ORACLE)
            assertThat(type).isEqualTo("NUMBER")
            assertThat(params).contains("10")
        }

        @Test
        fun `MySQL BIGINT to Oracle`() {
            val (type, params) = DataTypeMapper.mapType("BIGINT", DialectFamily.MYSQL, DialectFamily.ORACLE)
            assertThat(type).isEqualTo("NUMBER")
            assertThat(params).contains("19")
        }

        @Test
        fun `Oracle CLOB to MySQL`() {
            val (type, _) = DataTypeMapper.mapType("CLOB", DialectFamily.ORACLE, DialectFamily.MYSQL)
            assertThat(type).isEqualTo("LONGTEXT")
        }

        @Test
        fun `Oracle NUMBER with precision to MySQL`() {
            val (type, _) = DataTypeMapper.mapType("NUMBER", DialectFamily.ORACLE, DialectFamily.MYSQL, listOf("5"))
            assertThat(type).isEqualTo("SMALLINT")
        }

        @Test
        fun `PG BYTEA to MySQL`() {
            val (type, _) = DataTypeMapper.mapType("BYTEA", DialectFamily.POSTGRESQL, DialectFamily.MYSQL)
            assertThat(type).isEqualTo("LONGBLOB")
        }

        @Test
        fun `SQL Server BIT to PG`() {
            val (type, _) = DataTypeMapper.mapType("BIT", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL)
            assertThat(type).isEqualTo("BOOLEAN")
        }

        @Test
        fun `unknown type returns as-is`() {
            val (type, _) = DataTypeMapper.mapType("CUSTOM_TYPE", DialectFamily.MYSQL, DialectFamily.ORACLE)
            assertThat(type).isEqualTo("CUSTOM_TYPE")
        }
    }

    // ─── FunctionMapper Direct Tests ─────────────────────────────────────────

    @Nested
    inner class FunctionMapperTests {

        @Test
        fun `same dialect returns null`() {
            val mapping = FunctionMapper.mapFunction("IFNULL", DialectFamily.MYSQL, DialectFamily.MYSQL)
            assertThat(mapping).isNull()
        }

        @Test
        fun `MySQL IFNULL to Oracle NVL`() {
            val mapping = FunctionMapper.mapFunction("IFNULL", DialectFamily.MYSQL, DialectFamily.ORACLE)
            assertThat(mapping).isNotNull
            assertThat(mapping!!.targetName).isEqualTo("NVL")
        }

        @Test
        fun `Oracle NVL to PostgreSQL COALESCE`() {
            val mapping = FunctionMapper.mapFunction("NVL", DialectFamily.ORACLE, DialectFamily.POSTGRESQL)
            assertThat(mapping).isNotNull
            assertThat(mapping!!.targetName).isEqualTo("COALESCE")
        }

        @Test
        fun `Oracle SYSDATE to MySQL NOW`() {
            val mapping = FunctionMapper.mapFunction("SYSDATE", DialectFamily.ORACLE, DialectFamily.MYSQL)
            assertThat(mapping).isNotNull
            assertThat(mapping!!.targetName).isEqualTo("NOW")
        }

        @Test
        fun `MySQL NOW to SQL Server GETDATE`() {
            val mapping = FunctionMapper.mapFunction("NOW", DialectFamily.MYSQL, DialectFamily.SQLSERVER)
            assertThat(mapping).isNotNull
            assertThat(mapping!!.targetName).isEqualTo("GETDATE")
        }

        @Test
        fun `SQL Server LEN to PostgreSQL LENGTH`() {
            val mapping = FunctionMapper.mapFunction("LEN", DialectFamily.SQLSERVER, DialectFamily.POSTGRESQL)
            assertThat(mapping).isNotNull
            assertThat(mapping!!.targetName).isEqualTo("LENGTH")
        }

        @Test
        fun `Oracle SUBSTR to MySQL SUBSTRING`() {
            val mapping = FunctionMapper.mapFunction("SUBSTR", DialectFamily.ORACLE, DialectFamily.MYSQL)
            assertThat(mapping).isNotNull
            assertThat(mapping!!.targetName).isEqualTo("SUBSTRING")
        }

        @Test
        fun `unknown function returns null`() {
            val mapping = FunctionMapper.mapFunction("MY_CUSTOM_FUNC", DialectFamily.MYSQL, DialectFamily.ORACLE)
            assertThat(mapping).isNull()
        }
    }

    // ─── ConversionResult Tests ──────────────────────────────────────────────

    @Nested
    inner class ConversionResultTests {

        @Test
        fun `convert single statement`() {
            val stmt = simpleSelect(dialect = ProductDialect.MYSQL)
            val result = DialectConverter.convertStatement(
                stmt, ProductDialect.MYSQL, ProductDialect.ORACLE
            )
            assertThat(result.sql).isNotBlank()
            assertThat(result.sourceDialect).isEqualTo(ProductDialect.MYSQL)
            assertThat(result.targetDialect).isEqualTo(ProductDialect.ORACLE)
        }

        @Test
        fun `convert multiple statements`() {
            val stmt1 = simpleSelect(table = "users", dialect = ProductDialect.MYSQL)
            val stmt2 = simpleSelect(table = "orders", dialect = ProductDialect.MYSQL)
            val result = ParseResult(listOf(stmt1, stmt2), dialect = ProductDialect.MYSQL)

            val converted = DialectConverter.convert(result, ProductDialect.POSTGRESQL)
            assertThat(converted.sql).contains("\"users\"")
            assertThat(converted.sql).contains("\"orders\"")
        }

        @Test
        fun `GenericStmt returns original text with warning`() {
            val stmt = GenericStmt("DECLARE @x INT = 1")
            val result = ParseResult(listOf(stmt), dialect = ProductDialect.SQLSERVER)

            val converted = DialectConverter.convert(result, ProductDialect.MYSQL)
            assertThat(converted.sql).contains("DECLARE @x INT = 1")
            assertThat(converted.hasWarnings).isTrue()
        }
    }

    // ─── Complex Query Tests ─────────────────────────────────────────────────

    @Nested
    inner class ComplexQueryTests {

        @Test
        fun `generates UNION query`() {
            val generator = MySqlGenerator()
            val rightQuery = QueryBlock(
                selectItems = listOf(SelectItem(ColumnRef("name"))),
                fromItems = listOf(TableFromItem("admins"))
            )
            val query = QueryBlock(
                selectItems = listOf(SelectItem(ColumnRef("name"))),
                fromItems = listOf(TableFromItem("users")),
                setOp = SetOperation(SetOpType.UNION, all = true, right = rightQuery)
            )
            val stmt = SelectStmt(query = query)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("UNION ALL")
            assertThat(sql).contains("`users`")
            assertThat(sql).contains("`admins`")
        }

        @Test
        fun `generates CTE query`() {
            val generator = PostgreSqlGenerator()
            val cteQuery = QueryBlock(
                selectItems = listOf(SelectItem(StarExpr())),
                fromItems = listOf(TableFromItem("employees")),
                where = BinaryExpr(ColumnRef("salary"), ">", LiteralExpr(50000, LiteralType.INTEGER))
            )
            val mainQuery = QueryBlock(
                ctes = listOf(CteSpec("high_earners", query = cteQuery)),
                selectItems = listOf(SelectItem(StarExpr())),
                fromItems = listOf(TableFromItem("high_earners"))
            )
            val stmt = SelectStmt(query = mainQuery)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("WITH \"high_earners\" AS")
            assertThat(sql).contains("\"salary\" > 50000")
        }

        @Test
        fun `generates subquery in FROM`() {
            val generator = MySqlGenerator()
            val subQuery = QueryBlock(
                selectItems = listOf(
                    SelectItem(ColumnRef("dept_id")),
                    SelectItem(FunctionCall("COUNT", star = true), alias = "cnt")
                ),
                fromItems = listOf(TableFromItem("employees")),
                groupBy = listOf(ColumnRef("dept_id"))
            )
            val mainQuery = QueryBlock(
                selectItems = listOf(SelectItem(StarExpr())),
                fromItems = listOf(SubqueryFromItem(subQuery, alias = "dept_counts")),
                where = BinaryExpr(ColumnRef("cnt"), ">", LiteralExpr(5, LiteralType.INTEGER))
            )
            val stmt = SelectStmt(query = mainQuery)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("GROUP BY")
            assertThat(sql).contains("`dept_counts`")
            assertThat(sql).contains("COUNT(*)")
        }

        @Test
        fun `generates BETWEEN expression`() {
            val generator = MySqlGenerator()
            val between = BetweenExpr(
                ColumnRef("age"),
                LiteralExpr(18, LiteralType.INTEGER),
                LiteralExpr(65, LiteralType.INTEGER)
            )
            val stmt = simpleSelect(where = between)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("`age` BETWEEN 18 AND 65")
        }

        @Test
        fun `generates IN expression`() {
            val generator = MySqlGenerator()
            val inExpr = InExpr(
                ColumnRef("status"),
                values = listOf(
                    LiteralExpr("active", LiteralType.STRING),
                    LiteralExpr("pending", LiteralType.STRING)
                )
            )
            val stmt = simpleSelect(where = inExpr)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("`status` IN ('active', 'pending')")
        }

        @Test
        fun `generates CAST expression with type mapping`() {
            val generator = OracleSqlGenerator(DialectFamily.MYSQL)
            val cast = CastExpr(ColumnRef("value"), "INT")
            val columns = listOf(SelectItem(cast, alias = "int_val"))
            val stmt = simpleSelect(columns = columns)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("CAST")
            assertThat(sql).contains("NUMBER(10)")
        }

        @Test
        fun `generates EXISTS subquery`() {
            val generator = MySqlGenerator()
            val subQuery = QueryBlock(
                selectItems = listOf(SelectItem(LiteralExpr(1, LiteralType.INTEGER))),
                fromItems = listOf(TableFromItem("orders")),
                where = BinaryExpr(ColumnRef("user_id", "orders"), "=", ColumnRef("id", "users"))
            )
            val existsExpr = ExistsExpr(SubqueryExpr(subQuery))
            val stmt = simpleSelect(where = existsExpr)
            val sql = generator.generate(stmt)
            assertThat(sql).contains("EXISTS")
            assertThat(sql).contains("`orders`")
        }
    }
}
