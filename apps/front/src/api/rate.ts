import request from './request'

export interface RateConfig {
  id?: number
  tenantId?: number
  category: string
  subCategory: string
  personalRate?: number   // 个人费率，小数 0.08 = 8%
  employerRate?: number   // 单位费率，小数
  fixedRate?: number      // 固定税率
  unit?: string           // 'PERCENT' | 'FIXED'
  description?: string
  enabled?: number        // 1=启用 0=禁用
  createDate?: string
  updateDate?: string
}

export interface RateGroup {
  category: string
  label: string
  icon: string
  items: RateConfig[]
}

export const rateApi = {
  /** 获取当前租户全部费率配置（可选按分类过滤） */
  list(category?: string) {
    const params = category ? { category } : {}
    return request.get<{ data: RateConfig[] }>('/rateconfig/list', { params })
  },

  /** 批量保存/更新（自动使用当前登录用户的租户ID） */
  save(configs: Partial<RateConfig>[]) {
    return request.post('/rateconfig/save', configs)
  },
}
