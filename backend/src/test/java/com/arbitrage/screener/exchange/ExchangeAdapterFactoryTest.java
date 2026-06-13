package com.arbitrage.screener.exchange;

import com.arbitrage.screener.exchange.bingx.BingXAdapter;
import com.arbitrage.screener.exchange.mexc.MexcAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeAdapterFactoryTest {

    @Test
    @DisplayName("Should create BingXAdapter")
    void shouldCreateBingXAdapter() {
        var factory = new ExchangeAdapterFactory();
        var adapter = factory.getAdapter(ExchangeEnum.BINGX);
        assertInstanceOf(BingXAdapter.class, adapter);
    }

    @Test
    @DisplayName("Should create MexcAdapter")
    void shouldCreateMexcAdapter() {
        var factory = new ExchangeAdapterFactory();
        var adapter = factory.getAdapter(ExchangeEnum.MEXC);
        assertInstanceOf(MexcAdapter.class, adapter);
    }

    @Test
    @DisplayName("Should return same instance for same exchange")
    void shouldReturnSameInstance() {
        var factory = new ExchangeAdapterFactory();
        var first = factory.getAdapter(ExchangeEnum.BINGX);
        var second = factory.getAdapter(ExchangeEnum.BINGX);
        assertSame(first, second);
    }

    @Test
    @DisplayName("Should not be connected initially")
    void shouldNotBeConnectedInitially() {
        var factory = new ExchangeAdapterFactory();
        assertFalse(factory.getAdapter(ExchangeEnum.BINGX).isConnected());
        assertFalse(factory.getAdapter(ExchangeEnum.MEXC).isConnected());
    }
}
