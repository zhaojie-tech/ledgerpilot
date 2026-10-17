<template>
  <el-container class="app-layout">
    <!-- 移动端：顶部汉堡菜单按钮 -->
    <div v-if="isMobile" class="mobile-header-bar">
      <el-button text @click="drawerVisible = true" class="hamburger-btn">
        <el-icon size="22"><Menu /></el-icon>
      </el-button>
      <span class="mobile-logo">LedgerPilot</span>
      <div style="width: 40px"></div>
    </div>

    <!-- 移动端：侧边栏抽屉 -->
    <el-drawer
      v-if="isMobile"
      v-model="drawerVisible"
      direction="ltr"
      :with-header="false"
      size="260px"
      class="mobile-sidebar-drawer"
    >
      <div class="mobile-sidebar-inner">
        <div class="logo">LedgerPilot</div>
        <el-menu
          :default-active="$route.path"
          :collapse="false"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
          @select="drawerVisible = false"
        >
          <el-menu-item index="/ledgerpilot">
            <el-icon><MagicStick /></el-icon><span>AI智能做账</span>
          </el-menu-item>
          <el-menu-item index="/voucher">
            <el-icon><Document /></el-icon><span>凭证管理</span>
          </el-menu-item>
          <el-menu-item index="/opening-balance">
            <el-icon><Wallet /></el-icon><span>期初余额</span>
          </el-menu-item>
          <el-menu-item index="/subject">
            <el-icon><FolderOpened /></el-icon><span>科目管理</span>
          </el-menu-item>
          <el-menu-item index="/report">
            <el-icon><DataAnalysis /></el-icon><span>报表中心</span>
          </el-menu-item>
          <el-menu-item index="/tax">
            <el-icon><Money /></el-icon><span>税务中心</span>
          </el-menu-item>
          <el-menu-item index="/knowledge">
            <el-icon><Notebook /></el-icon><span>知识库管理</span>
          </el-menu-item>
          <el-menu-item index="/settings">
            <el-icon><Setting /></el-icon><span>系统设置</span>
          </el-menu-item>
        </el-menu>
      </div>
    </el-drawer>

    <!-- 桌面端：左侧导航 -->
    <el-aside v-if="!isMobile" :width="collapsed ? '64px' : '200px'" class="sidebar">
      <div class="logo">
        <span v-if="!collapsed">LedgerPilot</span>
        <span v-else>SB</span>
      </div>
      <el-menu
        :default-active="$route.path"
        :collapse="collapsed"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/ledgerpilot">
          <el-icon><MagicStick /></el-icon>
          <span>AI智能做账</span>
        </el-menu-item>
        <el-menu-item index="/voucher">
          <el-icon><Document /></el-icon>
          <span>凭证管理</span>
        </el-menu-item>
        <el-menu-item index="/opening-balance">
          <el-icon><Wallet /></el-icon>
          <span>期初余额</span>
        </el-menu-item>
        <el-menu-item index="/subject">
          <el-icon><FolderOpened /></el-icon>
          <span>科目管理</span>
        </el-menu-item>
        <el-menu-item index="/report">
          <el-icon><DataAnalysis /></el-icon>
          <span>报表中心</span>
        </el-menu-item>
        <el-menu-item index="/tax">
          <el-icon><Money /></el-icon>
          <span>税务中心</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Notebook /></el-icon>
          <span>知识库管理</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <div class="inner-container">
      <!-- 顶部栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-button v-if="!isMobile" text @click="toggleCollapse">
            <el-icon size="20"><Expand v-if="collapsed" /><Fold v-else /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="$route.meta.title">{{ $route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" style="margin-right: 8px">{{ userStore.userInfo?.username?.[0] || 'U' }}</el-avatar>
              <span>{{ userStore.userInfo?.username || '用户' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="main-content" :class="{ 'main-content--fullscreen': $route.path === '/ledgerpilot' }">
        <router-view />
      </el-main>
    </div>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import {
  MagicStick, Document, Tickets, FolderOpened,
  DataAnalysis, Money, Setting, Expand, Fold, Wallet, Menu, Notebook
} from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const collapsed = computed(() => appStore.sidebarCollapsed)
const drawerVisible = ref(false)
const windowWidth = ref(window.innerWidth)

const isMobile = computed(() => windowWidth.value <= 768)

function updateWidth() {
  windowWidth.value = window.innerWidth
}

function toggleCollapse() {
  appStore.toggleSidebar()
}

function handleCommand(cmd: string) {
  if (cmd === 'logout') {
    userStore.logout()
  }
}

onMounted(() => {
  window.addEventListener('resize', updateWidth)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateWidth)
})
</script>

<style>
/* 全局覆盖：el-main 在 app-layout 下禁用滚动和 padding（仅聊天页生效） */
.app-layout .main-content--fullscreen {
  padding: 0 !important;
  overflow: hidden !important;
}

@media (max-width: 768px) {
  .app-layout .main-content--fullscreen {
    padding-top: 2px !important;
  }
}
</style>

<style scoped>
.app-layout {
  height: 100vh;
  overflow: hidden;
}

/* 内层容器：替代被移除的 el-container */
.inner-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* ─── 移动端顶部栏 ─── */
.mobile-header-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  background: #304156;
  color: #fff;
  padding: 0 12px;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
}

.mobile-logo {
  font-size: 17px;
  font-weight: bold;
  color: #fff;
  letter-spacing: 1px;
}

.hamburger-btn {
  color: #fff !important;
}

/* ─── 移动端抽屉 ─── */
.mobile-sidebar-drawer :deep(.el-drawer__body) {
  padding: 0;
}

.mobile-sidebar-inner {
  height: 100%;
  overflow-y: auto;
}

/* ─── 桌面端侧边栏 ─── */
.sidebar {
  background-color: #304156;
  transition: width 0.3s;
  overflow-x: hidden;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 20px;
  font-weight: bold;
  background-color: #263445;
}

.header {
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.main-content {
  background-color: #f0f2f5;
  padding: 16px;
}

/* ─── 移动端主内容区 ─── */
@media (max-width: 768px) {
  .app-layout {
    height: 100svh;
    overflow: hidden;
  }

  .main-content {
    padding: 12px;
    padding-top: 72px; /* 躲开固定顶部栏 */
  }
}
</style>
