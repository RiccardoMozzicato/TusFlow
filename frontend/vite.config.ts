/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// The app is served THROUGH the ApiGateway at http://localhost:8080/ui
// (the gateway proxies /ui/** -> this Vite dev server on :5173).
// We must therefore build under the /ui/ base path so asset URLs resolve.
//
// Because the OIDC redirect-uri and session cookie are bound to :8080, you
// develop by opening http://localhost:8080/ui — NOT http://localhost:5173.
// HMR connects directly to :5173 since the servlet gateway does not proxy
// websockets.
export default defineConfig({
  // Note: '/ui' (no trailing slash) so the dev server serves BOTH /ui and /ui/.
  // The gateway's defaultSuccessUrl is "/ui" and it forwards that path verbatim
  // to this server — with '/ui/' the bare /ui would 404 after login.
  base: '/ui',
  plugins: [react(), tailwindcss()],
  // sockjs-client references Node's `global`, which doesn't exist in the browser
  // and Vite doesn't polyfill. Alias it to globalThis so the SockJS transport loads.
  define: {
    global: 'globalThis',
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    cors: true,
    // allow the page (loaded from :8080) to open the HMR websocket on :5173
    hmr: {
      host: 'localhost',
      protocol: 'ws',
      clientPort: 5173,
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: false,
  },
})
