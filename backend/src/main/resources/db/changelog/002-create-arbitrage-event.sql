--liquibase formatted sql

--changeset arbitrage-screener:2
CREATE TABLE IF NOT EXISTS arbitrage_event
(
    id            BIGSERIAL PRIMARY KEY,
    type          VARCHAR(20)    NOT NULL,
    symbol        VARCHAR(20)    NOT NULL,
    buy_exchange  VARCHAR(20)    NOT NULL,
    sell_exchange VARCHAR(20)    NOT NULL,
    buy_price     DECIMAL(18, 8) NOT NULL,
    sell_price    DECIMAL(18, 8) NOT NULL,
    spread_percent DECIMAL(10, 6) NOT NULL,
    net_profit    DECIMAL(18, 8) NOT NULL,
    max_volume    DECIMAL(18, 8) NOT NULL,
    slippage      DECIMAL(10, 6) NOT NULL DEFAULT 0,
    detected_at   TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_arbitrage_event_symbol ON arbitrage_event (symbol);
CREATE INDEX IF NOT EXISTS idx_arbitrage_event_detected_at ON arbitrage_event (detected_at);
CREATE INDEX IF NOT EXISTS idx_arbitrage_event_type ON arbitrage_event (type);
