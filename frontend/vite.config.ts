import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Vite 5 配置。
// 第 13 章会扩展 dev proxy、build rollup 选项；骨架阶段保持最小。
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 开发期把 /api 请求代理到后端 :8080，避免浏览器跨域
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
