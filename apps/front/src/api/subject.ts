import request from './request'

export interface SubjectVO {
  id: number
  subjectCode: string
  subjectName: string
  subjectType: string  // ASSET / LIABILITY / EQUITY / COST / PROFIT_LOSS
  level: number
  parentCode: string | null
  direction: string    // DEBIT / CREDIT
  isDetail: number     // 0=非末级 1=末级
  auxDimensions: string | null  // 逗号分隔
  isEnabled: number    // 0=停用 1=启用
  sysSort: number
  createdAt: string
  children?: SubjectVO[]
}

export interface SubjectCreateRequest {
  subjectCode: string
  subjectName: string
  subjectType: string
  parentCode?: string
  direction: string
  isDetail?: number
  auxDimensions?: string
}

export interface SubjectUpdateRequest {
  id: number
  subjectName?: string
  subjectType?: string
  auxDimensions?: string
  isEnabled?: number
  sysSort?: number
}

// 获取科目树
export function getSubjectTree() {
  return request.get<SubjectVO[]>('/subject/tree')
}

// 获取单个科目
export function getSubjectById(id: number) {
  return request.get<SubjectVO>(`/subject/${id}`)
}

// 创建科目
export function createSubject(data: SubjectCreateRequest) {
  return request.post<SubjectVO>('/subject', data)
}

// 更新科目
export function updateSubject(data: SubjectUpdateRequest) {
  return request.put<SubjectVO>('/subject', data)
}

// 删除科目
export function deleteSubject(id: number) {
  return request.delete(`/subject/${id}`)
}

// 从模板初始化
export function initFromTemplate(systemCode: string) {
  return request.post('/subject/init', { systemCode })
}

// 启用/停用切换
export function toggleSubjectEnabled(id: number) {
  return request.put(`/subject/${id}/toggle`)
}

// 科目类型选项
export const SUBJECT_TYPE_OPTIONS = [
  { label: '资产', value: 'ASSET' },
  { label: '负债', value: 'LIABILITY' },
  { label: '权益', value: 'EQUITY' },
  { label: '成本', value: 'COST' },
  { label: '损益', value: 'PROFIT_LOSS' },
]

// 余额方向选项
export const DIRECTION_OPTIONS = [
  { label: '借方', value: 'DEBIT' },
  { label: '贷方', value: 'CREDIT' },
]

// 辅助核算维度选项
export const AUX_DIMENSION_OPTIONS = [
  { label: '客户', value: 'customer' },
  { label: '供应商', value: 'supplier' },
  { label: '部门', value: 'department' },
  { label: '项目', value: 'project' },
  { label: '个人', value: 'employee' },
  { label: '仓库', value: 'warehouse' },
]

// 科目类型标签颜色
export const SUBJECT_TYPE_TAG: Record<string, string> = {
  ASSET: 'primary',
  LIABILITY: 'danger',
  EQUITY: 'warning',
  COST: 'success',
  PROFIT_LOSS: 'info',
}

export const SUBJECT_TYPE_LABEL: Record<string, string> = {
  ASSET: '资产',
  LIABILITY: '负债',
  EQUITY: '权益',
  COST: '成本',
  PROFIT_LOSS: '损益',
}
