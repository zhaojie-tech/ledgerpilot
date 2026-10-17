<template>
  <div class="report-center">
    <!-- 工具栏 -->
    <div class="report-toolbar">
      <div class="toolbar-left">
        <span class="page-title">报表中心</span>
        <el-date-picker
          v-if="isMobile"
          v-model="queryPeriod"
          type="month"
          format="YYYY年第MM期"
          value-format="YYYY-MM"
          placeholder="选择期间"
          :clearable="false"
          size="small"
          @change="onPeriodChange"
        />
      </div>
      <div class="toolbar-right">
        <el-date-picker
          v-if="!isMobile"
          v-model="queryPeriod"
          type="month"
          format="YYYY年第MM期"
          value-format="YYYY-MM"
          placeholder="选择会计期间"
          :clearable="false"
          @change="onPeriodChange"
        />
        <el-button type="primary" :loading="loading" :size="isMobile ? 'small' : 'default'" @click="loadReport">
          <el-icon><Refresh /></el-icon>{{ isMobile ? '' : ' 查询' }}
        </el-button>
        <el-button @click="handleExport" :size="isMobile ? 'small' : 'default'">
          <el-icon><Download /></el-icon>{{ isMobile ? '' : ' 导出' }}
        </el-button>
        <el-button type="warning" @click="openMonthEndDialog" :size="isMobile ? 'small' : 'default'">
          月结转
        </el-button>
      </div>
    </div>

    <!-- 月结转确认弹窗 -->
    <el-dialog v-model="monthEndVisible" title="月末结转" width="480px" :close-on-click-modal="false">
      <div v-if="monthEndStep === 'form'" class="month-end-form">
        <el-form label-width="100px">
          <el-form-item label="结转期间">
            <span class="period-label">{{ queryPeriod || '—' }} 期</span>
          </el-form-item>
          <el-form-item label="月结日期" required>
            <el-date-picker
              v-model="monthEndDate"
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              placeholder="选择月结日期"
              :disabled-date="(d: Date) => d > new Date()"
            />
          </el-form-item>
        </el-form>
        <div class="month-end-tip">
          <p>📋 结转内容：</p>
          <ul>
            <li>将当月所有已过账凭证的收入类科目结转至「本年利润」贷方</li>
            <li>将当月所有已过账凭证的成本费用类科目结转至「本年利润」借方</li>
            <li>结转后将自动生成一张「月结转账凭证」并过账</li>
            <li><b>月结后当期凭证将不能再修改，请确认后再操作</b></li>
          </ul>
        </div>
      </div>

      <div v-else-if="monthEndStep === 'result'" class="month-end-result">
        <div v-if="monthEndLoading" class="month-end-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <p>正在执行月末结转...</p>
        </div>
        <div v-else-if="monthEndError" class="month-end-error">
          <el-icon size="36" color="#f56c6c"><CircleClose /></el-icon>
          <p>{{ monthEndError }}</p>
        </div>
        <div v-else class="month-end-success">
          <el-icon size="48" color="#67c23a"><CircleCheck /></el-icon>
          <p>月末结转成功！</p>
          <p class="result-sub">结转凭证号：<b>{{ monthEndVoucherNo }}</b></p>
        </div>
      </div>

      <template #footer>
        <el-button @click="monthEndVisible = false" :disabled="monthEndLoading">
          {{ monthEndStep === 'result' ? '关闭' : '取消' }}
        </el-button>
        <el-button v-if="monthEndStep === 'form'" type="primary" :loading="monthEndLoading" @click="executeMonthEnd">
          确认月结转
        </el-button>
        <el-button v-else-if="monthEndStep === 'result' && !monthEndLoading && !monthEndError"
                   type="primary" @click="viewVoucher">
          查看凭证
        </el-button>
      </template>
    </el-dialog>

    <!-- 无数据提示 -->
    <div v-if="noData && !loading" class="empty-tip">
      <el-icon size="36"><Warning /></el-icon>
      <p>当前期间暂无余额数据</p>
      <p class="tip-sub">请先在「凭证管理」录入凭证，系统将自动汇总生成报表数据</p>
    </div>

    <!-- 报表内容 -->
    <div v-loading="loading" class="report-content">
      <el-tabs v-model="reportType" class="report-tabs" :class="{ 'is-mobile': isMobile }">
        <el-tab-pane label="利润表" name="profit">
          <div class="report-scroll-wrap" :class="{ 'is-mobile': isMobile }">
            <ProfitStatement v-if="reportType === 'profit'" :data="profitData" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="资产负债表" name="balance">
          <div class="report-scroll-wrap" :class="{ 'is-mobile': isMobile }">
            <BalanceSheet v-if="reportType === 'balance'" :data="balanceData" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="现金流量表" name="cashflow">
          <div class="report-scroll-wrap" :class="{ 'is-mobile': isMobile }">
            <CashFlowStatement v-if="reportType === 'cashflow'" :data="cashflowData" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="科目余额表" name="subject">
          <div class="report-scroll-wrap" :class="{ 'is-mobile': isMobile }">
            <SubjectBalanceReport v-if="reportType === 'subject'" :data="subjectData" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Download, Warning, Loading, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import {
  getProfitStatement,
  getBalanceSheet,
  getCashFlowStatement,
  getSubjectBalanceReport
} from '@/api/report'
import { monthEndTransfer } from '@/api/voucher'
import ProfitStatement from '@/views/ledgerpilot/report/ProfitStatement.vue'
import BalanceSheet from '@/views/ledgerpilot/report/BalanceSheet.vue'
import CashFlowStatement from '@/views/ledgerpilot/report/CashFlowStatement.vue'
import SubjectBalanceReport from '@/views/ledgerpilot/report/SubjectBalanceReport.vue'

const queryPeriod = ref('')
const reportType = ref('profit')
const loading = ref(false)
const noData = ref(false)
const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value <= 768)

function updateWidth() { windowWidth.value = window.innerWidth }

const profitData = ref<any>(null)
const balanceData = ref<any>(null)
const cashflowData = ref<any>(null)
const subjectData = ref<any>(null)

// 月末结转
const monthEndVisible = ref(false)
const monthEndStep = ref<'form' | 'result'>('form')
const monthEndLoading = ref(false)
const monthEndError = ref('')
const monthEndVoucherNo = ref('')
const monthEndVoucherId = ref<number | null>(null)
const monthEndDate = ref('')

function getLastDayOfMonth(year: number, month: number): string {
  const d = new Date(year, month, 0)
  return `${year}-${String(month).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function openMonthEndDialog() {
  if (!queryPeriod.value) {
    ElMessage.warning('请先选择结转期间')
    return
  }
  const [y, m] = queryPeriod.value.split('-').map(Number)
  monthEndDate.value = getLastDayOfMonth(y, m)
  monthEndStep.value = 'form'
  monthEndError.value = ''
  monthEndVoucherNo.value = ''
  monthEndVoucherId.value = null
  monthEndVisible.value = true
}

async function executeMonthEnd() {
  if (!monthEndDate.value) {
    ElMessage.warning('请选择月结日期')
    return
  }
  const [y, m] = queryPeriod.value.split('-').map(Number)
  monthEndStep.value = 'result'
  monthEndLoading.value = true
  monthEndError.value = ''
  try {
    const res = await monthEndTransfer(y, m, monthEndDate.value)
    monthEndVoucherId.value = res.data as number
    monthEndVoucherNo.value = ((res as unknown) as Record<string, unknown>).voucherNo as string
      || `结转凭证#${monthEndVoucherId.value}`
    ElMessage.success('月末结转成功')
  } catch (e: any) {
    monthEndError.value = (e?.data?.msg || e?.message || '月结转失败') as string
  } finally {
    monthEndLoading.value = false
  }
}

function viewVoucher() {
  if (monthEndVoucherId.value) {
    window.open(`/#/voucher/detail/${monthEndVoucherId.value}`, '_blank')
  }
  monthEndVisible.value = false
}

const now = new Date()
queryPeriod.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`

const onPeriodChange = () => loadReport()

const loadReport = async () => {
  if (!queryPeriod.value) return
  const [y, m] = queryPeriod.value.split('-').map(Number)
  loading.value = true
  noData.value = false
  try {
    const [p1, p2, p3, p4] = await Promise.allSettled([
      getProfitStatement(y, m),
      getBalanceSheet(y, m),
      getCashFlowStatement(y, m),
      getSubjectBalanceReport(y, m)
    ])

    if (p1.status === 'fulfilled') profitData.value = p1.value.data
    if (p2.status === 'fulfilled') { balanceData.value = p2.value.data; noData.value = false }
    else if (p2.status === 'rejected') {
      const msg = p2.reason?.message || p2.reason?.msg || ''
      if (msg.includes('NO_DATA')) {
        balanceData.value = null
        noData.value = true
      }
    }
    if (p3.status === 'fulfilled') cashflowData.value = p3.value.data
    if (p4.status === 'fulfilled') subjectData.value = p4.value.data
    else if (p4.status === 'rejected') {
      const msg = p4.reason?.message || p4.reason?.msg || ''
      if (msg.includes('NO_DATA')) subjectData.value = null
    }
  } catch (e: any) {
    const msg = e?.message || ''
    if (msg.includes('NO_DATA')) {
      noData.value = true
    } else {
      ElMessage.error(e?.message || '加载报表失败')
    }
  } finally {
    loading.value = false
  }
}

const handleExport = () => ElMessage.info('Excel导出功能开发中')

onMounted(() => {
  window.addEventListener('resize', updateWidth)
  loadReport()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateWidth)
})
</script>

<style scoped>
.report-center {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f7fa;
}

.report-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
}

.toolbar-left { display: flex; align-items: center; }
.page-title { font-size: 16px; font-weight: 600; color: #303133; }

.toolbar-right { display: flex; align-items: center; gap: 10px; }

.empty-tip {
  display: flex; flex-direction: column; align-items: center;
  justify-content: center; padding: 48px 20px;
  background: #fff; border-radius: 8px; color: #909399;
  text-align: center; margin: 16px;
}
.empty-tip .el-icon { margin-bottom: 10px; color: #e6a23c; }
.tip-sub { font-size: 12px; color: #c0c4cc; margin-top: 4px; }

.report-content { flex: 1; overflow: auto; padding: 16px 20px; }

.report-tabs {
  background: #fff;
  border-radius: 8px;
  padding: 0 16px;
}

/* 月末结转弹窗 */
.period-label { font-size: 15px; font-weight: 600; color: #409eff; }

.month-end-tip {
  background: #fdf6ec;
  border: 1px solid #f5dab1;
  border-radius: 6px;
  padding: 12px 16px;
  margin-top: 8px;
  font-size: 13px;
  color: #e6a23c;
}
.month-end-tip p { margin: 0 0 6px; font-weight: 600; }
.month-end-tip ul { margin: 0; padding-left: 18px; }
.month-end-tip li { margin-bottom: 4px; line-height: 1.6; }
.month-end-tip li b { color: #f56c6c; }

.month-end-loading, .month-end-error, .month-end-success {
  display: flex; flex-direction: column; align-items: center;
  padding: 24px; gap: 10px; text-align: center;
}
.month-end-loading { color: #409eff; font-size: 15px; }
.month-end-error { color: #f56c6c; font-size: 15px; }
.month-end-success { color: #67c23a; font-size: 15px; }
.result-sub { font-size: 13px; color: #606266; margin: 0; }

/* ─── 移动端响应式 ─── */
@media (max-width: 768px) {
  .report-toolbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
    padding: 10px 12px;
  }

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    width: 100%;
  }

  .toolbar-right {
    flex-wrap: wrap;
    gap: 8px;
    width: 100%;
  }

  .toolbar-right .el-button,
  .toolbar-right .el-date-picker {
    flex: 1 1 auto;
    min-width: 0;
  }

  .report-content { padding: 0; }

  /* 标签栏：横向滚动 + 滑动吸附 */
  .report-tabs {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    scroll-snap-type: x mandatory;
  }

  .report-tabs :deep(.el-tabs__header) {
    min-width: max-content;
    scroll-snap-align: start;
  }

  .report-tabs :deep(.el-tabs__item) {
    font-size: 13px !important;
    padding: 0 14px !important;
    height: 42px !important;
    line-height: 42px !important;
  }

  /* 报表内容区：允许横向滚动 */
  .report-scroll-wrap {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  /* 报表表头：紧凑 */
  .report-scroll-wrap .report-header h2 {
    font-size: 15px;
    margin: 8px 0 4px;
  }

  .report-scroll-wrap .report-header .report-subtitle,
  .report-scroll-wrap .report-header .report-note {
    font-size: 11px;
  }

  /* 报表表格：全宽滚动 */
  .report-scroll-wrap .report-table {
    min-width: 400px;
    font-size: 12px;
  }

  .report-scroll-wrap .report-table th,
  .report-scroll-wrap .report-table td {
    padding: 6px 8px !important;
    white-space: nowrap;
  }

  /* 资产负债表：两栏垂直堆叠 */
  .report-scroll-wrap .balance-grid {
    flex-direction: column !important;
  }

  .report-scroll-wrap .balance-section {
    min-width: 0;
    width: 100%;
  }
}
</style>
