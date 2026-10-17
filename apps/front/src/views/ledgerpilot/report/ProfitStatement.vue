<template>
  <div class="profit-statement">
    <!-- 表头 -->
    <div class="report-header">
      <h2>利 润 表</h2>
      <p class="report-subtitle">{{ data?.year }}年第{{ data?.period }}期</p>
      <p class="report-note">会小企02表　编制单位：XXX公司</p>
    </div>

    <table class="report-table" v-if="data">
      <thead>
        <tr>
          <th class="col-name">项　目</th>
          <th class="col-code">行次</th>
          <th class="col-amount">本期金额</th>
          <th class="col-amount">上期金额</th>
        </tr>
      </thead>
      <tbody>
        <!-- 营业收入 -->
        <tr class="section-row">
          <td colspan="4"><b>一、营业收入</b></td>
        </tr>
        <tr>
          <td class="cell-indent">　　主营业务收入</td>
          <td class="text-center">1</td>
          <td class="text-right num">{{ fmt(data.mainBusinessRevenue) }}</td>
          <td class="text-right num muted">—</td>
        </tr>
        <tr>
          <td class="cell-indent">　　其他业务收入</td>
          <td class="text-center">2</td>
          <td class="text-right num muted">—</td>
          <td class="text-right num muted">—</td>
        </tr>
        <tr class="sum-row">
          <td>　　<strong>营业收入合计</strong></td>
          <td class="text-center">3</td>
          <td class="text-right num"><strong>{{ fmt(data.operatingRevenue) }}</strong></td>
          <td class="text-right num muted">—</td>
        </tr>

        <!-- 营业成本 -->
        <tr class="section-row">
          <td colspan="4"><b>减：营业成本</b></td>
        </tr>
        <tr>
          <td class="cell-indent">　　主营业务成本</td>
          <td class="text-center">4</td>
          <td class="text-right num">{{ fmt(data.mainBusinessCost) }}</td>
          <td class="text-right num muted">—</td>
        </tr>
        <tr>
          <td class="cell-indent">　　其他业务成本</td>
          <td class="text-center">5</td>
          <td class="text-right num muted">—</td>
          <td class="text-right num muted">—</td>
        </tr>
        <tr class="sum-row">
          <td>　　<strong>营业成本合计</strong></td>
          <td class="text-center">6</td>
          <td class="text-right num"><strong>{{ fmt(data.operatingCost) }}</strong></td>
          <td class="text-right num muted">—</td>
        </tr>

        <!-- 毛利润 -->
        <tr class="gross-row">
          <td>　　<strong>毛利润</strong> <span class="formula">(营业收入 - 营业成本)</span></td>
          <td class="text-center">7</td>
          <td class="text-right num"><strong>{{ fmt(data.grossProfit) }}</strong></td>
          <td class="text-right num muted">—</td>
        </tr>

        <!-- 税金及附加 -->
        <tr class="section-row">
          <td colspan="4"><b>减：税金及附加</b></td>
        </tr>
        <tr>
          <td class="cell-indent">　　税金及附加</td>
          <td class="text-center">8</td>
          <td class="text-right num">{{ fmt(data.taxesAndSurcharges) }}</td>
          <td class="text-right num muted">—</td>
        </tr>

        <!-- 期间费用 -->
        <tr class="section-row">
          <td colspan="4"><b>减：期间费用</b></td>
        </tr>
        <tr>
          <td class="cell-indent">　　销售费用</td>
          <td class="text-center">9</td>
          <td class="text-right num">{{ fmt(data.sellingExpense) }}</td>
          <td class="text-right num muted">—</td>
        </tr>
        <tr>
          <td class="cell-indent">　　管理费用</td>
          <td class="text-center">10</td>
          <td class="text-right num">{{ fmt(data.administrativeExpense) }}</td>
          <td class="text-right num muted">—</td>
        </tr>
        <tr>
          <td class="cell-indent">　　财务费用</td>
          <td class="text-center">11</td>
          <td class="text-right num">{{ fmt(data.financialExpense) }}</td>
          <td class="text-right num muted">—</td>
        </tr>
        <tr class="sum-row">
          <td>　　<strong>期间费用合计</strong></td>
          <td class="text-center">12</td>
          <td class="text-right num"><strong>{{ fmt(data.periodExpenses) }}</strong></td>
          <td class="text-right num muted">—</td>
        </tr>

        <!-- 营业利润 -->
        <tr class="profit-row">
          <td><strong>二、营业利润</strong> <span class="formula">(毛利润 - 税金及附加 - 期间费用)</span></td>
          <td class="text-center">13</td>
          <td class="text-right num"><strong>{{ fmt(data.operatingProfit) }}</strong></td>
          <td class="text-right num muted">—</td>
        </tr>

        <!-- 营业外收支 -->
        <tr class="section-row">
          <td colspan="4"><b>加：营业外收支</b></td>
        </tr>
        <tr>
          <td class="cell-indent">　　营业外收入</td>
          <td class="text-center">14</td>
          <td class="text-right num">{{ fmt(data.nonOperatingIncome) }}</td>
          <td class="text-right num muted">—</td>
        </tr>
        <tr>
          <td class="cell-indent">　　营业外支出</td>
          <td class="text-center">15</td>
          <td class="text-right num">{{ fmt(data.nonOperatingExpense) }}</td>
          <td class="text-right num muted">—</td>
        </tr>

        <!-- 利润总额 -->
        <tr class="profit-row highlight">
          <td><strong>三、利润总额</strong> <span class="formula">(营业利润 + 营业外收支)</span></td>
          <td class="text-center">16</td>
          <td class="text-right num"><strong>{{ fmt(data.totalProfit) }}</strong></td>
          <td class="text-right num muted">—</td>
        </tr>

        <!-- 净利润 -->
        <tr class="profit-row highlight">
          <td><strong>四、净利润</strong></td>
          <td class="text-center">17</td>
          <td class="text-right num"><strong>{{ fmt(data.netProfit) }}</strong></td>
          <td class="text-right num muted">—</td>
        </tr>
      </tbody>
    </table>

    <div v-if="!data" class="no-data">请选择期间并点击「查询」</div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ data: any }>()

const fmt = (val: number | null | undefined) => {
  if (val == null || val === 0) return '—'
  const v = Math.abs(val)
  const sign = val < 0 ? '-' : ''
  return sign + v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<style scoped>
.profit-statement { padding: 24px 32px; }
.report-header { text-align: center; margin-bottom: 20px; }
.report-header h2 { font-size: 20px; font-weight: 700; margin: 0 0 6px; letter-spacing: 4px; }
.report-subtitle { font-size: 14px; color: #606266; margin: 0 0 2px; }
.report-note { font-size: 11px; color: #909399; margin: 0; }

.report-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.report-table th,
.report-table td { border: 1px solid #d8d8d8; padding: 7px 10px; }
.report-table th { background: #f0f0f0; font-weight: 600; text-align: center; }
.report-table .col-name { width: 45%; }
.report-table .col-code { width: 10%; }
.report-table .col-amount { width: 22.5%; }

.section-row td { background: #f8f8f8; color: #303133; }
.sum-row td { background: #fafafa; }
.gross-row td { background: #fff8f0; }
.profit-row td { background: #ecf5ff; }
.profit-row.highlight td { background: #e8f4ff; }

.cell-indent { padding-left: 24px !important; }
.text-center { text-align: center; }
.text-right { text-align: right; }
.num { font-family: 'Menlo', 'Consolas', monospace; letter-spacing: 0; }
.muted { color: #c0c4cc; }
.formula { font-size: 11px; color: #909399; font-weight: normal; }

.no-data { text-align: center; color: #909399; padding: 40px; }

@media (max-width: 768px) {
  .profit-statement { padding: 12px; }
  .report-header h2 { font-size: 15px; margin: 8px 0 4px; }
  .report-table { min-width: 0; width: 100%; font-size: 12px; }
  .report-table th, .report-table td { padding: 5px 6px !important; }
  .report-header .report-subtitle,
  .report-header .report-note { font-size: 11px; }
}
</style>
