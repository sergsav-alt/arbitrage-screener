import React, { useEffect, useState } from 'react';
import { Select, Slider, Space } from 'antd';
import { getExchanges, getSymbols } from '../services/api';
import { OpportunityFilters } from '../types';

interface FiltersProps {
  filters: OpportunityFilters;
  onChange: (filters: OpportunityFilters) => void;
}

export const Filters: React.FC<FiltersProps> = ({ filters, onChange }) => {
  const [symbols, setSymbols] = useState<string[]>([]);
  const [exchanges, setExchanges] = useState<string[]>([]);

  useEffect(() => {
    getSymbols().then(setSymbols).catch(console.error);
    getExchanges().then((data) =>
      setExchanges(data.map((e) => e.exchange))
    ).catch(console.error);
  }, []);

  return (
    <div>
      <Space direction="vertical" style={{ width: '100%' }} size="small">
        <div>
          <label style={{ fontSize: 12, color: '#888', marginBottom: 4, display: 'block' }}>
            Symbol
          </label>
          <Select
            allowClear
            placeholder="All symbols"
            style={{ width: '100%' }}
            value={filters.symbol}
            onChange={(value) =>
              onChange({ ...filters, symbol: value })
            }
            options={symbols.map((s) => ({ value: s, label: s }))}
          />
        </div>

        <div>
          <label style={{ fontSize: 12, color: '#888', marginBottom: 4, display: 'block' }}>
            Buy Exchange
          </label>
          <Select
            allowClear
            placeholder="All exchanges"
            style={{ width: '100%' }}
            value={filters.buyExchange}
            onChange={(value) =>
              onChange({ ...filters, buyExchange: value })
            }
            options={exchanges.map((e) => ({ value: e, label: e }))}
          />
        </div>

        <div>
          <label style={{ fontSize: 12, color: '#888', marginBottom: 4, display: 'block' }}>
            Sell Exchange
          </label>
          <Select
            allowClear
            placeholder="All exchanges"
            style={{ width: '100%' }}
            value={filters.sellExchange}
            onChange={(value) =>
              onChange({ ...filters, sellExchange: value })
            }
            options={exchanges.map((e) => ({ value: e, label: e }))}
          />
        </div>

        <div>
          <label style={{ fontSize: 12, color: '#888', marginBottom: 4, display: 'block' }}>
            Min Spread: {filters.minSpread ?? 0}%
          </label>
          <Slider
            min={0}
            max={2}
            step={0.01}
            value={filters.minSpread ?? 0}
            onChange={(value) =>
              onChange({ ...filters, minSpread: value })
            }
          />
        </div>
      </Space>
    </div>
  );
};
