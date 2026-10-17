import request from './request'

export interface VoucherEntry {
  subjectCode: string
  subjectName?: string
  direction: 'DEBIT' | 'CREDIT'
  amount: string
  summary?: string
  auxJson?: string
}

export interface VoucherCreateRequest {
  voucherDate: string
  businessType?: string
  businessNo?: string
  summary?: string
  sourceType?: string
  entries: VoucherEntry[]
}

export interface VoucherQuery {
  voucherNo?: string
  dateFrom?: string
  dateTo?: string
  businessType?: string
  status?: string
  sourceType?: string
  current?: number
  size?: number
}

export interface VoucherDetailVO {
  id: string | number
  voucherDate: string
  voucherNo: string
  businessType: string
  businessNo: string
  totalDebit: string
  totalCredit: string
  entryCount: number
  status: string
  sourceType: string
  summary: string
  createdAt: string
  approveAt: string | null
  postAt: string | null
  createdByName: string | null
  approveByName: string | null
  postByName: string | null
  entries: VoucherEntryVO[]
}

export interface VoucherEntryVO {
  id: number
  seq: number
  subjectId: number
  subjectCode: string
  subjectName: string
  direction: string
  amount: string
  summary: string
  auxJson: string | null
  auxDisplay: string | null
}

export interface VoucherVO {
  id: number
  voucherNo: string
  voucherDate: string
  businessType: string
  businessNo: string
  totalDebit: string
  totalCredit: string
  entryCount: number
  status: string
  sourceType: string
  summary: string
  createdAt: string
}

export interface PageResponse<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export const voucherApi = {
  page(params: VoucherQuery) {
    return request.get<PageResponse<VoucherVO>>('/voucher/page', { params })
  },
  getById(id: string | number) {
    return request.get(`/voucher/${id}`)
  },
  create(data: VoucherCreateRequest) {
    return request.post('/voucher', data)
  },
  approve(id: number) {
    return request.post(`/voucher/${id}/approve`)
  },
  unapprove(id: number) {
    return request.post(`/voucher/${id}/unapprove`)
  },
  post(id: number) {
    return request.post(`/voucher/${id}/post`)
  },
  voidVoucher(id: number) {
    return request.post(`/voucher/${id}/void`)
  },
  getNextNo(voucherDate: string) {
    return request.get<string>('/voucher/next-no', { params: { voucherDate } })
  },
}

export function monthEndTransfer(year: number, period: number, transferDate: string) {
  return request.post('/nlp/month-end-transfer', { year, period, transferDate })
}

// 期初余额（开账）
export interface OpeningBalanceVO {
  fiscalYear: number
  periodNo: number
  initialized: boolean
  hasVouchers: boolean
  subjects: SubjectBalanceVO[]
}

export interface SubjectBalanceVO {
  subjectCode: string
  subjectName: string
  direction: string       // DEBIT / CREDIT
  subjectType: string     // ASSET / LIABILITY / EQUITY / COST / PROFIT_LOSS
  balanceDirection: string
  beginDebit: string
  beginCredit: string
}

export interface OpeningBalanceSave {
  fiscalYear: number
  periodNo: number
  balances: { subjectCode: string; debitBalance?: number; creditBalance?: number }[]
}

export const openingBalanceApi = {
  get(params: { year?: number; period?: number }) {
    return request.get<OpeningBalanceVO>('/voucher/opening-balance', { params })
  },
  save(data: OpeningBalanceSave) {
    return request.post('/voucher/opening-balance', data)
  },
}
