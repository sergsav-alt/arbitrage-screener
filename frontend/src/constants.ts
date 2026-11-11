export const EXCHANGE_OPTIONS = [
  { value: 'BINANCE', label: 'Binance' },
  { value: 'BINGX', label: 'BingX' },
  { value: 'MEXC', label: 'MEXC' },
]

// Биржи, для которых реализована загрузка свечей (klines).
export const CANDLE_EXCHANGE_OPTIONS = [{ value: 'BINANCE', label: 'Binance' }]

export const CANDLE_INTERVAL_OPTIONS = [
  { value: '1m', label: '1 минута' },
  { value: '3m', label: '3 минуты' },
  { value: '5m', label: '5 минут' },
  { value: '15m', label: '15 минут' },
  { value: '30m', label: '30 минут' },
  { value: '1h', label: '1 час' },
  { value: '4h', label: '4 часа' },
  { value: '1d', label: '1 день' },
]
