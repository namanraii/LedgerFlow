import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5188,
    host: '127.0.0.1',
    strictPort: true,
    proxy: {
      '/svc/ledger': {
        target: 'http://127.0.0.1:8081',
        rewrite: (path) => path.replace(/^\/svc\/ledger/, ''),
      },
      '/svc/ingestion': {
        target: 'http://127.0.0.1:8082',
        rewrite: (path) => path.replace(/^\/svc\/ingestion/, ''),
      },
      '/svc/fraud': {
        target: 'http://127.0.0.1:8010',
        rewrite: (path) => path.replace(/^\/svc\/fraud/, ''),
      },
      '/svc/notify': {
        target: 'http://127.0.0.1:8003',
        rewrite: (path) => path.replace(/^\/svc\/notify/, ''),
      },
    },
  }
});
