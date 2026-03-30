import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
      "/v3/api-docs": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
      "/swagger-ui": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
    },
  }
});
