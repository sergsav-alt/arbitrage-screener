import React from 'react'
import { Layout, Typography } from 'antd'
import { ExchangeStatus } from './ExchangeStatus'

const { Header, Sider, Content } = Layout
const { Title } = Typography

interface AppLayoutProps {
  children: React.ReactNode
}

export const AppLayout: React.FC<AppLayoutProps> = ({ children }) => {
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          background: '#001529',
          padding: '0 24px',
        }}
      >
        <Title level={3} style={{ color: '#fff', margin: 0 }}>
          Arbitrage Screener
        </Title>
      </Header>
      <Layout>
        <Sider width={280} style={{ background: '#fff', padding: '16px', overflow: 'auto' }}>
          <ExchangeStatus />
        </Sider>
        <Content style={{ padding: '0 240px', background: '#f5f5f5' }}>{children}</Content>
      </Layout>
    </Layout>
  )
}
