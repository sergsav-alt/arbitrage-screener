import React, { useEffect, useRef, useState } from 'react'
import { Button, Card, Select, Tag, message } from 'antd'
import {
  CandlestickData,
  ColorType,
  createChart,
  IChartApi,
  ISeriesApi,
  UTCTimestamp,
} from 'lightweight-charts'
import { fetchCandles, subscribeCandleStream } from '../services/api'
import { CandleData } from '../types'
import { CANDLE_EXCHANGE_OPTIONS, CANDLE_INTERVAL_OPTIONS } from '../constants'
import { useSymbols } from '../hooks/useSymbols'

const HISTORY_LIMIT = 500
const CHART_HEIGHT = 520

function toCandlestick(candle: CandleData): CandlestickData {
  return {
    time: candle.time as UTCTimestamp,
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close,
  }
}

export const CandleChart: React.FC = () => {
  const [exchange, setExchange] = useState<string>('BINANCE')
  const { symbol, setSymbol, symbolOptions } = useSymbols()
  const [candleInterval, setCandleInterval] = useState<string>('1m')

  const [loading, setLoading] = useState(false)
  const [connected, setConnected] = useState(false)

  const chartContainerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const seriesRef = useRef<ISeriesApi<'Candlestick'> | null>(null)
  const eventSourceRef = useRef<EventSource | null>(null)
  const lastTimeRef = useRef<number | null>(null)

  // Создаём график один раз; ResizeObserver подстраивает ширину,
  // в том числе когда вкладка становится видимой.
  useEffect(() => {
    const container = chartContainerRef.current
    if (!container) return

    const chart = createChart(container, {
      width: container.clientWidth,
      height: CHART_HEIGHT,
      layout: {
        background: { type: ColorType.Solid, color: '#ffffff' },
        textColor: '#333333',
      },
      grid: {
        vertLines: { color: '#f0f0f0' },
        horzLines: { color: '#f0f0f0' },
      },
      rightPriceScale: { borderColor: '#d9d9d9' },
      timeScale: { borderColor: '#d9d9d9', timeVisible: true, secondsVisible: false },
    })

    const series = chart.addCandlestickSeries({
      upColor: '#52c41a',
      downColor: '#ff4d4f',
      borderUpColor: '#52c41a',
      borderDownColor: '#ff4d4f',
      wickUpColor: '#52c41a',
      wickDownColor: '#ff4d4f',
    })

    chartRef.current = chart
    seriesRef.current = series

    const resizeObserver = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width ?? 0
      if (width > 0) chart.applyOptions({ width })
    })
    resizeObserver.observe(container)

    return () => {
      resizeObserver.disconnect()
      chart.remove()
      chartRef.current = null
      seriesRef.current = null
    }
  }, [])

  useEffect(() => {
    return () => stopStream()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const stopStream = () => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }
    lastTimeRef.current = null
  }

  const resetConnection = () => {
    stopStream()
    seriesRef.current?.setData([])
    setConnected(false)
    setLoading(false)
  }

  const handleStart = async () => {
    if (!symbol) {
      message.warning('Выберите торговую пару')
      return
    }
    stopStream()
    setLoading(true)
    setConnected(false)

    try {
      const history = await fetchCandles(exchange, symbol, candleInterval, HISTORY_LIMIT)
      const data = history.map(toCandlestick)
      seriesRef.current?.setData(data)
      chartRef.current?.timeScale().fitContent()
      lastTimeRef.current = data.length ? (data[data.length - 1].time as number) : null
      setConnected(true)

      eventSourceRef.current = subscribeCandleStream(
        exchange,
        symbol,
        candleInterval,
        (candle) => {
          const point = toCandlestick(candle)
          // series.update требует неубывающего времени
          if (lastTimeRef.current == null || (point.time as number) >= lastTimeRef.current) {
            seriesRef.current?.update(point)
            lastTimeRef.current = point.time as number
          }
        },
        () => {
          // Закрываем источник, чтобы браузер не переподключался молча
          // (иначе история не перезагружается, а тост дублируется).
          stopStream()
          setConnected(false)
          message.error('SSE соединение потеряно')
        },
      )
    } catch (err) {
      message.error('Ошибка загрузки свечей: ' + (err instanceof Error ? err.message : String(err)))
      setConnected(false)
    } finally {
      setLoading(false)
    }
  }

  const handleStop = () => {
    stopStream()
    setConnected(false)
    message.info('Остановлено')
  }

  const statusColor = loading ? 'orange' : connected ? 'green' : 'default'
  const statusText = loading ? 'Загрузка...' : connected ? 'Подключено' : 'Не подключено'

  return (
    <div>
      <Card style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <div>
            <span style={{ marginRight: 8, color: '#666' }}>Exchange:</span>
            <Select
              style={{ width: 140 }}
              value={exchange}
              onChange={(value) => {
                setExchange(value)
                resetConnection()
              }}
              options={CANDLE_EXCHANGE_OPTIONS}
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
                resetConnection()
              }}
              options={symbolOptions}
              showSearch
              filterOption={(input, option) => (option?.label as string).toLowerCase().includes(input.toLowerCase())}
            />
          </div>

          <div>
            <span style={{ marginRight: 8, color: '#666' }}>Interval:</span>
            <Select
              style={{ width: 130 }}
              value={candleInterval}
              onChange={(value) => {
                setCandleInterval(value)
                resetConnection()
              }}
              options={CANDLE_INTERVAL_OPTIONS}
            />
          </div>

          {!connected ? (
            <Button type="primary" onClick={handleStart} loading={loading} disabled={!symbol}>
              Пуск
            </Button>
          ) : (
            <Button onClick={handleStop}>Стоп</Button>
          )}

          <Tag color={statusColor} style={{ marginLeft: 4 }}>
            {statusText}
          </Tag>
        </div>
      </Card>

      <Card title={`Свечной график${symbol ? ` — ${symbol} · ${candleInterval}` : ''}`} bodyStyle={{ padding: 12 }}>
        <div ref={chartContainerRef} style={{ width: '100%', height: CHART_HEIGHT }} />
      </Card>
    </div>
  )
}
