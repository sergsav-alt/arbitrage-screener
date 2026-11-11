import { useEffect, useMemo, useState } from 'react'
import { getSymbols } from '../services/api'

export interface SymbolOption {
  value: string
  label: string
}

export interface UseSymbolsResult {
  symbols: string[]
  symbol: string | null
  setSymbol: (symbol: string | null) => void
  symbolOptions: SymbolOption[]
}

/**
 * Загружает список доступных торговых пар с бэкенда и выбирает пару с BTC
 * по умолчанию. Инкапсулирует одинаковую для всех вкладок логику работы
 * с символами (список, выбранная пара, опции для Select).
 */
export function useSymbols(): UseSymbolsResult {
  const [symbols, setSymbols] = useState<string[]>([])
  const [symbol, setSymbol] = useState<string | null>(null)

  useEffect(() => {
    getSymbols()
      .then((list) => {
        setSymbols(list)
        const btc = list.find((s) => s.toUpperCase().includes('BTC'))
        if (btc) setSymbol(btc)
      })
      .catch(console.error)
  }, [])

  const symbolOptions = useMemo(() => symbols.map((s) => ({ value: s, label: s })), [symbols])

  return { symbols, symbol, setSymbol, symbolOptions }
}
