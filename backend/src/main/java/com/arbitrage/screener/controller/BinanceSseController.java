package com.arbitrage.screener.controller;

import com.arbitrage.screener.exchange.ExchangeAdapterFactory;
import com.arbitrage.screener.exchange.ExchangeEnum;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stream/binance")
public class BinanceSseController extends AbstractSseController {

    public BinanceSseController(ExchangeAdapterFactory factory) {
        super(factory, ExchangeEnum.BINANCE);
    }
}
