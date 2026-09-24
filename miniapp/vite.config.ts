import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')
  return {
    plugins: [vue()],
    resolve: { dedupe: ['katex'] },
    base: env.VITE_BASE || '/atrimeow/profile/',
    build: { outDir: '../src/main/resources/miniapp', emptyOutDir: true, target: 'es2020' },
    server: {
      port: 5174,
      strictPort: true,
      headers: { 'Referrer-Policy': 'no-referrer', 'Cache-Control': 'no-store' },
      proxy: { [env.VITE_API_BASE || '/atrimeow/profile/api']: { target: env.VITE_API_TARGET || 'http://127.0.0.1:1234', changeOrigin: true } }
    }
  }
})
