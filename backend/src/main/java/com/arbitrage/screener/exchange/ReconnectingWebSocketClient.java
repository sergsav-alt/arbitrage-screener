package com.arbitrage.screener.exchange;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * WebSocket-клиент с автоматическим переподключением по экспоненциальной схеме.
 * Инкапсулирует жизненный цикл {@link ExchangeWebSocketClient}: подключение,
 * повторные попытки с нарастающей задержкой и завершение при исчерпании лимита.
 * Владеет собственным планировщиком-демоном; освобождается через {@link #close()}.
 * <p>
 * Класс потокобезопасен: планирование переподключений сериализуется через
 * монитор экземпляра, а ссылка на активный сокет — {@code volatile}.
 */
@Slf4j
public final class ReconnectingWebSocketClient implements AutoCloseable {

    private final URI uri;
    private final String name;
    private final Runnable onConnected;
    private final Consumer<String> onMessage;
    private final Consumer<Throwable> onGiveUp;
    private final int maxAttempts;
    private final long initialDelaySeconds;
    private final long maxDelaySeconds;
    private final ScheduledExecutorService scheduler;

    private volatile ExchangeWebSocketClient client;
    private volatile boolean closing;
    private boolean reconnectPending;
    private int attempt;

    /**
     * @param uri                 адрес WebSocket-эндпоинта
     * @param name                человекочитаемое имя (для логов и имени потока)
     * @param onConnected         колбэк после каждого успешного открытия (nullable) —
     *                            например, для отправки подписок
     * @param onMessage           обработчик входящих текстовых сообщений
     * @param onGiveUp            колбэк при исчерпании лимита переподключений (nullable)
     * @param maxAttempts         максимум последовательных попыток переподключения
     * @param initialDelaySeconds базовая задержка перед первой повторной попыткой
     * @param maxDelaySeconds     верхняя граница задержки
     */
    public ReconnectingWebSocketClient(URI uri,
                                       String name,
                                       Runnable onConnected,
                                       Consumer<String> onMessage,
                                       Consumer<Throwable> onGiveUp,
                                       int maxAttempts,
                                       long initialDelaySeconds,
                                       long maxDelaySeconds) {
        this.uri = uri;
        this.name = name;
        this.onConnected = onConnected;
        this.onMessage = onMessage;
        this.onGiveUp = onGiveUp;
        this.maxAttempts = maxAttempts;
        this.initialDelaySeconds = initialDelaySeconds;
        this.maxDelaySeconds = maxDelaySeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, threadName(name));
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Инициирует первое подключение. Вызывать один раз после создания.
     */
    public void start() {
        connect();
    }

    /**
     * Отправляет текстовое сообщение, если сокет открыт (иначе — no-op).
     */
    public void send(String message) {
        var ws = client;
        if (ws != null && ws.isOpen()) {
            ws.send(message);
        }
    }

    public boolean isOpen() {
        var ws = client;
        return ws != null && ws.isOpen();
    }

    @Override
    public void close() {
        closing = true;
        log.info("{}: closing", name);
        var ws = client;
        if (ws != null && ws.isOpen()) {
            ws.close();
        }
        scheduler.shutdownNow();
    }

    private void connect() {
        var ws = new ExchangeWebSocketClient(
                uri,
                name,
                this::onOpen,
                onMessage,
                (code, reason) -> scheduleReconnect(),
                this::scheduleReconnect
        );
        this.client = ws;
        try {
            ws.connect();
        } catch (Exception e) {
            log.error("{}: failed to connect: {}", name, e.getMessage());
            scheduleReconnect();
        }
    }

    private void onOpen() {
        synchronized (this) {
            attempt = 0;
        }
        log.info("{}: connected to ws", name);
        if (onConnected != null) {
            onConnected.run();
        }
    }

    private synchronized void scheduleReconnect() {
        if (closing || reconnectPending) {
            return;
        }
        var current = ++attempt;
        if (current > maxAttempts) {
            giveUp();
            return;
        }
        reconnectPending = true;
        var delay = Math.min((long) (initialDelaySeconds * Math.pow(2, Math.min(current - 1, 6))), maxDelaySeconds);
        log.info("{}: reconnect attempt {} in {} seconds", name, current, delay);
        scheduler.schedule(() -> {
            synchronized (this) {
                reconnectPending = false;
            }
            if (!closing) {
                connect();
            }
        }, delay, TimeUnit.SECONDS);
    }

    private void giveUp() {
        closing = true;
        var message = name + ": giving up after " + maxAttempts + " reconnect attempts";
        log.warn(message);
        if (onGiveUp != null) {
            onGiveUp.accept(new IllegalStateException(message));
        }
    }

    private static String threadName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-ws";
    }
}
