package com.arbitrage.screener.exchange;

import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

/**
 * Обёртка над {@link WebSocketClient} с GZIP-поддержкой и ping/pong-обработкой.
 * Делегирует события жизненного цикла через колбэки, отделяя транспорт от бизнес-логики.
 */
@Slf4j
public class ExchangeWebSocketClient extends WebSocketClient {

    private final String exchangeName;
    private final Runnable onOpenCallback;
    private final Consumer<String> onMessageCallback;
    private final BiConsumer<Integer, String> onCloseCallback;
    private final Runnable onErrorCallback;

    public ExchangeWebSocketClient(URI serverUri,
                                   String exchangeName,
                                   Runnable onOpenCallback,
                                   Consumer<String> onMessageCallback,
                                   BiConsumer<Integer, String> onCloseCallback,
                                   Runnable onErrorCallback) {
        super(serverUri);
        this.exchangeName = exchangeName;
        this.onOpenCallback = onOpenCallback;
        this.onMessageCallback = onMessageCallback;
        this.onCloseCallback = onCloseCallback;
        this.onErrorCallback = onErrorCallback;
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        log.info("{}: webSocket connection opened", exchangeName);
        onOpenCallback.run();
    }

    @Override
    public void onMessage(String message) {
        log.info("{}: webSocket connection message: {}", exchangeName, message);
        onMessageCallback.accept(message);
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            var decoded = decodeGzip(bytes.array());
            if (decoded.contains("ping")) {
                send("pong");
            } else {
                log.info("{}: webSocket binary message: {}", exchangeName, decoded);
                onMessageCallback.accept(decoded);
            }
        } catch (IOException e) {
            log.error("{}: error decoding binary message: {}", exchangeName, e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("{}: webSocket closed: code={}, reason={}, remote={}", exchangeName, code, reason, remote);
        onCloseCallback.accept(code, reason);
    }

    @Override
    public void onError(Exception ex) {
        log.error("{}: webSocket error: {}", exchangeName, ex.getMessage());
        onErrorCallback.run();
    }

    static String decodeGzip(byte[] compressedData) throws IOException {
        var gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedData));
        var decodedMessage = new StringBuilder();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = gzipInputStream.read(buffer)) != -1) {
            decodedMessage.append(new String(buffer, 0, len));
        }
        gzipInputStream.close();
        return decodedMessage.toString();
    }
}
