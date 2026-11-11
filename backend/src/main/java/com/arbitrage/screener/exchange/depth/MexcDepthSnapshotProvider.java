package com.arbitrage.screener.exchange.depth;

import com.arbitrage.screener.exchange.ExchangeApiException;
import com.arbitrage.screener.exchange.ExchangeEnum;
import com.arbitrage.screener.model.MexcDepthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import static com.arbitrage.screener.util.FormatHelper.formatSymbol;

/**
 * Snapshot стакана MEXC Futures. Символ передаётся в пути запроса.
 *
 * @see <a href="https://mexcdevelop.github.io/apidocs/contract_v1_en/#public-channels">MEXC API Docs</a>
 */
@Slf4j
@Component
public class MexcDepthSnapshotProvider implements DepthSnapshotProvider {

    private final RestClient restClient;

    public MexcDepthSnapshotProvider(RestClient restClient) {
        this.restClient = restClient.mutate()
                .baseUrl("https://contract.mexc.com")
                .build();
    }

    @Override
    public ExchangeEnum exchange() {
        return ExchangeEnum.MEXC;
    }

    @Override
    public Object fetchSnapshot(String symbol, int depth) {
        var formattedSymbol = formatSymbol(symbol, ExchangeEnum.MEXC);
        var response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/contract/depth/" + formattedSymbol)
                        .queryParam("limit", depth)
                        .build())
                .retrieve()
                .body(MexcDepthResponse.class);

        if (response == null || !response.success() || response.code() != 0) {
            var code = response != null ? response.code() : -1;
            throw new ExchangeApiException("MEXC API error: code=" + code);
        }
        return response;
    }
}
