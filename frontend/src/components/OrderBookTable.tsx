import React, { useState } from 'react'
import { Card, Button, Segmented } from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import { OrderBookData, OrderBookLevel } from '../types'

const DEPTH_OPTIONS = [5, 10, 20]

interface OrderBookTableProps {
  data: OrderBookData | null
  loading: boolean
  onFetch: (depth: number) => void
}

const formatNumber = (value: number, decimals: number = 2): string => {
  return value.toLocaleString('ru-RU', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })
}

const formatQuantity = (value: number): string => {
  if (value >= 1000) {
    return value.toLocaleString('ru-RU', { maximumFractionDigits: 1 })
  }
  if (value >= 1) {
    return value.toLocaleString('ru-RU', { maximumFractionDigits: 4 })
  }
  return value.toLocaleString('ru-RU', { maximumFractionDigits: 6 })
}

export const OrderBookTable: React.FC<OrderBookTableProps> = ({ data, loading, onFetch }) => {
  const [depth, setDepth] = useState<number>(10)

  const handleFetch = () => {
    onFetch(depth)
  }

  const getTrimmedLevels = (levels: OrderBookLevel[]): OrderBookLevel[] => {
    return levels.slice(0, depth)
  }

  const computeMaxTotal = (levels: OrderBookLevel[]): number => {
    let total = 0
    let maxTotal = 0
    for (const level of levels) {
      total += level.quantity
      if (total > maxTotal) maxTotal = total
    }
    return maxTotal
  }

  const bids = data ? getTrimmedLevels(data.bids) : []
  const asks = data ? getTrimmedLevels(data.asks) : []

  const maxBidTotal = computeMaxTotal(bids)
  const maxAskTotal = computeMaxTotal(asks)
  const maxTotal = Math.max(maxBidTotal, maxAskTotal)

  const bestBid = bids.length > 0 ? bids[0].price : null
  const bestAsk = asks.length > 0 ? asks[0].price : null
  const spread = bestBid !== null && bestAsk !== null ? bestBid - bestAsk : null
  const spreadPercent = bestBid !== null && spread !== null ? (spread / bestBid) * 100 : null
  const midPrice = bestBid !== null && bestAsk !== null ? (bestBid + bestAsk) / 2 : null

  const rows: Array<{
    askPrice: number | null
    askQty: number | null
    askCumQty: number
    bidPrice: number | null
    bidQty: number | null
    bidCumQty: number
  }> = []

  let bidCum = 0

  const bidCumByIndex: number[] = []
  for (let i = 0; i < bids.length; i++) {
    bidCum += bids[i].quantity
    bidCumByIndex.push(bidCum)
  }

  let askCum = 0

  const askCumByIndex: number[] = []
  for (let i = 0; i < asks.length; i++) {
    askCum += asks[i].quantity
    askCumByIndex.push(askCum)
  }

  for (let i = 0; i < depth; i++) {
    const ask = i < asks.length ? asks[i] : null
    const bid = i < bids.length ? bids[i] : null

    rows.push({
      askPrice: ask ? ask.price : null,
      askQty: ask ? ask.quantity : null,
      askCumQty: ask ? askCumByIndex[i] : 0,
      bidPrice: bid ? bid.price : null,
      bidQty: bid ? bid.quantity : null,
      bidCumQty: bid ? bidCumByIndex[i] : 0,
    })
  }

  const barWidthPercent = (cumQty: number): number => {
    if (maxTotal === 0) return 0
    return Math.min((cumQty / maxTotal) * 100, 100)
  }

  const emptyState = !data && !loading

  return (
    <Card
      title="Стакан заявок"
      size="small"
      style={{ height: '100%' }}
      extra={
        <Button size="small" icon={<DownloadOutlined />} onClick={handleFetch} loading={loading} type="primary">
          Получить
        </Button>
      }
    >
      {emptyState ? (
        <div style={{ textAlign: 'center', padding: 40, color: '#999', fontSize: 13 }}>
          Нажмите «Получить» для загрузки стакана
        </div>
      ) : (
        <>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
            <thead>
              <tr style={{ color: '#888', fontSize: 11, textTransform: 'uppercase' }}>
                <th style={{ textAlign: 'right', padding: '4px 6px', fontWeight: 400, width: '36%' }}>Цена</th>
                <th style={{ textAlign: 'right', padding: '4px 6px', fontWeight: 400, width: '22%' }}>Кол-во</th>
                <th style={{ textAlign: 'center', padding: '4px 4px', fontWeight: 400, width: '6%' }}></th>
                <th style={{ textAlign: 'left', padding: '4px 6px', fontWeight: 400, width: '22%' }}>Кол-во</th>
                <th style={{ textAlign: 'left', padding: '4px 6px', fontWeight: 400, width: '36%' }}>Цена</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, idx) => (
                <tr
                  key={idx}
                  style={{
                    height: 24,
                    lineHeight: '24px',
                  }}
                >
                  {/* Ask Price */}
                  <td
                    style={{
                      textAlign: 'right',
                      padding: '0 6px',
                      color: row.askPrice !== null ? '#ff4d4f' : 'transparent',
                      position: 'relative',
                      fontFamily: 'monospace',
                      fontWeight: 500,
                    }}
                  >
                    <span style={{ position: 'relative', zIndex: 1 }}>
                      {row.askPrice !== null ? formatNumber(row.askPrice, 1) : '\u00A0'}
                    </span>
                  </td>

                  {/* Ask Quantity */}
                  <td
                    style={{
                      textAlign: 'right',
                      padding: '0 6px',
                      color: row.askQty !== null ? '#333' : 'transparent',
                      position: 'relative',
                      fontFamily: 'monospace',
                    }}
                  >
                    <div
                      style={{
                        position: 'absolute',
                        right: 0,
                        top: 0,
                        height: '100%',
                        width: `${barWidthPercent(row.askCumQty)}%`,
                        backgroundColor: 'rgba(255, 77, 79, 0.12)',
                        zIndex: 0,
                      }}
                    />
                    <span style={{ position: 'relative', zIndex: 1 }}>
                      {row.askQty !== null ? formatQuantity(row.askQty) : '\u00A0'}
                    </span>
                  </td>

                  {/* Separator */}
                  <td style={{ padding: 0 }} />

                  {/* Bid Quantity */}
                  <td
                    style={{
                      textAlign: 'left',
                      padding: '0 6px',
                      color: row.bidQty !== null ? '#333' : 'transparent',
                      position: 'relative',
                      fontFamily: 'monospace',
                    }}
                  >
                    <div
                      style={{
                        position: 'absolute',
                        left: 0,
                        top: 0,
                        height: '100%',
                        width: `${barWidthPercent(row.bidCumQty)}%`,
                        backgroundColor: 'rgba(82, 196, 26, 0.12)',
                        zIndex: 0,
                      }}
                    />
                    <span style={{ position: 'relative', zIndex: 1 }}>
                      {row.bidQty !== null ? formatQuantity(row.bidQty) : '\u00A0'}
                    </span>
                  </td>

                  {/* Bid Price */}
                  <td
                    style={{
                      textAlign: 'left',
                      padding: '0 6px',
                      color: row.bidPrice !== null ? '#52c41a' : 'transparent',
                      position: 'relative',
                      fontFamily: 'monospace',
                      fontWeight: 500,
                    }}
                  >
                    <span style={{ position: 'relative', zIndex: 1 }}>
                      {row.bidPrice !== null ? formatNumber(row.bidPrice, 1) : '\u00A0'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Spread indicator */}
          {spread !== null && midPrice !== null && (
            <div
              style={{
                textAlign: 'center',
                padding: '8px 0 4px',
                borderTop: '1px solid #f0f0f0',
                marginTop: 4,
              }}
            >
              <span style={{ fontSize: 15, fontWeight: 600, color: '#333', fontFamily: 'monospace' }}>
                {formatNumber(midPrice, 1)}
              </span>
              <span style={{ fontSize: 11, color: '#888', marginLeft: 8 }}>
                Спред: {formatNumber(spread, 1)} ({spreadPercent?.toFixed(3)}%)
              </span>
            </div>
          )}

          {/* Controls */}
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginTop: 8,
              paddingTop: 8,
              borderTop: '1px solid #f0f0f0',
            }}
          >
            <Segmented
              size="small"
              value={depth}
              onChange={(value) => setDepth(value as number)}
              options={DEPTH_OPTIONS.map((d) => ({ value: d, label: String(d) }))}
            />
          </div>
        </>
      )}
    </Card>
  )
}
