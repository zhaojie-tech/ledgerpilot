<template>
  <div class="subject-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="page-title">
        <h2>科目管理</h2>
        <span class="subject-count">共 {{ totalCount }} 个科目</span>
      </div>
      <div class="header-actions">
        <el-button type="primary" size="default" @click="showInitDialog = true">
          <el-icon><Download /></el-icon> 从模板初始化
        </el-button>
        <el-button type="primary" size="default" @click="openCreate(null)">
          <el-icon><Plus /></el-icon> 新增科目
        </el-button>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <!-- 移动端：科目树抽屉按钮 -->
      <el-button v-if="isMobile" size="small" @click="treeDrawerVisible = true">
        <el-icon><Menu /></el-icon> 科目
      </el-button>

      <el-input
        v-model="searchKeyword"
        placeholder="搜索科目编码或名称..."
        prefix-icon="Search"
        clearable
        :style="isMobile ? 'width: 100%' : 'width: 280px'"
        @input="onSearch"
      />
      <el-select v-if="!isMobile" v-model="filterType" placeholder="全部类型" clearable style="width: 140px" @change="onSearch">
        <el-option v-for="t in SUBJECT_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <el-select v-if="!isMobile" v-model="filterEnabled" placeholder="状态" clearable style="width: 120px" @change="onSearch">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <!-- 移动端简化过滤器 -->
      <el-select v-if="isMobile" v-model="filterType" placeholder="类型" clearable style="width: 100px" @change="onSearch">
        <el-option v-for="t in SUBJECT_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
    </div>

    <!-- 主体区域：左侧树 + 右侧表格 -->
    <div class="main-area">
      <!-- 桌面端：左侧科目树 -->
      <div v-if="!isMobile" class="tree-panel">
        <div class="panel-title">科目导航</div>
        <div class="all-subject-btn" :class="{ active: !currentNode }" @click="showAll">
          <el-icon><View /></el-icon> 全部科目
        </div>
        <el-scrollbar>
          <el-tree
            ref="treeRef"
            :data="treeData"
            :props="{ label: 'subjectName', children: 'children', isLeaf: (d: SubjectVO) => !d.children?.length }"
            node-key="id"
            :expand-on-click-node="false"
            :default-expand-all="true"
            highlight-current
            @node-click="onNodeClick"
            class="subject-tree"
          >
            <template #default="{ data }">
              <span class="tree-node" :class="{ 'is-disabled': data.isEnabled === 0 }">
                <span class="node-code">{{ data.subjectCode }}</span>
                <span class="node-name">{{ data.subjectName }}</span>
                <el-tag
                  v-if="data.isEnabled === 0"
                  size="small"
                  type="info"
                  class="node-disabled-tag"
                >停</el-tag>
              </span>
            </template>
          </el-tree>
          <div v-if="loading" class="tree-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
          </div>
        </el-scrollbar>
      </div>

      <!-- 移动端：树抽屉 -->
      <el-drawer
        v-if="isMobile"
        v-model="treeDrawerVisible"
        title="科目导航"
        direction="ltr"
        size="280px"
        :with-header="true"
      >
        <div class="all-subject-btn" :class="{ active: !currentNode }" @click="showAll; treeDrawerVisible = false">
          <el-icon><View /></el-icon> 全部科目
        </div>
        <el-tree
          ref="treeRef"
          :data="treeData"
          :props="{ label: 'subjectName', children: 'children', isLeaf: (d: SubjectVO) => !d.children?.length }"
          node-key="id"
          :expand-on-click-node="false"
          :default-expand-all="true"
          highlight-current
          @node-click="(node: SubjectVO) => { onNodeClick(node); treeDrawerVisible = false }"
          class="subject-tree"
        >
          <template #default="{ data }">
            <span class="tree-node" :class="{ 'is-disabled': data.isEnabled === 0 }">
              <span class="node-code">{{ data.subjectCode }}</span>
              <span class="node-name">{{ data.subjectName }}</span>
            </span>
          </template>
        </el-tree>
      </el-drawer>

      <!-- 右侧科目明细表 -->
      <div class="table-panel">
        <div class="panel-title">
          {{ currentNode ? `「${currentNode.subjectName}」的下级科目` : '全部科目' }}
          <span v-if="tableData.length" class="table-count">（{{ tableData.length }} 条）</span>
        </div>

        <!-- 表格工具栏 -->
        <div v-if="currentNode && !isMobile" class="table-toolbar">
          <el-button size="small" type="primary" @click="openCreate(currentNode)">
            <el-icon><Plus /></el-icon> 新增下级
          </el-button>
          <el-button size="small" @click="openEdit(currentNode)">
            <el-icon><Edit /></el-icon> 编辑
          </el-button>
          <el-button size="small" type="danger" @click="handleDelete(currentNode.id)" :disabled="(currentNode.children?.length ?? 0) > 0">
            <el-icon><Delete /></el-icon> 删除
          </el-button>
          <el-button
            size="small"
            :type="currentNode.isEnabled === 1 ? 'warning' : 'success'"
            @click="toggleEnabled(currentNode)"
          >
            {{ currentNode.isEnabled === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button size="small" @click="currentNode = null; loadTableData()">
            <el-icon><View /></el-icon> 显示全部
          </el-button>
        </div>

        <!-- 桌面端：科目表格 -->
        <el-table
          v-if="!isMobile"
          :data="tableData"
          v-loading="tableLoading"
          stripe
          size="small"
          class="subject-table"
          row-key="id"
          :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
          default-expand-all
        >
          <el-table-column label="科目编码" width="120" align="center">
            <template #default="{ row }">
              <span class="subject-code">{{ row.subjectCode }}</span>
            </template>
          </el-table-column>

          <el-table-column label="科目名称" min-width="180">
            <template #default="{ row }">
              <span class="subject-name">{{ row.subjectName }}</span>
            </template>
          </el-table-column>

          <el-table-column label="类型" width="80" align="center">
            <template #default="{ row }">
              <el-tag
                :type="SUBJECT_TYPE_TAG[row.subjectType]"
                size="small"
              >{{ SUBJECT_TYPE_LABEL[row.subjectType] || row.subjectType }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="方向" width="70" align="center">
            <template #default="{ row }">
              <span :class="row.direction === 'DEBIT' ? 'dir-debit' : 'dir-credit'">
                {{ row.direction === 'DEBIT' ? '借' : '贷' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="末级" width="60" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.isDetail === 1" type="success" size="small">是</el-tag>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>

          <el-table-column label="辅助核算" width="150">
            <template #default="{ row }">
              <template v-if="row.auxDimensions">
                <el-tag
                  v-for="aux in row.auxDimensions.split(',')"
                  :key="aux"
                  size="small"
                  type="info"
                  class="aux-tag"
                >{{ auxLabel(aux) }}</el-tag>
              </template>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'" size="small">
                {{ row.isEnabled === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" size="small" @click="openCreate(row)">增下级</el-button>
              <el-button
                link
                :type="row.isEnabled === 1 ? 'warning' : 'success'"
                size="small"
                @click="toggleEnabled(row)"
              >{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
              <el-button
                link
                type="danger"
                size="small"
                @click="handleDelete(row.id)"
                :disabled="!!row.children?.length"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 移动端：科目卡片列表 -->
        <div v-if="isMobile" v-loading="tableLoading" class="subject-card-list">
          <div v-if="!tableData.length && !tableLoading" class="mobile-empty">
            <el-empty description="暂无科目" :image-size="60" />
          </div>
          <div
            v-for="row in tableData"
            :key="row.id"
            class="subj-card"
            :class="{ 'is-disabled': row.isEnabled === 0 }"
          >
            <!-- 卡片头部：编码 + 名称 + 末级标签 -->
            <div class="sc-head">
              <div class="sc-info">
                <span class="sc-code">{{ row.subjectCode }}</span>
                <span class="sc-name">{{ row.subjectName }}</span>
              </div>
              <div class="sc-tags">
                <el-tag
                  :type="SUBJECT_TYPE_TAG[row.subjectType]"
                  size="small"
                >{{ SUBJECT_TYPE_LABEL[row.subjectType] }}</el-tag>
                <el-tag
                  :type="row.direction === 'DEBIT' ? 'success' : 'danger'"
                  size="small"
                >{{ row.direction === 'DEBIT' ? '借' : '贷' }}</el-tag>
                <el-tag v-if="row.isDetail === 1" type="warning" size="small">末级</el-tag>
              </div>
            </div>

            <!-- 辅助核算 & 状态 -->
            <div class="sc-meta">
              <span v-if="row.auxDimensions" class="sc-aux">
                <el-tag
                  v-for="aux in row.auxDimensions.split(',')"
                  :key="aux"
                  size="small"
                  type="info"
                  class="aux-tag"
                >{{ auxLabel(aux) }}</el-tag>
              </span>
              <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'" size="small">
                {{ row.isEnabled === 1 ? '启用' : '停用' }}
              </el-tag>
            </div>

            <!-- 操作按钮 -->
            <div class="sc-actions">
              <el-button type="primary" size="small" plain @click="openEdit(row)">编辑</el-button>
              <el-button type="success" size="small" plain @click="openCreate(row)">增下级</el-button>
              <el-button
                :type="row.isEnabled === 1 ? 'warning' : 'success'"
                size="small"
                plain
                @click="toggleEnabled(row)"
              >{{ row.isEnabled === 1 ? '停用' : '启用' }}</el-button>
              <el-button
                type="danger"
                size="small"
                plain
                @click="handleDelete(row.id)"
                :disabled="!!row.children?.length"
              >删除</el-button>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <el-empty v-if="!isMobile && !tableLoading && tableData.length === 0" description="暂无科目" class="table-empty">
          <el-button type="primary" @click="showInitDialog = true">从模板初始化</el-button>
          <el-button @click="openCreate(null)">手动新增</el-button>
        </el-empty>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? (parentSubject ? `新增「${parentSubject.subjectName}」的下级科目` : '新增科目') : '编辑科目'"
      width="580px"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="科目编码" prop="subjectCode" v-if="dialogMode === 'create'">
          <el-input v-model="form.subjectCode" placeholder="如：1002" />
          <span v-if="parentSubject" class="form-tip">上级：{{ parentSubject.subjectCode }}</span>
        </el-form-item>

        <el-form-item label="科目名称" prop="subjectName">
          <el-input v-model="form.subjectName" placeholder="如：银行存款" />
        </el-form-item>

        <el-form-item label="科目类型" prop="subjectType">
          <el-select v-model="form.subjectType" placeholder="请选择类型" style="width: 100%">
            <el-option v-for="t in SUBJECT_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="余额方向" prop="direction">
          <el-radio-group v-model="form.direction">
            <el-radio label="DEBIT">借方</el-radio>
            <el-radio label="CREDIT">贷方</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="辅助核算" prop="auxDimensions">
          <el-select
            v-model="form.auxDimensions"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择辅助核算维度（可选多个）"
            style="width: 100%"
          >
            <el-option v-for="a in AUX_DIMENSION_OPTIONS" :key="a.value" :label="a.label" :value="a.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="末级科目" prop="isDetail">
          <el-switch v-model="form.isDetail" :active-value="1" :inactive-value="0" />
          <span class="form-tip">设为末级后不能再添加下级科目</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 从模板初始化弹窗 -->
    <el-dialog v-model="showInitDialog" title="从模板初始化科目" width="420px">
      <el-alert
        title="初始化将清空现有科目，请谨慎操作"
        type="warning"
        :closable="false"
        style="margin-bottom: 16px"
      />
      <el-form label-width="120px">
        <el-form-item label="会计制度模板">
          <el-select v-model="initSystemCode" placeholder="请选择模板" style="width: 100%">
            <el-option label="小企业会计准则" value="SMALL_BUSINESS" />
            <el-option label="企业会计准则" value="CAS" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showInitDialog = false">取消</el-button>
        <el-button type="primary" :loading="initLoading" @click="doInit">确认初始化</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Edit, Delete, Download, View, Loading, Menu
} from '@element-plus/icons-vue'
import {
  getSubjectTree,
  createSubject,
  updateSubject,
  deleteSubject,
  initFromTemplate,
  toggleSubjectEnabled,
  SUBJECT_TYPE_OPTIONS,
  AUX_DIMENSION_OPTIONS,
  SUBJECT_TYPE_TAG,
  SUBJECT_TYPE_LABEL,
  type SubjectVO,
  type SubjectCreateRequest,
  type SubjectUpdateRequest,
} from '@/api/subject'

// ---- state ----
const loading = ref(false)
const tableLoading = ref(false)
const actionLoading = ref(false)
const initLoading = ref(false)
const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value <= 768)
const treeDrawerVisible = ref(false)

function updateWidth() { windowWidth.value = window.innerWidth }

const treeData = ref<SubjectVO[]>([])
const allSubjects = ref<SubjectVO[]>([]) // 平铺所有科目用于过滤
const tableData = ref<SubjectVO[]>([])
const currentNode = ref<SubjectVO | null>(null)
const treeRef = ref()

// 搜索/过滤
const searchKeyword = ref('')
const filterType = ref('')
const filterEnabled = ref<number | ''>('')

// 弹窗
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const parentSubject = ref<SubjectVO | null>(null)
const editingSubject = ref<SubjectVO | null>(null)
const showInitDialog = ref(false)
const initSystemCode = ref('SMALL_BUSINESS')

// 表单
const formRef = ref()
const form = ref<{
  subjectCode: string
  subjectName: string
  subjectType: string
  direction: string
  auxDimensions: string[]
  isDetail: number
}>({
  subjectCode: '',
  subjectName: '',
  subjectType: '',
  direction: 'DEBIT',
  auxDimensions: [],
  isDetail: 0,
})

const formRules = {
  subjectCode: [{ required: true, message: '请输入科目编码', trigger: 'blur' }],
  subjectName: [{ required: true, message: '请输入科目名称', trigger: 'blur' }],
  subjectType: [{ required: true, message: '请选择科目类型', trigger: 'change' }],
  direction: [{ required: true, message: '请选择余额方向', trigger: 'change' }],
}

// ---- computed ----
const totalCount = computed(() => allSubjects.value.length)

// ---- methods ----
const loadTree = async () => {
  loading.value = true
  try {
    const res = await getSubjectTree()
    treeData.value = res.data || []
    // 扁平化所有科目用于前端过滤
    allSubjects.value = flattenSubjects(treeData.value)
    loadTableData()
  } catch {
    ElMessage.error('加载科目树失败')
  } finally {
    loading.value = false
  }
}

// 递归扁平化
const flattenSubjects = (list: SubjectVO[]): SubjectVO[] => {
  const result: SubjectVO[] = []
  for (const item of list) {
    result.push(item)
    if (item.children?.length) {
      result.push(...flattenSubjects(item.children))
    }
  }
  return result
}

// 加载右侧表格数据（过滤后的子树或全部）
const loadTableData = () => {
  let data: SubjectVO[] = []

  if (!currentNode.value) {
    // 显示全部（根节点）
    data = [...allSubjects.value].filter(s => !s.parentCode || s.parentCode === '')
    if (searchKeyword.value || filterType.value || filterEnabled.value !== '') {
      data = filterSubjects(allSubjects.value)
    }
  } else {
    // 显示当前选中节点的所有下级（递归收集）
    const keyword = searchKeyword.value.toLowerCase()
    data = getDescendants(currentNode.value)
    if (keyword || filterType.value || filterEnabled.value !== '') {
      data = filterSubjects(data)
    }
  }

  // 过滤逻辑
  tableData.value = data
}

const filterSubjects = (list: SubjectVO[]): SubjectVO[] => {
  const keyword = searchKeyword.value.toLowerCase()
  return list.filter(s => {
    const matchKeyword = !keyword ||
      s.subjectCode.toLowerCase().includes(keyword) ||
      s.subjectName.toLowerCase().includes(keyword)
    const matchType = !filterType.value || s.subjectType === filterType.value
    const matchEnabled = filterEnabled.value === '' || s.isEnabled === filterEnabled.value
    return matchKeyword && matchType && matchEnabled
  })
}

const getDescendants = (node: SubjectVO): SubjectVO[] => {
  const result: SubjectVO[] = []
  if (!node.children?.length) return result
  for (const child of node.children) {
    result.push(child)
    result.push(...getDescendants(child))
  }
  return result
}

// 搜索防抖
let searchTimer: ReturnType<typeof setTimeout>
const onSearch = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadTableData, 300)
}

// 点击树节点
const onNodeClick = (node: SubjectVO) => {
  currentNode.value = node
  searchKeyword.value = ''
  filterType.value = ''
  filterEnabled.value = ''
  loadTableData()
}

// 显示全部科目
const showAll = () => {
  currentNode.value = null
  searchKeyword.value = ''
  filterType.value = ''
  filterEnabled.value = ''
  treeRef.value?.setCurrentKey(null)
  loadTableData()
}

// 打开新增
const openCreate = (parent: SubjectVO | null) => {
  dialogMode.value = 'create'
  parentSubject.value = parent
  editingSubject.value = null
  form.value = {
    subjectCode: '',
    subjectName: '',
    subjectType: '',
    direction: 'DEBIT',
    auxDimensions: [],
    isDetail: 0,
  }
  dialogVisible.value = true
}

// 打开编辑
const openEdit = (row: SubjectVO) => {
  dialogMode.value = 'edit'
  editingSubject.value = row
  parentSubject.value = null
  form.value = {
    subjectCode: row.subjectCode,
    subjectName: row.subjectName,
    subjectType: row.subjectType,
    direction: row.direction,
    auxDimensions: row.auxDimensions ? row.auxDimensions.split(',') : [],
    isDetail: row.isDetail,
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
}

// 提交表单
const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  actionLoading.value = true
  try {
    if (dialogMode.value === 'create') {
      const req: SubjectCreateRequest = {
        subjectCode: form.value.subjectCode,
        subjectName: form.value.subjectName,
        subjectType: form.value.subjectType,
        parentCode: parentSubject.value?.subjectCode || undefined,
        direction: form.value.direction,
        isDetail: form.value.isDetail,
        auxDimensions: form.value.auxDimensions.join(',') || undefined,
      }
      await createSubject(req)
      ElMessage.success('创建成功')
    } else {
      const req: SubjectUpdateRequest = {
        id: editingSubject.value!.id,
        subjectName: form.value.subjectName,
        subjectType: form.value.subjectType,
        auxDimensions: form.value.auxDimensions.join(',') || undefined,
        isEnabled: editingSubject.value!.isEnabled,
        sysSort: editingSubject.value!.sysSort,
      }
      await updateSubject(req)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    await loadTree()
    // 恢复树选中状态
    if (currentNode.value) {
      treeRef.value?.setCurrentKey(currentNode.value.id)
    }
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    actionLoading.value = false
  }
}

// 删除
const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定删除该科目吗？删除后将无法恢复。', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteSubject(id)
    ElMessage.success('删除成功')
    await loadTree()
    if (currentNode.value?.id === id) {
      currentNode.value = null
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

// 启用/停用
const toggleEnabled = async (row: SubjectVO) => {
  try {
    await toggleSubjectEnabled(row.id)
    ElMessage.success(row.isEnabled === 1 ? '已停用' : '已启用')
    await loadTree()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

// 从模板初始化
const doInit = async () => {
  initLoading.value = true
  try {
    await initFromTemplate(initSystemCode.value)
    ElMessage.success('初始化成功')
    showInitDialog.value = false
    await loadTree()
  } catch (e: any) {
    ElMessage.error(e.message || '初始化失败')
  } finally {
    initLoading.value = false
  }
}

// 辅助核算维度中文
const auxLabel = (val: string) => {
  return AUX_DIMENSION_OPTIONS.find(a => a.value === val)?.label || val
}

// 挂载
onMounted(() => {
  window.addEventListener('resize', updateWidth)
  loadTree()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateWidth)
})
</script>

<style scoped>
.subject-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px 24px;
  gap: 16px;
  background: #f5f7fa;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 16px 20px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}

.page-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.subject-count {
  font-size: 13px;
  color: #909399;
  padding: 2px 8px;
  background: #f4f4f5;
  border-radius: 10px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.search-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  background: #fff;
  padding: 12px 20px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}

.main-area {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.tree-panel {
  width: 260px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-title {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-count {
  font-weight: 400;
  color: #909399;
  font-size: 12px;
}

.table-panel {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
}

.subject-table {
  flex: 1;
  overflow: auto;
}

.table-loading {
  display: flex;
  justify-content: center;
  padding: 40px;
  color: #909399;
}

.table-empty {
  padding: 60px 0;
}

.tree-loading {
  display: flex;
  justify-content: center;
  padding: 20px;
  color: #909399;
}

.subject-tree {
  padding: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  width: 100%;
}

.tree-node.is-disabled {
  color: #c0c4cc;
}

.all-subject-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  margin: 8px 12px 4px;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}

.all-subject-btn:hover {
  background: #f5f7fa;
  color: #409eff;
}

.all-subject-btn.active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}

.node-code {
  color: #409eff;
  font-family: 'Monaco', 'Consolas', monospace;
  font-size: 12px;
  flex-shrink: 0;
}

.node-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-disabled-tag {
  margin-left: 4px;
  font-size: 10px;
  padding: 0 4px;
  height: 16px;
  line-height: 16px;
}

.subject-code {
  font-family: 'Monaco', 'Consolas', monospace;
  color: #409eff;
  font-size: 13px;
}

.subject-name {
  color: #303133;
}

.dir-debit {
  color: #67c23a;
  font-weight: 600;
}

.dir-credit {
  color: #f56c6c;
  font-weight: 600;
}

.aux-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.text-muted {
  color: #c0c4cc;
}

.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

/* ── 移动端响应式 ── */
@media (max-width: 768px) {
  .subject-page {
    padding: 8px;
    gap: 8px;
    height: auto;
    min-height: 100%;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
    padding: 12px 14px;
  }

  .page-title h2 {
    font-size: 16px;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .search-bar {
    flex-wrap: wrap;
    gap: 8px;
    padding: 10px 14px;
  }

  .search-bar .el-input {
    flex: 1 1 auto;
  }

  .search-bar .el-select {
    flex: 0 0 auto;
  }

  .main-area {
    flex-direction: column;
    gap: 8px;
    height: auto;
  }

  .tree-panel {
    display: none; /* 移动端用抽屉代替 */
  }

  .table-panel {
    min-height: 200px;
  }

  .table-panel .panel-title {
    font-size: 13px;
    padding: 10px 12px;
  }

  /* 移动端科目卡片列表 */
  .subject-card-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    padding: 10px 12px;
    overflow-y: auto;
  }

  .subj-card {
    background: #fff;
    border: 1px solid #ebeef5;
    border-radius: 10px;
    padding: 12px;
  }

  .subj-card.is-disabled {
    opacity: 0.55;
  }

  .sc-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 8px;
  }

  .sc-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
    flex: 1;
  }

  .sc-code {
    font-family: 'Monaco', 'Consolas', monospace;
    font-size: 12px;
    color: #409eff;
  }

  .sc-name {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .sc-tags {
    display: flex;
    gap: 4px;
    flex-shrink: 0;
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .sc-meta {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
    margin-bottom: 10px;
  }

  .sc-aux {
    display: flex;
    gap: 4px;
    flex-wrap: wrap;
  }

  .sc-actions {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    border-top: 1px dashed #f0f0f0;
    padding-top: 8px;
  }

  .sc-actions .el-button {
    margin: 0;
    font-size: 12px;
    padding: 4px 8px;
  }

  .mobile-empty {
    text-align: center;
    padding: 40px 0;
  }
}
</style>
