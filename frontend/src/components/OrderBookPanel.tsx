import React, { useEffect, useRef, useState } from 'react'
import { Button, Card, Select, Table, Tag, message } from 'antd'
import { connectExchange, subscribeStream, getSymbols, disconnectExchanges, disconnectExchange } from '../services/api'
import { DealData, OrderBookLevel } from '../types'
import { EXCHANGE_OPTIONS } from '../constants'

const MAX_TRADES = 30
const ORDER_BOOK_DEPTH = 100

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

function formatTime(duration: number): string {
  const milliseconds = parseInt(String((duration % 1000) / 100))
    let seconds: string | number = parseInt(String((duration / 1000) % 60))
    let minutes: string | number = parseInt(String((duration / (1000 * 60)) % 60))
    let hours: string | number = parseInt(String((duration / (1000 * 60 * 60)) % 24))

  hours = (hours < 10) ? "0" + hours : hours
  minutes = (minutes < 10) ? "0" + minutes : minutes
  seconds = (seconds < 10) ? "0" + seconds : seconds

  return hours + ":" + minutes + ":" + seconds + "." + milliseconds;
}

function formatPrice(value: number): string {
  return value?.toFixed(2).toString()
}

const colors = {
  turquoise: "#578BC2",
  cyan: "#A1C4EB",
  chlorine: "#37D83F",
  whitesmoke: "#F1FCFE",
}

export const OrderBookPanel: React.FC = () => {
  const [exchange, setExchange] = useState<string>('BINANCE')
  const [symbol, setSymbol] = useState<string | null>(null)
  const [symbols, setSymbols] = useState<string[]>([])
  const [connecting, setConnecting] = useState(false)
  const [connected, setConnected] = useState(false)
  const [stoped, setStoped] = useState(false)

  const [bids, setBids] = useState<OrderBookLevel[]>([])
  const [asks, setAsks] = useState<OrderBookLevel[]>([])
  const [trades, setTrades] = useState<TradeEntry[]>([])

  const eventSourceRef = useRef<EventSource | null>(null)
  const latestDealRef = useRef<DealData | null>(null)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const getTableHeight = () => Math.max(200, window.innerHeight - 400)
  const [tableHeight, setTableHeight] = useState(getTableHeight)

  useEffect(() => {
    disconnectExchanges().catch(e => console.error(e))
    getSymbols()
      .then((list) => {
        setSymbols(list)
        const btc = list.find((s) => s.toUpperCase().includes('BTC'))
        if (btc) setSymbol(btc)
      })
      .catch(console.error)
    const handleResize = () => setTableHeight(getTableHeight())
    window.addEventListener('resize', handleResize)

    return () => {
      window.removeEventListener('resize', handleResize)
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
    latestDealRef.current = null
  }

  const handleStart = async () => {
    if (!symbol) {
      message.warning('Выберите торговую пару')
      return
    }
    disconnectStream()
    setConnecting(true)
    setConnected(false)
    setStoped(false)
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
          onDeal: (deal) => {
            setConnected(true)
            setConnecting(false)
            latestDealRef.current = deal
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
        const deal = latestDealRef.current
        if (!deal) return
        setTrades((prev) => {
          const entry: TradeEntry = {
            price: deal.price,
            size: deal.quantity,
            time: formatTime(deal.timestamp),
          }
          const next = [entry, ...prev]
          return next.slice(0, MAX_TRADES)
        })
      }, 200)
    } catch (err) {
      setConnecting(false)
      message.error('Ошибка подключения: ' + (err instanceof Error ? err.message : String(err)))
    }
  }

  const handleStop = async () => {
    await disconnectExchange(exchange)
    disconnectStream()
    setConnected(false)
    setConnecting(false)
    setStoped(true)
    message.info('Остановлено')
  }

  const symbolOptions = symbols.map((s) => ({ value: s, label: s }))
  const statusColor = connecting ? 'orange' : connected ? 'green' : stoped ? 'blue' : 'default'
  const statusText = connecting ? 'Подключение...' : connected ? 'Подключено' : stoped ? 'Остановлено' : 'Не подключено'

  const maxRows = Math.max(bids.length, asks.length, Math.min(trades.length, ORDER_BOOK_DEPTH), 5)
  const rows: TableRow[] = []

  for (let i = 0; i < maxRows; i++) {
    rows.push({
      key: i,
      bidPrice: i < bids.length ? formatPrice(bids[i].price) : '',
      bidSize: i < bids.length ? bids[i].quantity.toString() : '',
      askPrice: i < asks.length ? formatPrice(asks[i].price) : '',
      askSize: i < asks.length ? asks[i].quantity.toString() : '',
      tradePrice: i < trades.length ? formatPrice(trades[i].price) : '',
      tradeSize: i < trades.length ? trades[i].size.toString() : '',
      tradeTime: i < trades.length ? trades[i].time : '',
    })
  }

  const colorValues = Object.values(colors)

  const bidPriceColorMap = new Map<number, string>()
  const uniqueBidPrices = Array.from(new Set(bids.map(b => b.price)))
  uniqueBidPrices.forEach((price, idx) => {
    bidPriceColorMap.set(price, colorValues[idx % colorValues.length])
  })

  const askPriceColorMap = new Map<number, string>()
  const uniqueAskPrices = Array.from(new Set(asks.map(a => a.price)))
  uniqueAskPrices.forEach((price, idx) => {
    askPriceColorMap.set(price, colorValues[idx % colorValues.length])
  })

  const columns = [
    {
      title: 'BID',
      dataIndex: 'bidPrice',
      key: 'bidPrice',
      width: 110,
      align: 'center' as const,
      onCell: (_record: TableRow, index?: number) => {
        const price = index != null ? bids[index]?.price : undefined
        return {
          style: price != null ? { backgroundColor: bidPriceColorMap.get(price) } : {}
        }
      },
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
      align: 'center' as const,
      onCell: (_record: TableRow, index?: number) => {
        const price = index != null ? bids[index]?.price : undefined
        return {
          style: price != null ? { backgroundColor: bidPriceColorMap.get(price) } : {}
        }
      },
      render: (v: string) => (
        <span style={{ fontFamily: 'monospace' }}>{v || '\u00A0'}</span>
      ),
    },
    {
      title: 'ASK',
      dataIndex: 'askPrice',
      key: 'askPrice',
      width: 110,
      align: 'center' as const,
      onCell: (_record: TableRow, index?: number) => {
        const price = index != null ? asks[index]?.price : undefined
        return {
          style: price != null ? { backgroundColor: askPriceColorMap.get(price) } : {}
        }
      },
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
      align: 'center' as const,
      onCell: (_record: TableRow, index?: number) => {
        const price = index != null ? asks[index]?.price : undefined
        return {
          style: price != null ? { backgroundColor: askPriceColorMap.get(price) } : {}
        }
      },
      render: (v: string) => (
        <span style={{ fontFamily: 'monospace' }}>{v || '\u00A0'}</span>
      ),
    },
    {
      title: 'T & S',
      dataIndex: 'tradePrice',
      key: 'tradePrice',
      width: 110,
      align: 'center' as const,
      onCell: () => ({ style: { backgroundColor: '#000000' } }),
      render: (v: string, _record: TableRow, index: number) => {
        const price = trades[index]?.price
        const topBid = bids[0]?.price
        let color = '#fff'
        if (price != null && topBid != null) {
          if (price > topBid) color = '#52c41a'
          else if (price === topBid) color = '#fa8c16'
        }
        return (
          <span style={{ fontFamily: 'monospace', fontWeight: 500, color }}>
            {v || '\u00A0'}
          </span>
        )
      },
    },
    {
      title: 'Size',
      dataIndex: 'tradeSize',
      key: 'tradeSize',
      width: 90,
      align: 'center' as const,
      onCell: () => ({ style: { backgroundColor: '#000000' } }),
      render: (v: string, _record: TableRow, index: number) => {
        const price = trades[index]?.price
        const topBid = bids[0]?.price
        let color = '#fff'
        if (price != null && topBid != null) {
          if (price > topBid) color = '#52c41a'
          else if (price === topBid) color = '#fa8c16'
        }
        return (
          <span style={{ fontFamily: 'monospace', color }}>{v || '\u00A0'}</span>
        )
      },
    },
    {
      title: 'Time',
      dataIndex: 'tradeTime',
      key: 'tradeTime',
      width: 130,
      align: 'center' as const,
      onCell: () => ({ style: { backgroundColor: '#000000' } }),
      render: (v: string, _record: TableRow, index: number) => {
        const price = trades[index]?.price
        const topBid = bids[0]?.price
        let color = '#fff'
        if (price != null && topBid != null) {
          if (price > topBid) color = '#52c41a'
          else if (price === topBid) color = '#fa8c16'
        }
        return (
          <span style={{ fontFamily: 'monospace', color }}>{v || '\u00A0'}</span>
        )
      },
    },
  ]

  const orderBookColumns = columns.slice(0, 4)
  const tradeColumns = columns.slice(4)

  const sharedTableProps = {
    dataSource: rows,
    pagination: false as const,
    size: 'small' as const,
    bordered: true,
    scroll: { y: tableHeight } as const,
    components: {
      header: {
        cell: (props: React.HTMLAttributes<HTMLElement>) => (
          <th
            {...props}
            style={{
              ...props.style,
              backgroundColor: '#738394',
              color: '#fff',
              padding: '12px 8px',
              fontSize: '18px',
              fontWeight: 700,
            }}
          />
        ),
      },
    },
  }

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
                setStoped(false)
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
                setStoped(false)
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
            <Button onClick={handleStop}>
              Стоп
            </Button>
          )}

          <Tag color={statusColor} style={{ marginLeft: 4 }}>
            {statusText}
          </Tag>
        </div>
      </Card>

      <Card
        title={`Order Book${symbol ? ` — ${symbol}` : ''}`}
        bodyStyle={{ padding: '8px' }}
      >
        <div style={{ display: 'flex', gap: 0 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <Table
              {...sharedTableProps}
              columns={orderBookColumns}
              rowKey="key"
            />
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <Table
              {...sharedTableProps}
              columns={tradeColumns}
              rowKey="key"
            />
          </div>
        </div>
      </Card>
    </div>
  )
}
