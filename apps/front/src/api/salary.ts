import request from './request'

/** 工资计算请求 */
export interface SalaryComputeRequest {
  period: string                     // yyyy-MM
  grossSalary: number                 // 应发工资（税前）
  socialBase?: number                // 社保公积金基数（默认=应发工资）
  calculateSocial?: boolean          // 是否计算社保（默认 true）
  calculateHousing?: boolean         // 是否计算公积金（默认 true）
  employeeType?: string              // NORMAL | CONTRACT | POSTDOC
  /** 专项附加扣除 */
  childrenEduDeduction?: number     // 子女教育
  continuingEduDeduction?: number     // 继续教育
  seriousIllnessDeduction?: number   // 大病医疗
  housingLoanDeduction?: number      // 住房贷款利息
  housingRentDeduction?: number      // 住房租金
  elderlyCareDeduction?: number      // 赡养老人
  voucherDate?: string               // 凭证日期（默认当天）
}

/** 工资项明细 */
export interface SalaryItem {
  type: string
  name: string
  amount: number
  formula: string
}

/** 工资计算响应 */
export interface SalaryComputeResponse {
  period: string
  grossSalary: number               // 应发工资
  netSalary: number                 // 实发工资
  personalDeductionTotal: number   // 个人扣款合计（社保+公积金+个税）
  employerSocialTotal: number       // 单位社保公积金合计
  personalIncomeTax: number         // 代扣个人所得税
  socialBase: number               // 社保基数
  remark: string
  items: SalaryItem[]
}

export const salaryApi = {
  /** 预览工资计算结果（不生成凭证） */
  compute(data: SalaryComputeRequest) {
    return request.post<{ data: SalaryComputeResponse }>('/nlp/salary-compute', data)
  },

  /** 计算工资并生成凭证，返回生成的凭证ID列表 */
  computeAndCreateVoucher(data: SalaryComputeRequest) {
    return request.post<{ data: number[] }>('/nlp/salary-create-voucher', data)
  },
}
