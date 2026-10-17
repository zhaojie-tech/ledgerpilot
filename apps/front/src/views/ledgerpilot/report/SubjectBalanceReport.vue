<template>
  <div class="subject-balance-report">
    <div class="report-header">
      <h2>科 目 余 额 表</h2>
      <p class="report-subtitle">{{ data?.year }}年第{{ data?.period }}期</p>
      <p class="report-note">单位：元</p>
    </div>

    <div v-if="data" class="report-body">
      <!-- 汇总行 -->
      <div class="summary-bar">
        <span>共 <strong>{{ data.rows?.length || 0 }}</strong> 个科目</span>
        <span>借贷方发生合计：借 <strong>{{ fmt(data.totalDebit) }}</strong> / 贷 <strong>{{ fmt(data.totalCredit) }}</strong></span>
      </div>

      <el-table
        :data="data.rows"
        border
        stripe
        size="small"
        :header-cell-style="{ background: '#f0f0f0', color: '#303133', fontWeight: '600' }"
        class="balance-table"
      >
        <el-table-column prop="subjectCode" label="科目编码" width="110" fixed />
        <el-table-column prop="subjectName" label="科目名称" min-width="160" />
        <el-table-column prop="subjectType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.subjectType)">{{ typeName(row.subjectType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="direction" label="方向" width="70" align="center">
          <template #default="{ row }">{{ row.direction === 'DEBIT' ? '借' : '贷' }}</template>
        </el-table-column>
        <el-table-column label="期初余额" align="center">
          <el-table-column label="借方" width="120" align="right">
            <template #default="{ row }">
              <span :class="{ muted: !row.beginDebit }">{{ fmt(row.beginDebit) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="贷方" width="120" align="right">
            <template #default="{ row }">
              <span :class="{ muted: !row.beginCredit }">{{ fmt(row.beginCredit) }}</span>
            </template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="本期发生" align="center">
          <el-table-column label="借方" width="120" align="right">
            <template #default="{ row }">
              <span :class="{ muted: !row.currentDebit }">{{ fmt(row.currentDebit) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="贷方" width="120" align="right">
            <template #default="{ row }">
              <span :class="{ muted: !row.currentCredit }">{{ fmt(row.currentCredit) }}</span>
            </template>
          </el-table-column>
        </el-table-column>
        <el-table-column label="期末余额" align="center">
          <el-table-column label="借方" width="120" align="right">
            <template #default="{ row }">
              <span :class="{ muted: !row.endDebit, 'end-zero': row.endDebit }">{{ fmt(row.endDebit) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="贷方" width="120" align="right">
            <template #default="{ row }">
              <span :class="{ muted: !row.endCredit }">{{ fmt(row.endCredit) }}</span>
            </template>
          </el-table-column>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="!data" class="no-data">请选择期间并点击「查询」</div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ data: any }>()

const fmt = (val: number | null | undefined) => {
  if (val == null || val === 0) return '—'
  return Math.abs(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const typeName = (t: string) => ({
  ASSET: '资产', LIABILITY: '负债', EQUITY: '权益', COST: '成本', PROFIT_LOSS: '损益'
}[t] || t)

const typeTag = (t: string) => ({
  ASSET: '', LIABILITY: 'warning', EQUITY: 'success', COST: 'info', PROFIT_LOSS: 'danger'
}[t] || '')
</script>

<style scoped>
.subject-balance-report { padding: 24px 20px; }
.report-header { text-align: center; margin-bottom: 16px; }
.report-header h2 { font-size: 20px; font-weight: 700; margin: 0 0 6px; letter-spacing: 4px; }
.report-subtitle { font-size: 14px; color: #606266; margin: 0 0 2px; }
.report-note { font-size: 11px; color: #909399; margin: 0; }

.summary-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 14px; background: #f5f7fa; border-radius: 6px;
  font-size: 13px; color: #606266; margin-bottom: 12px;
}

.balance-table { font-size: 13px; }
.muted { color: #c0c4cc; }
.end-zero { color: #67c23a; }

.no-data { text-align: center; color: #909399; padding: 40px; }

@media (max-width: 768px) {
  .subject-balance-report { padding: 12px; }
  .report-header h2 { font-size: 15px; margin: 8px 0 4px; }
  .report-table { min-width: 0; width: 100%; font-size: 12px; }
  .report-table th, .report-table td { padding: 5px 6px !important; white-space: nowrap; }
  .report-header .report-subtitle,
  .report-header .report-note { font-size: 11px; }
}
</style>
