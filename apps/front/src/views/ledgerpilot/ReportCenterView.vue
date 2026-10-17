<template>
  <div class="report-center">
    <!-- 头部工具栏 -->
    <div class="report-toolbar">
      <div class="toolbar-left">
        <span class="page-title">报表中心</span>
      </div>
      <div class="toolbar-right">
        <el-date-picker
          v-model="queryPeriod"
          type="month"
          format="YYYY年第MM期"
          value-format="YYYY-MM"
          placeholder="选择会计期间"
          :clearable="false"
          @change="onPeriodChange"
        />
        <el-button type="primary" :loading="loading" @click="loadReport">
          <el-icon><Refresh /></el-icon> 查询
        </el-button>
        <el-button @click="handleExport">
          <el-icon><Download /></el-icon> 导出Excel
        </el-button>
      </div>
    </div>

    <!-- 提示信息 -->
    <div v-if="!hasData && !loading && reportType === 'balance'" class="empty-tip">
      <el-icon size="32"><Warning /></el-icon>
      <p>当前期间暂无余额数据</p>
      <p class="tip-sub">请先在「凭证管理」录入凭证并审核过账，系统将自动汇总生成余额数据</p>
    </div>

    <!-- 报表内容 -->
    <div v-loading="loading" class="report-content">
      <!-- 报表Tab -->
      <el-tabs v-model="reportType" class="report-tabs">
        <el-tab-pane label="利润表" name="profit">
          <ProfitStatement :data="profitData" />
        </el-tab-pane>
        <el-tab-pane label="资产负债表" name="balance">
          <BalanceSheet :data="balanceData" />
        </el-tab-pane>
        <el-tab-pane label="现金流量表" name="cashflow">
          <CashFlowStatement :data="cashflowData" />
        </el-tab-pane>
        <el-tab-pane label="科目余额表" name="subject">
          <SubjectBalanceReport :data="subjectData" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh, Warning } from '@element-plus/icons-vue'
import {
  getProfitStatement,
  getBalanceSheet,
  getCashFlowStatement,
  getSubjectBalanceReport
} from '@/api/report'
import ProfitStatement from './report/ProfitStatement.vue'
import BalanceSheet from './report/BalanceSheet.vue'
import CashFlowStatement from './report/CashFlowStatement.vue'
import SubjectBalanceReport from './report/SubjectBalanceReport.vue'

const queryPeriod = ref('')
const reportType = ref('profit')
const loading = ref(false)
const hasData = ref(false)

// 各报表数据
const profitData = ref<any>(null)
const balanceData = ref<any>(null)
const cashflowData = ref<any>(null)
const subjectData = ref<any>(null)

// 初始化期间为当前年月
const now = new Date()
queryPeriod.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`

const [year, month] = queryPeriod.value.split('-').map(Number)

const onPeriodChange = () => {
  loadReport()
}

const loadReport = async () => {
  if (!queryPeriod.value) return
  const [y, m] = queryPeriod.value.split('-').map(Number)
  loading.value = true
  hasData.value = false
  try {
    const req = { year: y, period: m }
    const [profit, balance, cashflow, subject] = await Promise.allSettled([
      getProfitStatement(y, m),
      getBalanceSheet(y, m),
      getCashFlowStatement(y, m),
      getSubjectBalanceReport(y, m)
    ])

    if (profit.status === 'fulfilled') {
      profitData.value = profit.value.data
    }
    if (balance.status === 'fulfilled') {
      balanceData.value = balance.value.data
      hasData.value = true
    } else if (balance.reason?.message === 'NO_DATA') {
      balanceData.value = null
    }
    if (cashflow.status === 'fulfilled') {
      cashflowData.value = cashflow.value.data
    }
    if (subject.status === 'fulfilled') {
      subjectData.value = subject.value.data
    }
  } catch (e: any) {
    if (e?.message === 'NO_DATA') {
      ElMessage.warning(e?.detailMsg || '当前期间暂无数据')
    } else {
      ElMessage.error(e?.message || '加载报表失败')
    }
  } finally {
    loading.value = false
  }
}

const handleExport = () => {
  ElMessage.info('Excel导出功能开发中')
}

onMounted(() => {
  loadReport()
})
</script>

<script lang="ts">
export default { name: 'ReportCenterView' }
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

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.report-content {
  flex: 1;
  overflow: auto;
  padding: 16px 20px;
}

.report-tabs {
  background: #fff;
  border-radius: 8px;
  padding: 0 16px;
}

.empty-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  background: #fff;
  border-radius: 8px;
  color: #909399;
  text-align: center;
}

.empty-tip .el-icon {
  margin-bottom: 12px;
  color: #e6a23c;
}

.empty-tip p {
  margin: 4px 0;
}

.tip-sub {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
