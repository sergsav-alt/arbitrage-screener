import React, { useEffect, useState } from 'react';
import { AppLayout } from './components/AppLayout';
import { OpportunitiesTable } from './components/OpportunitiesTable';
import { getOpportunities, subscribeOpportunities } from './services/api';
import { ArbitrageOpportunity, OpportunityFilters } from './types';

const App: React.FC = () => {
  const [opportunities, setOpportunities] = useState<ArbitrageOpportunity[]>([]);
  const [filters, setFilters] = useState<OpportunityFilters>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getOpportunities()
      .then(setOpportunities)
      .catch(console.error)
      .finally(() => setLoading(false));

    const es = subscribeOpportunities(
      (data) => {
        setOpportunities(data);
        setLoading(false);
      },
      (error) => {
        console.error('SSE error, reconnecting...', error);
      }
    );

    return () => es.close();
  }, []);

  const filteredOpportunities = opportunities.filter((o) => {
    if (filters.symbol && o.symbol !== filters.symbol) return false;
    if (filters.buyExchange && o.buyExchange !== filters.buyExchange) return false;
    if (filters.sellExchange && o.sellExchange !== filters.sellExchange) return false;
    if (filters.minSpread !== undefined && o.spreadPercent < filters.minSpread) return false;
    return true;
  });

  return (
    <AppLayout filters={filters} onFiltersChange={setFilters}>
      <OpportunitiesTable
        opportunities={filteredOpportunities}
        loading={loading}
      />
    </AppLayout>
  );
};

export default App;
