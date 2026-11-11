package com.arbitrage.screener.exchange.depth;

import com.arbitrage.screener.exchange.ExchangeApiException;
import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.model.BingXDepthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import static com.arbitrage.screener.util.FormatHelper.formatSymbol;

/**
 * Snapshot стакана BingX (swap).
 *
 * @see <a href="https://bingx-api.github.io/docs/#/en-us/swapV2/market.html">BingX API Docs</a>
 */
@Slf4j
@Component
public class BingXDepthSnapshotProvider implements DepthSnapshotProvider {

    private final RestClient restClient;

    public BingXDepthSnapshotProvider(RestClient restClient) {
        this.restClient = restClient.mutate()
                .baseUrl("https://open-api.bingx.com")
                .build();
    }

    @Override
    public ExchangeEnum exchange() {
        return ExchangeEnum.BINGX;
    }

    @Override
    public Object fetchSnapshot(String symbol, int depth) {
        var response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/openApi/swap/v2/quote/depth")
                        .queryParam("symbol", formatSymbol(symbol, ExchangeEnum.BINGX))
                        .queryParam("limit", depth)
                        .build())
                .retrieve()
                .body(BingXDepthResponse.class);

        if (response == null || response.code() != 0) {
            var msg = response != null ? response.msg() : "no response";
            throw new ExchangeApiException("BingX API error: " + msg);
        }
        return response;
    }
}
