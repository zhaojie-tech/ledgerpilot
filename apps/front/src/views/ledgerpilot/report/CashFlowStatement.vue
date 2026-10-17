<template>
  <div class="cash-flow">
    <div class="report-header">
      <h2>现 金 流 量 表</h2>
      <p class="report-subtitle">{{ data?.year }}年第{{ data?.period }}期</p>
      <p class="report-note">会小企03表　编制单位：XXX公司　单位：元</p>
    </div>

    <div v-if="data" class="cash-container">
      <div class="cash-note">
        <el-icon><InfoFilled /></el-icon>
        {{ data.note || '说明：基于货币资金科目（库存现金、银行存款）本期收支变动估算' }}
      </div>

      <table class="report-table" v-if="data.operating">
        <thead>
          <tr>
            <th class="col-name">项　目</th>
            <th class="col-amount">现金流入</th>
            <th class="col-amount">现金流出</th>
            <th class="col-amount">净流量</th>
          </tr>
        </thead>
        <tbody>
          <tr class="section-row">
            <td colspan="4"><b>一、经营活动现金流量</b></td>
          </tr>
          <tr>
            <td class="item-indent">　经营活动现金流入合计</td>
            <td class="text-right num">{{ fmt(data.operating?.cashInflow) }}</td>
            <td class="text-right num muted">—</td>
            <td class="text-right num">{{ fmt(data.operating?.netCashFlow) }}</td>
          </tr>
          <tr>
            <td class="item-indent">　经营活动现金流出合计</td>
            <td class="text-right num muted">—</td>
            <td class="text-right num">{{ fmt(data.operating?.cashOutflow) }}</td>
            <td class="text-right num muted">—</td>
          </tr>

          <tr class="section-row">
            <td colspan="4"><b>二、投资活动现金流量</b></td>
          </tr>
          <tr>
            <td class="item-indent">　投资活动现金流入</td>
            <td class="text-right num muted">—</td>
            <td class="text-right num muted">—</td>
            <td class="text-right num muted">—</td>
          </tr>
          <tr>
            <td class="item-indent">　投资活动现金流出</td>
            <td class="text-right num muted">—</td>
            <td class="text-right num muted">—</td>
            <td class="text-right num muted">—</td>
          </tr>

          <tr class="section-row">
            <td colspan="4"><b>三、筹资活动现金流量</b></td>
          </tr>
          <tr>
            <td class="item-indent">　筹资活动现金流入</td>
            <td class="text-right num muted">—</td>
            <td class="text-right num muted">—</td>
            <td class="text-right num muted">—</td>
          </tr>
          <tr>
            <td class="item-indent">　筹资活动现金流出</td>
            <td class="text-right num muted">—</td>
            <td class="text-right num muted">—</td>
            <td class="text-right num muted">—</td>
          </tr>

          <tr class="total-row">
            <td><strong>现金净增加额</strong></td>
            <td class="text-right num muted">—</td>
            <td class="text-right num muted">—</td>
            <td class="text-right num" :class="{ positive: data.netIncrease > 0, negative: data.netIncrease < 0 }">
              <strong>{{ fmt(data.netIncrease) }}</strong>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="cash-summary">
        <div class="summary-item">
          <span class="label">本期现金增加</span>
          <span class="value" :class="{ positive: data.netIncrease > 0, negative: data.netIncrease < 0 }">
            {{ fmt(data.netIncrease) }}
          </span>
        </div>
      </div>
    </div>

    <div v-if="!data" class="no-data">请选择期间并点击「查询」</div>
  </div>
</template>

<script setup lang="ts">
import { InfoFilled } from '@element-plus/icons-vue'

const props = defineProps<{ data: any }>()

const fmt = (val: number | null | undefined) => {
  if (val == null || val === 0) return '—'
  const sign = val > 0 ? '+' : ''
  return sign + Math.abs(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<style scoped>
.cash-flow { padding: 24px 32px; }
.report-header { text-align: center; margin-bottom: 20px; }
.report-header h2 { font-size: 20px; font-weight: 700; margin: 0 0 6px; letter-spacing: 4px; }
.report-subtitle { font-size: 14px; color: #606266; margin: 0 0 2px; }
.report-note { font-size: 11px; color: #909399; margin: 0; }

.cash-note {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px; background: #f4f8ff; border-radius: 6px;
  font-size: 12px; color: #606266; margin-bottom: 16px;
}

.report-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.report-table th, .report-table td { border: 1px solid #d8d8d8; padding: 7px 10px; }
.report-table th { background: #f0f0f0; text-align: center; }
.col-name { }
.col-amount { width: 22%; text-align: right; }

.section-row td { background: #f8f8f8; color: #303133; }
.total-row td { background: #ecf5ff; }

.item-indent { padding-left: 12px !important; }
.text-right { text-align: right; }
.num { font-family: 'Menlo', 'Consolas', monospace; }
.muted { color: #c0c4cc; }
.positive { color: #67c23a; }
.negative { color: #f56c6c; }

.cash-summary {
  display: flex; justify-content: flex-end; margin-top: 16px;
}
.summary-item {
  text-align: center; padding: 12px 24px;
  background: #f5f7fa; border-radius: 8px;
}
.summary-item .label { display: block; font-size: 12px; color: #909399; margin-bottom: 4px; }
.summary-item .value { font-size: 20px; font-weight: 700; font-family: 'Menlo', monospace; }
.summary-item .positive { color: #67c23a; }
.summary-item .negative { color: #f56c6c; }

.no-data { text-align: center; color: #909399; padding: 40px; }

@media (max-width: 768px) {
  .cash-flow-statement { padding: 12px; }
  .report-header h2 { font-size: 15px; margin: 8px 0 4px; }
  .report-table { min-width: 0; width: 100%; font-size: 12px; }
  .report-table th, .report-table td { padding: 5px 6px !important; }
  .report-header .report-subtitle,
  .report-header .report-note { font-size: 11px; }
  .summary-section { flex-direction: column; gap: 8px; }
}
</style>
