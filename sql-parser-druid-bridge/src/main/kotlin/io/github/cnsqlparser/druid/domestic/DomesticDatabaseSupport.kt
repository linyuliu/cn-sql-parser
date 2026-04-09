package io.github.cnsqlparser.druid.domestic

import io.github.cnsqlparser.common.CompatibilityMode
import io.github.cnsqlparser.common.ParseContext
import io.github.cnsqlparser.common.ProductDialect
import io.github.cnsqlparser.druid.DruidParserBridge
import io.github.cnsqlparser.model.ParseResult

/**
 * 国产数据库支持层
 *
 * Domestic Chinese Database Support Layer.
 *
 * 设计原则：按"语法家族 + 兼容模式 + overlay"拆分
 * - 不按产品名硬拆 parser，而是按语法血缘映射
 * - overlay 层处理各厂商私有扩展
 *
 * 国产数据库路由策略：
 * ┌─────────────────┬──────────────────┬──────────────────────────────────────┐
 * │ 产品             │ 模式              │ 解析策略                              │
 * ├─────────────────┼──────────────────┼──────────────────────────────────────┤
 * │ KINGBASE        │ DEFAULT / PG     │ Druid kingbase / PG家族解析           │
 * │ KINGBASE        │ ORACLE           │ Druid oracle + kingbase overlay       │
 * │ KINGBASE        │ MYSQL            │ Druid mysql + kingbase overlay        │
 * │ KINGBASE        │ SQLSERVER        │ JSqlParser + kingbase overlay         │
 * ├─────────────────┼──────────────────┼──────────────────────────────────────┤
 * │ DM              │ DEFAULT / ORACLE │ Druid dm (Oracle兼容)                 │
 * │ DM              │ MYSQL            │ Druid mysql fallback                  │
 * ├─────────────────┼──────────────────┼──────────────────────────────────────┤
 * │ MOGDB           │ DEFAULT          │ Druid postgresql (PG系)               │
 * │ GAUSSDB         │ DEFAULT          │ Druid gaussdb                         │
 * │ HIGHGO          │ DEFAULT          │ Druid highgo                          │
 * │ VASTBASE        │ DEFAULT          │ Druid postgresql (PG系)               │
 * │ GREENPLUM       │ DEFAULT          │ Druid postgresql (PG系)               │
 * ├─────────────────┼──────────────────┼──────────────────────────────────────┤
 * │ OCEANBASE       │ DEFAULT / MYSQL  │ Druid oceanbase                       │
 * │ OCEANBASE       │ ORACLE           │ Druid oceanbase_oracle                │
 * ├─────────────────┼──────────────────┼──────────────────────────────────────┤
 * │ GOLDENDB        │ DEFAULT          │ Druid mysql                           │
 * │ TIDB            │ DEFAULT          │ Druid tidb                            │
 * │ STARROCKS       │ DEFAULT          │ Druid starrocks                       │
 * └─────────────────┴──────────────────┴──────────────────────────────────────┘
 */
object DomesticDatabaseSupport {

    private val druidBridge = DruidParserBridge()

    /**
     * 解析国产数据库SQL
     */
    fun parse(ctx: ParseContext): ParseResult {
        val handler = getHandler(ctx.product)
        return handler.parse(ctx)
    }

    private fun getHandler(product: ProductDialect): DomesticDbHandler = when (product) {
        ProductDialect.KINGBASE  -> KingbaseHandler(druidBridge)
        ProductDialect.DM        -> DmHandler(druidBridge)
        ProductDialect.MOGDB     -> MogDbHandler(druidBridge)
        ProductDialect.GAUSSDB   -> GaussDbHandler(druidBridge)
        ProductDialect.HIGHGO    -> HighGoHandler(druidBridge)
        ProductDialect.VASTBASE  -> VastbaseHandler(druidBridge)
        ProductDialect.OCEANBASE -> OceanBaseHandler(druidBridge)
        ProductDialect.GOLDENDB  -> GoldenDbHandler(druidBridge)
        ProductDialect.TIDB      -> TiDbHandler(druidBridge)
        ProductDialect.STARROCKS -> StarRocksHandler(druidBridge)
        ProductDialect.GREENPLUM -> GreenplumHandler(druidBridge)
        else -> DefaultDruidHandler(druidBridge)
    }
}

/** 国产数据库处理器接口 */
interface DomesticDbHandler {
    fun parse(ctx: ParseContext): ParseResult
}

/** 默认Druid处理器（直接转发） */
internal class DefaultDruidHandler(private val bridge: DruidParserBridge) : DomesticDbHandler {
    override fun parse(ctx: ParseContext): ParseResult = bridge.parse(ctx)
}

/**
 * 人大金仓 KingbaseES 处理器
 *
 * 支持四种兼容模式：Oracle / MySQL / SQL Server / PostgreSQL
 * 路由到对应的解析策略
 */
internal class KingbaseHandler(private val bridge: DruidParserBridge) : DomesticDbHandler {
    override fun parse(ctx: ParseContext): ParseResult {
        // 根据兼容模式选择对应的Druid DbType
        val effectiveCtx = when (ctx.mode) {
            CompatibilityMode.ORACLE     -> ctx.copy(product = ProductDialect.ORACLE)
            CompatibilityMode.MYSQL      -> ctx.copy(product = ProductDialect.MYSQL)
            CompatibilityMode.SQLSERVER  -> ctx.copy(product = ProductDialect.SQLSERVER)
            else                         -> ctx  // DEFAULT / PG -> kingbase
        }
        return bridge.parse(effectiveCtx).copy(dialect = ProductDialect.KINGBASE)
    }
}

/**
 * 达梦 DM Database 处理器
 *
 * 以Oracle兼容语法为主
 */
internal class DmHandler(private val bridge: DruidParserBridge) : DomesticDbHandler {
    override fun parse(ctx: ParseContext): ParseResult {
        val effectiveCtx = when (ctx.mode) {
            CompatibilityMode.MYSQL -> ctx.copy(product = ProductDialect.MYSQL)
            else -> ctx  // DEFAULT / ORACLE -> dm
        }
        return bridge.parse(effectiveCtx).copy(dialect = ProductDialect.DM)
    }
}

/**
 * MogDB 处理器
 * 基于openGauss，PG系
 */
internal class MogDbHandler(private val bridge: DruidParserBridge) : DomesticDbHandler {
    override fun parse(ctx: ParseContext): ParseResult {
        val pgCtx = ctx.copy(product = ProductDialect.POSTGRESQL)
        return bridge.parse(pgCtx).copy(dialect = ProductDialect.MOGDB)
    }
}

/**
 * GaussDB / openGauss 处理器
 * 基于PostgreSQL
 */
internal class GaussDbHandler(private val bridge: DruidParserBridge) : DomesticDbHandler {
    override fun parse(ctx: ParseContext): ParseResult =
        bridge.parse(ctx).copy(dialect = ProductDialect.GAUSSDB)
}

/**
 * HighGo DB 瀚高 处理器
 * 基于PostgreSQL
 */
internal class HighGoHandler(private val bridge: DruidParserBridge) : DomesticDbHandler {
    override fun parse(ctx: ParseContext): ParseResult =
        bridge.parse(ctx).copy(dialect = ProductDialect.HIGHGO)
}

/**
 * Vastbase G100 海量数据 处理器
 * 基于openGauss/PostgreSQL
 */
internal class VastbaseHandler(private val bridge: DruidParserBridge) : DomesticDbHandler {
    override fun parse(ctx: ParseContext): ParseResult {
        val pgCtx = ctx.copy(product = ProductDialect.POSTGRESQL)
        return bridge.parse(pgCtx).copy(dialect = ProductDialect.VASTBASE)
    }
}

/**
 * OceanBase 处理器
 * 支持MySQL模式（默认）和Oracle模式
 */
internal class OceanBaseHandler(private val bridge: DruidParserBridge) : DomesticDbHandler {
    override fun parse(ctx: ParseContext): ParseResult =
        bridge.parse(ctx).copy(dialect = ProductDialect.OCEANBASE)
}

/**
 * GoldenDB 处理器
 * MySQL兼容为主
 */
internal class GoldenDbHandler(private val bridge: DruidParserBridge) : DomesticDbHandler {
    override fun parse(ctx: ParseContext): ParseResult {
        val mysqlCtx = ctx.copy(product = ProductDialect.MYSQL)
        return bridge.parse(mysqlCtx).copy(dialect = ProductDialect.GOLDENDB)
    }
}

/**
 * TiDB 处理器
 * MySQL兼容协议
 */
internal class TiDbHandler(private val bridge: DruidParserBridge) : DomesticDbHandler {
    override fun parse(ctx: ParseContext): ParseResult =
        bridge.parse(ctx).copy(dialect = ProductDialect.TIDB)
}

/**
 * StarRocks 处理器
 * MySQL兼容 OLAP
 */
internal class StarRocksHandler(private val bridge: DruidParserBridge) : DomesticDbHandler {
    override fun parse(ctx: ParseContext): ParseResult =
        bridge.parse(ctx).copy(dialect = ProductDialect.STARROCKS)
}

/**
 * Greenplum 处理器
 * PG系 MPP数仓
 */
internal class GreenplumHandler(private val bridge: DruidParserBridge) : DomesticDbHandler {
    override fun parse(ctx: ParseContext): ParseResult {
        val pgCtx = ctx.copy(product = ProductDialect.POSTGRESQL)
        return bridge.parse(pgCtx).copy(dialect = ProductDialect.GREENPLUM)
    }
}
