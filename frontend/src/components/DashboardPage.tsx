import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic } from 'antd';
import { AggregatedStatistics } from '../types';
import { getAggregatedStatistics, getSymbolHistory, getSymbols } from '../services/api';
import { PairSelector } from './PairSelector';
import { SpreadChart } from './SpreadChart';
import { SpreadHistoryPoint } from '../types';

export const DashboardPage: React.FC = () => {
  const [stats, setStats] = useState<AggregatedStatistics | null>(null);
  const [symbols, setSymbols] = useState<string[]>([]);
  const [selectedSymbol, setSelectedSymbol] = useState<string | null>(null);
  const [history, setHistory] = useState<SpreadHistoryPoint[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  useEffect(() => {
    getAggregatedStatistics().then(setStats).catch(console.error);
    getSymbols().then(setSymbols).catch(console.error);
  }, []);

  useEffect(() => {
    if (!selectedSymbol) {
      setHistory([]);
      return;
    }

    setHistoryLoading(true);
    getSymbolHistory(selectedSymbol)
      .then(setHistory)
      .catch(console.error)
      .finally(() => setHistoryLoading(false));
  }, [selectedSymbol]);

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Средний спред (24ч)"
              value={stats?.averageSpread ?? 0}
              suffix="%"
              precision={4}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Максимальный спред (24ч)"
              value={stats?.maxSpread ?? 0}
              suffix="%"
              precision={4}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Всего снапшотов (24ч)"
              value={stats?.opportunityCount ?? 0}
              valueStyle={{ color: '#722ed1' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Топ-пар"
              value={stats?.topSymbols?.length ?? 0}
              suffix="пар"
              valueStyle={{ color: '#fa8c16' }}
            />
          </Card>
        </Col>
      </Row>

      <Card
        title="График спреда"
        extra={
          <PairSelector
            symbols={symbols}
            selectedSymbol={selectedSymbol}
            onSelect={setSelectedSymbol}
          />
        }
        style={{ marginBottom: 24 }}
      >
        <SpreadChart data={history} loading={historyLoading} />
      </Card>

      {stats && stats.topSymbols.length > 0 && (
        <Card title="Топ-10 пар по среднему спреду">
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
            {stats.topSymbols.map((s) => (
              <Card
                key={s.symbol}
                size="small"
                style={{ width: 220, cursor: 'pointer' }}
                onClick={() => setSelectedSymbol(s.symbol)}
                hoverable
              >
                <Statistic
                  title={s.symbol}
                  value={s.averageSpread}
                  suffix="%"
                  precision={4}
                  valueStyle={{ fontSize: 18 }}
                />
                <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>
                  Макс: {s.maxSpread.toFixed(4)}% | Снапшотов: {s.count}
                </div>
              </Card>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
};
