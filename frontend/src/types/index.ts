export interface ExchangeStatus {
  exchange: string
  displayName: string
  connected: boolean
}

export interface TickerData {
  exchange: string
  symbol: string
  bidPrice: number
  bidQty: number
  askPrice: number
  askQty: number
  timestamp: number
}

export interface DealData {
  exchange: string
  symbol: string
  price: number
  quantity: number
  timestamp: number
}

export interface ConnectResult {
  connected: boolean
  message: string
}

export interface OrderBookLevel {
  price: number
  quantity: number
}

export interface OrderBookData {
  exchange: string
  symbol: string
  bids: OrderBookLevel[]
  asks: OrderBookLevel[]
}

export interface CandleData {
  time: number
  open: number
  high: number
  low: number
  close: number
  volume: number
  closed: boolean
}
