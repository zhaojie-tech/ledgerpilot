<template>
  <div class="voucher-create">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ isEdit ? '编辑凭证' : '新增凭证' }}</span>
          <el-button text @click="$router.back()">
            <el-icon><ArrowLeft /></el-icon> 返回
          </el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="凭证日期" prop="voucherDate">
              <el-date-picker
                v-model="form.voucherDate"
                type="date"
                placeholder="选择日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="业务类型">
              <el-select v-model="form.businessType" placeholder="选择类型" style="width: 100%">
                <el-option label="采购" value="PURCHASE" />
                <el-option label="销售" value="SALE" />
                <el-option label="费用" value="EXPENSE" />
                <el-option label="收款" value="RECEIVE" />
                <el-option label="付款" value="PAY" />
                <el-option label="转账" value="TRANSFER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="业务单号">
              <el-input v-model="form.businessNo" placeholder="发票号/订单号等" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="凭证摘要">
          <el-input v-model="form.summary" placeholder="自动取第一条分录摘要" maxlength="500" />
        </el-form-item>

        <!-- 分录区 -->
        <div class="entry-section">
          <div class="entry-header">
            <span>分录信息</span>
            <el-button type="primary" size="small" @click="addEntry">
              <el-icon><Plus /></el-icon> 添加分录
            </el-button>
          </div>

          <el-table :data="form.entries" border stripe show-summary>
            <el-table-column label="序号" width="60">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="科目" min-width="200">
              <template #default="{ row }">
                <el-select
                  v-model="row.subjectCode"
                  filterable
                  placeholder="搜索科目"
                  style="width: 100%"
                  @change="onSubjectChange(row)"
                >
                  <el-option
                    v-for="s in subjectList"
                    :key="s.code"
                    :label="`${s.code} - ${s.name}`"
                    :value="s.code"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="方向" width="100">
              <template #default="{ row }">
                <el-select v-model="row.direction" style="width: 100%">
                  <el-option label="借" value="DEBIT" />
                  <el-option label="贷" value="CREDIT" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="金额" width="150">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.amount"
                  :precision="2"
                  :min="0"
                  :controls="false"
                  style="width: 100%"
                  placeholder="0.00"
                />
              </template>
            </el-table-column>
            <el-table-column label="摘要" min-width="150">
              <template #default="{ row }">
                <el-input v-model="row.summary" placeholder="分录摘要" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60">
              <template #default="{ $index }">
                <el-button type="danger" text size="small" @click="removeEntry($index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="balance-hint" :class="balanceClass">
            <span v-if="isBalanced">✅ 借贷平衡</span>
            <span v-else>❌ 借方 {{ debitTotal }} ≠ 贷方 {{ creditTotal }}，差异 {{ Math.abs(balanceDiff) }}</span>
          </div>
        </div>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            保存凭证
          </el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { voucherApi, type VoucherCreateRequest } from '@/api/voucher'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const isEdit = ref(false)

const form = reactive({
  voucherDate: '',
  businessType: '',
  businessNo: '',
  summary: '',
  sourceType: 'MANUAL',
  entries: [] as VoucherCreateRequest['entries'],
})

const rules = {
  voucherDate: [{ required: true, message: '请选择凭证日期', trigger: 'change' }],
}

// 模拟科目列表（实际从API加载）
const subjectList = ref([
  { code: '1001', name: '库存现金' },
  { code: '1002', name: '银行存款' },
  { code: '1122', name: '应收账款' },
  { code: '1123', name: '预付账款' },
  { code: '2202', name: '应付账款' },
  { code: '4001', name: '实收资本' },
  { code: '5001', name: '生产成本' },
  { code: '6001', name: '主营业务收入' },
  { code: '6401', name: '主营业务成本' },
  { code: '6602', name: '销售费用' },
])

const debitTotal = computed(() =>
  form.entries.reduce((sum, e) => sum + (e.direction === 'DEBIT' ? Number(e.amount) || 0 : 0), 0)
)

const creditTotal = computed(() =>
  form.entries.reduce((sum, e) => sum + (e.direction === 'CREDIT' ? Number(e.amount) || 0 : 0), 0)
)

const balanceDiff = computed(() => debitTotal.value - creditTotal.value)
const isBalanced = computed(() => Math.abs(balanceDiff.value) < 0.01)
const balanceClass = computed(() => isBalanced.value ? 'balanced' : 'unbalanced')

function addEntry() {
  form.entries.push({
    subjectCode: '',
    subjectName: '',
    direction: 'DEBIT',
    amount: '',
    summary: '',
    auxJson: '',
  })
}

function removeEntry(index: number) {
  form.entries.splice(index, 1)
}

function onSubjectChange(row: any) {
  const subject = subjectList.value.find(s => s.code === row.subjectCode)
  if (subject) {
    row.subjectName = subject.name
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (form.entries.length === 0) {
    ElMessage.warning('请添加至少一条分录')
    return
  }

  if (!isBalanced.value) {
    ElMessage.warning('借贷不平衡，请检查分录金额')
    return
  }

  submitting.value = true
  try {
    await voucherApi.create(form)
    ElMessage.success('凭证保存成功')
    router.push('/voucher')
  } catch {
    // handled
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  // 如果从NLP解析结果跳转来，读取暂存数据
  const pending = sessionStorage.getItem('pending_voucher')
  if (pending) {
    try {
      const parsed = JSON.parse(pending)
      // 将NLP解析结果转换为分录
      if (parsed.bizItems) {
        form.entries = parsed.bizItems.map((item: any) => ({
          subjectCode: item.subjectCode || '',
          subjectName: '',
          direction: item.direction || 'DEBIT',
          amount: item.amount?.toString() || '',
          summary: item.description || '',
          auxJson: '',
        }))
        sessionStorage.removeItem('pending_voucher')
      }
    } catch {}
  }

  // 获取下一个凭证号
  const today = new Date().toISOString().split('T')[0]
  form.voucherDate = today
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.entry-section {
  margin: 16px 0;
}

.entry-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 500;
}

.balance-hint {
  padding: 8px 16px;
  border-radius: 4px;
  margin-top: 8px;
  font-size: 14px;
}

.balanced {
  background-color: #f0f9eb;
  color: #67c23a;
  border: 1px solid #e1f3d8;
}

.unbalanced {
  background-color: #fef0f0;
  color: #f56c6c;
  border: 1px solid #fde2e2;
}

/* ─── 移动端响应式 ─── */
@media (max-width: 768px) {
  .voucher-create {
    padding: 0;
  }

  .voucher-create :deep(.el-card) {
    border-radius: 0;
  }

  /* 表单：所有字段全宽堆叠 */
  .voucher-create :deep(.el-row) {
    display: flex;
    flex-direction: column;
  }

  .voucher-create :deep(.el-col) {
    max-width: 100% !important;
    width: 100% !important;
  }

  .voucher-create :deep(.el-form-item) {
    margin-bottom: 12px;
  }

  .voucher-create :deep(.el-form-item__label) {
    min-width: 80px !important;
  }

  /* 分录表格横向滚动 */
  .voucher-create :deep(.el-table) {
    overflow-x: auto;
    width: 100%;
    font-size: 12px;
  }

  .voucher-create :deep(.el-table__body-wrapper) {
    overflow-x: auto;
  }

  .voucher-create :deep(.el-table) {
    min-width: 600px;
  }

  .balance-hint {
    font-size: 13px;
    padding: 8px 12px;
  }
}
</style>
