import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.tsx'
import './index.css'

// Steps between modules run just after a command commits, so every list is polled briefly
// instead of assuming the result is there when the command returns.
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { refetchInterval: 2500, staleTime: 1000, retry: 1 },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
)
