<template>
  <div class="chat-home">
    <!-- 移动端：历史抽屉入口 -->
    <div v-if="isMobile" class="mobile-history-btn">
      <el-button size="small" @click="historyDrawerVisible = true">
        💬 历史对话
      </el-button>
    </div>

    <!-- 移动端：历史对话抽屉 -->
    <el-drawer v-if="isMobile" v-model="historyDrawerVisible" direction="ltr" size="260px" title="💡 对话历史">
      <div class="drawer-inner">
        <div class="sidebar-header" style="padding: 0 0 12px">
          <el-button text type="primary" size="small" @click="startNewChat(); historyDrawerVisible = false">
            <el-icon><Plus /></el-icon> 新对话
          </el-button>
        </div>
        <div v-loading="historyLoading" class="conversation-list">
          <div
            v-for="conv in conversations"
            :key="conv.id"
            class="conv-item"
            :class="{ active: currentConvId === conv.id }"
            @click="loadConversation(conv.id); historyDrawerVisible = false"
          >
            <div class="conv-status-dot" :class="conv.status" />
            <div class="conv-info">
              <div class="conv-summary">{{ conv.title }}</div>
              <div class="conv-vouchers" v-if="conv.voucherIds.length">
                <el-tag v-for="vid in conv.voucherIds" :key="vid" size="small" type="success" style="margin: 2px" @click.stop="openVoucherDetail(vid)">
                  {{ conv.voucherNoMap[vid] || '凭证' }}
                </el-tag>
              </div>
            </div>
            <div class="conv-time">{{ conv.time }}</div>
          </div>
          <el-empty v-if="!conversations.length" description="暂无对话" :image-size="40" />
        </div>
      </div>
    </el-drawer>

    <!-- 桌面端：左侧面板：历史对话 -->
    <aside v-if="!isMobile" class="sidebar">
      <div class="sidebar-header">
        <span class="sidebar-title">💡 对话历史</span>
        <el-button text type="primary" size="small" @click="startNewChat">
          <el-icon><Plus /></el-icon> 新对话
        </el-button>
      </div>

      <div class="conversation-list" v-loading="historyLoading">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: currentConvId === conv.id }"
          @click="loadConversation(conv.id)"
        >
          <div class="conv-status-dot" :class="conv.status" />
          <div class="conv-info">
            <div class="conv-summary">{{ conv.title }}</div>
            <div class="conv-vouchers" v-if="conv.voucherIds.length">
              <el-tag
                v-for="vid in conv.voucherIds"
                :key="vid"
                size="small"
                type="success"
                style="margin: 2px"
                @click.stop="openVoucherDetail(vid)"
              >
                {{ conv.voucherNoMap[vid] || '凭证' }}
              </el-tag>
            </div>
          </div>
          <div class="conv-time">{{ conv.time }}</div>
        </div>
        <el-empty v-if="!conversations.length" description="暂无对话" :image-size="40" />
      </div>
    </aside>

    <!-- 右侧主区域：聊天界面 -->
    <main class="chat-main">
      <!-- 聊天头部 -->
      <div class="chat-header">
        <span>🤖 AI智能做账</span>
        <div class="chat-header-tags">
          <el-tag v-if="currentConv?.status === 'done'" type="success" size="small">
            ✅ 已生成凭证
          </el-tag>
          <el-tag v-else-if="currentConv?.status === 'parsing'" type="primary" size="small">
            <i class="el-icon-loading" /> 解析中...
          </el-tag>
          <el-tag v-else type="info" size="small">等待输入</el-tag>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="chat-messages" ref="messagesRef">
        <!-- 欢迎消息 -->
        <div v-if="!messages.length" class="welcome-area">
          <div class="welcome-icon">🤖</div>
          <div class="welcome-text">用自然语言描述你的业务，我来帮你生成会计凭证</div>
          <div class="welcome-hints">
            <div class="hint" @click="sendQuickHint('收到张三商贸货款10000元，银行转账')">
              💰 收到客户货款10000元
            </div>
            <div class="hint" @click="sendQuickHint('采购办公用品5000元含税已入库未付款')">
              🛒 采购办公用品5000元含税
            </div>
            <div class="hint" @click="sendQuickHint('办公室买打印机花了3500元银行转账')">
              🖨️ 购买打印机3500元
            </div>
          </div>
        </div>

        <!-- 消息气泡 -->
        <template v-for="(msg, idx) in messages" :key="idx">
          <!-- 用户消息 -->
          <div v-if="msg.role === 'user'" class="msg msg-user">
            <div class="msg-bubble msg-bubble-user">
              <div class="msg-text">{{ msg.content }}</div>
            </div>
          </div>

          <!-- AI 消息 -->
          <div v-else class="msg msg-ai">
            <div class="msg-avatar">🤖</div>
            <div class="msg-body">
              <!-- 文本消息 -->
              <div v-if="msg.content" class="msg-bubble msg-bubble-ai">
                <div class="msg-text">{{ msg.content }}</div>
              </div>

              <!-- 凭证卡片 -->
              <div v-if="msg.voucherCard" class="voucher-card">
                <div class="vc-header">
                  <el-tag :type="bizTypeTagType(msg.voucherCard.bizType)" size="small">
                    {{ bizTypeLabel(msg.voucherCard.bizType) }}
                  </el-tag>
                  <span class="vc-date" v-if="msg.voucherCard.date">{{ msg.voucherCard.date }}</span>
                  <el-tag
                    :type="msg.voucherCard.confidenceLevel === 'HIGH' ? 'success' : msg.voucherCard.confidenceLevel === 'MEDIUM' ? 'warning' : 'danger'"
                    size="small"
                  >
                    {{ msg.voucherCard.confidenceLevel === 'HIGH' ? '高置信' : msg.voucherCard.confidenceLevel === 'MEDIUM' ? '中置信' : '低置信' }}
                  </el-tag>
                </div>

                <div class="vc-summary">{{ msg.voucherCard.summary }}</div>

                <!-- 分录表格 -->
                <el-table :data="msg.voucherCard.entries" border size="small" class="vc-entries">
                  <el-table-column label="" width="36" align="center">
                    <template #default="{ row }">
                      <span :class="row.direction === 'DEBIT' ? 'dir-debit' : 'dir-credit'">
                        {{ row.direction === 'DEBIT' ? '借' : '贷' }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column label="科目" min-width="140">
                    <template #default="{ row }">
                      {{ row.subjectCode }} {{ row.subjectName }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="summary" label="摘要" min-width="120" show-overflow-tooltip />
                  <el-table-column label="金额" width="110" align="right">
                    <template #default="{ row }">
                      ¥{{ Number(row.amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}
                    </template>
                  </el-table-column>
                  <el-table-column label="辅助" width="100">
                    <template #default="{ row }">
                      <template v-if="row.aux">
                        <span
                          v-for="(val, key) in row.aux"
                          v-show="val"
                          :key="key"
                          class="aux-tag"
                        >
                          {{ auxLabel(key) }}:{{ val }}
                        </span>
                      </template>
                    </template>
                  </el-table-column>
                </el-table>

                <!-- 销售成本录入（仅销售业务出现） -->
                <div v-if="msg.voucherCard.bizType === 'SALE' && !msg.voucherCard.confirmed" class="vc-cost-input">
                  <span class="vc-cost-label">💡 请录入销售成本（确认后自动生成主营业务成本分录）：</span>
                  <el-input-number
                    v-model="msg.voucherCard.costAmount"
                    :min="0"
                    :precision="2"
                    placeholder="0.00"
                    style="width: 160px"
                  />
                  <span class="vc-cost-hint" v-if="msg.voucherCard.costAmount">
                    将自动生成：借 主营业务成本 ¥{{ msg.voucherCard.costAmount }} / 贷 库存商品 ¥{{ msg.voucherCard.costAmount }}
                  </span>
                </div>

                <!-- 借贷合计 -->
                <div class="vc-footer">
                  <span>借方 <b>{{ formatMoney(sumDir(msg.voucherCard.entries, 'DEBIT') + (msg.voucherCard.costAmount || 0)) }}</b></span>
                  <span>贷方 <b>{{ formatMoney(sumDir(msg.voucherCard.entries, 'CREDIT')) }}</b></span>
                  <el-tag
                    :type="isBalanced(msg.voucherCard.entries, msg.voucherCard.costAmount) ? 'success' : 'danger'"
                    size="small"
                  >
                    {{ isBalanced(msg.voucherCard.entries, msg.voucherCard.costAmount) ? '✓ 平衡' : '✗ 不平' }}
                  </el-tag>
                </div>

                <!-- 确认按钮 -->
                <div v-if="!msg.voucherCard.confirmed" class="vc-actions">
                  <el-button
                    type="primary"
                    size="default"
                    :loading="msg.voucherCard.confirming"
                    :disabled="!isBalanced(msg.voucherCard.entries, msg.voucherCard.costAmount)"
                    @click="confirmVoucher(idx)"
                  >
                    <el-icon><Check /></el-icon> 确认生成凭证
                  </el-button>
                  <span v-if="!isBalanced(msg.voucherCard.entries)" class="vc-warn">
                    借贷不平衡，请修改后确认
                  </span>
                </div>
                <!-- 已确认 -->
                <div v-else class="vc-confirmed">
                  <el-tag type="success">✅ 已生成凭证: {{ msg.voucherCard.voucherNo }}</el-tag>
                </div>
              </div>

              <!-- 加载中 -->
              <div v-if="msg.loading" class="msg-bubble msg-bubble-ai loading-bubble">
                <span class="dot-loading">
                  <span></span><span></span><span></span>
                </span>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- 输入区域 -->
      <div class="chat-input-area">
        <!-- 隐藏的文件输入 -->
        <input
          ref="batchFileInput"
          type="file"
          accept=".txt,.md,.csv,.tsv"
          style="display:none"
          @change="handleFileUpload"
        />
        <el-button
          :icon="Upload"
          circle
          class="upload-btn"
          title="导入文件（TXT/MD/CSV）"
          @click="batchFileInput?.click()"
          :disabled="sending"
        />
        <el-input
          ref="inputRef"
          v-model="inputText"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="描述你的业务，每行一条可批量处理..."
          @keydown.enter.exact="handleEnterKey"
          :disabled="sending"
        />
        <el-button
          type="primary"
          :icon="Promotion"
          :loading="sending"
          :disabled="!inputText.trim()"
          @click="sendMessage"
          circle
          class="send-btn"
        />
      </div>

      <!-- 批量导入预览面板 -->
      <div v-if="batchPanelVisible" class="batch-panel">
        <div class="batch-panel-header">
          <span>📋 批量导入（{{ batchItems.filter(i => i.checked).length }}/{{ batchItems.length }} 条）</span>
          <div class="batch-panel-actions">
            <el-button size="small" @click="batchSelectAll">全选</el-button>
            <el-button size="small" @click="batchDeselectAll">取消全选</el-button>
            <el-button size="small" type="primary" @click="batchParse" :loading="batchParsing">
              🚀 解析并生成凭证
            </el-button>
            <el-button size="small" @click="batchPanelVisible = false">关闭</el-button>
          </div>
        </div>
        <div class="batch-panel-list">
          <div
            v-for="item in batchItems"
            :key="item.index"
            class="batch-item"
            :class="{ checked: item.checked }"
            @click="item.checked = !item.checked"
          >
            <el-checkbox :model-value="item.checked" @change="(val: any) => item.checked = val" />
            <span class="batch-item-index">{{ item.index }}.</span>
            <span class="batch-item-text">{{ item.text }}</span>
          </div>
        </div>
      </div>
    </main>

    <!-- 凭证详情抽屉 -->
    <el-drawer v-model="detailVisible" title="凭证详情" direction="rtl" size="900px">
      <template v-if="detailDrawerId">
        <VoucherDetail
          :voucher-id="detailDrawerId"
          :show-actions="true"
          @close="detailVisible = false"
        />
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Check, Promotion, Upload } from '@element-plus/icons-vue'
import VoucherDetail from '@/views/voucher/VoucherDetail.vue'

// ─── 类型 ───

interface AuxDim { customer?: string | null; supplier?: string | null; department?: string | null; project?: string | null; employee?: string | null }
interface CardEntry { direction: string; subjectCode: string; subjectName: string; amount: number; summary: string; aux?: AuxDim | null }
interface VoucherCard { bizType: string; summary: string; date: string; confidenceLevel: string; entries: CardEntry[]; confirmed?: boolean; confirming?: boolean; voucherNo?: string; costAmount?: number | null }

interface ChatMessage {
  role: 'user' | 'ai'
  content?: string
  voucherCard?: VoucherCard
  loading?: boolean
}

interface Conversation {
  id: string
  title: string
  time: string
  status: 'active' | 'parsing' | 'done'
  messages: ChatMessage[]
  voucherIds: number[]
  voucherNoMap: Record<number, string>
}

// ─── 状态 ───

const conversations = ref<Conversation[]>([])
const currentConvId = ref('')
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const sending = ref(false)
const historyLoading = ref(false)
const historyDrawerVisible = ref(false)
const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value <= 768)

// ─── 批量导入状态 ───

const batchFileInput = ref<HTMLInputElement | null>(null)
const batchItems = ref<{ index: number; text: string; checked: boolean }[]>([])
const batchPanelVisible = ref(false)
const batchParsing = ref(false)

function updateWidth() { windowWidth.value = window.innerWidth }
const messagesRef = ref<HTMLElement>()
const inputRef = ref()
const detailVisible = ref(false)
const detailDrawerId = ref<number | null>(null)

const currentConv = ref<Conversation | null>(null)

// ─── 持久化 ───

const STORAGE_KEY = 'ledgerpilot_nlp_conversations'
const CURRENT_KEY = 'ledgerpilot_nlp_current_id'

function saveConversations() {
  try {
    const meta = conversations.value.map(c => ({
      id: c.id, title: c.title, time: c.time, status: c.status,
      voucherIds: c.voucherIds, voucherNoMap: c.voucherNoMap,
      messages: c.messages.slice(-50),
    }))
    localStorage.setItem(STORAGE_KEY, JSON.stringify(meta))
    localStorage.setItem(CURRENT_KEY, currentConvId.value)
  } catch {
    const meta = conversations.value.slice(0, 20).map(c => ({
      id: c.id, title: c.title, time: c.time, status: c.status,
      voucherIds: c.voucherIds, voucherNoMap: c.voucherNoMap,
      messages: c.messages.slice(-20),
    }))
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(meta)) } catch { /* give up */ }
  }
}

function loadConversations(): boolean {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return false
    const meta = JSON.parse(raw)
    if (!Array.isArray(meta) || !meta.length) return false
    conversations.value = meta
    const saved = localStorage.getItem(CURRENT_KEY)
    if (saved && conversations.value.find(c => c.id === saved)) {
      currentConvId.value = saved
      loadConversation(saved)
    }
    return true
  } catch {
    return false
  }
}

// ─── 计算属性 ───

// ─── 方法 ───

function startNewChat() {
  const id = 'conv-' + Date.now()
  const conv: Conversation = {
    id, title: '新对话', time: formatTime(new Date()),
    status: 'active', messages: [], voucherIds: [], voucherNoMap: {},
  }
  conversations.value.unshift(conv)
  currentConvId.value = id
  currentConv.value = conv
  messages.value = []
}

function loadConversation(id: string) {
  const conv = conversations.value.find(c => c.id === id)
  if (!conv) return
  currentConvId.value = id
  currentConv.value = conv
  messages.value = [...conv.messages]
  nextTick(scrollToBottom)
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  // 检测多行输入：自动进入批量模式
  const lines = text.split('\n').map(l => l.trim()).filter(l => l.length > 0)
  if (lines.length > 1) {
    inputText.value = ''
    batchItems.value = lines.map((l, i) => ({ index: i + 1, text: l, checked: true }))
    batchPanelVisible.value = true
    return
  }

  // 单条：原有逻辑
  // 确保有活跃对话
  if (!currentConv.value) startNewChat()

  sending.value = true
  inputText.value = ''

  // 添加用户消息
  messages.value.push({ role: 'user', content: text })
  nextTick(scrollToBottom)

  // 判断是修改请求还是新业务描述
  const isModification = isModifyRequest(text, messages.value)

  if (isModification) {
    // 修改模式：把用户修改意见 + 原始业务描述一起发给 AI
    await handleModification(text)
  } else {
    // 新业务：调 AI 解析
    await handleNewBusiness(text)
  }

  // 更新对话信息
  currentConv.value!.messages = [...messages.value]
  currentConv.value!.time = formatTime(new Date())
  if (!currentConv.value!.title || currentConv.value!.title === '新对话') {
    currentConv.value!.title = text.length > 20 ? text.substring(0, 20) + '...' : text
  }

  sending.value = false
  saveConversations()
  nextTick(() => { inputRef.value?.focus() })
}

function isModifyRequest(text: string, msgs: ChatMessage[]): boolean {
  const modifyHints = ['修改', '改一下', '换成', '不对', '不是', '金额不对', '科目不对', '把', '更正', '应该是', '重新']
  const lower = text.toLowerCase()
  if (modifyHints.some(h => lower.includes(h))) return true
  // 检查上下文是否有未确认的凭证卡片
  const hasUnconfirmedCard = msgs.some(m => m.voucherCard && !m.voucherCard.confirmed)
  return hasUnconfirmedCard && text.length < 50
}

async function handleNewBusiness(text: string) {
  // AI loading
  messages.value.push({ role: 'ai', loading: true, content: '正在解析您的业务描述...' })
  nextTick(scrollToBottom)
  currentConv.value!.status = 'parsing'

  try {
    // 只解析，不自动创建
    const res = await fetch('/api/nlp/parse', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`,
      },
      body: JSON.stringify({ userInput: text, bizType: '' }),
    })
    const json = await res.json()

    // 移除 loading
    messages.value = messages.value.filter(m => !m.loading)

    if (json.code === 0 && json.data?.items?.length) {
      const data = json.data

      // 校验错误提示
      if (data.validation?.errors?.length) {
        messages.value.push({
          role: 'ai',
          content: '⚠️ ' + data.validation.errors.map((e: any) => e.message).join('；'),
        })
      }

      // 为每个业务条目生成一张待确认卡片
      data.items.forEach((item: any) => {
        const card: VoucherCard = {
          bizType: item.bizType || 'OTHER',
          summary: item.summary || '',
          date: data.voucherDate || '',
          confidenceLevel: data.confidenceLevel || 'MEDIUM',
          entries: (item.entries || []).map((e: any) => ({
            direction: e.direction,
            subjectCode: e.subjectCode,
            subjectName: e.subjectName,
            amount: e.amount,
            summary: e.summary || '',
            aux: e.aux || null,
          })),
          confirmed: false,
          confirming: false,
          costAmount: null,
        }
        // user_note 提示信息（如：请录入销售成本）
        if (data.userNote) {
          messages.value.push({ role: 'ai', content: '💡 ' + data.userNote })
        }
        messages.value.push({ role: 'ai', voucherCard: card })
      })
    } else {
      messages.value.push({
        role: 'ai',
        content: json.msg || '系统异常，服务暂不可用，请稍后再试',
      })
    }
  } catch (e: any) {
    messages.value = messages.value.filter(m => !m.loading)
    messages.value.push({ role: 'ai', content: '系统异常，服务暂不可用，请稍后再试' })
    currentConv.value!.status = 'active'
  }
  nextTick(scrollToBottom)
}

async function handleModification(text: string) {
  // 找到未确认的凭证卡片
  const lastCardIdx = messages.value.findLastIndex(m => m.voucherCard && !m.voucherCard.confirmed)
  if (lastCardIdx === -1) {
    messages.value.push({ role: 'ai', content: '当前没有待确认的凭证卡片。请先输入新的业务描述。' })
    nextTick(scrollToBottom)
    return
  }

  messages.value.push({ role: 'ai', loading: true, content: '正在根据您的反馈调整...' })
  nextTick(scrollToBottom)

  try {
    // 找到原始用户输入
    const originalInput = messages.value.find(m => m.role === 'user')?.content || ''
    const combinedInput = `${originalInput}。用户修改要求：${text}`

    const res = await fetch('/api/nlp/parse', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`,
      },
      body: JSON.stringify({ userInput: combinedInput, bizType: '' }),
    })
    const json = await res.json()
    messages.value = messages.value.filter(m => !m.loading)

    if (json.code === 0 && json.data?.items?.length) {
      const data = json.data
      // 移除旧的未确认卡片
      messages.value = messages.value.filter(m => !(m.voucherCard && !m.voucherCard.confirmed))

      data.items.forEach((item: any) => {
        const card: VoucherCard = {
          bizType: item.bizType || 'OTHER',
          summary: item.summary || '',
          date: data.voucherDate || '',
          confidenceLevel: data.confidenceLevel || 'MEDIUM',
          entries: (item.entries || []).map((e: any) => ({
            direction: e.direction,
            subjectCode: e.subjectCode,
            subjectName: e.subjectName,
            amount: e.amount,
            summary: e.summary || '',
            aux: e.aux || null,
          })),
          confirmed: false,
          confirming: false,
        }
        messages.value.push({ role: 'ai', content: '已根据您的反馈调整，请确认：', voucherCard: card })
      })
    } else {
      messages.value.push({ role: 'ai', content: json.msg || '系统异常，服务暂不可用，请稍后再试' })
    }
  } catch {
    messages.value = messages.value.filter(m => !m.loading)
    messages.value.push({ role: 'ai', content: '系统异常，服务暂不可用，请稍后再试' })
  }
  nextTick(scrollToBottom)
}

async function confirmVoucher(msgIdx: number) {
  const msg = messages.value[msgIdx]
  if (!msg?.voucherCard) return

  msg.voucherCard.confirming = true

  // 构建创建请求
  const card = msg.voucherCard
  const entries = card.entries.map(e => ({
    direction: e.direction,
    subjectCode: e.subjectCode,
    subjectName: e.subjectName,
    amount: e.amount.toString(),
    summary: e.summary,
    aux: e.aux || undefined,
  }))
  // 销售成本：追加"主营业务成本(借)" + "库存商品(贷)" 分录
  if (card.bizType === 'SALE' && card.costAmount && card.costAmount > 0) {
    entries.push(
      { direction: 'DEBIT', subjectCode: '6401', subjectName: '主营业务成本', amount: card.costAmount.toString(), summary: '结转销售成本', aux: undefined },
      { direction: 'CREDIT', subjectCode: '1405', subjectName: '库存商品', amount: card.costAmount.toString(), summary: '结转销售成本', aux: undefined }
    )
  }
  const req = {
    voucherDate: card.date || new Date().toISOString().split('T')[0],
    items: [{ bizType: card.bizType, summary: card.summary, entries }],
  }

  try {
    const res = await fetch('/api/nlp/create-voucher', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`,
      },
      body: JSON.stringify(req),
    })
    const json = await res.json()

    if (json.code === 0 && json.data?.length) {
      const ids = json.data as number[]
      // 获取凭证详情
      const detailRes = await fetch(`/api/voucher/${ids[0]}`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
      })
      const detailJson = await detailRes.json()

      card.confirmed = true
      card.confirming = false
      card.voucherNo = detailJson.data?.voucherNo || `#${ids[0]}`
      if (!card.voucherNo) card.voucherNo = ''

      // 记录到对话
      currentConv.value!.voucherIds.push(ids[0])
      currentConv.value!.voucherNoMap[ids[0]] = card.voucherNo

      // 检查是否所有卡片都已确认
      const allConfirmed = messages.value.every(m => !m.voucherCard || m.voucherCard.confirmed)
      if (allConfirmed) {
        currentConv.value!.status = 'done'
      }

      ElMessage.success(`凭证 ${card.voucherNo} 生成成功`)
      saveConversations()
    } else {
      card.confirming = false
      ElMessage.error(json.msg || '系统异常，服务暂不可用，请稍后再试')
    }
  } catch {
    card.confirming = false
    ElMessage.error('系统异常，服务暂不可用，请稍后再试')
  }
}

function openVoucherDetail(id: string | number) {
  detailDrawerId.value = typeof id === 'number' ? id : Number(id)
  detailVisible.value = true
}

function sendQuickHint(text: string) {
  inputText.value = text
  sendMessage()
}

function handleEnterKey(e: KeyboardEvent) {
  if (e.shiftKey) return // Shift+Enter 换行
  e.preventDefault()
  sendMessage()
}

// ─── 批量导入 ───

function handleFileUpload(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // 文件大小检查 5MB
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 5MB')
    input.value = ''
    return
  }

  // 类型检查
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!['txt', 'md', 'csv', 'tsv'].includes(ext || '')) {
    ElMessage.error('仅支持 TXT、MD、CSV 格式')
    input.value = ''
    return
  }

  const formData = new FormData()
  formData.append('file', file)

  sending.value = true
  fetch('/api/nlp/parse-file', { method: 'POST', body: formData })
    .then(res => res.json())
    .then(json => {
      if (json.code === 0 && json.data?.items?.length) {
        batchItems.value = json.data.items.map((i: any) => ({
          index: i.index,
          text: i.text,
          checked: true,
        }))
        batchPanelVisible.value = true
        ElMessage.success(`已导入 ${json.data.total} 条业务描述`)
      } else {
        ElMessage.error(json.msg || '文件解析失败')
      }
    })
    .catch(() => ElMessage.error('文件上传失败'))
    .finally(() => {
      sending.value = false
      input.value = ''
    })
}

function batchSelectAll() {
  batchItems.value.forEach(i => i.checked = true)
}

function batchDeselectAll() {
  batchItems.value.forEach(i => i.checked = false)
}

async function batchParse() {
  const selected = batchItems.value.filter(i => i.checked)
  if (!selected.length) {
    ElMessage.warning('请至少选择一条业务')
    return
  }
  if (selected.length > 20) {
    ElMessage.warning('单次最多解析 20 条')
    return
  }

  if (!currentConv.value) startNewChat()

  // 关闭预览面板
  batchPanelVisible.value = false
  batchParsing.value = true
  sending.value = true

  // 添加用户消息
  const userMsg = `📋 批量导入 ${selected.length} 条业务`
  messages.value.push({ role: 'user', content: userMsg })
  nextTick(scrollToBottom)

  // AI loading
  messages.value.push({ role: 'ai', loading: true, content: `正在解析 ${selected.length} 条业务...` })
  nextTick(scrollToBottom)

  try {
    const res = await fetch('/api/nlp/parse-batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ inputs: selected.map(i => i.text) }),
    })
    const json = await res.json()

    // 移除 loading
    messages.value = messages.value.filter(m => !m.loading)

    if (json.code === 0 && json.data?.items) {
      const data = json.data
      let successCount = 0
      let failCount = 0

      for (const item of data.items) {
        if (item.status === 'SUCCESS' && item.result?.items?.length) {
          // 为每个业务条目生成凭证卡片
          for (const bizItem of item.result.items) {
            const card: VoucherCard = {
              bizType: bizItem.bizType || 'OTHER',
              summary: bizItem.summary || item.originalInput,
              date: item.result.voucherDate || '',
              confidenceLevel: item.result.confidenceLevel || 'MEDIUM',
              entries: (bizItem.entries || []).map((e: any) => ({
                direction: e.direction,
                subjectCode: e.subjectCode,
                subjectName: e.subjectName,
                amount: e.amount,
                summary: e.summary || '',
                aux: e.aux || null,
              })),
              confirmed: false,
              confirming: false,
              costAmount: null,
            }
            messages.value.push({ role: 'ai', voucherCard: card })
            successCount++
          }
        } else {
          failCount++
          messages.value.push({
            role: 'ai',
            content: `❌ 第 ${item.index} 条解析失败：${item.errorMsg || '未知错误'}\n   原文：${item.originalInput}`,
          })
        }
      }

      messages.value.push({
        role: 'ai',
        content: `✅ 批量解析完成：成功 ${successCount} 条，失败 ${failCount} 条`,
      })

      // 更新对话标题
      currentConv.value!.title = `批量导入 ${selected.length} 条`
    } else {
      messages.value.push({ role: 'ai', content: json.msg || '批量解析失败' })
    }
  } catch {
    messages.value = messages.value.filter(m => !m.loading)
    messages.value.push({ role: 'ai', content: '系统异常，批量解析失败' })
  }

  currentConv.value!.status = 'parsing'
  currentConv.value!.messages = [...messages.value]
  currentConv.value!.time = formatTime(new Date())
  batchParsing.value = false
  sending.value = false
  saveConversations()
  nextTick(scrollToBottom)
}

function scrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function formatMoney(val: number | string | null | undefined) {
  if (val === null || val === undefined) return '—'
  const num = typeof val === 'string' ? parseFloat(val) : val
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function sumDir(entries: CardEntry[], dir: string): number {
  return entries.filter(e => e.direction === dir).reduce((s, e) => s + e.amount, 0)
}

function isBalanced(entries: CardEntry[], costAmount?: number | null): boolean {
  const debit = sumDir(entries, 'DEBIT') + (costAmount || 0)
  const credit = sumDir(entries, 'CREDIT') + (costAmount || 0)
  return Math.abs(debit - credit) < 0.01
}

function bizTypeLabel(t: string) {
  return ({ PURCHASE: '采购', SALE: '销售', EXPENSE: '费用', RECEIVE: '收款', PAY: '付款', TRANSFER: '转账' })[t] || t
}

function bizTypeTagType(t: string) {
  return ({ PURCHASE: '', SALE: 'success', EXPENSE: 'warning', RECEIVE: '', PAY: 'danger', TRANSFER: 'info' })[t] || 'info'
}

function auxLabel(k: string) {
  return ({ customer: '客户', supplier: '供应商', department: '部门', project: '项目', employee: '员工' })[k] || k
}

function formatTime(d: Date) {
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// ─── 加载最近凭证历史 ───

async function loadRecentVouchers() {
  historyLoading.value = true
  try {
    const res = await fetch('/api/voucher/page?current=1&size=20&sourceType=NLP', {
      headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
    })
    const json = await res.json()
    if (json.code === 0 && json.data?.records) {
      // 把最近的 NLP 凭证转为历史对话
      const records = json.data.records as any[]
      // 按 voucherDate+id 分组（简化：每张凭证一个对话条目）
      const grouped = new Map<string, any[]>()
      records.forEach(r => {
        // 用 createdAt 按小时分组
        const hour = r.createdAt?.substring(0, 13) || r.id.toString()
        if (!grouped.has(hour)) grouped.set(hour, [])
        grouped.get(hour)!.push(r)
      })

      grouped.forEach((vouchers, key) => {
        const first = vouchers[0]
        const conv: Conversation = {
          id: 'hist-' + key,
          title: first.summary || '历史凭证',
          time: first.createdAt?.substring(11, 16) || '',
          status: 'done',
          messages: [],
          voucherIds: vouchers.map((v: any) => v.id),
          voucherNoMap: Object.fromEntries(vouchers.map((v: any) => [v.id, v.voucherNo])),
        }
        conversations.value.push(conv)
      })
    }
  } catch {
    // ignore
  } finally {
    historyLoading.value = false
  }
}

onMounted(async () => {
  window.addEventListener('resize', updateWidth)
  // 优先加载本地持久化的对话
  const loaded = loadConversations()
  // 首次使用或本地为空时，从服务端加载最近 NLP 凭证
  if (!loaded) {
    loadRecentVouchers()
  }
  inputRef.value?.focus()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateWidth)
})
</script>

<style scoped>
.chat-home {
  display: flex;
  height: 100%;
  overflow: hidden;
  background: #f5f6fa;
  box-sizing: border-box;
}

/* ─── 左侧边栏 ─── */

.sidebar {
  width: 280px;
  min-width: 280px;
  background: #fff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.sidebar-title {
  font-weight: 600;
  font-size: 15px;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conv-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.conv-item:hover { background: #f5f6fa; }
.conv-item.active { background: #e8f0fe; }

.conv-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
}
.conv-status-dot.active { background: #409eff; }
.conv-status-dot.done { background: #67c23a; }
.conv-status-dot.parsing { background: #e6a23c; animation: pulse 1s infinite; }

@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }

.conv-info { flex: 1; min-width: 0; }
.conv-summary {
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-vouchers { margin-top: 4px; }
.conv-time {
  font-size: 11px;
  color: #c0c4cc;
  flex-shrink: 0;
  margin-top: 2px;
}

/* ─── 右侧聊天区 ─── */

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  font-weight: 600;
  font-size: 15px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ─── 欢迎区 ─── */

.welcome-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.welcome-icon { font-size: 64px; }
.welcome-text { font-size: 16px; color: #606266; }

.welcome-hints {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}

.hint {
  padding: 10px 16px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
  transition: all 0.15s;
}
.hint:hover { border-color: #409eff; color: #409eff; }

/* ─── 消息 ─── */

.msg { display: flex; gap: 10px; max-width: 85%; }
.msg-user { justify-content: flex-end; align-self: flex-end; }
.msg-ai { align-self: flex-start; }

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #e8f0fe;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.msg-body { max-width: 100%; }

.msg-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.msg-bubble-user {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.msg-bubble-ai {
  background: #fff;
  color: #303133;
  border-bottom-left-radius: 4px;
  border: 1px solid #ebeef5;
}

.loading-bubble { padding: 14px 20px; }

.dot-loading { display: flex; gap: 4px; }
.dot-loading span {
  width: 6px; height: 6px; border-radius: 50%;
  background: #c0c4cc; animation: dot-bounce 1.2s infinite;
}
.dot-loading span:nth-child(2) { animation-delay: 0.2s; }
.dot-loading span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-bounce { 0%, 80%, 100% { transform: translateY(0); } 40% { transform: translateY(-6px); } }

/* ─── 凭证卡片 ─── */

.voucher-card {
  margin-top: 8px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  overflow: hidden;
  max-width: 620px;
}

.vc-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #f8f9fb;
  border-bottom: 1px solid #ebeef5;
}

.vc-date { font-size: 12px; color: #909399; }
.vc-summary { padding: 10px 14px; font-weight: 600; font-size: 14px; color: #303133; }

.vc-entries { margin: 0 10px 8px; }

.vc-cost-input {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  background: #ecf5ff;
  border-radius: 6px;
  margin: 0 10px 6px;
  flex-wrap: wrap;
}

.vc-cost-label {
  font-size: 13px;
  color: #409eff;
  font-weight: 500;
}

.vc-cost-hint {
  font-size: 12px;
  color: #909399;
}

.vc-footer {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 8px 14px;
  border-top: 1px dashed #ebeef5;
  font-size: 13px;
  color: #606266;
}

.vc-actions {
  padding: 8px 14px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.vc-warn { font-size: 12px; color: #e6a23c; }

.vc-confirmed {
  padding: 8px 14px 14px;
}

.dir-debit { color: #e6a23c; font-weight: 700; font-size: 13px; }
.dir-credit { color: #67c23a; font-weight: 700; font-size: 13px; }

.aux-tag {
  display: inline-block;
  font-size: 11px;
  color: #909399;
  background: #f4f4f5;
  border-radius: 3px;
  padding: 1px 4px;
  margin: 1px;
}

/* ─── 输入区 ─── */

.chat-input-area {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 16px 20px;
  background: #fff;
  border-top: 1px solid #e8e8e8;
}

.chat-input-area :deep(.el-textarea__inner) {
  border-radius: 12px;
  padding: 10px 14px;
}

.upload-btn {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
}

.send-btn {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
}

/* ─── 批量导入面板 ─── */
.batch-panel {
  background: #fff;
  border-top: 1px solid #e8e8e8;
  max-height: 260px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.batch-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: #f8f9fa;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.batch-panel-actions {
  display: flex;
  gap: 8px;
}

.batch-panel-list {
  overflow-y: auto;
  flex: 1;
}

.batch-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 20px;
  cursor: pointer;
  transition: background 0.15s;
  border-bottom: 1px solid #f0f0f0;
}

.batch-item:hover {
  background: #f0f7ff;
}

.batch-item.checked {
  background: #ecf5ff;
}

.batch-item-index {
  color: #999;
  font-size: 13px;
  min-width: 24px;
}

.batch-item-text {
  font-size: 13px;
  color: #333;
}

/* ─── 移动端响应式 ─── */
@media (max-width: 768px) {
  .chat-home {
    height: calc(100svh - 56px);
    position: relative;
  }

  /* 隐藏左侧历史面板 */
  .sidebar {
    display: none;
  }

  /* 聊天主区域全宽 */
  .chat-main {
    width: 100%;
    flex: 1;
  }

  /* 顶部欢迎提示竖排 */
  .welcome-bar {
    flex-direction: column;
    gap: 8px;
    padding: 12px;
  }

  /* 消息气泡缩小 */
  .msg-bubble {
    max-width: calc(100vw - 80px) !important;
    padding: 8px 12px !important;
    font-size: 14px !important;
  }

  .msg-assistant .msg-bubble {
    margin-left: 0 !important;
  }

  .msg-user .msg-bubble {
    margin-right: 0 !important;
  }

  /* 输入区固定底部 */
  .chat-messages {
    padding-bottom: 80px;
  }

  .chat-input-area {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    padding: 10px 12px;
    background: #fff;
    box-shadow: 0 -2px 8px rgba(0,0,0,0.08);
    z-index: 100;
  }

  /* 移动端历史按钮 */
  .mobile-history-btn {
    position: fixed;
    bottom: 80px;
    left: 12px;
    z-index: 99;
  }

  /* 凭证卡片在小屏 — 核心修复 */
  .voucher-card {
    max-width: 100% !important;
    width: 100%;
    padding: 0 !important;
    overflow: hidden;
  }

  /* 表头：允许换行 */
  .voucher-card .vc-header {
    flex-wrap: wrap;
    gap: 4px;
    font-size: 12px !important;
    padding: 8px 10px !important;
  }

  /* 摘要全宽 */
  .voucher-card .vc-summary {
    font-size: 13px !important;
    padding: 8px 10px !important;
    word-break: break-all;
  }

  /* 分录表格：横向滚动 */
  .voucher-card .vc-entries {
    overflow-x: auto;
    margin: 0 !important;
    width: 100%;
  }

  .voucher-card .vc-entries .el-table {
    min-width: 500px;
    font-size: 12px;
  }

  .voucher-card .vc-entries .el-table__body-wrapper {
    overflow-x: auto;
  }

  /* 辅助项标签：小屏不换行 */
  .voucher-card .aux-tag {
    font-size: 10px !important;
    padding: 1px 3px !important;
  }

  /* 底部信息：允许换行 */
  .voucher-card .vc-footer {
    flex-wrap: wrap;
    gap: 6px;
    font-size: 12px !important;
    padding: 6px 10px !important;
  }

  /* 操作按钮：紧凑排列 */
  .voucher-card .vc-actions {
    padding: 6px 10px 10px !important;
    flex-wrap: wrap;
    gap: 8px;
  }

  .drawer-inner {
    height: 100%;
    overflow-y: auto;
    padding: 0 12px;
  }
}
</style>
