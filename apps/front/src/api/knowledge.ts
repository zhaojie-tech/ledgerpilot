/**
 * AI 服务知识库 API（直接用 fetch 调用，通过 Vite 代理到 AI 服务）
 */

async function fetcher<T>(path: string, body?: unknown): Promise<T> {
  const opts: RequestInit = {
    headers: { 'Content-Type': 'application/json' },
  }
  if (body) {
    opts.method = 'POST'
    opts.body = JSON.stringify(body)
  }
  const res = await fetch(`/ai-api${path}`, opts)
  if (!res.ok) {
    throw new Error(`请求失败 (${res.status})`)
  }
  return res.json() as Promise<T>
}

export const knowledgeApi = {
  stats() {
    return fetcher<{ total: number; by_category: Record<string, number>; error?: string }>(
      '/api/v1/knowledge/stats'
    )
  },

  reload() {
    return fetcher<{ total: number; by_category: Record<string, number>; error?: string }>(
      '/api/v1/knowledge/import'
    )
  },

  search(query: string) {
    return fetcher<{
      query: string
      count: number
      results: Array<{
        content: string
        source: string
        category: string
        heading: string
        relevance: number
      }>
      message: string
      error?: string
    }>('/api/v1/knowledge/search', { query })
  },
}
