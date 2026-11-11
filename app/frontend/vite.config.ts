import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// In development the API is the Spring Boot app on port 8080; in production the built files are
// served by that same app, so requests stay same-origin.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      // API_TARGET points the dev server at another backend, for example a local container on 18080.
      '/api': process.env.API_TARGET ?? 'http://localhost:8080',
    },
  },
})
