<template>
  <div class="balance-sheet">
    <!-- 表头 -->
    <div class="report-header">
      <h2>资 产 负 债 表</h2>
      <p class="report-subtitle">{{ data?.year }}年{{ data?.period }}月　{{ data?.period }}期</p>
      <p class="report-note">会小企01表　编制单位：XXX公司　单位：元</p>
    </div>

    <div v-if="data" class="balance-container">
      <!-- 平衡提示 -->
      <div v-if="data.balanced" class="balance-ok">
        <el-icon><CircleCheck /></el-icon> 资产负债表平衡（资产 = 负债 + 所有者权益）
      </div>
      <div v-else class="balance-warn">
        <el-icon><Warning /></el-icon> 资产负债表不平衡，请检查凭证数据
      </div>

      <div class="balance-grid">
        <!-- 资产 -->
        <div class="balance-section">
          <table class="report-table">
            <thead>
              <tr><th colspan="2" class="section-title">资　产</th></tr>
              <tr><th class="col-name">项　目</th><th class="col-amount">期末余额</th></tr>
            </thead>
            <tbody>
              <tr class="group-row"><td colspan="2"><b>流动资产</b></td></tr>
              <tr><td class="item-name">　货币资金</td><td class="text-right num">{{ fmt(data.monetaryFunds) }}</td></tr>
              <tr><td class="item-name">　应收票据</td><td class="text-right num muted">—</td></tr>
              <tr><td class="item-name">　应收账款</td><td class="text-right num">{{ fmt(data.accountsReceivable) }}</td></tr>
              <tr><td class="item-name">　预付款项</td><td class="text-right num muted">—</td></tr>
              <tr><td class="item-name">　其他应收款</td><td class="text-right num muted">—</td></tr>
              <tr><td class="item-name">　存　货</td><td class="text-right num">{{ fmt(data.inventory) }}</td></tr>
              <tr><td class="item-name">　其他流动资产</td><td class="text-right num muted">—</td></tr>
              <tr class="sum-row"><td><strong>流动资产合计</strong></td><td class="text-right num"><strong>{{ fmt(data.currentAssets) }}</strong></td></tr>

              <tr class="group-row"><td colspan="2"><b>非流动资产</b></td></tr>
              <tr><td class="item-name">　固定资产</td><td class="text-right num muted">—</td></tr>
              <tr><td class="item-name">　无形资产</td><td class="text-right num muted">—</td></tr>
              <tr><td class="item-name">　长期待摊费用</td><td class="text-right num muted">—</td></tr>
              <tr><td class="item-name">　其他非流动资产</td><td class="text-right num muted">—</td></tr>
              <tr class="sum-row"><td><strong>非流动资产合计</strong></td><td class="text-right num"><strong>{{ fmt(data.nonCurrentAssets) }}</strong></td></tr>

              <tr class="total-row"><td><strong>资 产 总 计</strong></td><td class="text-right num"><strong>{{ fmt(data.totalAssets) }}</strong></td></tr>
            </tbody>
          </table>
        </div>

        <!-- 负债和所有者权益 -->
        <div class="balance-section">
          <table class="report-table">
            <thead>
              <tr><th colspan="2" class="section-title">负债和所有者权益</th></tr>
              <tr><th class="col-name">项　目</th><th class="col-amount">期末余额</th></tr>
            </thead>
            <tbody>
              <tr class="group-row"><td colspan="2"><b>流动负债</b></td></tr>
              <tr><td class="item-name">　短期借款</td><td class="text-right num muted">—</td></tr>
              <tr><td class="item-name">　应付票据</td><td class="text-right num muted">—</td></tr>
              <tr><td class="item-name">　应付账款</td><td class="text-right num">{{ fmt(data.accountsPayable) }}</td></tr>
              <tr><td class="item-name">　预收款项</td><td class="text-right num">{{ fmt(data.advanceReceipts) }}</td></tr>
              <tr><td class="item-name">　合同负债</td><td class="text-right num muted">—</td></tr>
              <tr><td class="item-name">　应付职工薪酬</td><td class="text-right num">{{ fmt(data.employeeCompensationPayable) }}</td></tr>
              <tr><td class="item-name">　应交税费</td><td class="text-right num">{{ fmt(data.taxesPayable) }}</td></tr>
              <tr><td class="item-name">　其他应付款</td><td class="text-right num muted">—</td></tr>
              <tr class="sum-row"><td><strong>流动负债合计</strong></td><td class="text-right num"><strong>{{ fmt(data.currentLiabilities) }}</strong></td></tr>

              <tr class="group-row"><td colspan="2"><b>非流动负债</b></td></tr>
              <tr><td class="item-name">　长期借款</td><td class="text-right num muted">—</td></tr>
              <tr class="sum-row"><td><strong>非流动负债合计</strong></td><td class="text-right num"><strong>{{ fmt(data.nonCurrentLiabilities) }}</strong></td></tr>

              <tr class="total-row"><td><strong>负 债 总 计</strong></td><td class="text-right num"><strong>{{ fmt(data.totalLiabilities) }}</strong></td></tr>

              <tr class="group-row"><td colspan="2"><b>所有者权益</b></td></tr>
              <tr><td class="item-name">　实收资本</td><td class="text-right num">{{ fmt(data.paidInCapital) }}</td></tr>
              <tr><td class="item-name">　资本公积</td><td class="text-right num muted">—</td></tr>
              <tr><td class="item-name">　盈余公积</td><td class="text-right num muted">—</td></tr>
              <tr><td class="item-name">　未分配利润</td><td class="text-right num">{{ fmt(data.retainedEarnings) }}</td></tr>
              <tr class="sum-row"><td><strong>所有者权益合计</strong></td><td class="text-right num"><strong>{{ fmt(data.totalEquity) }}</strong></td></tr>

              <tr class="total-row liability-total"><td><strong>负债和所有者权益总计</strong></td><td class="text-right num"><strong>{{ fmt(data.totalLiabilitiesAndEquity) }}</strong></td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 对比行 -->
      <div class="compare-bar">
        <div class="compare-item">
          <span class="label">资产总计</span>
          <span class="value">{{ fmt(data.totalAssets) }}</span>
        </div>
        <div class="compare-eq">=</div>
        <div class="compare-item">
          <span class="label">负债合计</span>
          <span class="value">{{ fmt(data.totalLiabilities) }}</span>
        </div>
        <div class="compare-plus">+</div>
        <div class="compare-item">
          <span class="label">所有者权益合计</span>
          <span class="value">{{ fmt(data.totalEquity) }}</span>
        </div>
        <div class="compare-eq">=</div>
        <div class="compare-item">
          <span class="label">负债+权益</span>
          <span class="value">{{ fmt(data.totalLiabilitiesAndEquity) }}</span>
        </div>
        <div class="compare-status" :class="data.balanced ? 'ok' : 'warn'">
          {{ data.balanced ? '✓ 平衡' : '✗ 不平衡' }}
        </div>
      </div>
    </div>

    <div v-if="!data" class="no-data">请选择期间并点击「查询」</div>
  </div>
</template>

<script setup lang="ts">
import { CircleCheck, Warning } from '@element-plus/icons-vue'

const props = defineProps<{ data: any }>()

const fmt = (val: number | null | undefined) => {
  if (val == null || val === 0) return '—'
  return Math.abs(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<style scoped>
.balance-sheet { padding: 24px 32px; }
.report-header { text-align: center; margin-bottom: 16px; }
.report-header h2 { font-size: 20px; font-weight: 700; margin: 0 0 6px; letter-spacing: 4px; }
.report-subtitle { font-size: 14px; color: #606266; margin: 0 0 2px; }
.report-note { font-size: 11px; color: #909399; margin: 0; }

.balance-ok, .balance-warn {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px; border-radius: 6px; font-size: 13px;
  margin-bottom: 16px;
}
.balance-ok { background: #f0f9eb; color: #67c23a; }
.balance-warn { background: #fef0f0; color: #f56c6c; }

.balance-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }

.report-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.report-table th, .report-table td { border: 1px solid #d8d8d8; padding: 7px 10px; }
.section-title { background: #f0f0f0; text-align: center; font-size: 14px; }
.col-name { }
.col-amount { width: 40%; text-align: right; }

.group-row td { background: #f8f8f8; color: #303133; }
.sum-row td { background: #fafafa; }
.total-row td { background: #ecf5ff; font-weight: 600; }
.liability-total td { background: #e8f4ff; }

.item-name { padding-left: 8px !important; }
.text-right { text-align: right; }
.num { font-family: 'Menlo', 'Consolas', monospace; }
.muted { color: #c0c4cc; }

.compare-bar {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; background: #f5f7fa; border-radius: 8px;
  margin-top: 16px; font-size: 13px;
}
.compare-item { text-align: center; }
.compare-item .label { display: block; font-size: 11px; color: #909399; }
.compare-item .value { display: block; font-weight: 600; font-family: 'Menlo', monospace; }
.compare-eq, .compare-plus { color: #409eff; font-size: 18px; font-weight: 700; }
.compare-status { margin-left: auto; padding: 4px 12px; border-radius: 4px; font-size: 12px; font-weight: 600; }
.compare-status.ok { background: #f0f9eb; color: #67c23a; }
.compare-status.warn { background: #fef0f0; color: #f56c6c; }

.no-data { text-align: center; color: #909399; padding: 40px; }

@media (max-width: 768px) {
  .balance-sheet {
    padding: 12px;
  }

  .balance-grid {
    grid-template-columns: 1fr !important;
    gap: 12px;
  }

  .report-table {
    min-width: 0;
    width: 100%;
    font-size: 12px;
  }

  .report-table th,
  .report-table td {
    padding: 5px 6px !important;
  }
}
</style>
