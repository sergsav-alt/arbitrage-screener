import React from 'react';
import { Select } from 'antd';

interface PairSelectorProps {
  symbols: string[];
  selectedSymbol: string | null;
  onSelect: (symbol: string) => void;
}

export const PairSelector: React.FC<PairSelectorProps> = ({
  symbols,
  selectedSymbol,
  onSelect,
}) => {
  return (
    <Select
      style={{ width: 200 }}
      placeholder="Выберите торговую пару"
      value={selectedSymbol}
      onChange={onSelect}
      options={symbols.map((s) => ({ value: s, label: s }))}
      showSearch
      filterOption={(input, option) =>
        (option?.label as string).toLowerCase().includes(input.toLowerCase())
      }
    />
  );
};
