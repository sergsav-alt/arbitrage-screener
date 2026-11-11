import axios from 'axios'
import { ExchangeStatus, TickerData, ConnectResult, OrderBookData, DealData, CandleData } from '../types'

const API_BASE = '/api'
const API_SERVER = 'http://localhost:8080/api'

export async function getExchanges(): Promise<ExchangeStatus[]> {
  const response = await axios.get<ExchangeStatus[]>(`${API_BASE}/exchanges`)
  return response.data
}

export async function getSymbols(): Promise<string[]> {
  const response = await axios.get<string[]>(`${API_BASE}/symbols`)
  return response.data
}

export async function connectExchange(exchange: string, symbol: string): Promise<ConnectResult> {
  const params = new URLSearchParams()
  params.append('exchange', exchange)
  params.append('symbol', symbol)
  const response = await axios.get<ConnectResult>(`${API_BASE}/exchange/connect?${params.toString()}`)
  return response.data
}

export async function disconnectExchange(exchange: string) {
  const params = new URLSearchParams()
  params.append('exchange', exchange)
  await axios.get<void>(`${API_BASE}/exchange/disconnect?${params.toString()}`)
}

export async function disconnectExchanges(): Promise<void> {
  const exchanges = await getExchanges()
  await Promise.all(exchanges.map((e) => disconnectExchange(e.exchange)))
}

export function subscribeBookTickerStream(
  exchange: string,
  onBookTicker: (ticker: TickerData) => void,
  onError?: (error: Event) => void,
): EventSource {
  const url = `${API_SERVER}/stream/${exchange.toLowerCase()}`
  const eventSource = new EventSource(url)

  eventSource.addEventListener('bookticker', (event) => {
    try {
      const data = JSON.parse(event.data) as TickerData
      onBookTicker(data)
    } catch (e) {
      console.error('Failed to parse ticker SSE data:', e)
    }
  })
  if (onError) {
    eventSource.onerror = onError
  }
  return eventSource
}

export interface StreamHandlers {
  onDeal?: (deal: DealData) => void
  onBookTicker?: (ticker: TickerData) => void
  onOrderBook?: (orderBook: OrderBookData) => void
}

export function subscribeStream(
  exchange: string,
  handlers: StreamHandlers,
  onError?: (error: Event) => void,
): EventSource {
  const url = `${API_SERVER}/stream/${exchange.toLowerCase()}`
  const eventSource = new EventSource(url)

  if (handlers.onDeal) {
    eventSource.addEventListener('deal', (event) => {
      try {
        const data = JSON.parse(event.data) as DealData
        handlers.onDeal!(data)
      } catch (e) {
        console.error('Failed to parse ticker SSE data:', e)
      }
    })
  }

  if (handlers.onOrderBook) {
    eventSource.addEventListener('orderbook', (event) => {
      try {
        const raw = JSON.parse(event.data)
        const nested = raw.data ?? raw
        const mapLevels = (levels: unknown[]): { price: number; quantity: number }[] =>
          (levels ?? []).map((item) => {
            const { price, quantity } = item as { price: number; quantity: number }
            return {
              price: parseFloat(String(price)),
              quantity: parseFloat(String(quantity)),
            }
          })
        const data: OrderBookData = {
          exchange,
          symbol: nested.symbol ?? raw.symbol ?? '',
          bids: mapLevels(nested.bids ?? []),
          asks: mapLevels(nested.asks ?? []),
        }
        handlers.onOrderBook!(data)
      } catch (e) {
        console.error('Failed to parse orderbook SSE data:', e)
      }
    })
  }

  if (onError) {
    eventSource.onerror = onError
  }
  return eventSource
}

export async function fetchCandles(
  exchange: string,
  symbol: string,
  interval: string,
  limit: number,
): Promise<CandleData[]> {
  const response = await axios.get<CandleData[]>(`${API_BASE}/candles/${exchange.toLowerCase()}/history`, {
    params: { symbol, interval, limit },
  })
  return response.data
}

export function subscribeCandleStream(
  exchange: string,
  symbol: string,
  interval: string,
  onCandle: (candle: CandleData) => void,
  onError?: (error: Event) => void,
): EventSource {
  const params = new URLSearchParams({ symbol, interval })
  const url = `${API_SERVER}/candles/${exchange.toLowerCase()}/stream?${params.toString()}`
  const eventSource = new EventSource(url)

  eventSource.addEventListener('candle', (event) => {
    try {
      const data = JSON.parse(event.data) as CandleData
      onCandle(data)
    } catch (e) {
      console.error('Failed to parse candle SSE data:', e)
    }
  })
  if (onError) {
    eventSource.onerror = onError
  }
  return eventSource
}

export async function fetchOrderBook(exchange: string, symbol: string, depth: number): Promise<OrderBookData> {
  const response = await axios.get(`${API_BASE}/${exchange.toLowerCase()}/ob`, {
    params: { symbol, depth },
  })
  const { data } = response

  const mapLevels = (levels: string[][]): { price: number; quantity: number }[] =>
    levels.map(([price, qty]) => ({
      price: parseFloat(price),
      quantity: parseFloat(qty),
    }))

  const nested = data?.data ?? data

  return {
    exchange,
    symbol,
    bids: mapLevels(nested.bids ?? []),
    asks: mapLevels(nested.asks ?? []),
  }
}
