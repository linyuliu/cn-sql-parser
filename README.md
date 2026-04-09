# cn-sql-parser

统一SQL解析器 — Unified SQL Parser for Chinese and mainstream databases

## 设计理念

> 不做"一个超大 parser 硬吃所有数据库"，而是做：  
> **统一SQL模型内核 + 多方言前端 + 多模式补丁层 + 一组能力插件**

### 核心概念：统一SQL模型（Unified SQL Model，USM）

统一SQL模型是本项目的核心，分四层：

| 层次 | 内容 |
|------|------|
| **Statement层** | SelectStmt、InsertStmt、UpdateStmt、DeleteStmt、MergeStmt、CreateTableStmt、AlterTableStmt、DropStmt、UseStmt |
| **Query层** | QueryBlock、FromItem、JoinSpec、CteSpec、OrderItem、LimitSpec、FetchSpec、WindowSpec |
| **Expression层** | ColumnRef、LiteralExpr、BinaryExpr、FunctionCall、CaseExpr、CastExpr、SubqueryExpr 等 |
| **DialectExtension层** | HintNode、VendorTypeRef、ModeSpecificNode、SqlServerTopClause、IndexHintNode |

> **为什么要有第四层（方言扩展层）？**  
> Oracle hint `/*+ INDEX(t idx) */`、MySQL index hint `USE INDEX`、ClickHouse FINAL 等方言特性，
> 如果没有专属存储层，就无处安放。

---

## 项目结构

```
sql-parser-parent
├─ sql-common/              # 公共枚举、异常、工具类
│   ├─ DialectFamily        # 语法家族（MySQL/PG/Oracle/SQLServer/Hive/Trino/ClickHouse）
│   ├─ ProductDialect       # 具体数据库产品（含13个国产数据库）
│   ├─ CompatibilityMode    # 兼容模式（DEFAULT/ORACLE/MYSQL/POSTGRESQL/SQLSERVER）
│   └─ ParseContext         # 解析请求上下文（模式是一等公民）
│
├─ sql-model/               # 统一SQL模型（USM）
│
├─ sql-api/                 # Java友好API门面
│
├─ sql-antlr-runtime/       # ANTLR4公共适配层
│
├─ sql-parser-mysql/        # MySQL ANTLR4解析器（含OceanBase-MySQL/TiDB/StarRocks/GoldenDB）
├─ sql-parser-postgresql/   # PostgreSQL ANTLR4解析器（含Kingbase-PG/MogDB/GaussDB/HighGo/Vastbase）
├─ sql-parser-oracle/       # Oracle解析器（Druid驱动，含DM/Kingbase-Oracle/OceanBase-Oracle）
├─ sql-parser-sqlserver/    # SQL Server解析器（JSqlParser驱动，含Kingbase-SQLServer模式）
│
├─ sql-parser-jsqlparser-bridge/  # JSqlParser桥接（主流SQL fallback/回归对比）
├─ sql-parser-druid-bridge/       # Alibaba Druid桥接（国产数据库核心支持）
│   └─ domestic/            # 国产数据库路由层
│       ├─ KingbaseHandler  # 人大金仓（四种兼容模式）
│       ├─ DmHandler        # 达梦数据库
│       ├─ MogDbHandler     # MogDB
│       ├─ GaussDbHandler   # GaussDB/openGauss
│       ├─ HighGoHandler    # HighGo DB 瀚高
│       ├─ VastbaseHandler  # Vastbase G100 海量数据
│       ├─ OceanBaseHandler # OceanBase（MySQL/Oracle双模）
│       ├─ GoldenDbHandler  # GoldenDB 中兴
│       ├─ TiDbHandler      # TiDB PingCAP
│       └─ StarRocksHandler # StarRocks
│
├─ sql-semantic/            # 语义分析（作用域、别名、元数据绑定）
├─ sql-rewrite/             # SQL改写（LIMIT改写、租户条件注入）
├─ sql-audit/               # 审核规则引擎（无WHERE的DELETE/UPDATE、DROP检查等）
└─ sql-testkit/             # 回归测试框架 + SQL样本库
```

---

## 技术选型

| 组件 | 技术 | 理由 |
|------|------|------|
| 内核语言 | **Kotlin 2.1** | sealed class、data class、扩展函数对语法树建模更顺手 |
| 构建 | **Maven 3 + Kotlin Maven Plugin** | JVM生态，Java项目接入无缝 |
| JDK | **Java 17** | LTS版本 |
| 主流方言解析 | **ANTLR4 4.13** | MySQL/PG visitor/listener 机制解耦 grammar 与业务逻辑 |
| 国产数据库支持 | **Alibaba Druid 1.2** | 原生支持DM、Kingbase、GaussDB、HighGo、OceanBase、StarRocks、TiDB等 |
| 主流SQL fallback | **JSqlParser 4.9** | 快速支持已知语法、回归比对、保底 fallback |
| 对外API | **Java-friendly** | `SqlParserClient`（Java类）+ `SqlParsers`（Kotlin object） |

---

## 国产数据库支持策略

### 核心原则：按"语法家族 + 兼容模式"路由，不按产品名硬拆

```
产品                  模式              解析策略
─────────────────────────────────────────────────────────────────
KINGBASE             DEFAULT / PG    Druid kingbase
KINGBASE             ORACLE          Druid oracle + kingbase overlay
KINGBASE             MYSQL           Druid mysql + kingbase overlay
KINGBASE             SQLSERVER       JSqlParser + kingbase overlay
─────────────────────────────────────────────────────────────────
DM                   DEFAULT/ORACLE  Druid dm
MOGDB                DEFAULT         Druid postgresql
GAUSSDB              DEFAULT         Druid gaussdb
HIGHGO               DEFAULT         Druid highgo
VASTBASE             DEFAULT         Druid postgresql
─────────────────────────────────────────────────────────────────
OCEANBASE            DEFAULT/MYSQL   Druid oceanbase
OCEANBASE            ORACLE          Druid oceanbase_oracle
GOLDENDB             DEFAULT         Druid mysql
TIDB                 DEFAULT         Druid tidb
STARROCKS            DEFAULT         Druid starrocks
```

### 模式是一等公民

```kotlin
// 人大金仓 - Oracle兼容模式（ROWNUM、DUAL、CONNECT BY等）
val ctx = ParseContext(
    sql = "SELECT ROWNUM, name FROM users WHERE ROWNUM <= 10",
    product = ProductDialect.KINGBASE,
    mode = CompatibilityMode.ORACLE
)

// OceanBase - Oracle兼容模式
val ctx = ParseContext(
    sql = "SELECT * FROM t WHERE ROWNUM <= 10",
    product = ProductDialect.OCEANBASE,
    mode = CompatibilityMode.ORACLE
)
```

---

## 快速开始

### Kotlin

```kotlin
import io.github.cnsqlparser.api.SqlParsers
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.common.CompatibilityMode

// 解析MySQL SQL
val result = SqlParsers.parse("SELECT id, name FROM users WHERE age > 18", ProductDialect.MYSQL)

// 解析Kingbase Oracle兼容模式
val result = SqlParsers.parse(
    "SELECT ROWNUM, name FROM users WHERE ROWNUM <= 10",
    ProductDialect.KINGBASE,
    CompatibilityMode.ORACLE
)

// 提取表名
val tables = SqlParsers.extractTables("SELECT a.id FROM orders a JOIN users b ON a.id=b.id", ProductDialect.MYSQL)

// 添加LIMIT
val limited = SqlParsers.addLimit("SELECT * FROM users", ProductDialect.MYSQL, 100L)

// 审核SQL
val violations = SqlParsers.audit("DELETE FROM users", ProductDialect.MYSQL)
```

### Java

```java
import io.github.cnsqlparser.api.SqlParserClient;
import io.github.cnsqlparser.common.ProductDialect;
import io.github.cnsqlparser.common.CompatibilityMode;

SqlParserClient client = new SqlParserClient();

// 解析MySQL SQL
ParseResult result = client.parse("SELECT id, name FROM users WHERE age > 18", ProductDialect.MYSQL);

// 解析达梦 DM（Oracle兼容）
ParseResult dmResult = client.parse("SELECT NVL(salary, 0) FROM employees", ProductDialect.DM);

// 解析人大金仓 Oracle模式
ParseResult kbResult = client.parse(
    "SELECT ROWNUM, name FROM users WHERE ROWNUM <= 10",
    ProductDialect.KINGBASE,
    CompatibilityMode.ORACLE
);

// 添加LIMIT
String limited = client.addLimit("SELECT * FROM users", ProductDialect.MYSQL, 1000L);

// 审核SQL
List<AuditViolation> violations = client.audit("DELETE FROM users", ProductDialect.MYSQL);
```

---

## 支持的数据库

### 主流数据库

| 数据库 | 解析引擎 | 说明 |
|--------|----------|------|
| MySQL | ANTLR4 + Druid | 完整支持 |
| PostgreSQL | ANTLR4 + Druid | 完整支持 |
| Oracle | Druid | 完整支持（ROWNUM、CONNECT BY等） |
| SQL Server | JSqlParser | 完整支持（TOP、T-SQL等） |
| ClickHouse | Druid | 基础支持 |
| Hive | Druid | 基础支持 |
| Trino | Druid | 基础支持 |

### 国产数据库

| 数据库 | 厂商 | 语法家族 | 支持模式 |
|--------|------|----------|----------|
| KingbaseES（人大金仓） | 人大金仓 | PG系 | Oracle/MySQL/SQLServer/PG |
| DM（达梦） | 武汉达梦 | Oracle系 | Oracle（主）/MySQL |
| GaussDB/openGauss | 华为 | PG系 | PG |
| MogDB | 云和恩墨 | PG系 | PG |
| HighGo DB（瀚高） | 瀚高 | PG系 | PG |
| Vastbase G100（海量数据） | 海量数据 | PG系 | PG |
| OceanBase | 蚂蚁集团 | MySQL系 | MySQL/Oracle |
| TiDB | PingCAP | MySQL系 | MySQL |
| GoldenDB | 中兴通讯 | MySQL系 | MySQL |
| StarRocks | StarRocks | MySQL系 | MySQL |
| Greenplum | VMware | PG系 | PG |

---

## 构建

```bash
mvn clean compile
mvn clean test
```

---

## 路线图

### 阶段1（当前）：基础内核
- [x] 统一SQL模型（USM）四层结构
- [x] Maven多模块工程（Kotlin 2 + JDK 17）
- [x] Java友好API
- [x] ANTLR4 MySQL/PG 解析器
- [x] Druid桥接（Oracle/DM/Kingbase/GaussDB/HighGo/OceanBase等）
- [x] JSqlParser桥接（SQL Server/回归对比）
- [x] 国产数据库路由层
- [x] 基础审核规则（无WHERE的DELETE/UPDATE/DROP）
- [x] LIMIT改写规则
- [x] SQL样本库

### 阶段2：四大方言完善
- [ ] MySQL/PG ANTLR4完整AST visitor
- [ ] Oracle ANTLR4 grammar
- [ ] SQL Server ANTLR4 grammar
- [ ] 完整Expression层visitor

### 阶段3：国产数据库专项
- [ ] Kingbase方言差异词典（关键字/函数/类型）
- [ ] 达梦DM专属函数映射
- [ ] OceanBase分区语法支持
- [ ] GaussDB JSON操作支持

### 阶段4：高级能力
- [ ] 列级血缘分析
- [ ] 方言转换（MySQL ↔ Oracle等）
- [ ] 容错解析
- [ ] 增量解析

---

## License

Apache License 2.0
