--liquibase formatted sql

--changeset arbitrage-screener:1
CREATE TABLE IF NOT EXISTS spread_history
(
    id            BIGSERIAL PRIMARY KEY,
    symbol        VARCHAR(20)    NOT NULL,
    buy_exchange  VARCHAR(20)    NOT NULL,
    sell_exchange VARCHAR(20)    NOT NULL,
    buy_price     DECIMAL(18, 8) NOT NULL,
    sell_price    DECIMAL(18, 8) NOT NULL,
    spread_percent DECIMAL(10, 6) NOT NULL,
    net_profit    DECIMAL(18, 8) NOT NULL,
    max_volume    DECIMAL(18, 8) NOT NULL,
    recorded_at   TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_spread_history_symbol ON spread_history (symbol);
CREATE INDEX IF NOT EXISTS idx_spread_history_recorded_at ON spread_history (recorded_at);
CREATE INDEX IF NOT EXISTS idx_spread_history_symbol_recorded ON spread_history (symbol, recorded_at);
