<template>
  <div class="opening-balance-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>期初余额录入</span>
          <el-button type="primary" size="small" @click="handleSave" :loading="saving">
            保存期初余额
          </el-button>
        </div>
      </template>

      <!-- 状态说明 -->
      <el-alert v-if="!data.initialized && !data.hasVouchers" type="info" :closable="false" style="margin-bottom: 16px">
        当前期间尚未录入期初余额，请填写各科目期初余额后保存。录入期初余额后，方可开始录入凭证。
      </el-alert>
      <el-alert v-else-if="data.hasVouchers" type="warning" :closable="false" style="margin-bottom: 16px">
        当前期间已有凭证录入，期初余额不可修改。如需调整，请先冲销所有凭证。
      </el-alert>
      <el-alert v-else type="success" :closable="false" style="margin-bottom: 16px">
        期初余额已录入完毕，可在各科目余额列直接修改后保存。
      </el-alert>

      <!-- 桌面端：表格 -->
      <el-table v-if="!isMobile" :data="groupedSubjects" border stripe style="width: 100%" show-summary>
        <el-table-column label="科目编码" prop="subjectCode" width="120" fixed />
        <el-table-column label="科目名称" prop="subjectName" min-width="160" fixed />
        <el-table-column label="类型" prop="subjectTypeText" width="90" align="center" />
        <el-table-column label="余额方向" prop="directionText" width="80" align="center" />
        <el-table-column label="借方余额" width="160" align="right" :prop="'debit'">
          <template #default="{ row }">
            <el-input-number
              v-if="row.direction === 'DEBIT'"
              v-model="row.beginDebitNum"
              :min="0"
              :precision="2"
              :step="100"
              :controls="false"
              size="small"
              style="width: 100%"
              :disabled="data.hasVouchers"
              placeholder="0.00"
            />
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="贷方余额" width="160" align="right">
          <template #default="{ row }">
            <el-input-number
              v-if="row.direction === 'CREDIT'"
              v-model="row.beginCreditNum"
              :min="0"
              :precision="2"
              :step="100"
              :controls="false"
              size="small"
              style="width: 100%"
              :disabled="data.hasVouchers"
              placeholder="0.00"
            />
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="余额方向" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.balanceDirection === 'DEBIT' ? 'success' : 'danger'" size="small">
              {{ row.balanceDirection === 'DEBIT' ? '借' : '贷' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 移动端：科目卡片列表 -->
      <div v-if="isMobile" v-loading="loading" class="subject-card-list">
        <div
          v-for="row in groupedSubjects"
          :key="row.subjectCode"
          class="subject-card"
          :class="{ 'is-disabled': data.hasVouchers }"
        >
          <!-- 卡片头部：编码 + 名称 + 类型标签 -->
          <div class="sc-header">
            <div class="sc-title">
              <span class="sc-code">{{ row.subjectCode }}</span>
              <span class="sc-name">{{ row.subjectName }}</span>
            </div>
            <div class="sc-badges">
              <el-tag size="small" type="info">{{ row.subjectTypeText }}</el-tag>
              <el-tag size="small" :type="row.balanceDirection === 'DEBIT' ? 'success' : 'danger'">
                {{ row.balanceDirection === 'DEBIT' ? '借余' : '贷余' }}
              </el-tag>
            </div>
          </div>

          <!-- 余额输入区 -->
          <div class="sc-balance">
            <!-- 借方余额 -->
            <div v-if="row.direction === 'DEBIT'" class="sc-input-row">
              <span class="sc-input-label">借方余额</span>
              <el-input-number
                v-model="row.beginDebitNum"
                :min="0"
                :precision="2"
                :step="100"
                :controls="false"
                :disabled="data.hasVouchers"
                placeholder="0.00"
                class="sc-input"
              />
            </div>
            <!-- 贷方余额 -->
            <div v-else class="sc-input-row">
              <span class="sc-input-label">贷方余额</span>
              <el-input-number
                v-model="row.beginCreditNum"
                :min="0"
                :precision="2"
                :step="100"
                :controls="false"
                :disabled="data.hasVouchers"
                placeholder="0.00"
                class="sc-input"
              />
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { openingBalanceApi, type OpeningBalanceVO, type SubjectBalanceVO } from '@/api/voucher'

const loading = ref(false)
const saving = ref(false)
const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value <= 768)

function updateWidth() { windowWidth.value = window.innerWidth }

const data = ref<OpeningBalanceVO>({
  fiscalYear: new Date().getFullYear(),
  periodNo: new Date().getMonth() + 1,
  initialized: false,
  hasVouchers: false,
  subjects: [],
})

// 原始数据用于 diff
const originalBalances = ref<Map<string, { debit: number; credit: number }>>(new Map())

// 展开后的行数据（包含双向绑定的数值）
interface RowData extends SubjectBalanceVO {
  beginDebitNum: number
  beginCreditNum: number
  subjectTypeText: string
  directionText: string
}

const tableRows = ref<RowData[]>([])

const subjectTypeMap: Record<string, string> = {
  ASSET: '资产',
  LIABILITY: '负债',
  EQUITY: '权益',
  COST: '成本',
  PROFIT_LOSS: '损益',
}

const directionTextMap: Record<string, string> = {
  DEBIT: '借',
  CREDIT: '贷',
}

const groupedSubjects = computed(() => tableRows.value)

function buildRows(subjects: SubjectBalanceVO[]) {
  tableRows.value = subjects.map(s => {
    const debit = parseFloat(s.beginDebit) || 0
    const credit = parseFloat(s.beginCredit) || 0
    return {
      ...s,
      subjectTypeText: subjectTypeMap[s.subjectType] || s.subjectType,
      directionText: directionTextMap[s.direction] || s.direction,
      beginDebitNum: debit,
      beginCreditNum: credit,
    }
  })
}

async function fetchData() {
  loading.value = true
  try {
    const res = await openingBalanceApi.get({})
    data.value = res.data as OpeningBalanceVO
    originalBalances.value.clear()
    res.data.subjects.forEach((s: SubjectBalanceVO) => {
      originalBalances.value.set(s.subjectCode, {
        debit: parseFloat(s.beginDebit) || 0,
        credit: parseFloat(s.beginCredit) || 0,
      })
    })
    buildRows(res.data.subjects)
  } catch (e: unknown) {
    ElMessage.error('加载期初余额失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (data.value.hasVouchers) {
    ElMessage.warning('当前期间已有凭证，期初余额不可修改')
    return
  }

  // 收集有变化的余额
  const balances = tableRows.value
    .filter(row => {
      const orig = originalBalances.value.get(row.subjectCode)
      const debit = row.beginDebitNum || 0
      const credit = row.beginCreditNum || 0
      return (
        !orig ||
        orig.debit !== debit ||
        orig.credit !== credit ||
        debit > 0 ||
        credit > 0
      )
    })
    .map(row => ({
      subjectCode: row.subjectCode,
      debitBalance: row.beginDebitNum || 0,
      creditBalance: row.beginCreditNum || 0,
    }))

  // 过滤掉余额为0的
  const nonZero = balances.filter(b => b.debitBalance > 0 || b.creditBalance > 0)

  saving.value = true
  try {
    await openingBalanceApi.save({
      fiscalYear: data.value.fiscalYear,
      periodNo: data.value.periodNo,
      balances: nonZero,
    })
    ElMessage.success('保存成功')
    originalBalances.value.clear()
    tableRows.value.forEach(row => {
      originalBalances.value.set(row.subjectCode, {
        debit: row.beginDebitNum,
        credit: row.beginCreditNum,
      })
    })
    await fetchData()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { msg?: string } } }
    ElMessage.error(err.response?.data?.msg || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  window.addEventListener('resize', updateWidth)
  fetchData()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateWidth)
})
</script>

<style scoped>
.opening-balance-page {
  padding: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.text-muted {
  color: #999;
}

/* ─── 移动端响应式 ─── */
@media (max-width: 768px) {
  .opening-balance-page {
    padding: 8px;
  }

  /* 横向滚动表格 */
  .opening-balance-page :deep(.el-table) {
    overflow-x: auto;
    width: 100%;
    font-size: 12px;
  }

  .opening-balance-page :deep(.el-table__body-wrapper) {
    overflow-x: auto;
  }

  .opening-balance-page :deep(.el-table) {
    min-width: 700px;
  }

  /* 表头响应式 */
  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  /* el-input-number 全宽 */
  .opening-balance-page :deep(.el-input-number) {
    width: 100% !important;
    min-width: 80px;
  }

  /* ── 移动端科目卡片 ── */
  .subject-card-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding-bottom: 10px;
  }

  .subject-card {
    background: #fff;
    border: 1px solid #ebeef5;
    border-radius: 10px;
    padding: 12px 14px;
  }

  .subject-card.is-disabled {
    opacity: 0.6;
    pointer-events: none;
  }

  .sc-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 10px;
  }

  .sc-title {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  .sc-code {
    font-size: 12px;
    color: #909399;
    font-family: monospace;
  }

  .sc-name {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .sc-badges {
    display: flex;
    gap: 4px;
    flex-shrink: 0;
  }

  .sc-balance {
    background: #f8f9fb;
    border-radius: 6px;
    padding: 8px 10px;
  }

  .sc-input-row {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .sc-input-label {
    font-size: 12px;
    color: #606266;
    flex-shrink: 0;
    min-width: 60px;
  }

  .sc-input {
    flex: 1;
    min-width: 0;
  }

  .sc-input :deep(.el-input__inner) {
    text-align: right;
    font-size: 15px;
    font-weight: 700;
    color: #303133;
  }
}
</style>
