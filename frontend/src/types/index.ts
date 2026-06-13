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
