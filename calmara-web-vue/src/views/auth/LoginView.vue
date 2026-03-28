<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <div class="logo-icon">🧠</div>
        <h1 class="login-title">欢迎来到 Calmara</h1>
        <p class="login-subtitle">智能心理关怀助手</p>
      </div>
      
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
        
        <div class="login-actions">
          <el-button text type="primary" @click="handleGuestLogin">
            游客体验
          </el-button>
          <el-button text type="primary" @click="goToRegister">
            没有账号？立即注册
          </el-button>
        </div>
      </el-form>
      
      <div class="login-features">
        <div class="feature-item">
          <span class="feature-icon">💬</span>
          <span>倾诉倾听</span>
        </div>
        <div class="feature-item">
          <span class="feature-icon">😊</span>
          <span>情绪识别</span>
        </div>
        <div class="feature-item">
          <span class="feature-icon">📊</span>
          <span>专业分析</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    loading.value = true
    
    try {
      const result = await userStore.login(form.username, form.password)
      
      if (result.success) {
        ElMessage.success('登录成功！')
        const redirect = route.query.redirect || '/'
        router.push(redirect)
      } else {
        ElMessage.error(result.message || '登录失败')
      }
    } catch (error) {
      ElMessage.error('登录失败，请稍后重试')
    } finally {
      loading.value = false
    }
  })
}

const handleGuestLogin = () => {
  userStore.guestLogin()
  ElMessage.success('欢迎游客体验！')
  router.push('/')
}

const goToRegister = () => {
  router.push({ name: 'Register' })
}
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-primary);
  padding: 20px;
}

.login-container {
  width: 100%;
  max-width: 420px;
  background: var(--bg-secondary);
  border-radius: var(--radius-xl);
  padding: 40px;
  box-shadow: var(--shadow-lg);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo-icon {
  width: 80px;
  height: 80px;
  background: var(--gradient-calm);
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  margin: 0 auto 20px;
  box-shadow: var(--shadow-glow);
}

.login-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.login-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
}

.login-form {
  :deep(.el-input__wrapper) {
    background: var(--bg-tertiary);
    border: 1px solid var(--border);
    box-shadow: none;
    
    &:hover,
    &.is-focus {
      border-color: var(--primary);
    }
  }
  
  :deep(.el-input__inner) {
    color: var(--text-primary);
    
    &::placeholder {
      color: var(--text-muted);
    }
  }
}

.login-btn {
  width: 100%;
  background: var(--gradient-primary);
  border: none;
  font-size: 16px;
  height: 48px;
  
  &:hover {
    opacity: 0.9;
    transform: translateY(-2px);
    box-shadow: var(--shadow-glow);
  }
}

.login-actions {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
}

.login-features {
  display: flex;
  justify-content: center;
  gap: 32px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--border);
}

.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 13px;
}

.feature-icon {
  font-size: 24px;
}
</style>
