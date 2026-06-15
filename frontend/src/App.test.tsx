import React from 'react'
import { render, screen } from '@testing-library/react'
import App from './App'

test('renders arbitrage screener header', () => {
  render(<App />)
  const headerElement = screen.getByText(/Arbitrage Screener/i)
  expect(headerElement).toBeInTheDocument()
})
