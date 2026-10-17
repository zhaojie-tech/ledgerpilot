<template>
  <div class="settings-view">
    <el-card>
      <template #header>
        <span>系统设置</span>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="租户信息" name="tenant">
          <el-form label-width="120px" style="max-width: 600px">
            <el-form-item label="公司名称">
              <el-input v-model="tenantInfo.companyName" />
            </el-form-item>
            <el-form-item label="纳税人类型">
              <el-select v-model="tenantInfo.taxpayerType" style="width: 100%">
                <el-option label="一般纳税人" value="GENERAL" />
                <el-option label="小规模纳税人" value="SMALL_SCALE" />
              </el-select>
            </el-form-item>
            <el-form-item label="行业">
              <el-select v-model="tenantInfo.industry" style="width: 100%">
                <el-option label="商贸企业" value="TRADING" />
                <el-option label="服务企业" value="SERVICE" />
                <el-option label="制造企业" value="MANUFACTURING" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveTenant">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="会计期间" name="period">
          <el-form label-width="120px" style="max-width: 600px">
            <el-form-item label="当前会计年度">
              <el-input-number v-model="period.year" :min="2020" :max="2099" />
            </el-form-item>
            <el-form-item label="当前期间">
              <el-input-number v-model="period.month" :min="1" :max="12" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="savePeriod">保存</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="AI设置" name="ai">
          <el-form label-width="140px" style="max-width: 700px">
            <el-form-item label="LLM服务商">
              <el-select v-model="aiConfig.provider" style="width: 100%">
                <el-option label="OpenAI (GPT-4o)" value="openai" />
                <el-option label="Claude 3.5" value="anthropic" />
                <el-option label="DeepSeek" value="deepseek" />
              </el-select>
            </el-form-item>
            <el-form-item label="API Key">
              <el-input v-model="aiConfig.apiKey" type="password" show-password />
            </el-form-item>
            <el-form-item label="模型名称">
              <el-input v-model="aiConfig.model" placeholder="如 gpt-4o, claude-3-5-sonnet" />
            </el-form-item>
            <el-form-item label="Temperature">
              <el-slider v-model="aiConfig.temperature" :min="0" :max="1" :step="0.1" show-stops />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveAi">保存</el-button>
              <el-button @click="testAi">测试连接</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ===================== 费率设置 ===================== -->
        <el-tab-pane name="rates">
          <template #label>
            <el-icon><Coin /></el-icon>
            费率设置
          </template>

          <div class="rate-config">
            <div class="rate-tip">
              <el-icon color="#409eff"><InfoFilled /></el-icon>
              <span>
                此处设置的费率用于 AI 生成工资社保分录时自动计算。
                如用户在业务描述中已提供具体金额，则使用用户提供的金额。
                费率数据以当地政策及企业实际方案为准。
              </span>
            </div>

            <!-- 子 tab：分类 -->
            <el-tabs v-model="rateActiveCategory" class="rate-sub-tabs">
              <el-tab-pane
                v-for="grp in rateGroups"
                :key="grp.category"
                :label="grp.label"
                :name="grp.category"
              >
                <el-table :data="getDisplayItems(grp.category)" border stripe class="rate-table">
                  <el-table-column label="项目" prop="subCategory" min-width="140">
                    <template #default="{ row }">
                      {{ subCategoryLabel(row.subCategory) }}
                    </template>
                  </el-table-column>

                  <!-- 增值税（固定税率，只有税率一列） -->
                  <template v-if="grp.category === 'vat' || grp.category === 'corp_tax'">
                    <el-table-column label="税率（%）" min-width="120">
                      <template #default="{ row }">
                        <el-input-number
                          v-model="row.fixedRateDisplay"
                          :min="0"
                          :max="100"
                          :precision="2"
                          :step="0.1"
                          size="small"
                          style="width: 100%"
                        />
                      </template>
                    </el-table-column>
                  </template>

                  <!-- 社保/公积金（个人费率 + 单位费率） -->
                  <template v-else-if="grp.category === 'social' || grp.category === 'housing'">
                    <el-table-column label="个人费率（%）" min-width="140">
                      <template #default="{ row }">
                        <el-input-number
                          v-model="row.personalRateDisplay"
                          :min="0"
                          :max="100"
                          :precision="2"
                          :step="0.1"
                          size="small"
                          style="width: 100%"
                        />
                      </template>
                    </el-table-column>
                    <el-table-column label="单位费率（%）" min-width="140">
                      <template #default="{ row }">
                        <el-input-number
                          v-model="row.employerRateDisplay"
                          :min="0"
                          :max="100"
                          :precision="2"
                          :step="0.1"
                          size="small"
                          style="width: 100%"
                        />
                      </template>
                    </el-table-column>
                  </template>

                  <!-- 固定资产折旧（折旧年限 + 残值率 + 增值税税率） -->
                  <template v-else-if="grp.category === 'depreciation'">
                    <el-table-column label="折旧年限（年）" min-width="120">
                      <template #default="{ row }">
                        <el-input-number
                          v-model="row.personalRateDisplay"
                          :min="1"
                          :max="50"
                          :precision="0"
                          :step="1"
                          size="small"
                          style="width: 100%"
                        />
                      </template>
                    </el-table-column>
                    <el-table-column label="残值率（%）" min-width="120">
                      <template #default="{ row }">
                        <el-input-number
                          v-model="row.employerRateDisplay"
                          :min="0"
                          :max="100"
                          :precision="2"
                          :step="0.5"
                          size="small"
                          style="width: 100%"
                        />
                      </template>
                    </el-table-column>
                    <el-table-column label="进项税率（%）" min-width="120">
                      <template #default="{ row }">
                        <el-input-number
                          v-model="row.fixedRateDisplay"
                          :min="0"
                          :max="100"
                          :precision="2"
                          :step="0.1"
                          size="small"
                          style="width: 100%"
                        />
                      </template>
                    </el-table-column>
                  </template>

                  <el-table-column label="说明" prop="description" min-width="240" />
                  <el-table-column label="启用" width="80" align="center">
                    <template #default="{ row }">
                      <el-switch v-model="row.enabledDisplay" :active-value="1" :inactive-value="0" size="small" />
                    </template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
            </el-tabs>

            <div class="rate-actions">
              <el-button type="primary" :loading="rateSaving" @click="saveRates">
                保存费率配置
              </el-button>
              <el-button @click="loadRates">重置</el-button>
            </div>
          </div>
        </el-tab-pane>

        <!-- ===================== 工资计算器 ===================== -->
        <el-tab-pane name="salaryCalc">
          <template #label>
            <el-icon><Coin /></el-icon>
            工资计算器
          </template>
          <div class="salary-calc">
            <div class="salary-tip">
              <el-icon color="#409eff"><InfoFilled /></el-icon>
              <span>
                输入工资信息，系统根据当前费率配置（社保、公积金、个税）计算精确金额，
                确认后可一键生成工资凭证（计提工资 → 计提社保 → 发放工资，共 3 张）。
              </span>
            </div>

            <el-form :model="salaryForm" label-width="130px" class="salary-form" inline>
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item label="工资所属期" required>
                    <el-date-picker
                      v-model="salaryForm.period"
                      type="month"
                      placeholder="选择月份"
                      value-format="YYYY-MM"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="应发工资（税前）" required>
                    <el-input-number
                      v-model="salaryForm.grossSalary"
                      :min="0"
                      :precision="2"
                      :step="100"
                      placeholder="0.00"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="社保公积金基数">
                    <el-input-number
                      v-model="salaryForm.socialBase"
                      :min="0"
                      :precision="2"
                      :step="100"
                      placeholder="默认=应发工资"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item label="员工类型">
                    <el-select v-model="salaryForm.employeeType" style="width: 100%">
                      <el-option label="普通员工" value="NORMAL" />
                      <el-option label="合同工" value="CONTRACT" />
                      <el-option label="博士后" value="POSTDOC" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="社保公积金">
                    <el-checkbox v-model="salaryForm.calculateSocial">计算社保</el-checkbox>
                    <el-checkbox v-model="salaryForm.calculateHousing">计算公积金</el-checkbox>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="凭证日期">
                    <el-date-picker
                      v-model="salaryForm.voucherDate"
                      type="date"
                      placeholder="默认当天"
                      value-format="YYYY-MM-DD"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
              </el-row>

              <!-- 专项附加扣除 -->
              <div class="deduction-section">
                <div class="deduction-title">专项附加扣除（每月）</div>
                <el-row :gutter="16">
                  <el-col :span="8">
                    <el-form-item label="子女教育">
                      <el-input-number v-model="salaryForm.childrenEduDeduction" :min="0" :precision="2" :step="100" placeholder="0" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="继续教育">
                      <el-input-number v-model="salaryForm.continuingEduDeduction" :min="0" :precision="2" :step="100" placeholder="0" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="大病医疗（年）">
                      <el-input-number v-model="salaryForm.seriousIllnessDeduction" :min="0" :precision="2" :step="1000" placeholder="0" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="住房贷款利息">
                      <el-input-number v-model="salaryForm.housingLoanDeduction" :min="0" :precision="2" :step="100" placeholder="0" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="住房租金">
                      <el-input-number v-model="salaryForm.housingRentDeduction" :min="0" :precision="2" :step="100" placeholder="0" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="赡养老人">
                      <el-input-number v-model="salaryForm.elderlyCareDeduction" :min="0" :precision="2" :step="100" placeholder="0" style="width:100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
              </div>

              <el-form-item>
                <el-button type="primary" :loading="salaryCalcLoading" @click="previewSalary">
                  预览计算结果
                </el-button>
                <el-button
                  v-if="salaryResult"
                  type="success"
                  :loading="salaryVoucherLoading"
                  @click="createSalaryVoucher"
                >
                  生成工资凭证
                </el-button>
              </el-form-item>
            </el-form>

            <!-- 计算结果展示 -->
            <div v-if="salaryResult" class="salary-result">
              <el-divider content-position="left">
                <strong>计算结果 — {{ salaryResult.period }}月</strong>
              </el-divider>

              <!-- 汇总卡片 -->
              <el-row :gutter="12" class="summary-cards">
                <el-col :span="6">
                  <div class="summary-card primary">
                    <div class="sc-label">应发工资</div>
                    <div class="sc-value">{{ fmt(salaryResult.grossSalary) }}</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="summary-card">
                    <div class="sc-label">实发工资</div>
                    <div class="sc-value success">{{ fmt(salaryResult.netSalary) }}</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="summary-card">
                    <div class="sc-label">代扣个税</div>
                    <div class="sc-value warning">{{ fmt(salaryResult.personalIncomeTax) }}</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="summary-card">
                    <div class="sc-label">单位社保公积金</div>
                    <div class="sc-value">{{ fmt(salaryResult.employerSocialTotal) }}</div>
                  </div>
                </el-col>
              </el-row>

              <!-- 明细表格 -->
              <el-table :data="salaryResult.items.filter(i => i.amount !== 0)" border stripe size="small">
                <el-table-column label="类型" width="100">
                  <template #default="{ row }">
                    <el-tag v-if="row.type === 'GROSS'" type="info">应发</el-tag>
                    <el-tag v-else-if="row.type === 'NET'" type="success">实发</el-tag>
                    <el-tag v-else-if="row.type === 'PERSONAL_SOCIAL' || row.type === 'PERSONAL_HOUSING'" type="warning">个人社保</el-tag>
                    <el-tag v-else-if="row.type === 'IIT'" type="danger">个税</el-tag>
                    <el-tag v-else type="info">{{ row.type }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="项目" prop="name" min-width="160" />
                <el-table-column label="金额（元）" align="right" min-width="120">
                  <template #default="{ row }">
                    <span :class="row.type === 'GROSS' ? 'amount-gross' : row.type === 'NET' ? 'amount-net' : ''">
                      {{ row.amount != null ? row.amount.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) : '-' }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="计算公式" prop="formula" min-width="240" />
              </el-table>

              <p v-if="salaryResult.remark" class="salary-remark">
                <el-icon><InfoFilled /></el-icon>
                {{ salaryResult.remark }}
              </p>
            </div>

            <!-- 凭证生成结果 -->
            <div v-if="salaryVoucherIds && salaryVoucherIds.length" class="voucher-result">
              <el-alert type="success" :closable="false" show-icon>
                <template #title>
                  凭证生成成功！共 {{ salaryVoucherIds.length }} 张
                  <span class="voucher-ids">凭证ID：{{ salaryVoucherIds.join('、') }}</span>
                </template>
              </el-alert>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="系统初始化" name="reset">
          <div class="danger-zone">
            <div class="danger-header">
              <el-icon size="20" color="#f56c6c"><Warning /></el-icon>
              <span>危险区域 — 不可逆操作</span>
            </div>
            <p class="danger-desc">
              点击下方按钮将清除系统中的<strong>所有业务数据</strong>，包括：</p>
            <ul class="danger-list">
              <li>所有凭证及分录</li>
              <li>所有科目余额记录</li>
              <li>所有期间结账记录</li>
              <li>所有AI做账聊天记录</li>
            </ul>
            <p class="danger-desc">科目字典（会计科目表）将被保留，不会被清除。</p>
            <el-divider />
            <el-form label-width="140px" style="max-width: 500px">
              <el-form-item>
                <template #label>请输入 <b>初始化</b> 确认</template>
                <el-input v-model="resetConfirmText" placeholder="请输入：初始化" />
              </el-form-item>
              <el-form-item>
                <el-button type="danger" :disabled="resetConfirmText !== '初始化'" :loading="resetLoading" @click="doReset">
                  确认清空全部数据
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Warning, InfoFilled, Coin } from '@element-plus/icons-vue'
import { rateApi, type RateConfig } from '@/api/rate'
import { salaryApi, type SalaryComputeRequest, type SalaryComputeResponse } from '@/api/salary'

const activeTab = ref('tenant')

// ===================== 工资计算器 =====================
const salaryForm = reactive<SalaryComputeRequest>({
  period: new Date().toISOString().slice(0, 7),  // 当前年月 yyyy-MM
  grossSalary: 30000,
  socialBase: undefined,
  calculateSocial: true,
  calculateHousing: true,
  employeeType: 'NORMAL',
  childrenEduDeduction: undefined,
  continuingEduDeduction: undefined,
  seriousIllnessDeduction: undefined,
  housingLoanDeduction: undefined,
  housingRentDeduction: undefined,
  elderlyCareDeduction: undefined,
  voucherDate: undefined,
})

const salaryCalcLoading = ref(false)
const salaryVoucherLoading = ref(false)
const salaryResult = ref<SalaryComputeResponse | null>(null)
const salaryVoucherIds = ref<number[]>([])

function fmt(n: number): string {
  if (n == null) return '-'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function previewSalary() {
  if (!salaryForm.period) { ElMessage.warning('请选择工资所属期'); return }
  if (!salaryForm.grossSalary || salaryForm.grossSalary <= 0) { ElMessage.warning('请输入正确的应发工资金额'); return }
  salaryCalcLoading.value = true
  salaryResult.value = null
  salaryVoucherIds.value = []
  try {
    const res: any = await salaryApi.compute(salaryForm as SalaryComputeRequest)
    salaryResult.value = res.data || null
  } catch (e: any) {
    ElMessage.error(e?.message || '计算失败')
  } finally {
    salaryCalcLoading.value = false
  }
}

async function createSalaryVoucher() {
  if (!salaryResult.value) return
  salaryVoucherLoading.value = true
  salaryVoucherIds.value = []
  try {
    const res: any = await salaryApi.computeAndCreateVoucher(salaryForm as SalaryComputeRequest)
    salaryVoucherIds.value = res.data || []
    if (salaryVoucherIds.value.length > 0) {
      ElMessage.success(`已生成 ${salaryVoucherIds.value.length} 张工资凭证`)
    } else {
      ElMessage.warning('未生成任何凭证')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '生成凭证失败')
  } finally {
    salaryVoucherLoading.value = false
  }
}
const tenantInfo = reactive({ companyName: '', taxpayerType: 'SMALL_SCALE', industry: 'TRADING' })
const period = reactive({ year: new Date().getFullYear(), month: new Date().getMonth() + 1 })
const aiConfig = reactive({ provider: 'openai', apiKey: '', model: 'gpt-4o', temperature: 0.3 })

const resetConfirmText = ref('')
const resetLoading = ref(false)

// ===================== 费率设置 =====================
const rateActiveCategory = ref('social')
const rateAllItems = ref<RateConfig[]>([])
const rateSaving = ref(false)

const rateGroups = [
  { category: 'social',      label: '社保',             icon: 'Lock' },
  { category: 'housing',     label: '住房公积金',          icon: 'House' },
  { category: 'vat',         label: '增值税',             icon: 'Money' },
  { category: 'depreciation', label: '固定资产折旧',       icon: 'Box' },
  { category: 'corp_tax',    label: '企业所得税',          icon: 'TrendCharts' },
]

// 用于表格展示的本地副本（百分比显示，原始数据是小数）
interface RateDisplayItem extends RateConfig {
  personalRateDisplay: number
  employerRateDisplay: number
  fixedRateDisplay: number
  enabledDisplay: number
}

const displayCache = ref<Record<string, RateDisplayItem[]>>({})

function toPercent(val?: number): number {
  if (val == null) return 0
  return parseFloat((val * 100).toFixed(4))
}
function fromPercent(val?: number): number {
  if (val == null) return 0
  return parseFloat((val / 100).toFixed(6))
}

function getDisplayItems(category: string): RateDisplayItem[] {
  if (!displayCache.value[category]) {
    const raw = rateAllItems.value.filter(i => i.category === category)
    displayCache.value[category] = raw.map(item => ({
      ...item,
      // 折旧配置：personal_rate 存的是年份（不是百分比），直接用原值
      personalRateDisplay: category === 'depreciation'
        ? (item.personalRate ?? 0)
        : toPercent(item.personalRate),
      employerRateDisplay: toPercent(item.employerRate),
      fixedRateDisplay:    toPercent(item.fixedRate),
      enabledDisplay: item.enabled ?? 1,
    }))
  }
  return displayCache.value[category]
}

const subCategoryLabels: Record<string, string> = {
  // 社保
  pension:       '养老保险',
  medical:       '医疗保险',
  unemployment:  '失业保险',
  work_injury:   '工伤保险',
  maternity:     '生育保险',
  disability:    '残疾人就业保障金',
  // 住房公积金
  housing_fund:  '住房公积金',
  // 增值税
  general_13: '一般纳税人 13%',
  general_9:  '一般纳税人 9%',
  general_6:  '一般纳税人 6%',
  small_3:   '小规模纳税人 3%',
  small_5:   '小规模纳税人 5%',
  // 固定资产折旧
  building:   '房屋建筑物',
  structure:  '构筑物',
  machinery:  '通用设备',
  vehicle:    '运输设备',
  electronic: '电子设备',
  furniture:  '办公家具',
  // 企业所得税
  standard_25:     '标准税率25%',
  small_profit_20: '小型微利企业20%',
  high_tech_15:    '高新技术企业15%',
  software_15:     '软件企业15%',
  atech_15:        '技术先进型服务企业15%',
  ic_wide_20:      '集成电路生产企业(宽)20%',
  ic_narrow_15:     '集成电路生产企业(窄)15%',
}

function subCategoryLabel(sub?: string): string {
  return sub ? (subCategoryLabels[sub] ?? sub) : ''
}

async function loadRates() {
  try {
    const res: any = await rateApi.list()
    rateAllItems.value = res.data || []
    // 刷新缓存
    displayCache.value = {}
    getDisplayItems('social')
    getDisplayItems('housing')
    getDisplayItems('vat')
    getDisplayItems('depreciation')
    getDisplayItems('corp_tax')
    rateActiveCategory.value = 'social'
  } catch (e: any) {
    ElMessage.error(e?.message || '加载费率失败')
  }
}

async function saveRates() {
  // 从 displayCache 收集所有修改过的分类
  const allItems: Partial<RateConfig>[] = []
  for (const cat of Object.keys(displayCache.value)) {
    const items = displayCache.value[cat]
    for (const item of items) {
      allItems.push({
        id: item.id,
        category: item.category,
        subCategory: item.subCategory,
        // 折旧配置：personal_rate 存的是年份（不是百分比），无需 fromPercent 转换
        personalRate: item.category === 'depreciation'
          ? item.personalRateDisplay
          : fromPercent(item.personalRateDisplay),
        employerRate: fromPercent(item.employerRateDisplay),
        fixedRate: item.unit === 'FIXED' ? fromPercent(item.fixedRateDisplay) : item.fixedRate,
        unit: item.unit,
        description: item.description,
        enabled: item.enabledDisplay,
      })
    }
  }
  rateSaving.value = true
  try {
    await rateApi.save(allItems)
    ElMessage.success('费率配置已保存')
    await loadRates()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    rateSaving.value = false
  }
}

onMounted(() => {
  loadRates()
})

// ===================== 其他设置 =====================
function saveTenant() { ElMessage.info('设置持久化开发中，当前为演示界面') }
function savePeriod() { ElMessage.info('设置持久化开发中，当前为演示界面') }
function saveAi() { ElMessage.info('设置持久化开发中，当前为演示界面') }
function testAi() { ElMessage.info('AI连接测试开发中') }

async function doReset() {
  if (resetConfirmText.value !== '初始化') return
  try {
    await ElMessageBox.confirm(
      '此操作将永久删除所有凭证、分录、科目余额、结账记录及AI聊天历史，科目字典将保留。是否继续？',
      '警告：此操作不可逆',
      { confirmButtonText: '确认清空', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  resetLoading.value = true
  try {
    const res = await fetch('/api/system/reset', { method: 'POST' })
    const data = await res.json()
    if (data.success) {
      localStorage.removeItem('ledgerpilot_nlp_conversations')
      localStorage.removeItem('ledgerpilot_nlp_current_id')
      ElMessage.info('设置持久化开发中，当前为演示界面')
      resetConfirmText.value = ''
      activeTab.value = 'tenant'
    } else {
      ElMessage.error(data.msg || '初始化失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '初始化失败')
  } finally {
    resetLoading.value = false
  }
}
</script>

<style scoped>
.settings-view { padding: 20px; }
.danger-zone { max-width: 600px; }
.danger-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: bold;
  color: #f56c6c;
  margin-bottom: 12px;
}
.danger-desc { color: #606266; line-height: 1.6; margin-bottom: 8px; }
.danger-list {
  color: #606266;
  margin: 0 0 8px 20px;
  padding-left: 0;
}
.danger-list li { margin-bottom: 4px; }

/* 费率设置 */
.rate-config { padding: 0 4px; }
.rate-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background: #f0f9ff;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #1e40af;
  line-height: 1.5;
}
.rate-tip .el-icon { margin-top: 2px; flex-shrink: 0; }
.rate-sub-tabs { margin-bottom: 16px; }
.rate-table { margin-top: 12px; }
.rate-actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}

/* 工资计算器 */
.salary-calc {
  padding: 0 4px;
  max-width: 1100px;
}
.salary-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  background: #f0f9ff;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #1e40af;
  line-height: 1.5;
}
.salary-tip .el-icon { margin-top: 2px; flex-shrink: 0; }
.salary-form { margin-top: 12px; }
.deduction-section {
  background: #fafafa;
  border: 1px solid #eee;
  border-radius: 6px;
  padding: 12px 12px 4px;
  margin-bottom: 4px;
}
.deduction-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}
.salary-result { margin-top: 16px; }
.summary-cards { margin-bottom: 12px; }
.summary-card {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px 16px;
  text-align: center;
}
.summary-card.primary { background: #ecf5ff; border-color: #b3d8fd; }
.summary-card .sc-label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.summary-card .sc-value { font-size: 20px; font-weight: bold; color: #303133; }
.summary-card .sc-value.success { color: #67c23a; }
.summary-card .sc-value.warning { color: #e6a23c; }
.amount-gross { font-weight: bold; color: #303133; }
.amount-net { font-weight: bold; color: #67c23a; }
.salary-remark {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.voucher-result { margin-top: 16px; }
.voucher-ids { font-size: 12px; color: #606266; margin-left: 8px; }
</style>
