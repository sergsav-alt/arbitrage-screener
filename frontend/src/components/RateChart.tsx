import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import { Button, Card, Select, Tag, message } from 'antd';
import { connectExchange, subscribeTickerStream, getSymbols } from '../services/api';
import { TickerPoint } from '../types';

const MAX_POINTS = 200;

const EXCHANGE_OPTIONS = [
  { value: 'BINANCE', label: 'Binance' },
  { value: 'BINGX', label: 'BingX' },
  { value: 'MEXC', label: 'MEXC' },
];

export const RateChart: React.FC = () => {
  const [exchange, setExchange] = useState<string>('BINANCE');
  const [symbol, setSymbol] = useState<string | null>(null);
  const [symbols, setSymbols] = useState<string[]>([]);
  const [connecting, setConnecting] = useState(false);
  const [connected, setConnected] = useState(false);
  const [dataPoints, setDataPoints] = useState<TickerPoint[]>([]);
  const eventSourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    getSymbols().then(setSymbols).catch(console.error);
  }, []);

  const disconnectStream = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
  }, []);

  useEffect(() => {
    return () => disconnectStream();
  }, [disconnectStream]);

  const formatTime = (timestamp: string) => {
    return new Date(timestamp).toLocaleTimeString('ru-RU', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  };

  const handleConnect = useCallback(async () => {
    if (!symbol) {
      message.warning('Выберите торговую пару');
      return;
    }

    disconnectStream();
    setConnecting(true);
    setConnected(false);
    setDataPoints([]);

    try {
      const result = await connectExchange(exchange, symbol);
      if (result.connected) {
        message.success(result.message || 'Подключено');
      } else {
        message.info(result.message || 'Ожидание подключения...');
      }

      const es = subscribeTickerStream(
        exchange,
        (ticker) => {
          setConnected(true);
          setConnecting(false);
          setDataPoints((prev) => {
            const point: TickerPoint = {
              time: formatTime(ticker.timestamp),
              timestamp: new Date(ticker.timestamp).getTime(),
              bidPrice: ticker.bidPrice,
              askPrice: ticker.askPrice,
            };
            const next = [...prev, point];
            if (next.length > MAX_POINTS) {
              return next.slice(next.length - MAX_POINTS);
            }
            return next;
          });
        },
        () => {
          setConnected(false);
          setConnecting(false);
          message.error('SSE соединение потеряно');
        }
      );

      eventSourceRef.current = es;
    } catch (err) {
      setConnecting(false);
      message.error('Ошибка подключения: ' + (err instanceof Error ? err.message : String(err)));
    }
  }, [exchange, symbol, disconnectStream]);

  const handleDisconnect = useCallback(() => {
    disconnectStream();
    setConnected(false);
    setConnecting(false);
    message.info('Отключено');
  }, [disconnectStream]);

  const symbolOptions = symbols.map((s) => ({ value: s, label: s }));

  const statusColor = connecting ? 'orange' : connected ? 'green' : 'default';

  return (
    <div>
      <Card style={{ marginBottom: 24 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <div>
            <span style={{ marginRight: 8, color: '#666' }}>Биржа:</span>
            <Select
              style={{ width: 140 }}
              value={exchange}
              onChange={(value) => {
                setExchange(value);
                if (connected) disconnectStream();
                setConnected(false);
                setConnecting(false);
              }}
              options={EXCHANGE_OPTIONS}
            />
          </div>

          <div>
            <span style={{ marginRight: 8, color: '#666' }}>Пара:</span>
            <Select
              style={{ width: 160 }}
              placeholder="Выберите пару"
              value={symbol}
              onChange={(value) => {
                setSymbol(value);
                if (connected) disconnectStream();
                setConnected(false);
                setConnecting(false);
              }}
              options={symbolOptions}
              showSearch
              filterOption={(input, option) =>
                (option?.label as string).toLowerCase().includes(input.toLowerCase())
              }
            />
          </div>

          {!connected ? (
            <Button
              type="primary"
              onClick={handleConnect}
              loading={connecting}
              disabled={!symbol}
            >
              Подключить
            </Button>
          ) : (
            <Button danger onClick={handleDisconnect}>
              Отключить
            </Button>
          )}

          <Tag color={statusColor} style={{ marginLeft: 4 }}>
            {connecting ? 'Подключение...' : connected ? 'Подключено' : 'Не подключено'}
          </Tag>
        </div>
      </Card>

      <Card
        title={
          connected
            ? `Курс ${symbol || ''} на ${EXCHANGE_OPTIONS.find((e) => e.value === exchange)?.label || exchange}`
            : 'График курса'
        }
      >
        {dataPoints.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 60, color: '#999' }}>
            {connecting
              ? 'Подключение к бирже...'
              : 'Выберите биржу и торговую пару, затем нажмите «Подключить» для отображения real-time графика'}
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={400}>
            <LineChart data={dataPoints} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="time" fontSize={11} interval="preserveStartEnd" />
              <YAxis
                fontSize={12}
                domain={['auto', 'auto']}
                tickFormatter={(value: number) => value.toFixed(2)}
              />
              <Tooltip
                formatter={(value) => [
                  typeof value === 'number' ? value.toFixed(4) : value,
                  undefined,
                ]}
                labelFormatter={(label) => `Время: ${label}`}
              />
              <Legend />
              <Line
                type="monotone"
                dataKey="bidPrice"
                stroke="#52c41a"
                strokeWidth={2}
                dot={false}
                name="Bid (покупка)"
              />
              <Line
                type="monotone"
                dataKey="askPrice"
                stroke="#ff4d4f"
                strokeWidth={2}
                dot={false}
                name="Ask (продажа)"
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </Card>
    </div>
  );
};
