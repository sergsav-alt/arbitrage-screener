import React, { useEffect, useRef, useState } from 'react'
import { Button, Card, Select, Table, Tag, message } from 'antd'
import { connectExchange, subscribeStream, getSymbols } from '../services/api'
import { DealData, OrderBookLevel } from '../types'
import { EXCHANGE_OPTIONS } from '../constants'

const MAX_TRADES = 20
const ORDER_BOOK_DEPTH = 10

interface TradeEntry {
  price: number
  size: number
  time: string
}

interface TableRow {
  key: number
  bidPrice: string
  bidSize: string
  askPrice: string
  askSize: string
  tradePrice: string
  tradeSize: string
  tradeTime: string
}

function formatTime(isoString: string): string {
  const match = isoString.match(/T(\d{2}):(\d{2}):(\d{2})\.?(\d*)/)
  if (match) {
    const [, h, m, s, ms] = match
    const frac = (ms || '000').padEnd(4, '0').slice(0, 4)
    return `${h}:${m}:${s}.${frac}`
  }
  const d = new Date(isoString)
  if (isNaN(d.getTime())) return isoString
  const h = d.getHours().toString().padStart(2, '0')
  const m = d.getMinutes().toString().padStart(2, '0')
  const s = d.getSeconds().toString().padStart(2, '0')
  const ms = d.getMilliseconds().toString().padStart(3, '0')
  return `${h}:${m}:${s}.${ms}0`
}

function formatPrice(value: number): string {
  return value.toLocaleString('ru-RU', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 4,
  })
}

function formatSize(value: number): string {
  if (value >= 1000) return value.toLocaleString('ru-RU', { maximumFractionDigits: 1 })
  if (value >= 1) return value.toLocaleString('ru-RU', { maximumFractionDigits: 4 })
  return value.toLocaleString('ru-RU', { maximumFractionDigits: 6 })
}

export const OrderBookPanel: React.FC = () => {
  const [exchange, setExchange] = useState<string>('BINANCE')
  const [symbol, setSymbol] = useState<string | null>(null)
  const [symbols, setSymbols] = useState<string[]>([])
  const [connecting, setConnecting] = useState(false)
  const [connected, setConnected] = useState(false)
  const [paused, setPaused] = useState(false)

  const [bids, setBids] = useState<OrderBookLevel[]>([])
  const [asks, setAsks] = useState<OrderBookLevel[]>([])
  const [trades, setTrades] = useState<TradeEntry[]>([])

  const eventSourceRef = useRef<EventSource | null>(null)
  const latestTickerRef = useRef<DealData | null>(null)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    getSymbols()
      .then((list) => {
        setSymbols(list)
        const btc = list.find((s) => s.toUpperCase().includes('BTC'))
        if (btc) setSymbol(btc)
      })
      .catch(console.error)

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
    if (intervalRef.current) {
      clearInterval(intervalRef.current)
      intervalRef.current = null
    }
    latestTickerRef.current = null
  }

  const handleStart = async () => {
    if (!symbol) {
      message.warning('Выберите торговую пару')
      return
    }
    disconnectStream()
    setConnecting(true)
    setConnected(false)
    setPaused(false)
    setBids([])
    setAsks([])
    setTrades([])

    try {
      const result = await connectExchange(exchange, symbol)
      if (result.connected) {
        message.success(result.message || 'Подключено')
      } else {
        message.info(result.message || 'Ожидание подключения...')
      }

      eventSourceRef.current = subscribeStream(
        exchange,
        {
          onDeal: (ticker) => {
            setConnected(true)
            setConnecting(false)
            latestTickerRef.current = ticker
          },
          onOrderBook: (ob) => {
            setConnected(true)
            setConnecting(false)
            setBids(ob.bids.slice(0, ORDER_BOOK_DEPTH))
            setAsks(ob.asks.slice(0, ORDER_BOOK_DEPTH))
          },
        },
        () => {
          setConnected(false)
          setConnecting(false)
          message.error('SSE соединение потеряно')
        },
      )

      intervalRef.current = setInterval(() => {
        const ticker = latestTickerRef.current
        if (!ticker) return
        setTrades((prev) => {
          const entry: TradeEntry = {
            price: ticker.price,
            size: ticker.quantity,
            time: formatTime(ticker.timestamp),
          }
          const next = [entry, ...prev]
          return next.slice(0, MAX_TRADES)
        })
      }, 1000)
    } catch (err) {
      setConnecting(false)
      message.error('Ошибка подключения: ' + (err instanceof Error ? err.message : String(err)))
    }
  }

  const handlePause = () => {
    disconnectStream()
    setConnected(false)
    setConnecting(false)
    setPaused(true)
    message.info('Пауза')
  }

  const symbolOptions = symbols.map((s) => ({ value: s, label: s }))
  const statusColor = connecting ? 'orange' : connected ? 'green' : paused ? 'blue' : 'default'
  const statusText = connecting ? 'Подключение...' : connected ? 'Подключено' : paused ? 'Пауза' : 'Не подключено'

  const maxRows = Math.max(bids.length, asks.length, Math.min(trades.length, ORDER_BOOK_DEPTH), 5)
  const rows: TableRow[] = []

  for (let i = 0; i < maxRows; i++) {
    rows.push({
      key: i,
      bidPrice: i < bids.length ? formatPrice(bids[i].price) : '',
      bidSize: i < bids.length ? formatSize(bids[i].quantity) : '',
      askPrice: i < asks.length ? formatPrice(asks[i].price) : '',
      askSize: i < asks.length ? formatSize(asks[i].quantity) : '',
      tradePrice: i < trades.length ? formatPrice(trades[i].price) : '',
      tradeSize: i < trades.length ? formatSize(trades[i].size) : '',
      tradeTime: i < trades.length ? trades[i].time : '',
    })
  }

  const columns = [
    {
      title: 'BID',
      children: [
        {
          title: 'Price',
          dataIndex: 'bidPrice',
          key: 'bidPrice',
          width: 110,
          align: 'right' as const,
          render: (v: string) => (
            <span style={{ color: '#52c41a', fontFamily: 'monospace', fontWeight: 500 }}>
              {v || '\u00A0'}
            </span>
          ),
        },
        {
          title: 'Size',
          dataIndex: 'bidSize',
          key: 'bidSize',
          width: 90,
          align: 'right' as const,
          render: (v: string) => (
            <span style={{ fontFamily: 'monospace' }}>{v || '\u00A0'}</span>
          ),
        },
      ],
    },
    {
      title: 'ASK',
      children: [
        {
          title: 'Price',
          dataIndex: 'askPrice',
          key: 'askPrice',
          width: 110,
          align: 'right' as const,
          render: (v: string) => (
            <span style={{ color: '#ff4d4f', fontFamily: 'monospace', fontWeight: 500 }}>
              {v || '\u00A0'}
            </span>
          ),
        },
        {
          title: 'Size',
          dataIndex: 'askSize',
          key: 'askSize',
          width: 90,
          align: 'right' as const,
          render: (v: string) => (
            <span style={{ fontFamily: 'monospace' }}>{v || '\u00A0'}</span>
          ),
        },
      ],
    },
    {
      title: 'T&S',
      children: [
        {
          title: 'Price',
          dataIndex: 'tradePrice',
          key: 'tradePrice',
          width: 110,
          align: 'right' as const,
          render: (v: string) => (
            <span style={{ fontFamily: 'monospace', fontWeight: 500 }}>{v || '\u00A0'}</span>
          ),
        },
        {
          title: 'Size',
          dataIndex: 'tradeSize',
          key: 'tradeSize',
          width: 90,
          align: 'right' as const,
          render: (v: string) => (
            <span style={{ fontFamily: 'monospace' }}>{v || '\u00A0'}</span>
          ),
        },
        {
          title: 'Time',
          dataIndex: 'tradeTime',
          key: 'tradeTime',
          width: 130,
          align: 'right' as const,
          render: (v: string) => (
            <span style={{ fontFamily: 'monospace', fontSize: 11 }}>{v || '\u00A0'}</span>
          ),
        },
      ],
    },
  ]

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
                setPaused(false)
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
                setPaused(false)
              }}
              options={symbolOptions}
              showSearch
              filterOption={(input, option) =>
                (option?.label as string).toLowerCase().includes(input.toLowerCase())
              }
            />
          </div>

          {!connected ? (
            <Button type="primary" onClick={handleStart} loading={connecting} disabled={!symbol}>
              Пуск
            </Button>
          ) : (
            <Button onClick={handlePause}>
              Пауза
            </Button>
          )}

          <Tag color={statusColor} style={{ marginLeft: 4 }}>
            {statusText}
          </Tag>
        </div>
      </Card>

      <Card title={`Order Book${symbol ? ` — ${symbol}` : ''}`}>
        <Table
          columns={columns}
          dataSource={rows}
          pagination={false}
          size="small"
          bordered
          scroll={{ x: 'max-content' }}
          locale={{
            emptyText: connected ? 'Ожидание данных...' : 'Нажмите «Пуск» для загрузки',
          }}
        />
      </Card>
    </div>
  )
}
