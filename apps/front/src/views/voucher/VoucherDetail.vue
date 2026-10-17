<template>
  <div class="voucher-detail">

    <!-- ========== 凭证主表头部 ========== -->
    <div class="voucher-header">
      <div class="voucher-stamps">
        <span v-if="detail.status" :class="['status-tag', `status-${detail.status.toLowerCase()}`]">
          {{ statusText }}
        </span>
        <span v-if="detail.sourceType" class="source-tag">
          {{ sourceTypeText }}
        </span>
      </div>

      <div class="voucher-title">
        <h2>记账凭证</h2>
        <div class="voucher-meta">
          <span class="voucher-no">{{ detail.voucherNo || '—' }}</span>
          <span class="voucher-date">{{ detail.voucherDate || '—' }}</span>
        </div>
      </div>

      <div class="voucher-info-grid">
        <div class="info-item">
          <label>业务类型</label>
          <span>{{ businessTypeText }}</span>
        </div>
        <div class="info-item">
          <label>业务单号</label>
          <span>{{ detail.businessNo || '—' }}</span>
        </div>
        <div class="info-item">
          <label>凭证来源</label>
          <span>{{ sourceTypeText }}</span>
        </div>
        <div class="info-item">
          <label>制单人</label>
          <span>{{ detail.createdByName || '—' }}</span>
        </div>
        <div class="info-item" v-if="detail.approveAt">
          <label>审核人</label>
          <span>{{ detail.approveByName || '—' }} · {{ detail.approveAt }}</span>
        </div>
        <div class="info-item" v-if="detail.postAt">
          <label>过账人</label>
          <span>{{ detail.postByName || '—' }} · {{ detail.postAt }}</span>
        </div>
      </div>
    </div>

    <!-- ========== 凭证分录表（标准财会三栏式） ========== -->
    <div class="entry-section">
      <div class="section-subtitle">
        <span>共 {{ detail.entries?.length || 0 }} 条分录</span>
        <span class="entry-summary">{{ detail.summary || '—' }}</span>
      </div>

      <!-- 100% 填满 drawer，列宽按百分比固定分配 -->
      <el-table
        ref="tableRef"
        :data="detail.entries"
        border
        stripe
        size="small"
        class="entry-table"
        :style="{ tableLayout: 'fixed', width: '100%' }"
        show-summary
        :summary-method="summaryMethod"
      >
        <!-- 序号 -->
        <el-table-column label="行号" width="50" align="center">
          <template #default="{ $index }">
            {{ $index + 1 }}
          </template>
        </el-table-column>

        <!-- 科目编码 -->
        <el-table-column label="科目编码" width="100" align="center">
          <template #default="{ row }">
            <span class="subject-code">{{ row.subjectCode || '—' }}</span>
          </template>
        </el-table-column>

        <!-- 科目名称 -->
        <el-table-column label="科目名称" min-width="180">
          <template #default="{ row }">
            <span class="subject-name">{{ row.subjectName || '—' }}</span>
          </template>
        </el-table-column>

        <!-- 辅助核算 -->
        <el-table-column label="辅助核算" width="150">
          <template #default="{ row }">
            <div v-if="row.auxDisplay" class="aux-tags">
              <el-tag
                v-for="(val, key) in parseAux(row.auxJson)"
                :key="key"
                size="small"
                type="info"
                class="aux-tag"
              >
                {{ key }}：{{ val }}
              </el-tag>
            </div>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <!-- 摘要 -->
        <el-table-column label="摘要" min-width="140">
          <template #default="{ row }">
            <span class="summary-cell">{{ row.summary || '—' }}</span>
          </template>
        </el-table-column>

        <!-- 借方金额 -->
        <el-table-column label="借方金额（元）" width="140" align="right">
          <template #default="{ row }">
            <span v-if="row.direction === 'DEBIT'" class="amount-debit">
              {{ formatMoney(row.amount) }}
            </span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <!-- 贷方金额 -->
        <el-table-column label="贷方金额（元）" width="140" align="right">
          <template #default="{ row }">
            <span v-if="row.direction === 'CREDIT'" class="amount-credit">
              {{ formatMoney(row.amount) }}
            </span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- ========== 操作按钮 ========== -->
    <div class="voucher-actions" v-if="showActions">
      <el-button
        v-if="detail.status === 'DRAFT'"
        type="primary"
        @click="handleApprove"
        :loading="actionLoading"
      >
        审核
      </el-button>
      <el-button
        v-if="detail.status === 'APPROVED'"
        type="warning"
        @click="handleUnapprove"
        :loading="actionLoading"
      >
        取消审核
      </el-button>
      <el-button
        v-if="detail.status === 'APPROVED'"
        type="success"
        @click="handlePost"
        :loading="actionLoading"
      >
        过账
      </el-button>
      <el-button
        v-if="detail.status === 'DRAFT'"
        type="danger"
        plain
        @click="handleVoid"
        :loading="actionLoading"
      >
        作废
      </el-button>
      <el-button @click="$emit('close')">关闭</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import type { VoucherEntryVO } from '@/api/voucher'
import { ElMessage } from 'element-plus'
import { voucherApi, type VoucherDetailVO } from '@/api/voucher'

const props = defineProps<{
  voucherId: number | null
  showActions?: boolean
}>()

const emit = defineEmits<{
  close: []
  refresh: []
}>()

const detail = ref<VoucherDetailVO>({} as VoucherDetailVO)
const loading = ref(false)
const actionLoading = ref(false)
const tableRef = ref()

// 合计行（插入表格末尾，colspan 让"合计"横跨前5列，借贷合计填入后2列）
// fixFooterColspan: Element Plus 的 summary-cell 默认 colspan=1，手动改为 5
// 使"合计"横跨前5列（行号+科目编码+科目名称+辅助核算+摘要），借贷金额填入后2列
const fixFooterColspan = async () => {
  await nextTick()
  await nextTick() // two ticks to ensure DOM updated after data change
  const tfoot = (tableRef.value?.$el as HTMLElement)?.querySelector('tfoot')
  if (!tfoot) return
  const firstCell = tfoot.querySelector('.cell') as HTMLElement | null
  if (firstCell) {
    firstCell.setAttribute('colspan', '5')
    firstCell.style.textAlign = 'center'
  }
}

// Element Plus show-summary 调用此方法返回每列的合计文本
const summaryMethod = (): (string | number)[] => {
  return [
    '合计',       // col 1: 行号 (colspan=5 手动施加于 DOM)
    '',          // col 2: 科目编码
    '',          // col 3: 科目名称
    '',          // col 4: 辅助核算
    '',          // col 5: 摘要
    detail.value.totalDebit || 0,   // col 6: 借方合计
    detail.value.totalCredit || 0   // col 7: 贷方合计
  ]
}

// 加载凭证详情
const fetchDetail = async () => {
  if (!props.voucherId) return
  loading.value = true
  try {
    const res = await voucherApi.getById(props.voucherId)
    detail.value = res.data || res
  } catch {
    ElMessage.error('加载凭证详情失败')
  } finally {
    loading.value = false
    fixFooterColspan()
  }
}

watch(() => props.voucherId, fetchDetail, { immediate: true })

// 格式化金额
const formatMoney = (val: string | number | null): string => {
  if (val === null || val === undefined) return '—'
  const num = typeof val === 'string' ? parseFloat(val) : val
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 解析辅助核算JSON
const AUX_KEY_LABELS: Record<string, string> = {
  customer: '客户',
  supplier: '供应商',
  department: '部门',
  project: '项目',
  employee: '员工',
}

const parseAux = (auxJson: string | null): Record<string, string> => {
  if (!auxJson) return {}
  try {
    const parsed = JSON.parse(auxJson)
    return Object.fromEntries(
      Object.entries(parsed)
        .filter(([, v]) => v !== null && v !== undefined && v !== '')
        .map(([k, v]) => [AUX_KEY_LABELS[k] ?? k, v as string])
    )
  } catch {
    return {}
  }
}

// 状态文本
const statusText = computed(() => {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    APPROVED: '已审核',
    POSTED: '已过账',
    VOIDED: '已作废'
  }
  return map[detail.value.status || ''] || detail.value.status || '—'
})

// 来源文本
const sourceTypeText = computed(() => {
  const map: Record<string, string> = {
    NLP: 'AI智能解析',
    OCR: '发票OCR识别',
    MANUAL: '手工录入'
  }
  return map[detail.value.sourceType || ''] || detail.value.sourceType || '—'
})

// 业务类型文本
const businessTypeText = computed(() => {
  const map: Record<string, string> = {
    PURCHASE: '采购',
    SALE: '销售',
    EXPENSE: '费用报销',
    RECEIVE: '收款',
    PAY: '付款',
    TRANSFER: '转账'
  }
  return map[detail.value.businessType || ''] || detail.value.businessType || '—'
})

// 操作
const doAction = async (api: (id: number) => Promise<any>, msg: string) => {
  if (!props.voucherId) return
  actionLoading.value = true
  try {
    await api(props.voucherId)
    ElMessage.success(msg)
    emit('refresh')
    fetchDetail()
  } catch (e: any) {
    ElMessage.error(e?.message || e?.msg || '操作失败')
  } finally {
    actionLoading.value = false
  }
}

const handleApprove = () => doAction(voucherApi.approve, '审核成功')
const handleUnapprove = () => doAction(voucherApi.unapprove, '取消审核成功')
const handlePost = () => doAction(voucherApi.post, '过账成功')
const handleVoid = () => doAction(voucherApi.voidVoucher, '作废成功')
</script>

<style scoped>
.voucher-detail {
  padding: 0 4px 16px;
}

/* ========== 头部 ========== */
.voucher-header {
  border-bottom: 2px solid #409eff;
  padding-bottom: 12px;
  margin-bottom: 16px;
}

.voucher-stamps {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.status-tag {
  padding: 2px 10px;
  border-radius: 3px;
  font-size: 12px;
  font-weight: bold;
  color: #fff;
}
.status-draft { background: #909399; }
.status-approved { background: #409eff; }
.status-posted { background: #67c23a; }
.status-voided { background: #f56c6c; }

.source-tag {
  padding: 2px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  font-size: 12px;
  color: #606266;
}

.voucher-title {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin-bottom: 12px;
}
.voucher-title h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
.voucher-meta {
  display: flex;
  gap: 12px;
  font-size: 14px;
  color: #606266;
}
.voucher-no {
  font-weight: bold;
  color: #303133;
}

.voucher-info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px 20px;
}
.info-item {
  display: flex;
  gap: 8px;
  font-size: 13px;
}
.info-item label {
  color: #909399;
  white-space: nowrap;
}
.info-item span {
  color: #303133;
}

/* ========== 分录表 ========== */
.entry-section {
  margin-bottom: 16px;
}
.section-subtitle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  color: #909399;
}
.entry-summary {
  color: #606266;
  font-style: italic;
}

.entry-table {
  font-size: 13px;
}
.entry-table .el-table__row td .cell,
.entry-table .el-table__header th .cell,
.entry-table .el-table__footer td .cell {
  line-height: 32px !important;
  padding-left: 8px !important;
  padding-right: 8px !important;
  white-space: nowrap !important;
}
.subject-code {
  font-family: 'Courier New', monospace;
  color: #409eff;
}
.subject-name {
  font-weight: 500;
}
.aux-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.aux-tag {
  font-size: 11px;
}
.amount-debit {
  color: #e6a23c;
  font-weight: 600;
}
.amount-credit {
  color: #67c23a;
  font-weight: 600;
}
.text-muted {
  color: #c0c4cc;
}
.summary-cell {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ========== 合计行 ========== */
.entry-totals {
  display: table;
  table-layout: fixed;
  width: 100%;
  border: 1px solid #ebeef5;
  border-top: none;
  background: #f5f7fa;
  font-size: 13px;
}
.entry-totals > div {
  display: table-cell;
  padding: 8px 8px;
  border-right: 1px solid #ebeef5;
  vertical-align: middle;
  line-height: 32px;
  overflow: hidden;
  white-space: nowrap;
}
.entry-totals > div:last-child {
  border-right: none;
}
.total-label       { width: 40px; text-align: center; font-weight: bold; color: #303133; }
.total-seq         { width: 100px; text-align: center; }
.total-subject     { min-width: 180px; }
.total-aux         { width: 150px; }
.total-summary     { min-width: 140px; }
.total-debit-wrap  { width: 140px; text-align: right; color: #e6a23c; font-weight: 600; }
.total-credit-wrap { width: 140px; text-align: right; color: #67c23a; font-weight: 600; }

/* ========== 操作按钮 ========== */
.voucher-actions {
  display: flex;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}
</style>
