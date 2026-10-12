import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the browser talks to Vite (5173) and Vite forwards /api to the Spring Boot app (8080),
// so the app is same-origin exactly like in production and the session cookie just works.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': 'http://localhost:8080', '/actuator': 'http://localhost:8080' } },
  test: { environment: 'jsdom', globals: true },
});
