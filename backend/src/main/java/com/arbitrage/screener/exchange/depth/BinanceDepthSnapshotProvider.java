package com.arbitrage.screener.exchange.depth;

import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.model.BinanceDepthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import static com.arbitrage.screener.util.FormatHelper.formatSymbol;

/**
 * Snapshot стакана Binance USDⓈ-M Futures.
 *
 * @see <a href="https://developers.binance.com/docs/derivatives/usds-margined-futures/market-data/rest-api/Order-Book">Binance API Docs</a>
 */
@Slf4j
@Component
public class BinanceDepthSnapshotProvider implements DepthSnapshotProvider {

    private final RestClient restClient;

    public BinanceDepthSnapshotProvider(RestClient restClient) {
        this.restClient = restClient.mutate()
                .baseUrl("https://fapi.binance.com")
                .build();
    }

    @Override
    public ExchangeEnum exchange() {
        return ExchangeEnum.BINANCE;
    }

    @Override
    public Object fetchSnapshot(String symbol, int depth) {
        var response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/fapi/v1/depth")
                        .queryParam("symbol", formatSymbol(symbol, ExchangeEnum.BINANCE))
                        .queryParam("limit", depth)
                        .build())
                .retrieve()
                .body(BinanceDepthResponse.class);

        if (response == null) {
            throw new ExchangeApiException("Binance API returned empty response");
        }
        return response;
    }
}
