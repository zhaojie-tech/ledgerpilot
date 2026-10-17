import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// 代理目标可通过 env 覆盖（.env.development 或 shell）：
//   DEV_PROXY_BACKEND  默认 http://localhost:8080 （Spring Boot）
//   DEV_PROXY_AI       默认 http://127.0.0.1:8000 （LedgerPilot AI，FastAPI）
//   DEV_AI_TOKEN       开发态由代理注入给 AI 服务的 X-API-Token（不进浏览器代码）
const backendTarget = process.env.DEV_PROXY_BACKEND || 'http://localhost:8080'
const aiTarget = process.env.DEV_PROXY_AI || 'http://127.0.0.1:8000'
const aiToken = process.env.DEV_AI_TOKEN || ''

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: backendTarget,
        changeOrigin: true,
      },
      // AI 服务代理（知识库管理界面用）；AI 侧开启鉴权时代理注入服务 token
      '/ai-api': {
        target: aiTarget,
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/ai-api/, ''),
        headers: aiToken ? { 'X-API-Token': aiToken } : undefined,
      },
    },
  },
})
