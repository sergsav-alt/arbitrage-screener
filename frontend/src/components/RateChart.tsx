import React, { useCallback, useEffect, useRef, useState } from 'react'
import { Line } from 'react-chartjs-2'
import '../chartSetup'
import { Button, Card, Select, Tag, message } from 'antd'
import { connectExchange, subscribeBookTickerStream, fetchOrderBook, disconnectExchange } from '../services/api'
import { TickerData, OrderBookData } from '../types'
import { EXCHANGE_OPTIONS } from '../constants'
import { OrderBookTable } from './OrderBookTable'
import { useSymbols } from '../hooks/useSymbols'

const MAX_POINTS = 60
const POLL_INTERVAL_MS = 1000

interface ChartPoint {
  bidPrice: number
  askPrice: number
}

interface RateLineChartProps {
  dataPoints: ChartPoint[]
}

const RateLineChart: React.FC<RateLineChartProps> = ({ dataPoints }) => {
  const data = () => ({
    datasets: [
      {
        label: 'Bid (покупка)',
        data: dataPoints.map((p, i) => ({ x: i, y: p.bidPrice })),
        borderColor: '#52c41a',
        backgroundColor: '#52c41a',
        borderWidth: 2,
        pointRadius: 0,
        tension: 0.3,
      },
      {
        label: 'Ask (продажа)',
        data: dataPoints.map((p, i) => ({ x: i, y: p.askPrice })),
        borderColor: '#ff4d4f',
        backgroundColor: '#ff4d4f',
        borderWidth: 2,
        pointRadius: 0,
        tension: 0.3,
      },
    ],
  })

  const options = () => ({
    responsive: true,
    maintainAspectRatio: false,
    animation: false as const,
    interaction: {
      mode: 'index' as const,
      intersect: false,
    },
    scales: {
      x: {
        type: 'linear' as const,
        min: 0,
        max: MAX_POINTS - 1,
        grid: {
          color: '#f0f0f0',
          drawTicks: true,
        },
        title: {
          display: true,
          text: 'Время',
          font: { size: 12 },
        },
      },
      y: {
        grid: {
          color: '#f0f0f0',
        },
        ticks: {
          font: { size: 12 },
          callback: (value: number | string) => (typeof value === 'number' ? value.toFixed(1) : value),
        },
      },
    },
    plugins: {
      legend: {
        position: 'top' as const,
      },
    },
  })

  return (
    <div style={{ height: 300 }}>
      <Line data={data()} options={options()} />
    </div>
  )
}

export const RateChart: React.FC = () => {
  const [exchange, setExchange] = useState<string>('BINANCE')
  const { symbol, setSymbol, symbolOptions } = useSymbols()

  // connecting to exchange
  const [connecting, setConnecting] = useState(false)

  // connected - status start/stop button
  const [connected, setConnected] = useState(false)

  const [dataPoints, setDataPoints] = useState<ChartPoint[]>([])
  const [orderBook, setOrderBook] = useState<OrderBookData | null>(null)
  const [orderBookLoading, setOrderBookLoading] = useState(false)
  const eventSourceRef = useRef<EventSource | null>(null)
  const obBufferRef = useRef<{ bidMap: Map<number, number>; askMap: Map<number, number> }>({
    bidMap: new Map(),
    askMap: new Map(),
  })
  const latestTickerRef = useRef<TickerData | null>(null)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    return () => {
      disconnectStream()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const disconnectStream = () => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }
    disconnectExchange(exchange).catch(console.error)
    obBufferRef.current = { bidMap: new Map(), askMap: new Map() }
    latestTickerRef.current = null
  }

  const handleConnect = async () => {
    if (!symbol) {
      message.warning('Выберите торговую пару')
      return
    }
    disconnectStream()
    setConnecting(true)
    setConnected(false)
    setDataPoints([])
    setOrderBook(null)

    try {
      const result = await connectExchange(exchange, symbol)
      if (result.connected) {
        message.success(result.message || 'Подключено')
      } else {
        message.info(result.message || 'Ожидание подключения...')
      }

      eventSourceRef.current = subscribeBookTickerStream(
        exchange,
        (ticker) => {
          setConnected(true)
          setConnecting(false)

          // Сохраняем последний тикер — интервал заберёт его раз в секунду
          latestTickerRef.current = ticker

          const buf = obBufferRef.current
          buf.bidMap.set(ticker.bidPrice, ticker.bidQty)
          buf.askMap.set(ticker.askPrice, ticker.askQty)
        },
        () => {
          setConnected(false)
          setConnecting(false)
          message.error('SSE соединение потеряно')
        },
      )

      // Каждую секунду забираем последний тикер и добавляем на график
      intervalRef.current = setInterval(() => {
        const ticker = latestTickerRef.current
        if (!ticker) return

        setDataPoints((prev) => {
          const point: ChartPoint = {
            bidPrice: ticker.bidPrice,
            askPrice: ticker.askPrice,
          }
          const next = [...prev, point]
          if (next.length > MAX_POINTS) {
            return next.slice(next.length - MAX_POINTS)
          }
          return next
        })
      }, POLL_INTERVAL_MS)
    } catch (err) {
      setConnecting(false)
      message.error('Ошибка подключения: ' + (err instanceof Error ? err.message : String(err)))
    }
  }

  const handleDisconnect = () => {
    disconnectStream()
    setConnected(false)
    setConnecting(false)
    message.info('Отключено')
  }

  const handleFetchOrderBook = useCallback(
    async (depth: number) => {
      if (!symbol) {
        message.warning('Выберите торговую пару')
        return
      }
      setOrderBookLoading(true)
      try {
        const data = await fetchOrderBook(exchange, symbol, depth)
        setOrderBook(data)
      } catch (err) {
        message.error('Ошибка загрузки стакана: ' + (err instanceof Error ? err.message : String(err)))
      } finally {
        setOrderBookLoading(false)
      }
    },
    [exchange, symbol],
  )

  const statusColor = connecting ? 'orange' : connected ? 'green' : 'default'

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
            <span style={{ marginRight: 8, color: '#666' }}>Exchange:</span>
            <Select
              style={{ width: 140 }}
              value={exchange}
              onChange={(value) => {
                setExchange(value)
                if (connected) disconnectStream()
                setConnected(false)
                setConnecting(false)
              }}
              options={EXCHANGE_OPTIONS}
            />
          </div>

          <div>
            <span style={{ marginRight: 8, color: '#666' }}>Symbol:</span>
            <Select
              style={{ width: 160 }}
              placeholder="Выберите пару"
              value={symbol}
              onChange={(value) => {
                setSymbol(value)
                if (connected) disconnectStream()
                setConnected(false)
                setConnecting(false)
              }}
              options={symbolOptions}
              showSearch
              filterOption={(input, option) => (option?.label as string).toLowerCase().includes(input.toLowerCase())}
            />
          </div>

          {!connected ? (
            <Button type="primary" onClick={handleConnect} loading={connecting} disabled={!symbol}>
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

      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
        <div style={{ width: 340, flexShrink: 0 }}>
          <OrderBookTable data={orderBook} loading={orderBookLoading} onFetch={handleFetchOrderBook} />
        </div>

        <Card
          style={{ flex: 1 }}
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
            <RateLineChart dataPoints={dataPoints} />
          )}
        </Card>
      </div>
    </div>
  )
}
