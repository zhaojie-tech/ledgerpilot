<template>
  <div class="voucher-list-view">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>凭证管理</h2>
      <el-button type="primary" @click="router.push('/voucher/create')">
        <el-icon><Plus /></el-icon> 新增凭证
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="凭证号">
          <el-input v-model="query.voucherNo" placeholder="模糊搜索" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD"
            style="width:240px"
          />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="query.businessType" placeholder="全部" clearable style="width:130px">
            <el-option label="采购" value="PURCHASE" />
            <el-option label="销售" value="SALE" />
            <el-option label="费用报销" value="EXPENSE" />
            <el-option label="收款" value="RECEIVE" />
            <el-option label="付款" value="PAY" />
            <el-option label="转账" value="TRANSFER" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width:120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已审核" value="APPROVED" />
            <el-option label="已过账" value="POSTED" />
            <el-option label="已作废" value="VOIDED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 凭证表格 -->
    <el-card shadow="never" class="table-card">
      <!-- 批量操作栏 -->
      <div v-if="selectedVouchers.length > 0" class="batch-bar">
        <el-badge :value="selectedVouchers.length" type="primary">
          <el-button size="small" @click="openBatchDrawer">
            批量查看 ({{ selectedVouchers.length }} 条)
          </el-button>
        </el-badge>
        <el-button size="small" @click="tableRef?.clearSelection()">清除选择</el-button>
      </div>

      <!-- ── 桌面端：表格 ── -->
      <el-table
        v-if="!isMobile"
        ref="tableRef"
        :data="records"
        v-loading="loading"
        stripe
        @sort-change="onSort"
        @selection-change="onSelectionChange"
        class="voucher-table"
      >
        <el-table-column type="selection" width="40" align="center" />
        <el-table-column label="凭证号" width="150" sortable="custom" prop="voucherNo">
          <template #default="{ row }">
            <el-link type="primary" @click="viewDetail(row)">
              <b>{{ row.voucherNo }}</b>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="凭证日期" width="110" sortable="custom" prop="voucherDate">
          <template #default="{ row }">
            {{ row.voucherDate }}
          </template>
        </el-table-column>
        <el-table-column label="业务类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ businessTypeText(row.businessType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="摘要" min-width="200">
          <template #default="{ row }">
            {{ row.summary || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="借方合计" width="130" align="right">
          <template #default="{ row }">
            <span class="amount-debit">{{ formatMoney(row.totalDebit) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="贷方合计" width="130" align="right">
          <template #default="{ row }">
            <span class="amount-credit">{{ formatMoney(row.totalCredit) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="90" align="center">
          <template #default="{ row }">
            {{ sourceTypeText(row.sourceType) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewDetail(row)">查看</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              type="success"
              link
              size="small"
              @click="quickApprove(row)"
            >审核</el-button>
            <el-button
              v-if="row.status === 'APPROVED'"
              type="warning"
              link
              size="small"
              @click="quickPost(row)"
            >过账</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              type="danger"
              link
              size="small"
              @click="quickVoid(row)"
            >作废</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- ── 移动端：凭证卡片列表 ── -->
      <div v-if="isMobile" v-loading="loading" class="voucher-card-list">
        <div v-if="!records.length && !loading" class="mobile-empty">
          <el-empty description="暂无凭证" :image-size="60" />
        </div>
        <div
          v-for="row in records"
          :key="row.id"
          class="vc-record-card"
          @click="viewDetail(row)"
        >
          <!-- 第一行：凭证号 + 状态 -->
          <div class="rc-top">
            <span class="rc-no">{{ row.voucherNo }}</span>
            <el-tag :type="statusTagType(row.status)" size="small" effect="plain">
              {{ statusText(row.status) }}
            </el-tag>
          </div>

          <!-- 第二行：日期 + 来源 + 类型 -->
          <div class="rc-meta">
            <span class="rc-date">{{ row.voucherDate }}</span>
            <span class="rc-sep">·</span>
            <span class="rc-source">{{ sourceTypeText(row.sourceType) }}</span>
            <span class="rc-sep">·</span>
            <el-tag size="small" type="info">{{ businessTypeText(row.businessType) }}</el-tag>
          </div>

          <!-- 摘要 -->
          <div v-if="row.summary" class="rc-summary">{{ row.summary }}</div>

          <!-- 借贷金额 -->
          <div class="rc-amounts">
            <span class="rc-debit">借 <b>¥{{ formatMoney(row.totalDebit) }}</b></span>
            <span class="rc-credit">贷 <b>¥{{ formatMoney(row.totalCredit) }}</b></span>
          </div>

          <!-- 操作按钮 -->
          <div class="rc-actions" @click.stop>
            <el-button type="primary" size="small" plain @click="viewDetail(row)">查看</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              type="success"
              size="small"
              plain
              @click="quickApprove(row)"
            >审核</el-button>
            <el-button
              v-if="row.status === 'APPROVED'"
              type="warning"
              size="small"
              plain
              @click="quickPost(row)"
            >过账</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              type="danger"
              size="small"
              plain
              @click="quickVoid(row)"
            >作废</el-button>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="isMobile ? [10, 20] : [10, 20, 50]"
          :layout="isMobile ? 'prev, pager, next' : 'total, sizes, prev, pager, next, jumper'"
          :small="isMobile"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 凭证详情抽屉（单条） -->
    <el-drawer
      v-model="detailVisible"
      title="凭证详情"
      direction="rtl"
      size="1100px"
      :before-close="() => (detailVisible = false)"
    >
      <template v-if="detailDrawerId">
        <VoucherDetail
          :voucher-id="detailDrawerId"
          :show-actions="true"
          @close="detailVisible = false"
          @refresh="loadData"
        />
      </template>
    </el-drawer>

    <!-- 批量查看抽屉 -->
    <el-drawer
      v-model="batchDrawerVisible"
      title="批量查看凭证"
      direction="rtl"
      size="1100px"
      :before-close="() => (batchDrawerVisible = false)"
    >
      <div class="batch-container">
        <VoucherDetail
          v-for="v in batchDrawerVouchers"
          :key="v.id"
          :voucher-id="v.id"
          :show-actions="true"
          class="batch-item"
          @close="() => { batchDrawerVouchers = batchDrawerVouchers.filter(item => item.id !== v.id); if (batchDrawerVouchers.length === 0) batchDrawerVisible = false }"
          @refresh="loadData"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import type { ElTable } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { voucherApi } from '@/api/voucher'
import VoucherDetail from './VoucherDetail.vue'

const router = useRouter()
const tableRef = ref<InstanceType<typeof ElTable>>()
const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value <= 768)

function updateWidth() { windowWidth.value = window.innerWidth }

// 搜索条件
const query = ref({
  voucherNo: '',
  businessType: '',
  status: '',
  dateFrom: '',
  dateTo: '',
  current: 1,
  size: 20
})
const dateRange = ref<string[]>([])

// 数据
const loading = ref(false)
const records = ref<any[]>([])
const total = ref(0)

// 详情抽屉
const detailVisible = ref(false)
const detailDrawerId = ref<number | null>(null)

// 批量查看
const selectedVouchers = ref<any[]>([])
const batchDrawerVisible = ref(false)
const batchDrawerVouchers = ref<any[]>([])

// 多选回调
const onSelectionChange = (rows: any[]) => {
  selectedVouchers.value = rows
}

// 批量查看
const openBatchDrawer = () => {
  batchDrawerVouchers.value = [...selectedVouchers.value]
  batchDrawerVisible.value = true
}

// 加载数据
const loadData = async () => {
  if (dateRange.value?.length === 2) {
    query.value.dateFrom = dateRange.value[0]
    query.value.dateTo = dateRange.value[1]
  } else {
    query.value.dateFrom = ''
    query.value.dateTo = ''
  }

  loading.value = true
  try {
    const res = await voucherApi.page(query.value)
    records.value = res.data?.records || res.data || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const search = () => {
  query.value.current = 1
  loadData()
}

const reset = () => {
  query.value = { voucherNo: '', businessType: '', status: '', dateFrom: '', dateTo: '', current: 1, size: 20 }
  dateRange.value = []
  loadData()
}

const onSort = ({ prop, order }: any) => {
  console.log('sort', prop, order)
}

// 查看凭证详情
const viewDetail = (row: any) => {
  detailDrawerId.value = row.id
  detailVisible.value = true
}

// 快捷审核
const quickApprove = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认审核凭证 ${row.voucherNo}？`, '审核确认')
    await voucherApi.approve(row.id)
    ElMessage.success('审核成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '审核失败')
  }
}

// 快捷过账
const quickPost = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认过账凭证 ${row.voucherNo}？`, '过账确认')
    await voucherApi.post(row.id)
    ElMessage.success('过账成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '过账失败')
  }
}

// 作废
const quickVoid = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认作废凭证 ${row.voucherNo}？此操作不可逆。`, '作废确认', {
      confirmButtonText: '确认作废',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await voucherApi.voidVoucher(row.id)
    ElMessage.success('作废成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '作废失败')
  }
}

// 格式化金额
const formatMoney = (val: string | number | null) => {
  if (val === null || val === undefined) return '—'
  const num = typeof val === 'string' ? parseFloat(val) : val
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 状态文本
const statusText = (s: string) => ({ DRAFT: '草稿', APPROVED: '已审核', POSTED: '已过账', VOIDED: '已作废' }[s] || s)
const statusTagType = (s: string) => ({ DRAFT: 'info', APPROVED: '', POSTED: 'success', VOIDED: 'danger' }[s] || 'info')

// 来源文本
const sourceTypeText = (s: string) => ({ NLP: 'AI解析', OCR: '发票OCR', MANUAL: '手工' }[s] || s)

// 业务类型文本
const businessTypeText = (s: string) => ({
  PURCHASE: '采购', SALE: '销售', EXPENSE: '报销',
  RECEIVE: '收款', PAY: '付款', TRANSFER: '转账'
}[s] || s)

onMounted(() => {
  window.addEventListener('resize', updateWidth)
  loadData()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateWidth)
})
</script>

<style scoped>
.voucher-list-view {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.search-card {
  margin-bottom: 12px;
}

.table-card :deep(.el-table) {
  font-size: 13px;
}

.amount-debit {
  color: #e6a23c;
  font-weight: 600;
}
.amount-credit {
  color: #67c23a;
  font-weight: 600;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

.batch-container {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.batch-item {
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 16px;
  margin-bottom: 16px;
}
.batch-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
}

/* ─── 移动端响应式 ─── */
@media (max-width: 768px) {
  .voucher-list-view {
    padding: 12px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .search-card :deep(.el-form) {
    display: flex;
    flex-direction: column;
    gap: 0;
  }

  .search-card :deep(.el-form-item) {
    margin-bottom: 8px;
    display: flex;
  }

  .search-card :deep(.el-form-item__label) {
    min-width: 70px !important;
  }

  .search-card :deep(.el-select),
  .search-card :deep(.el-input) {
    width: 100% !important;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .page-header h2 {
    margin: 0;
    font-size: 18px;
  }

  /* 表格横向滚动 */
  .voucher-table {
    overflow-x: auto;
    width: 100%;
  }

  .voucher-table :deep(.el-table__body-wrapper) {
    overflow-x: auto;
  }

  .table-card :deep(.el-table) {
    min-width: 700px;
    font-size: 12px;
  }

  .pagination-wrap {
    justify-content: center;
  }

  /* ── 移动端凭证卡片列表 ── */
  .voucher-card-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding-bottom: 12px;
  }

  .vc-record-card {
    background: #fff;
    border: 1px solid #ebeef5;
    border-radius: 10px;
    padding: 12px 14px;
    cursor: pointer;
    transition: box-shadow 0.2s;
  }

  .vc-record-card:active {
    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  }

  .rc-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;
  }

  .rc-no {
    font-weight: 700;
    font-size: 14px;
    color: #303133;
  }

  .rc-meta {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #909399;
    margin-bottom: 6px;
    flex-wrap: wrap;
  }

  .rc-sep { color: #d0d4da; }

  .rc-summary {
    font-size: 13px;
    color: #606266;
    margin-bottom: 8px;
    line-height: 1.4;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .rc-amounts {
    display: flex;
    gap: 16px;
    margin-bottom: 10px;
    font-size: 13px;
  }

  .rc-debit { color: #e6a23c; }
  .rc-credit { color: #67c23a; }
  .rc-debit b, .rc-credit b { font-weight: 700; }

  .rc-actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    border-top: 1px dashed #f0f0f0;
    padding-top: 8px;
  }

  .rc-actions .el-button {
    margin: 0;
  }

  .mobile-empty {
    text-align: center;
    padding: 40px 0;
  }
}
</style>
