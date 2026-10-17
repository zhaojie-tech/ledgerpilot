import request from './request'

export interface ProfitStatementVO {
  year: number
  period: number
  operatingRevenue: number
  mainBusinessRevenue: number
  otherBusinessRevenue: number
  operatingCost: number
  mainBusinessCost: number
  otherBusinessCost: number
  taxesAndSurcharges: number
  periodExpenses: number
  sellingExpense: number
  administrativeExpense: number
  financialExpense: number
  nonOperatingIncome: number
  nonOperatingExpense: number
  grossProfit: number
  operatingProfit: number
  totalProfit: number
  netProfit: number
  rows: Array<{
    subjectCode: string
    subjectName: string
    amount: number
    category: string
  }>
}

export interface BalanceSheetVO {
  year: number
  period: number
  totalAssets: number
  currentAssets: number
  nonCurrentAssets: number
  monetaryFunds: number
  accountsReceivable: number
  inventory: number
  totalLiabilities: number
  currentLiabilities: number
  nonCurrentLiabilities: number
  accountsPayable: number
  taxesPayable: number
  employeeCompensationPayable: number
  totalEquity: number
  paidInCapital: number
  surplusReserve: number
  retainedEarnings: number
  totalLiabilitiesAndEquity: number
  balanced: boolean
  registeredCapital: number
}

export interface SubjectBalanceVO {
  year: number
  period: number
  totalDebit: number
  totalCredit: number
  netBalance: number
  rows: Array<{
    subjectCode: string
    subjectName: string
    subjectType: string
    direction: string
    isDetail: boolean
    level: number
    beginDebit: number
    beginCredit: number
    currentDebit: number
    currentCredit: number
    endDebit: number
    endCredit: number
  }>
}

export interface CashFlowVO {
  operating: { name: string; cashInflow: number; cashOutflow: number; netCashFlow: number }
  investing: { name: string; cashInflow: number; cashOutflow: number; netCashFlow: number }
  financing: { name: string; cashInflow: number; cashOutflow: number; netCashFlow: number }
  netIncrease: number
  note?: string
}

export function getProfitStatement(year: number, period: number) {
  return request.get<ProfitStatementVO>('/report/profit', { params: { year, period } })
}

export function getBalanceSheet(year: number, period: number) {
  return request.get<BalanceSheetVO>('/report/balance', { params: { year, period } })
}

export function getCashFlowStatement(year: number, period: number) {
  return request.get<CashFlowVO>('/report/cashflow', { params: { year, period } })
}

export function getSubjectBalanceReport(year: number, period: number) {
  return request.get<SubjectBalanceVO>('/report/subject-balance', { params: { year, period } })
}

export function exportReport(type: string, year: number, period: number) {
  return request.get<string>('/report/export/' + type, { params: { year, period } })
}
