<template>
  <div class="install-page">
    <div class="install-container">
      <div class="install-header">
        <div class="logo-icon">🧠</div>
        <h1>Calmara 心理健康智能体</h1>
        <p>系统安装向导 · 版本 1.0.0</p>
      </div>
      
      <div class="install-steps">
        <div
          v-for="step in steps"
          :key="step.id"
          class="step"
          :class="{
            active: currentStep === step.id,
            completed: currentStep > step.id
          }"
        >
          <div class="step-number">{{ currentStep > step.id ? '✓' : step.id }}</div>
          <span>{{ step.name }}</span>
        </div>
      </div>
      
      <div class="install-content">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: `${(currentStep / 4) * 100}%` }"></div>
        </div>
        
        <div v-if="currentStep === 1" class="step-content">
          <h2>环境检测</h2>
          <p class="text-secondary">正在检测您的服务器环境是否满足安装要求...</p>
          
          <div class="requirement-list">
            <div
              v-for="req in requirements"
              :key="req.key"
              class="requirement-item"
              :class="req.status"
            >
              <div class="requirement-icon">{{ req.status === 'success' ? '✓' : req.status === 'error' ? '✗' : '⏳' }}</div>
              <span>{{ req.name }}</span>
            </div>
          </div>
          
          <div class="btn-group">
            <div></div>
            <el-button type="primary" :disabled="!canProceed" @click="nextStep">
              下一步
            </el-button>
          </div>
        </div>
        
        <div v-if="currentStep === 2" class="step-content">
          <h2>数据库配置</h2>
          <p class="text-secondary">检测数据库连接状态</p>
          
          <div class="database-info">
            <div class="info-row">
              <span class="label">状态:</span>
              <span :class="dbInfo.connected ? 'text-success' : 'text-danger'">
                {{ dbInfo.connected ? '✓ 已连接' : '✗ 连接失败' }}
              </span>
            </div>
            <div class="info-row">
              <span class="label">类型:</span>
              <span>{{ dbInfo.type || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="label">版本:</span>
              <span>{{ dbInfo.version || '-' }}</span>
            </div>
          </div>
          
          <div class="btn-group">
            <el-button @click="prevStep">上一步</el-button>
            <el-button type="primary" :disabled="!dbInfo.connected" @click="nextStep">
              下一步
            </el-button>
          </div>
        </div>
        
        <div v-if="currentStep === 3" class="step-content">
          <h2>管理员设置</h2>
          <p class="text-secondary">创建系统管理员账户</p>
          
          <el-form :model="adminForm" :rules="adminRules" ref="adminFormRef" label-width="100px">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="adminForm.username" placeholder="请输入管理员用户名" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="adminForm.password" type="password" show-password placeholder="请输入管理员密码" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="adminForm.confirmPassword" type="password" show-password placeholder="请再次输入密码" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="adminForm.email" placeholder="请输入管理员邮箱" />
            </el-form-item>
          </el-form>
          
          <div class="btn-group">
            <el-button @click="prevStep">上一步</el-button>
            <el-button type="primary" @click="executeInstall" :loading="installing">
              开始安装
            </el-button>
          </div>
        </div>
        
        <div v-if="currentStep === 4" class="step-content">
          <div v-if="installing" class="installing">
            <h2>正在安装...</h2>
            <p class="text-secondary">请稍候，系统正在进行安装配置</p>
            <div class="install-log">
              <div v-for="(log, index) in installLogs" :key="index" class="log-item" :class="log.type">
                [{{ log.time }}] {{ log.message }}
              </div>
            </div>
          </div>
          
          <div v-else class="install-success">
            <div class="success-icon">✓</div>
            <h2>🎉 安装完成！</h2>
            <p class="text-secondary">Calmara 心理健康智能体系统已成功安装</p>
            <div class="install-info">
              <p><strong>系统版本:</strong> 1.0.0</p>
              <p><strong>安装时间:</strong> {{ installTime }}</p>
            </div>
            <el-button type="primary" size="large" @click="goToSystem">
              进入系统
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'

const router = useRouter()

const steps = [
  { id: 1, name: '环境检测' },
  { id: 2, name: '数据库配置' },
  { id: 3, name: '管理员设置' },
  { id: 4, name: '完成安装' }
]

const currentStep = ref(1)
const canProceed = ref(false)
const installing = ref(false)
const installTime = ref('')

const requirements = ref([
  { key: 'java', name: 'Java 环境', status: 'loading' },
  { key: 'memory', name: '内存配置', status: 'loading' },
  { key: 'database', name: '数据库连接', status: 'loading' }
])

const dbInfo = ref({
  connected: false,
  type: '',
  version: ''
})

const adminFormRef = ref(null)
const adminForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: ''
})

const adminRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码长度至少8位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== adminForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
}

const installLogs = ref([])

const checkRequirements = async () => {
  try {
    const response = await fetch('/install/requirements')
    const result = await response.json()
    
    if (result.success) {
      const data = result.data
      requirements.value = [
        { key: 'java', name: 'Java 环境', status: data.javaOk ? 'success' : 'error' },
        { key: 'memory', name: '内存配置', status: data.memoryOk ? 'success' : 'error' },
        { key: 'database', name: '数据库连接', status: data.databaseConnected ? 'success' : 'error' }
      ]
      canProceed.value = data.allOk
    }
  } catch (error) {
    requirements.value.forEach(r => r.status = 'error')
  }
}

const checkDatabase = async () => {
  try {
    const response = await fetch('/install/database-info')
    const result = await response.json()
    
    if (result.success) {
      dbInfo.value = {
        connected: result.data.connected,
        type: result.data.databaseProductName,
        version: result.data.databaseProductVersion
      }
    }
  } catch (error) {
    dbInfo.value.connected = false
  }
}

const addLog = (message, type = 'info') => {
  installLogs.value.push({
    time: dayjs().format('HH:mm:ss'),
    message,
    type
  })
}

const nextStep = () => {
  if (currentStep.value < 4) {
    currentStep.value++
  }
}

const prevStep = () => {
  if (currentStep.value > 1) {
    currentStep.value--
  }
}

const executeInstall = async () => {
  if (!adminFormRef.value) return
  
  await adminFormRef.value.validate(async (valid) => {
    if (!valid) return
    
    currentStep.value = 4
    installing.value = true
    
    addLog('开始系统安装...', 'info')
    
    try {
      addLog('正在初始化数据库...', 'info')
      
      const response = await fetch('/install/execute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          adminUsername: adminForm.username,
          adminPassword: adminForm.password,
          adminEmail: adminForm.email
        })
      })
      
      const result = await response.json()
      
      if (result.success) {
        addLog('数据库初始化完成', 'success')
        addLog('管理员账户创建成功', 'success')
        addLog('系统配置完成', 'success')
        addLog('安装完成！', 'success')
        
        installTime.value = dayjs().format('YYYY-MM-DD HH:mm:ss')
        
        setTimeout(() => {
          installing.value = false
        }, 1000)
      } else {
        addLog('安装失败: ' + result.message, 'error')
      }
    } catch (error) {
      addLog('安装失败: ' + error.message, 'error')
    }
  })
}

const goToSystem = () => {
  router.push('/')
}

onMounted(() => {
  checkRequirements()
  checkDatabase()
})
</script>

<style lang="scss" scoped>
.install-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.install-container {
  background: var(--bg-secondary);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  max-width: 800px;
  width: 100%;
  overflow: hidden;
}

.install-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 40px;
  text-align: center;
  
  .logo-icon {
    font-size: 48px;
    margin-bottom: 16px;
  }
  
  h1 {
    font-size: 28px;
    margin-bottom: 8px;
  }
  
  p {
    opacity: 0.9;
    font-size: 14px;
  }
}

.install-steps {
  display: flex;
  justify-content: center;
  padding: 20px;
  background: var(--bg-tertiary);
  border-bottom: 1px solid var(--border);
}

.step {
  display: flex;
  align-items: center;
  padding: 10px 20px;
  color: var(--text-muted);
  
  &.active {
    color: var(--primary);
    font-weight: 600;
  }
  
  &.completed {
    color: var(--success);
  }
}

.step-number {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--bg-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
  font-size: 14px;
  
  .active & {
    background: var(--primary);
    color: white;
  }
  
  .completed & {
    background: var(--success);
    color: white;
  }
}

.install-content {
  padding: 40px;
}

.progress-bar {
  height: 4px;
  background: var(--bg-tertiary);
  border-radius: 2px;
  margin-bottom: 24px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  transition: width 0.3s;
}

.step-content {
  h2 {
    font-size: 20px;
    margin-bottom: 8px;
  }
  
  .text-secondary {
    color: var(--text-secondary);
    margin-bottom: 24px;
  }
}

.requirement-list {
  margin-bottom: 24px;
}

.requirement-item {
  display: flex;
  align-items: center;
  padding: 12px;
  margin-bottom: 10px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-md);
  
  &.success {
    background: rgba(16, 185, 129, 0.15);
    border: 1px solid rgba(16, 185, 129, 0.3);
  }
  
  &.error {
    background: rgba(239, 68, 68, 0.15);
    border: 1px solid rgba(239, 68, 68, 0.3);
  }
  
  .requirement-icon {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: 12px;
    font-size: 14px;
  }
  
  &.success .requirement-icon {
    background: var(--success);
    color: white;
  }
  
  &.error .requirement-icon {
    background: var(--danger);
    color: white;
  }
}

.database-info {
  background: var(--bg-tertiary);
  padding: 20px;
  border-radius: var(--radius-md);
  margin-bottom: 24px;
  
  .info-row {
    display: flex;
    margin-bottom: 12px;
    
    &:last-child {
      margin-bottom: 0;
    }
    
    .label {
      width: 80px;
      color: var(--text-secondary);
    }
  }
}

.text-success { color: var(--success); }
.text-danger { color: var(--danger); }

.btn-group {
  display: flex;
  justify-content: space-between;
  margin-top: 24px;
}

.install-log {
  background: var(--bg-primary);
  padding: 16px;
  border-radius: var(--radius-md);
  font-family: 'Consolas', monospace;
  font-size: 13px;
  max-height: 200px;
  overflow-y: auto;
  
  .log-item {
    margin-bottom: 8px;
    
    &.success { color: var(--success); }
    &.error { color: var(--danger); }
    &.info { color: var(--info); }
  }
}

.install-success {
  text-align: center;
  
  .success-icon {
    width: 80px;
    height: 80px;
    background: var(--success);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 40px;
    color: white;
    margin: 0 auto 24px;
  }
  
  h2 {
    margin-bottom: 12px;
  }
  
  .install-info {
    background: var(--bg-tertiary);
    padding: 16px;
    border-radius: var(--radius-md);
    margin: 24px 0;
    text-align: left;
    
    p {
      margin-bottom: 8px;
      
      &:last-child {
        margin-bottom: 0;
      }
    }
  }
}
</style>
