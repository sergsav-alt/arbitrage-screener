export interface ArbitrageOpportunity {
  id: string;
  symbol: string;
  buyExchange: string;
  sellExchange: string;
  buyPrice: number;
  sellPrice: number;
  spreadPercent: number;
  netProfit: number;
  maxVolume: number;
  slippage: number;
  type: string;
  detectedAt: string;
}

export interface ExchangeStatus {
  exchange: string;
  displayName: string;
  connected: boolean;
}

export interface OpportunityFilters {
  symbol?: string;
  buyExchange?: string;
  sellExchange?: string;
  minSpread?: number;
}

export interface AggregatedStatistics {
  averageSpread: number;
  maxSpread: number;
  opportunityCount: number;
  topSymbols: SymbolStats[];
}

export interface SymbolStats {
  symbol: string;
  averageSpread: number;
  maxSpread: number;
  count: number;
}

export interface SpreadHistoryPoint {
  recordedAt: string;
  spreadPercent: number;
  buyPrice: number;
  sellPrice: number;
  netProfit: number;
}
