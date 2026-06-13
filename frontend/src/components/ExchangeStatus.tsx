import React, { useEffect, useState } from 'react';
import { Badge, Card, List } from 'antd';
import { getExchanges } from '../services/api';
import { ExchangeStatus as ExchangeStatusType } from '../types';

export const ExchangeStatus: React.FC = () => {
  const [exchanges, setExchanges] = useState<ExchangeStatusType[]>([]);

  useEffect(() => {
    const fetchStatus = () => {
      getExchanges().then(setExchanges).catch(console.error);
    };

    fetchStatus();
    const interval = setInterval(fetchStatus, 5000);
    return () => clearInterval(interval);
  }, []);

  return (
    <Card title="Exchange Status" size="small" style={{ marginBottom: 16 }}>
      <List
        size="small"
        dataSource={exchanges}
        renderItem={(item) => (
          <List.Item>
            <Badge
              status={item.connected ? 'success' : 'error'}
              text={item.displayName}
            />
          </List.Item>
        )}
        locale={{ emptyText: 'No exchanges available' }}
      />
    </Card>
  );
};
