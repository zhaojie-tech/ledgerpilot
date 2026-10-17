<template>
  <div class="tax-center">
    <!-- 工具栏 -->
    <div class="report-toolbar">
      <div class="toolbar-left">
        <span class="page-title">税务中心</span>
        <el-radio-group v-model="activeTax" size="default" @change="onTaxSelect">
          <el-radio-button value="vat">增值税</el-radio-button>
          <el-radio-button value="corporate-income-tax">企业所得税</el-radio-button>
          <el-radio-button value="surtax">税金及附加</el-radio-button>
        </el-radio-group>
      </div>
      <div class="toolbar-right">
        <el-date-picker
          v-model="selectedPeriod"
          type="month"
          format="yyyy年第M期"
          value-format="YYYY-M"
          placeholder="选择会计期间"
          :clearable="false"
          size="default"
          style="width: 150px"
          @change="onPeriodChange"
        />
        <el-button type="primary" :loading="loading" @click="loadTaxData">
          <el-icon><Refresh /></el-icon> 查询
        </el-button>
        <el-button @click="exportTaxReport">
          <el-icon><Download /></el-icon> 导出
        </el-button>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-wrap">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>正在计算{{ taxTypeLabel }}...</span>
    </div>

    <!-- 税务数据 -->
    <div v-else-if="taxData" class="tax-detail">
      <!-- 汇总卡片 -->
      <div class="tax-summary-cards">
        <div class="summary-card primary">
          <div class="card-label">应纳税额</div>
          <div class="card-amount">¥ {{ formatMoney(taxData.taxAmount) }}</div>
          <div class="card-sub">
            <span>税率 {{ taxData.taxRate }}%</span>
            <span>{{ taxData.period }}</span>
          </div>
        </div>
        <div class="summary-card">
          <div class="card-label">计税基础</div>
          <div class="card-amount secondary">¥ {{ formatMoney(taxData.taxBase) }}</div>
        </div>
        <div class="summary-card">
          <div class="card-label">已交税额</div>
          <div class="card-amount secondary">¥ {{ formatMoney(taxData.taxPaid) }}</div>
        </div>
        <div class="summary-card">
          <div class="card-label">欠交税额</div>
          <div class="card-amount" :class="{ unpaid: Number(taxData.taxUnpaid) > 0 }">
            ¥ {{ formatMoney(taxData.taxUnpaid) }}
          </div>
        </div>
      </div>

      <!-- 计算明细 -->
      <div class="tax-table-wrap">
        <div class="table-title">{{ taxTypeLabel }}计算明细</div>
        <el-table :data="taxData.items" border stripe>
          <el-table-column prop="itemName" label="项目" min-width="200" />
          <el-table-column prop="amount" label="金额（元）" width="160" align="right">
            <template #default="{ row }">
              <span :class="{ 'amount-negative': Number(row.amount) < 0 }">
                {{ formatMoney(row.amount) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="计算说明" min-width="260" />
        </el-table>
      </div>

      <!-- 申报提醒 -->
      <div class="tax-action">
        <el-alert :type="Number(taxData.taxUnpaid) > 0 ? 'warning' : 'success'" :closable="false" show-icon>
          <template #title>
            <span v-if="Number(taxData.taxUnpaid) > 0">
              本期应申报缴纳 <strong>¥ {{ formatMoney(taxData.taxUnpaid) }}</strong>，
              请在税务申报截止日前完成申报缴纳（增值税：每月15日前；企业所得税：季度结束后15日内）。
            </span>
            <span v-else>
              本期应纳税额为 0 或已缴清，无需申报。
            </span>
          </template>
        </el-alert>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty-wrap">
      <el-empty description="请选择税种和期间，查看税务数据">
        <template #image>
          <el-icon :size="60" color="#c0c4cc"><TrendCharts /></el-icon>
        </template>
      </el-empty>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { TrendCharts, Download, Refresh, Loading } from '@element-plus/icons-vue'
import { getVAT, getCorporateIncomeTax, getSurtax } from '@/api/tax'
import type { TaxVO } from '@/api/tax'

const activeTax = ref('vat')
const selectedPeriod = ref('2026-3')
const loading = ref(false)
const taxData = ref<TaxVO | null>(null)

const taxTypeLabel = computed(() => {
  const map: Record<string, string> = {
    vat: '增值税',
    'corporate-income-tax': '企业所得税',
    surtax: '税金及附加',
  }
  return map[activeTax.value] || ''
})

function formatMoney(val: number | string): string {
  const num = typeof val === 'string' ? parseFloat(val) : val
  if (isNaN(num)) return '0.00'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadTaxData() {
  if (!selectedPeriod.value) return
  const [year, period] = selectedPeriod.value.split('-').map(Number)
  loading.value = true
  taxData.value = null
  try {
    let res: any = null
    if (activeTax.value === 'vat') {
      res = await getVAT(year, period)
    } else if (activeTax.value === 'corporate-income-tax') {
      res = await getCorporateIncomeTax(year, period)
    } else {
      res = await getSurtax(year, period)
    }
    if (res.code === 0) {
      taxData.value = res.data
    } else {
      ElMessage.error(res.msg || '加载失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '网络错误')
  } finally {
    loading.value = false
  }
}

function onTaxSelect() {
  loadTaxData()
}

function onPeriodChange() {
  loadTaxData()
}

function exportTaxReport() {
  if (!taxData.value) return
  const rows = [
    [`${taxTypeLabel.value}计算表`, ''],
    ['期间', taxData.value.period],
    ['税种', taxData.value.taxTypeName],
    ['税率', `${taxData.value.taxRate}%`],
    ['计税基础', `¥ ${formatMoney(taxData.value.taxBase)}`],
    ['应纳税额', `¥ ${formatMoney(taxData.value.taxAmount)}`],
    ['已交税额', `¥ ${formatMoney(taxData.value.taxPaid)}`],
    ['欠交税额', `¥ ${formatMoney(taxData.value.taxUnpaid)}`],
    [],
    ['明细项目', ''],
    ...taxData.value.items.map((item: any) => [item.itemName, `¥ ${formatMoney(item.amount)}`, item.remark || '']),
  ]
  const csv = rows.map(r => r.join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${taxTypeLabel.value}_${selectedPeriod.value}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
}

onMounted(loadTaxData)
</script>

<style scoped>
.tax-center {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f7fa;
}

.report-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 12px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.loading-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: center;
  padding: 60px;
  color: #909399;
  font-size: 14px;
}

/* 汇总卡片 */
.tax-summary-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding: 20px 20px 0;
}

.summary-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  border: 1px solid #ebeef5;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.summary-card.primary {
  background: linear-gradient(135deg, #409eff 0%, #337ecc 100%);
  border: none;
  color: #fff;
}

.summary-card.primary .card-label {
  color: rgba(255, 255, 255, 0.85);
}

.summary-card.primary .card-sub {
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
}

.card-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.card-amount {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  font-family: 'PingFang SC', 'Microsoft YaHei', monospace;
}

.card-amount.secondary { color: #606266; }
.card-amount.unpaid { color: #f56c6c; }

/* 明细表 */
.tax-table-wrap {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin: 16px 20px;
  border: 1px solid #ebeef5;
}

.table-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 2px solid #409eff;
  display: inline-block;
}

.amount-negative { color: #67c23a; }

/* 操作区 */
.tax-action {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  margin: 0 20px 20px;
  border: 1px solid #ebeef5;
}

.empty-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}
</style>
