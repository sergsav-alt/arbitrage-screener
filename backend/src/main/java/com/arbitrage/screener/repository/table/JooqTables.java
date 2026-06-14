package com.arbitrage.screener.repository.table;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.math.BigDecimal;
import java.time.Instant;

 public class JooqTables {

    private JooqTables() {}

    public static final Table<?> SPREAD_HISTORY = DSL.table("spread_history");
    public static final Field<Long> SH_ID = DSL.field("id", Long.class);
    public static final Field<String> SH_SYMBOL = DSL.field("symbol", String.class);
    public static final Field<String> SH_BUY_EXCHANGE = DSL.field("buy_exchange", String.class);
    public static final Field<String> SH_SELL_EXCHANGE = DSL.field("sell_exchange", String.class);
    public static final Field<BigDecimal> SH_BUY_PRICE = DSL.field("buy_price", BigDecimal.class);
    public static final Field<BigDecimal> SH_SELL_PRICE = DSL.field("sell_price", BigDecimal.class);
    public static final Field<BigDecimal> SH_SPREAD_PERCENT = DSL.field("spread_percent", BigDecimal.class);
    public static final Field<BigDecimal> SH_NET_PROFIT = DSL.field("net_profit", BigDecimal.class);
    public static final Field<BigDecimal> SH_MAX_VOLUME = DSL.field("max_volume", BigDecimal.class);
    public static final Field<Instant> SH_RECORDED_AT = DSL.field("recorded_at", Instant.class);

    public static final Table<?> ARBITRAGE_EVENT = DSL.table("arbitrage_event");
    public static final Field<Long> AE_ID = DSL.field("id", Long.class);
    public static final Field<String> AE_TYPE = DSL.field("type", String.class);
    public static final Field<String> AE_SYMBOL = DSL.field("symbol", String.class);
    public static final Field<String> AE_BUY_EXCHANGE = DSL.field("buy_exchange", String.class);
    public static final Field<String> AE_SELL_EXCHANGE = DSL.field("sell_exchange", String.class);
    public static final Field<BigDecimal> AE_BUY_PRICE = DSL.field("buy_price", BigDecimal.class);
    public static final Field<BigDecimal> AE_SELL_PRICE = DSL.field("sell_price", BigDecimal.class);
    public static final Field<BigDecimal> AE_SPREAD_PERCENT = DSL.field("spread_percent", BigDecimal.class);
    public static final Field<BigDecimal> AE_NET_PROFIT = DSL.field("net_profit", BigDecimal.class);
    public static final Field<BigDecimal> AE_MAX_VOLUME = DSL.field("max_volume", BigDecimal.class);
    public static final Field<BigDecimal> AE_SLIPPAGE = DSL.field("slippage", BigDecimal.class);
    public static final Field<Instant> AE_DETECTED_AT = DSL.field("detected_at", Instant.class);
}
