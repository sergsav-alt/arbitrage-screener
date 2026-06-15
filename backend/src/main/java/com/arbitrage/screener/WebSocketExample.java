package com.arbitrage.screener;

import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;

@Slf4j
public class WebSocketExample {
    private static final String PATH = "wss://open-api-cswap-ws.bingx.com/market";
    private static final String CHANNEL = "{\"id\":\"24dd0e35-56a4-4f7a-af8a-394c7060909c\",\"reqType\":\"sub\",\"dataType\":\"BTC-USD@trade\"}";

    public static void main(String[] args) {
        try {
            URI uri = new URI(PATH);
            WebSocketClient webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakeData) {
                    log.info("WebSocket connection opened");
                    send(CHANNEL);
                }

                @Override
                public void onMessage(String message) {
                    log.info("Received Text Message: {}", message);
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    try {
                        String decodedMsg = decodeGzip(bytes.array());
                        log.info("Received Binary Message: {}", decodedMsg);
                        if (decodedMsg.contains("ping")) {
                            send("pong");
                        }
                    } catch (IOException e) {
                        log.info("Error decoding message: {}", e.getMessage());
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.info("WebSocket connection closed: {}", reason);
                }

                @Override
                public void onError(Exception ex) {
                    log.info("WebSocket error: {}", ex.getMessage());
                }
            };

            webSocketClient.connectBlocking();


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // GZIP decoding function
    private static String decodeGzip(byte[] compressedData) throws IOException {
        GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedData));
        byte[] buffer = new byte[1024];
        StringBuilder decodedMessage = new StringBuilder();
        int len;
        while ((len = gzipInputStream.read(buffer)) != -1) {
            decodedMessage.append(new String(buffer, 0, len));
        }
        gzipInputStream.close();
        return decodedMessage.toString();
    }
}

