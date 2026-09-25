import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Dev-сервер фиксирован на порту 3000 (см. общую карту портов проекта), backend — на 8080.
// /api проксируется на backend, поэтому в dev-режиме CORS не нужен (запросы идут с того же
// origin, что и страница); api.js всё равно поддерживает явный apiBase на случай отдельного
// хоста backend.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    strictPort: true,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 3000,
    strictPort: true
  },
  build: {
    outDir: "dist"
  }
});
