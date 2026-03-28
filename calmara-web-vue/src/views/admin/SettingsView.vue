<template>
  <div class="settings-view">
    <div class="page-header">
      <h1 class="page-title">系统设置</h1>
    </div>
    
    <div class="settings-grid">
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">个人信息</h3>
        </div>
        <el-form :model="profileForm" label-width="100px">
          <el-form-item label="用户名">
            <el-input v-model="profileForm.username" disabled />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="profileForm.email" />
          </el-form-item>
          <el-form-item label="真实姓名">
            <el-input v-model="profileForm.realName" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveProfile">保存</el-button>
          </el-form-item>
        </el-form>
      </div>
      
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">修改密码</h3>
        </div>
        <el-form :model="passwordForm" label-width="100px">
          <el-form-item label="当前密码">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="passwordForm.newPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="changePassword">修改密码</el-button>
          </el-form-item>
        </el-form>
      </div>
      
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">界面设置</h3>
        </div>
        <el-form label-width="100px">
          <el-form-item label="主题">
            <el-radio-group v-model="theme" @change="changeTheme">
              <el-radio value="dark">深色</el-radio>
              <el-radio value="light">浅色</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="侧边栏">
            <el-switch v-model="sidebarCollapsed" @change="toggleSidebar" />
            <span class="ml-2 text-secondary">{{ sidebarCollapsed ? '收起' : '展开' }}</span>
          </el-form-item>
        </el-form>
      </div>
      
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">系统信息</h3>
        </div>
        <div class="system-info">
          <div class="info-item">
            <span class="label">系统版本</span>
            <span class="value">1.0.0</span>
          </div>
          <div class="info-item">
            <span class="label">前端框架</span>
            <span class="value">Vue 3.4 + Element Plus</span>
          </div>
          <div class="info-item">
            <span class="label">后端框架</span>
            <span class="value">Spring Boot 3.2</span>
          </div>
          <div class="info-item">
            <span class="label">数据库</span>
            <span class="value">PostgreSQL</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useUserStore, useAppStore } from '@/stores'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const appStore = useAppStore()

const theme = ref('dark')
const sidebarCollapsed = ref(false)

const profileForm = reactive({
  username: '',
  email: '',
  realName: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const initProfile = () => {
  if (userStore.user) {
    profileForm.username = userStore.user.username || ''
    profileForm.email = userStore.user.email || ''
    profileForm.realName = userStore.user.realName || ''
  }
  theme.value = appStore.theme
  sidebarCollapsed.value = appStore.sidebarCollapsed
}

const saveProfile = () => {
  userStore.updateUser({
    email: profileForm.email,
    realName: profileForm.realName
  })
  ElMessage.success('保存成功')
}

const changePassword = () => {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.error('密码长度至少6位')
    return
  }
  ElMessage.success('密码修改成功')
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

const changeTheme = (value) => {
  appStore.setTheme(value)
}

const toggleSidebar = (value) => {
  appStore.setSidebarCollapsed(value)
}

onMounted(() => {
  initProfile()
})
</script>

<style lang="scss" scoped>
.settings-view {
  .settings-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 24px;
    
    @media (max-width: 1200px) {
      grid-template-columns: 1fr;
    }
  }
  
  .ml-2 {
    margin-left: 8px;
  }
  
  .text-secondary {
    color: var(--text-secondary);
  }
  
  .system-info {
    .info-item {
      display: flex;
      justify-content: space-between;
      padding: 12px 0;
      border-bottom: 1px solid var(--border-light);
      
      &:last-child {
        border-bottom: none;
      }
      
      .label {
        color: var(--text-secondary);
      }
      
      .value {
        color: var(--text-primary);
        font-weight: 500;
      }
    }
  }
}
</style>
