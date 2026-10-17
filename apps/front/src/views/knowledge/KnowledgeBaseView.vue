<template>
  <div class="kb-view">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6" v-for="(item, key) in categoryLabels" :key="key">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.by_category[key] || 0 }}</div>
          <div class="stat-label">{{ item }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card total">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">知识片段总数</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="action-row">
      <!-- 检索测试 -->
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>🔍 检索测试</span>
              <el-button size="small" @click="doSearch" :loading="searchLoading" :disabled="!searchQuery">
                测试检索
              </el-button>
            </div>
          </template>

          <el-input
            v-model="searchQuery"
            placeholder="输入业务描述，如：购买办公用品、收到客户货款、计提工资..."
            size="large"
            clearable
            @keyup.enter="doSearch"
          >
            <template #append>
              <el-button :disabled="!searchQuery" @click="doSearch">搜索</el-button>
            </template>
          </el-input>

          <div v-if="searchResults.length > 0" class="results-list">
            <el-divider content-position="left">检索结果（{{ searchResults.length }} 条）</el-divider>
            <el-timeline>
              <el-timeline-item
                v-for="(item, idx) in searchResults"
                :key="idx"
                :color="getRelevanceColor(item.relevance)"
                :hollow="true"
              >
                <div class="result-item">
                  <div class="result-header">
                    <el-tag size="small" type="info">{{ categoryLabels[item.category] || item.category }}</el-tag>
                    <span class="result-source">{{ item.source }}</span>
                    <span class="result-score" :style="{ color: getRelevanceColor(item.relevance) }">
                      {{ (item.relevance * 100).toFixed(0) }}% 匹配
                    </span>
                  </div>
                  <div class="result-heading" v-if="item.heading">{{ item.heading }}</div>
                  <div class="result-content">{{ item.content }}</div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>

          <el-empty v-else-if="searched && searchResults.length === 0" description="未找到相关知识" />
        </el-card>
      </el-col>

      <!-- 右侧：操作 + 文件列表 -->
      <el-col :span="10">
        <!-- 重新导入 -->
        <el-card class="import-card">
          <template #header>
            <div class="card-header">
              <span>📚 知识库维护</span>
              <el-button type="primary" size="small" @click="doImport" :loading="importLoading">
                重新导入
              </el-button>
            </div>
          </template>
          <p class="import-desc">
            从 <code>knowledge/</code> 目录导入所有 Markdown 知识文件，分块后存入 ChromaDB 向量数据库。
          </p>
          <el-alert v-if="importMsg" :title="importMsg" :type="importSuccess ? 'success' : 'danger'" show-icon :closable="false" />
        </el-card>

        <!-- 知识文件列表 -->
        <el-card class="files-card">
          <template #header>
            <span>📁 已加载的知识文件</span>
          </template>
          <el-table :data="knowledgeFiles" stripe size="small" :show-header="true">
            <el-table-column prop="name" label="文件名" />
            <el-table-column prop="category" label="分类" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ categoryLabels[row.category] || row.category }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="count" label="片段数" width="80" align="center" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { knowledgeApi } from '@/api/knowledge'

// 分类标签
const categoryLabels: Record<string, string> = {
  standard: '会计准则',
  tax: '税法规定',
  subject: '科目说明',
  template: '分录模板',
  tax_rate: '税率信息',
  history: '历史分录',
}

// 静态知识文件信息（与 knowledge/ 目录对应）
const knowledgeFiles = [
  { name: '常见业务分录模板.md', category: 'template', count: 62 },
  { name: '会计科目说明.md', category: 'subject', count: 31 },
  { name: '增值税税率.md', category: 'tax_rate', count: 5 },
  { name: '企业所得税税率.md', category: 'tax_rate', count: 7 },
  { name: '关键准则条款.md', category: 'standard', count: 13 },
  { name: '税法要点.md', category: 'tax', count: 12 },
]

// 统计数据
const stats = reactive({ total: 0, by_category: {} as Record<string, number> })

// 检索
const searchQuery = ref('')
const searchResults = ref<any[]>([])
const searched = ref(false)
const searchLoading = ref(false)

async function doSearch() {
  if (!searchQuery.value.trim()) return
  searchLoading.value = true
  searched.value = false
  try {
    const res = await knowledgeApi.search(searchQuery.value)
    searchResults.value = res.results || []
    searched.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '检索失败')
  } finally {
    searchLoading.value = false
  }
}

// 导入
const importLoading = ref(false)
const importMsg = ref('')
const importSuccess = ref(false)

async function doImport() {
  importLoading.value = true
  importMsg.value = ''
  try {
    const res = await knowledgeApi.reload()
    if (res.error) throw new Error(res.error)
    importMsg.value = `导入完成：共 ${res.total} 条知识片段`
    importSuccess.value = true
    stats.total = res.total
    stats.by_category = res.by_category || {}
    ElMessage.success('知识库导入成功')
  } catch (e: any) {
    importMsg.value = `导入失败：${e?.message || e}`
    importSuccess.value = false
    ElMessage.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

// 相关度颜色
function getRelevanceColor(relevance: number): string {
  if (relevance >= 0.6) return '#67c23a'
  if (relevance >= 0.5) return '#e6a23c'
  return '#909399'
}

// 加载统计
async function loadStats() {
  try {
    const res = await knowledgeApi.stats()
    if (res.error) return
    stats.total = res.total
    stats.by_category = res.by_category || {}
  } catch (e) {
    // ignore
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.kb-view {
  padding: 20px;
}

.stats-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
  cursor: default;
}

.stat-card.total {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.stat-card.total .stat-label {
  color: rgba(255, 255, 255, 0.85);
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.action-row .el-col {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.import-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 12px;
}

.import-desc code {
  background: #f5f7fa;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12px;
}

.results-list {
  margin-top: 16px;
}

.result-item {
  padding-bottom: 4px;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.result-source {
  font-size: 12px;
  color: #909399;
  flex: 1;
}

.result-score {
  font-size: 12px;
  font-weight: bold;
}

.result-heading {
  font-size: 13px;
  color: #409eff;
  margin-bottom: 4px;
}

.result-content {
  font-size: 13px;
  color: #303133;
  line-height: 1.5;
  background: #f5f7fa;
  padding: 8px 10px;
  border-radius: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
