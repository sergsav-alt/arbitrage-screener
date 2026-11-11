import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Line } from 'react-chartjs-2'
import '../chartSetup'
import { Button, Card, Checkbox, Select, Tag, message } from 'antd'
import { connectExchange, disconnectExchange, subscribeBookTickerStream } from '../services/api'
import { TickerData } from '../types'
import { EXCHANGE_OPTIONS } from '../constants'
import { useSymbols } from '../hooks/useSymbols'

const MAX_POINTS = 60
const POLL_INTERVAL_MS = 1000

interface SpreadPoint {
  spreadA: number | null
  spreadB: number | null
  spreadAB: number | null
  spreadBA: number | null
  timestamp: number
}

interface ExchangeConnection {
  exchange: string
  eventSource: EventSource | null
  connected: boolean
}

interface LineConfig {
  key: string
  label: string
  color: string
}

const LINE_CONFIGS: Record<string, LineConfig> = {
  spreadA: { key: 'spreadA', label: 'Внутренний A  (Bid₁ − Ask₁)', color: '#52c41a' },
  spreadB: { key: 'spreadB', label: 'Внутренний B  (Bid₂ − Ask₂)', color: '#ff4d4f' },
  spreadAB: { key: 'spreadAB', label: 'Кросс-спред AB (Bid₁ − Ask₂)', color: '#1677ff' },
  spreadBA: { key: 'spreadBA', label: 'Кросс-спред BA (Bid₂ − Ask₁)', color: '#fa8c16' },
}

const LINE_ORDER = ['spreadA', 'spreadB', 'spreadAB', 'spreadBA']

interface SpreadLineChartProps {
  dataPoints: SpreadPoint[]
  visibleLines: string[]
}

const getSpreadValue = (p: SpreadPoint, key: string): number | null => {
  switch (key) {
    case 'spreadA':
      return p.spreadA
    case 'spreadB':
      return p.spreadB
    case 'spreadAB':
      return p.spreadAB
    case 'spreadBA':
      return p.spreadBA
    default:
      return null
  }
}

const SpreadLineChart: React.FC<SpreadLineChartProps> = ({ dataPoints, visibleLines }) => {
  const data = useMemo(
    () => ({
      datasets: LINE_ORDER.filter((key) => visibleLines.includes(key)).map((key) => {
        const cfg = LINE_CONFIGS[key]
        return {
          label: cfg.label,
          data: dataPoints.map((p, i) => ({
            x: i,
            y: getSpreadValue(p, key),
            ts: p.timestamp,
          })),
          borderColor: cfg.color,
          backgroundColor: cfg.color + '10',
          borderWidth: 2,
          pointRadius: 0,
          tension: 0.4,
          spanGaps: false,
        }
      }),
    }),
    [dataPoints, visibleLines],
  )

  const options = useMemo(
    () => ({
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
          ticks: {
            stepSize: 10,
            font: { size: 11 },
            callback: (value: number | string) => {
              const idx = typeof value === 'string' ? parseInt(value, 10) : value
              return `${idx}с`
            },
          },
          title: {
            display: true,
            text: 'Секунды',
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
          title: {
            display: true,
            text: 'Спред',
            font: { size: 12 },
          },
        },
      },
      plugins: {
        tooltip: {
          callbacks: {
            title(items: any[]) {
              const raw = items[0]?.raw as { ts?: number } | undefined
              if (!raw?.ts) return 'Спред'
              return `Время: ${new Date(raw.ts).toLocaleTimeString('ru-RU', {
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
              })}`
            },
            label(item: any) {
              const raw = item.raw as { y?: number | null } | undefined
              const label = item.dataset?.label || 'Спред'
              if (raw?.y == null) return `${label}: нет данных`
              return `${label}: ${raw.y.toFixed(8)}`
            },
          },
        },
        legend: {
          position: 'top' as const,
        },
      },
    }),
    [],
  )

  return (
    <div style={{ height: 400 }}>
      <Line data={data} options={options} />
    </div>
  )
}

export const CrossRatesChart: React.FC = () => {
  const [exchangeA, setExchangeA] = useState<string>('BINANCE')
  const [exchangeB, setExchangeB] = useState<string>('BINGX')
  const { symbol, setSymbol, symbolOptions } = useSymbols()
  const [connecting, setConnecting] = useState(false)
  const [connectionEpoch, setConnectionEpoch] = useState(0)
  const [dataPoints, setDataPoints] = useState<SpreadPoint[]>([])
  const [visibleLines, setVisibleLines] = useState<string[]>(['spreadAB', 'spreadBA'])

  const notifyConnectionChange = useCallback(() => {
    setConnectionEpoch((v) => v + 1)
  }, [])

  const connARef = useRef<ExchangeConnection>({ exchange: '', eventSource: null, connected: false })
  const connBRef = useRef<ExchangeConnection>({ exchange: '', eventSource: null, connected: false })
  const latestTickerARef = useRef<TickerData | null>(null)
  const latestTickerBRef = useRef<TickerData | null>(null)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    return () => {
      disconnectStream(connARef)
      disconnectStream(connBRef)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const disconnectStream = (conn: React.MutableRefObject<ExchangeConnection>) => {
    if (conn.current.eventSource) {
      conn.current.eventSource.close()
      conn.current.eventSource = null
    }
    disconnectExchange(exchangeA).catch(console.error)
    disconnectExchange(exchangeB).catch(console.error)
    conn.current.connected = false
  }

  const disconnectAll = () => {
    disconnectStream(connARef)
    disconnectStream(connBRef)
    latestTickerARef.current = null
    latestTickerBRef.current = null
  }

  const areBothConnected = (): boolean => connARef.current.connected && connBRef.current.connected

  const subscribeToExchange = (
    exchange: string,
    connRef: React.MutableRefObject<ExchangeConnection>,
    onTicker: (ticker: TickerData) => void,
  ): EventSource => {
    return subscribeBookTickerStream(
      exchange,
      (ticker) => {
        connRef.current.connected = true
        setConnecting(false)
        notifyConnectionChange()
        onTicker(ticker)
      },
      () => {
        connRef.current.connected = false
        setConnecting(false)
        notifyConnectionChange()
        message.error(
          `${EXCHANGE_OPTIONS.find((e) => e.value === exchange)?.label || exchange}: SSE соединение потеряно`,
        )
      },
    )
  }

  const handleConnect = async () => {
    if (!symbol) {
      message.warning('Выберите торговую пару')
      return
    }

    if (exchangeA === exchangeB) {
      message.warning('Выберите разные биржи для сравнения')
      return
    }

    disconnectAll()
    setConnecting(true)
    setDataPoints([])

    try {
      const [resultA, resultB] = await Promise.all([
        connectExchange(exchangeA, symbol),
        connectExchange(exchangeB, symbol),
      ])

      if (resultA.connected) {
        message.success(`${EXCHANGE_OPTIONS.find((e) => e.value === exchangeA)?.label}: подключено`)
      }
      if (resultB.connected) {
        message.success(`${EXCHANGE_OPTIONS.find((e) => e.value === exchangeB)?.label}: подключено`)
      }

      connARef.current.exchange = exchangeA
      connARef.current.eventSource = subscribeToExchange(exchangeA, connARef, (ticker) => {
        latestTickerARef.current = ticker
      })

      connBRef.current.exchange = exchangeB
      connBRef.current.eventSource = subscribeToExchange(exchangeB, connBRef, (ticker) => {
        latestTickerBRef.current = ticker
      })

      intervalRef.current = setInterval(() => {
        const tickerA = latestTickerARef.current
        const tickerB = latestTickerBRef.current

        setDataPoints((prev) => {
          const point: SpreadPoint = {
            spreadA: tickerA ? tickerA.bidPrice - tickerA.askPrice : null,
            spreadB: tickerB ? tickerB.bidPrice - tickerB.askPrice : null,
            spreadAB: tickerA && tickerB ? tickerA.bidPrice - tickerB.askPrice : null,
            spreadBA: tickerA && tickerB ? tickerB.bidPrice - tickerA.askPrice : null,
            timestamp: Date.now(),
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
    disconnectAll()
    notifyConnectionChange()
    message.info('Отключено')
  }

  const exchangeALabel = EXCHANGE_OPTIONS.find((e) => e.value === exchangeA)?.label || exchangeA
  const exchangeBLabel = EXCHANGE_OPTIONS.find((e) => e.value === exchangeB)?.label || exchangeB

  const connectionStatus = (conn: React.MutableRefObject<ExchangeConnection>): 'success' | 'error' | 'processing' => {
    if (conn.current.connected) return 'success'
    if (connecting) return 'processing'
    return 'error'
  }

  const checkboxOptions = LINE_ORDER.map((key) => ({
    label: <span style={{ color: LINE_CONFIGS[key].color, fontWeight: 500 }}>{LINE_CONFIGS[key].label}</span>,
    value: key,
  }))

  return (
    <div>
      <Card style={{ marginBottom: 24 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 16,
            flexWrap: 'wrap',
          }}
        >
          <div>
            <span style={{ marginRight: 8, color: '#666' }}>Биржа 1:</span>
            <Select
              style={{ width: 140 }}
              value={exchangeA}
              onChange={(value) => {
                if (areBothConnected()) disconnectAll()
                setExchangeA(value)
              }}
              options={EXCHANGE_OPTIONS}
              disabled={connecting}
            />
          </div>

          <div>
            <span style={{ marginRight: 8, color: '#666' }}>Биржа 2:</span>
            <Select
              style={{ width: 140 }}
              value={exchangeB}
              onChange={(value) => {
                if (areBothConnected()) disconnectAll()
                setExchangeB(value)
              }}
              options={EXCHANGE_OPTIONS}
              disabled={connecting}
            />
          </div>

          <div>
            <span style={{ marginRight: 8, color: '#666' }}>Symbol:</span>
            <Select
              style={{ width: 160 }}
              placeholder="Выберите пару"
              value={symbol}
              onChange={(value) => {
                if (areBothConnected()) disconnectAll()
                setSymbol(value)
              }}
              options={symbolOptions}
              showSearch
              filterOption={(input, option) => (option?.label as string).toLowerCase().includes(input.toLowerCase())}
              disabled={connecting}
            />
          </div>

          {!areBothConnected() ? (
            <Button
              type="primary"
              onClick={handleConnect}
              loading={connecting}
              disabled={!symbol || exchangeA === exchangeB}
            >
              Подключить
            </Button>
          ) : (
            <Button danger onClick={handleDisconnect} disabled={connecting}>
              Отключить
            </Button>
          )}

          <Tag color="default" style={{ marginLeft: 4 }}>
            {connecting ? 'Подключение...' : areBothConnected() ? 'Обе биржи подключены' : 'Не подключено'}
          </Tag>
        </div>

        <div style={{ display: 'flex', gap: 16, marginTop: 12 }}>
          <Tag color={connectionStatus(connARef)}>
            {exchangeALabel}: {connARef.current.connected ? 'OK' : connecting ? '...' : '—'}
          </Tag>
          <Tag color={connectionStatus(connBRef)}>
            {exchangeBLabel}: {connBRef.current.connected ? 'OK' : connecting ? '...' : '—'}
          </Tag>
        </div>
      </Card>

      <Card
        title={
          areBothConnected()
            ? `Кросс-спреды · ${symbol || ''} · ${exchangeALabel} ↔ ${exchangeBLabel}`
            : 'График кросс-спредов'
        }
        extra={
          <Checkbox.Group
            options={checkboxOptions}
            value={visibleLines}
            onChange={(values) => setVisibleLines(values as string[])}
          />
        }
      >
        {dataPoints.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 60, color: '#999' }}>
            {connecting
              ? 'Подключение к биржам...'
              : 'Выберите две биржи и торговую пару, затем нажмите «Подключить» для отображения графика кросс-курсов'}
          </div>
        ) : (
          <SpreadLineChart dataPoints={dataPoints} visibleLines={visibleLines} />
        )}
      </Card>
    </div>
  )
}
