import request from './request'

export interface ParseRequest {
  userInput: string
  bizType?: string
  requestId?: string
  autoCreateVoucher?: boolean
}

export interface BizItem {
  bizDate: string
  bizType: string
  description: string
  amount: number
  direction: 'DEBIT' | 'CREDIT'
  subjectCode: string
  confidence: number
}

export interface ParseResponse {
  requestId: string
  bizItems: BizItem[]
  confidence: number
  rawResponse?: string
}

export const nlpApi = {
  parse(data: ParseRequest) {
    return request.post<ParseResponse>('/nlp/parse', data)
  },
  createVoucher(data: ParseRequest) {
    return request.post('/nlp/create-voucher', data)
  },
  getHistory(requestId?: string) {
    return request.get('/nlp/history', { params: { requestId } })
  },
}
