import axios from 'axios';
import {
  ArbitrageOpportunity,
  ExchangeStatus,
  OpportunityFilters,
  AggregatedStatistics,
  SymbolStats,
  SpreadHistoryPoint,
  TickerData,
  ConnectResult,
} from '../types';

const API_BASE = '/api';

export async function getExchanges(): Promise<ExchangeStatus[]> {
  const response = await axios.get<ExchangeStatus[]>(`${API_BASE}/exchanges`);
  return response.data;
}

export async function getSymbols(): Promise<string[]> {
  const response = await axios.get<string[]>(`${API_BASE}/symbols`);
  return response.data;
}

export async function getOpportunities(filters: OpportunityFilters = {}): Promise<ArbitrageOpportunity[]> {
  const params = new URLSearchParams();
  if (filters.symbol) params.append('symbol', filters.symbol);
  if (filters.buyExchange) params.append('buyExchange', filters.buyExchange);
  if (filters.sellExchange) params.append('sellExchange', filters.sellExchange);
  if (filters.minSpread !== undefined) params.append('minSpread', String(filters.minSpread));

  const response = await axios.get<ArbitrageOpportunity[]>(
    `${API_BASE}/opportunities?${params.toString()}`
  );
  return response.data;
}

export function subscribeOpportunities(
  onData: (opportunities: ArbitrageOpportunity[]) => void,
  onError?: (error: Event) => void
): EventSource {
  const eventSource = new EventSource(`${API_BASE}/stream/opportunities`);

  eventSource.addEventListener('opportunities', (event) => {
    try {
      const data = JSON.parse(event.data) as ArbitrageOpportunity[];
      onData(data);
    } catch (e) {
      console.error('Failed to parse SSE data:', e);
    }
  });

  if (onError) {
    eventSource.onerror = onError;
  }

  return eventSource;
}

export async function getAggregatedStatistics(): Promise<AggregatedStatistics> {
  const response = await axios.get<AggregatedStatistics>(`${API_BASE}/statistics`);
  return response.data;
}

export async function getSymbolStatistics(
  symbol: string,
  from?: string,
  to?: string
): Promise<SymbolStats> {
  const params = new URLSearchParams();
  if (from) params.append('from', from);
  if (to) params.append('to', to);
  const response = await axios.get<SymbolStats>(
    `${API_BASE}/statistics/${symbol}?${params.toString()}`
  );
  return response.data;
}

export async function getSymbolHistory(
  symbol: string,
  from?: string,
  to?: string
): Promise<SpreadHistoryPoint[]> {
  const params = new URLSearchParams();
  if (from) params.append('from', from);
  if (to) params.append('to', to);
  const response = await axios.get<SpreadHistoryPoint[]>(
    `${API_BASE}/statistics/${symbol}/history?${params.toString()}`
  );
  return response.data;
}

export async function connectExchange(
  exchange: string,
  symbol: string
): Promise<ConnectResult> {
  const params = new URLSearchParams();
  params.append('exchange', exchange);
  params.append('symbol', symbol);
  const response = await axios.get<ConnectResult>(
    `${API_BASE}/exchange/connect?${params.toString()}`
  );
  return response.data;
}

export function subscribeTickerStream(
  exchange: string,
  onTicker: (ticker: TickerData) => void,
  onError?: (error: Event) => void
): EventSource {
  const url = `${API_BASE}/stream/${exchange.toLowerCase()}`;
  const eventSource = new EventSource(url);

  eventSource.addEventListener('ticker', (event) => {
    try {
      console.log('------', event.data)
      const data = JSON.parse(event.data) as TickerData;
      onTicker(data);
    } catch (e) {
      console.error('Failed to parse ticker SSE data:', e);
    }
  });

  if (onError) {
    eventSource.onerror = onError;
  }

  return eventSource;
}
