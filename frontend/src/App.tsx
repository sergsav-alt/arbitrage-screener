import React from 'react'
import { Tabs } from 'antd'
import { AppLayout } from './components/AppLayout'
import { OrderBookPanel } from './components/OrderBookPanel'
import { RateChart } from './components/RateChart'
import { CrossRatesChart } from './components/CrossRatesChart'
import { CandleChart } from './components/CandleChart'

const App: React.FC = () => {
  const tabs = [
    {
      key: 'order-book',
      label: 'Order Book',
      children: <OrderBookPanel />,
    },
    {
      key: 'rates',
      label: 'Rates',
      children: <RateChart />,
    },
    {
      key: 'cross-rates',
      label: 'Cross-Rates',
      children: <CrossRatesChart />,
    },
    {
      key: 'candles',
      label: 'Candles',
      children: <CandleChart />,
    },
  ]

  return (
    <AppLayout>
      <Tabs defaultActiveKey="order-book" items={tabs} size="large" />
    </AppLayout>
  )
}

export default App
