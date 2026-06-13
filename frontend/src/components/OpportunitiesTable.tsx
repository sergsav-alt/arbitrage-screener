import React from 'react';
import { Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ArbitrageOpportunity } from '../types';

const getSpreadColor = (spreadPercent: number): string => {
  if (spreadPercent > 0.5) return 'green';
  if (spreadPercent >= 0.1) return 'orange';
  return 'red';
};

const formatPrice = (price: number): string => {
  return price < 1 ? price.toFixed(6) : price.toFixed(2);
};

const columns: ColumnsType<ArbitrageOpportunity> = [
  {
    title: 'Symbol',
    dataIndex: 'symbol',
    key: 'symbol',
    width: 100,
  },
  {
    title: 'Buy',
    dataIndex: 'buyExchange',
    key: 'buyExchange',
    width: 90,
    render: (value: string) => <Tag color="blue">{value}</Tag>,
  },
  {
    title: 'Sell',
    dataIndex: 'sellExchange',
    key: 'sellExchange',
    width: 90,
    render: (value: string) => <Tag color="cyan">{value}</Tag>,
  },
  {
    title: 'Buy Price',
    dataIndex: 'buyPrice',
    key: 'buyPrice',
    width: 110,
    render: (value: number) => formatPrice(value),
  },
  {
    title: 'Sell Price',
    dataIndex: 'sellPrice',
    key: 'sellPrice',
    width: 110,
    render: (value: number) => formatPrice(value),
  },
  {
    title: 'Spread %',
    dataIndex: 'spreadPercent',
    key: 'spreadPercent',
    width: 100,
    sorter: (a, b) => a.spreadPercent - b.spreadPercent,
    defaultSortOrder: 'descend',
    render: (value: number) => (
      <Tag color={getSpreadColor(value)}>{value.toFixed(4)}%</Tag>
    ),
  },
  {
    title: 'Net Profit',
    dataIndex: 'netProfit',
    key: 'netProfit',
    width: 110,
    render: (value: number) => (
      <span style={{ color: value >= 0 ? '#52c41a' : '#ff4d4f' }}>
        {formatPrice(value)}
      </span>
    ),
  },
  {
    title: 'Slippage',
    dataIndex: 'slippage',
    key: 'slippage',
    width: 110,
    render: (value: number) => (
      <span style={{ color: value > 0 ? '#faad14' : '#999' }}>
        {formatPrice(value)}
      </span>
    ),
  },
  {
    title: 'Max Volume',
    dataIndex: 'maxVolume',
    key: 'maxVolume',
    width: 110,
    render: (value: number) => value.toFixed(4),
  },
];

interface OpportunitiesTableProps {
  opportunities: ArbitrageOpportunity[];
  loading?: boolean;
}

export const OpportunitiesTable: React.FC<OpportunitiesTableProps> = ({
  opportunities,
  loading,
}) => {
  return (
    <Table<ArbitrageOpportunity>
      dataSource={opportunities}
      columns={columns}
      rowKey="id"
      loading={loading}
      pagination={{ pageSize: 20, showSizeChanger: false }}
      size="small"
      locale={{ emptyText: 'No arbitrage opportunities found' }}
      style={{ background: '#fff', borderRadius: 8 }}
    />
  );
};
