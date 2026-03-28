<template>
  <div class="admin-layout">
    <aside class="admin-sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
      <div class="sidebar-header">
        <div class="logo" v-if="!appStore.sidebarCollapsed">
          <div class="logo-icon">🧠</div>
          <div class="logo-text">Calmara</div>
        </div>
        <div class="logo-mini" v-else>🧠</div>
      </div>
      
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        class="admin-menu"
        router
      >
        <el-menu-item index="/admin">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>仪表盘</template>
        </el-menu-item>
        
        <el-menu-item index="/admin/students">
          <el-icon><User /></el-icon>
          <template #title>学生管理</template>
        </el-menu-item>
        
        <el-menu-item index="/admin/alerts">
          <el-icon><Bell /></el-icon>
          <template #title>预警管理</template>
        </el-menu-item>
        
        <el-menu-item index="/admin/knowledge">
          <el-icon><Collection /></el-icon>
          <template #title>知识库</template>
        </el-menu-item>
        
        <el-menu-item index="/admin/statistics">
          <el-icon><TrendCharts /></el-icon>
          <template #title>统计分析</template>
        </el-menu-item>
        
        <el-menu-item index="/admin/finetune">
          <el-icon><Cpu /></el-icon>
          <template #title>模型微调</template>
        </el-menu-item>
        
        <el-menu-item index="/admin/settings">
          <el-icon><Setting /></el-icon>
          <template #title>系统设置</template>
        </el-menu-item>
      </el-menu>
      
      <div class="sidebar-toggle" @click="appStore.toggleSidebar">
        <el-icon>
          <Expand v-if="appStore.sidebarCollapsed" />
          <Fold v-else />
        </el-icon>
      </div>
    </aside>
    
    <div class="admin-main">
      <header class="admin-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/admin' }">管理后台</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        
        <div class="header-right">
          <el-button text @click="goToChat">
            <el-icon><ChatDotRound /></el-icon>
            <span>返回对话</span>
          </el-button>
          
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-dropdown">
              <div class="user-avatar">{{ userStore.username.charAt(0).toUpperCase() }}</div>
              <span class="user-name">{{ userStore.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人信息
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      
      <main class="admin-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { useUserStore, useAppStore } from '@/stores'
import { useRouter, useRoute } from 'vue-router'
import {
  DataAnalysis,
  User,
  Bell,
  Collection,
  TrendCharts,
  Cpu,
  Setting,
  Expand,
  Fold,
  ChatDotRound,
  ArrowDown,
  SwitchButton
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '管理后台')

const goToChat = () => {
  router.push({ name: 'Chat' })
}

const handleCommand = (command) => {
  if (command === 'logout') {
    userStore.logout()
  } else if (command === 'profile') {
    router.push({ name: 'Settings' })
  }
}
</script>

<style lang="scss" scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  background: var(--bg-primary);
}

.admin-sidebar {
  width: 240px;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-normal);
  
  &.collapsed {
    width: 64px;
  }
}

.sidebar-header {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid var(--border);
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  
  .logo-icon {
    font-size: 28px;
  }
  
  .logo-text {
    font-size: 20px;
    font-weight: 700;
    background: var(--gradient-calm);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}

.logo-mini {
  font-size: 28px;
}

.admin-menu {
  flex: 1;
  border-right: none;
  background: transparent;
  
  :deep(.el-menu-item) {
    color: var(--text-secondary);
    height: 48px;
    line-height: 48px;
    margin: 4px 8px;
    border-radius: var(--radius-sm);
    
    &:hover {
      background: var(--bg-tertiary);
      color: var(--text-primary);
    }
    
    &.is-active {
      background: var(--primary);
      color: white;
    }
  }
}

.sidebar-toggle {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  cursor: pointer;
  border-top: 1px solid var(--border);
  transition: all var(--transition-fast);
  
  &:hover {
    color: var(--text-primary);
    background: var(--bg-tertiary);
  }
}

.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.admin-header {
  height: var(--header-height);
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-dropdown {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  transition: all var(--transition-fast);
  
  &:hover {
    background: var(--bg-tertiary);
  }
}

.user-avatar {
  width: 32px;
  height: 32px;
  background: var(--gradient-primary);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 600;
  font-size: 14px;
}

.user-name {
  color: var(--text-primary);
  font-size: 14px;
}

.admin-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
