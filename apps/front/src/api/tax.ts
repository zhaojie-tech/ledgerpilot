import request from './request'

export interface TaxItemVO {
  itemName: string
  amount: number | string
  remark?: string
}

export interface TaxVO {
  taxTypeName: string
  taxRate: number | string
  taxBase: number | string
  taxAmount: number | string
  taxPaid: number | string
  taxUnpaid: number | string
  period: string
  items: TaxItemVO[]
}

export interface PeriodVO {
  fiscal_year: number
  period_no: number
}

/** 税务总览 */
export function getTaxSummary(year: number, period: number) {
  return request({ url: '/tax/summary', params: { year, period } })
}

/** 增值税明细 */
export function getVAT(year: number, period: number) {
  return request({ url: '/tax/vat', params: { year, period } })
}

/** 企业所得税明细 */
export function getCorporateIncomeTax(year: number, period: number) {
  return request({ url: '/tax/corporate-income-tax', params: { year, period } })
}

/** 税金及附加明细 */
export function getSurtax(year: number, period: number) {
  return request({ url: '/tax/surtax', params: { year, period } })
}

/** 可用期间列表 */
export function getAvailablePeriods() {
  return request({ url: '/tax/periods' })
}
