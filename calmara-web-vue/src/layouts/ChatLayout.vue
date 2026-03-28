<template>
  <div class="chat-layout">
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="logo">
          <div class="logo-icon">🧠</div>
          <div class="logo-info">
            <div class="logo-text">Calmara</div>
            <div class="tagline">智能心理关怀助手</div>
          </div>
        </div>
      </div>
      
      <div class="sidebar-content">
        <button class="new-chat-btn" @click="startNewChat">
          <span>✨</span>
          <span>开始新的对话</span>
        </button>
        
        <div class="chat-history">
          <div class="chat-history-title">最近对话</div>
          <div class="chat-history-list">
            <div
              v-for="item in chatHistory"
              :key="item.sessionId"
              class="chat-history-item"
              :class="{ active: item.sessionId === currentSessionId }"
              @click="loadSession(item)"
            >
              <span>💬</span>
              <span class="history-title">{{ item.title }}</span>
            </div>
            <div v-if="chatHistory.length === 0" class="no-history">
              暂无历史对话
            </div>
          </div>
        </div>
      </div>
      
      <div class="sidebar-footer">
        <div class="user-info" v-if="userStore.isAuthenticated">
          <div class="user-avatar">{{ userStore.username.charAt(0).toUpperCase() }}</div>
          <div class="user-details">
            <div class="user-name">{{ userStore.username }}</div>
            <div class="user-role">{{ userStore.isAdmin ? '管理员' : '用户' }}</div>
          </div>
          <el-dropdown trigger="click" @command="handleUserCommand">
            <button class="user-menu-btn">
              <el-icon><MoreFilled /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="admin" v-if="userStore.isAdmin">
                  <el-icon><Setting /></el-icon>管理后台
                </el-dropdown-item>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="guest-info" v-else>
          <button class="login-btn" @click="goToLogin">登录</button>
        </div>
      </div>
    </aside>
    
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { useUserStore, useChatStore } from '@/stores'
import { useRouter } from 'vue-router'
import { MoreFilled, Setting, SwitchButton } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

const chatHistory = computed(() => chatStore.chatHistory)
const currentSessionId = computed(() => chatStore.currentSessionId)

onMounted(() => {
  chatStore.loadChatHistory()
})

const startNewChat = () => {
  chatStore.clearMessages()
}

const loadSession = (item) => {
  chatStore.setSessionId(item.sessionId)
}

const goToLogin = () => {
  router.push({ name: 'Login' })
}

const handleUserCommand = (command) => {
  if (command === 'admin') {
    router.push({ name: 'Dashboard' })
  } else if (command === 'logout') {
    userStore.logout()
  }
}
</script>

<style lang="scss" scoped>
.chat-layout {
  display: flex;
  height: 100vh;
  background: var(--bg-primary);
}

.sidebar {
  width: var(--sidebar-width);
  background: var(--bg-secondary);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  transition: width var(--transition-normal);
  
  @media (max-width: 768px) {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 100;
    transform: translateX(-100%);
    
    &.open {
      transform: translateX(0);
    }
  }
}

.sidebar-header {
  padding: 24px;
  border-bottom: 1px solid var(--border);
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  width: 48px;
  height: 48px;
  background: var(--gradient-calm);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  box-shadow: var(--shadow-glow);
}

.logo-text {
  font-size: 24px;
  font-weight: 700;
  background: var(--gradient-calm);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.tagline {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.sidebar-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 16px;
  background: transparent;
  border: 1px dashed var(--border);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all var(--transition-fast);
  
  &:hover {
    background: var(--bg-tertiary);
    border-color: var(--primary);
    color: var(--text-primary);
  }
}

.chat-history {
  margin-top: 20px;
}

.chat-history-title {
  font-size: 12px;
  text-transform: uppercase;
  color: var(--text-muted);
  padding: 8px 12px;
  letter-spacing: 0.5px;
}

.chat-history-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all var(--transition-fast);
  margin-bottom: 4px;
  
  &:hover {
    background: var(--bg-tertiary);
    color: var(--text-primary);
  }
  
  &.active {
    background: var(--primary-dark);
    color: var(--text-primary);
  }
  
  .history-title {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.no-history {
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  padding: 20px;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid var(--border);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-md);
}

.user-avatar {
  width: 40px;
  height: 40px;
  background: var(--gradient-primary);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 600;
  font-size: 16px;
}

.user-details {
  flex: 1;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.user-role {
  font-size: 12px;
  color: var(--text-muted);
}

.user-menu-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  border-radius: 8px;
  transition: all var(--transition-fast);
  
  &:hover {
    background: var(--bg-secondary);
    color: var(--text-primary);
  }
}

.login-btn {
  width: 100%;
  padding: 12px;
  background: var(--gradient-primary);
  border-radius: var(--radius-md);
  color: white;
  font-size: 14px;
  font-weight: 500;
  transition: all var(--transition-normal);
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-glow);
  }
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
