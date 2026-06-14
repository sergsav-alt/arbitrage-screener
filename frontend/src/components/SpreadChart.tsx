import React from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { SpreadHistoryPoint } from '../types';

interface SpreadChartProps {
  data: SpreadHistoryPoint[];
  loading: boolean;
}

export const SpreadChart: React.FC<SpreadChartProps> = ({ data, loading }) => {
  if (loading) {
    return <div style={{ textAlign: 'center', padding: 40 }}>Загрузка данных...</div>;
  }

  if (data.length === 0) {
    return <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>Нет данных для отображения</div>;
  }

  const chartData = data.map((point) => ({
    time: new Date(point.recordedAt).toLocaleTimeString('ru-RU', {
      hour: '2-digit',
      minute: '2-digit',
    }),
    spread: point.spreadPercent,
    netProfit: point.netProfit,
  }));

  return (
    <ResponsiveContainer width="100%" height={400}>
      <LineChart data={chartData} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
        <XAxis dataKey="time" fontSize={12} />
        <YAxis
          fontSize={12}
          tickFormatter={(value: number) => `${value.toFixed(2)}%`}
        />
        <Tooltip
          formatter={(value) => [
            typeof value === 'number' ? value.toFixed(4) + '%' : value,
            undefined,
          ]}
          labelFormatter={(label) => `Время: ${label}`}
        />
        <Line
          type="monotone"
          dataKey="spread"
          stroke="#1890ff"
          strokeWidth={2}
          dot={false}
          name="Спред %"
        />
        <Line
          type="monotone"
          dataKey="netProfit"
          stroke="#52c41a"
          strokeWidth={1}
          dot={false}
          name="Чистая прибыль"
        />
      </LineChart>
    </ResponsiveContainer>
  );
};
