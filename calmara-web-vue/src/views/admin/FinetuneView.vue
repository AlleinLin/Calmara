<template>
  <div class="finetune-view">
    <div class="page-header">
      <h1 class="page-title">模型微调</h1>
      <div class="page-actions">
        <el-button
          v-if="!trainingStatus.isTraining"
          type="primary"
          @click="triggerFinetune"
        >
          <el-icon><VideoPlay /></el-icon>
          开始微调
        </el-button>
        <el-button v-else type="danger" @click="stopFinetune">
          <el-icon><VideoPause /></el-icon>
          停止微调
        </el-button>
      </div>
    </div>
    
    <div class="status-card">
      <div class="status-header">
        <h3>训练状态</h3>
        <el-tag :type="trainingStatus.isTraining ? 'warning' : 'success'">
          {{ trainingStatus.isTraining ? '训练中' : '空闲' }}
        </el-tag>
      </div>
      
      <div class="status-grid">
        <div class="status-item">
          <span class="label">当前状态</span>
          <span class="value">{{ trainingStatus.status || 'IDLE' }}</span>
        </div>
        <div class="status-item">
          <span class="label">进度</span>
          <span class="value">{{ trainingStatus.progress || 0 }}%</span>
        </div>
        <div class="status-item">
          <span class="label">当前轮次</span>
          <span class="value">{{ trainingStatus.currentEpoch || 0 }} / {{ trainingStatus.totalEpochs || 0 }}</span>
        </div>
        <div class="status-item">
          <span class="label">损失值</span>
          <span class="value">{{ trainingStatus.currentLoss?.toFixed(4) || '-' }}</span>
        </div>
      </div>
      
      <el-progress
        v-if="trainingStatus.isTraining"
        :percentage="trainingStatus.progress || 0"
        :stroke-width="8"
        class="mt-4"
      />
    </div>
    
    <div class="config-grid">
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">触发条件</h3>
          <el-button text type="primary" @click="saveTriggerConfig">保存</el-button>
        </div>
        <el-form :model="config.trigger" label-width="120px" class="config-form">
          <el-form-item label="自动触发">
            <el-switch v-model="config.trigger.autoTrigger" />
          </el-form-item>
          <el-form-item label="最小新文档">
            <el-input-number v-model="config.trigger.minNewDocuments" :min="0" />
          </el-form-item>
          <el-form-item label="最小间隔天数">
            <el-input-number v-model="config.trigger.minDaysSinceLast" :min="1" />
          </el-form-item>
          <el-form-item label="定时触发">
            <el-input v-model="config.trigger.cronExpression" placeholder="Cron表达式" />
          </el-form-item>
        </el-form>
      </div>
      
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">资源配置</h3>
          <el-button text type="primary" @click="saveResourceConfig">保存</el-button>
        </div>
        <el-form :model="config.resourceControl" label-width="120px" class="config-form">
          <el-form-item label="最大CPU">
            <el-slider v-model="config.resourceControl.maxCpuPercent" :min="10" :max="100" show-input />
          </el-form-item>
          <el-form-item label="最大内存">
            <el-slider v-model="config.resourceControl.maxMemoryPercent" :min="10" :max="100" show-input />
          </el-form-item>
          <el-form-item label="最大训练时长">
            <el-input-number v-model="config.resourceControl.maxTrainingHours" :min="1" :max="24" />
          </el-form-item>
          <el-form-item label="GPU监控">
            <el-switch v-model="config.resourceControl.enableGpuMonitoring" />
          </el-form-item>
        </el-form>
      </div>
      
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">训练策略</h3>
          <el-button text type="primary" @click="saveTrainingConfig">保存</el-button>
        </div>
        <el-form :model="config.trainingStrategy" label-width="120px" class="config-form">
          <el-form-item label="学习率">
            <el-input-number v-model="config.trainingStrategy.learningRate" :min="0" :max="1" :step="0.0001" :precision="4" />
          </el-form-item>
          <el-form-item label="训练轮次">
            <el-input-number v-model="config.trainingStrategy.epochs" :min="1" :max="100" />
          </el-form-item>
          <el-form-item label="批次大小">
            <el-input-number v-model="config.trainingStrategy.batchSize" :min="1" :max="64" />
          </el-form-item>
          <el-form-item label="LoRA Rank">
            <el-input-number v-model="config.trainingStrategy.loraRank" :min="1" :max="128" />
          </el-form-item>
        </el-form>
      </div>
      
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">远程服务器</h3>
          <el-button text type="primary" @click="testConnection">测试连接</el-button>
        </div>
        <el-form :model="config.remoteServer" label-width="120px" class="config-form">
          <el-form-item label="启用远程">
            <el-switch v-model="config.remoteServer.enabled" />
          </el-form-item>
          <el-form-item label="主机地址">
            <el-input v-model="config.remoteServer.host" placeholder="SSH主机地址" />
          </el-form-item>
          <el-form-item label="端口">
            <el-input-number v-model="config.remoteServer.port" :min="1" :max="65535" />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="config.remoteServer.username" />
          </el-form-item>
          <el-form-item label="私钥路径">
            <el-input v-model="config.remoteServer.privateKeyPath" placeholder="本地私钥文件路径" />
          </el-form-item>
        </el-form>
      </div>
    </div>
    
    <div class="card mt-4">
      <div class="card-header">
        <h3 class="card-title">训练日志</h3>
        <el-button text @click="refreshStatus">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
      <div class="log-container">
        <div v-for="(log, index) in trainingLogs" :key="index" class="log-item" :class="log.level">
          <span class="log-time">{{ log.time }}</span>
          <span class="log-level">[{{ log.level }}]</span>
          <span class="log-message">{{ log.message }}</span>
        </div>
        <div v-if="trainingLogs.length === 0" class="no-logs">
          暂无训练日志
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { finetuneApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoPlay, VideoPause, Refresh } from '@element-plus/icons-vue'

const trainingStatus = ref({
  isTraining: false,
  status: 'IDLE',
  progress: 0,
  currentEpoch: 0,
  totalEpochs: 0,
  currentLoss: null
})

const config = reactive({
  trigger: {
    autoTrigger: true,
    minNewDocuments: 1000,
    minDaysSinceLast: 7,
    cronExpression: ''
  },
  resourceControl: {
    maxCpuPercent: 70,
    maxMemoryPercent: 85,
    maxTrainingHours: 6,
    enableGpuMonitoring: true
  },
  trainingStrategy: {
    learningRate: 0.0001,
    epochs: 3,
    batchSize: 4,
    loraRank: 16
  },
  remoteServer: {
    enabled: false,
    host: '',
    port: 22,
    username: '',
    privateKeyPath: ''
  }
})

const trainingLogs = ref([])
let statusInterval = null

const fetchStatus = async () => {
  try {
    const response = await finetuneApi.getStatus()
    if (response.success) {
      trainingStatus.value = response.data
    }
  } catch (error) {
    console.error('获取状态失败:', error)
  }
}

const fetchConfig = async () => {
  try {
    const response = await finetuneApi.getConfig()
    if (response.success && response.data) {
      Object.assign(config.trigger, response.data.trigger || {})
      Object.assign(config.resourceControl, response.data.resourceControl || {})
      Object.assign(config.trainingStrategy, response.data.trainingStrategy || {})
      Object.assign(config.remoteServer, response.data.remoteServer || {})
    }
  } catch (error) {
    console.error('获取配置失败:', error)
  }
}

const triggerFinetune = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要开始模型微调吗？这可能需要较长时间。',
      '确认操作',
      { type: 'warning' }
    )
    
    const response = await finetuneApi.triggerFinetune('手动触发')
    if (response.success) {
      ElMessage.success('微调已触发')
      fetchStatus()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('触发失败')
    }
  }
}

const stopFinetune = async () => {
  try {
    await ElMessageBox.confirm('确定要停止微调吗？', '确认操作', { type: 'warning' })
    
    const response = await finetuneApi.stopFinetune()
    if (response.success) {
      ElMessage.success('已发送停止信号')
      fetchStatus()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('停止失败')
    }
  }
}

const saveTriggerConfig = async () => {
  try {
    const response = await finetuneApi.updateTriggerConfig(config.trigger)
    if (response.success) {
      ElMessage.success('保存成功')
    }
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

const saveResourceConfig = async () => {
  try {
    const response = await finetuneApi.updateResourceConfig(config.resourceControl)
    if (response.success) {
      ElMessage.success('保存成功')
    }
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

const saveTrainingConfig = async () => {
  try {
    const response = await finetuneApi.updateTrainingConfig(config.trainingStrategy)
    if (response.success) {
      ElMessage.success('保存成功')
    }
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

const testConnection = async () => {
  try {
    const response = await finetuneApi.testCurrentRemoteConnection()
    if (response.success) {
      if (response.data.success) {
        ElMessage.success('连接成功')
      } else {
        ElMessage.error(response.data.message || '连接失败')
      }
    }
  } catch (error) {
    ElMessage.error('连接测试失败')
  }
}

const refreshStatus = () => {
  fetchStatus()
}

onMounted(() => {
  fetchStatus()
  fetchConfig()
  statusInterval = setInterval(fetchStatus, 5000)
})

onUnmounted(() => {
  if (statusInterval) {
    clearInterval(statusInterval)
  }
})
</script>

<style lang="scss" scoped>
.finetune-view {
  .status-card {
    background: var(--bg-card);
    border: 1px solid var(--border);
    border-radius: var(--radius-lg);
    padding: 24px;
    margin-bottom: 24px;
  }
  
  .status-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    
    h3 {
      font-size: 18px;
      font-weight: 600;
    }
  }
  
  .status-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 24px;
    
    @media (max-width: 768px) {
      grid-template-columns: repeat(2, 1fr);
    }
  }
  
  .status-item {
    .label {
      display: block;
      font-size: 12px;
      color: var(--text-muted);
      margin-bottom: 4px;
    }
    
    .value {
      font-size: 20px;
      font-weight: 600;
      color: var(--text-primary);
    }
  }
  
  .config-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 24px;
    
    @media (max-width: 1200px) {
      grid-template-columns: 1fr;
    }
  }
  
  .config-form {
    :deep(.el-form-item) {
      margin-bottom: 16px;
    }
    
    :deep(.el-form-item__label) {
      color: var(--text-secondary);
    }
  }
  
  .log-container {
    background: var(--bg-primary);
    border-radius: var(--radius-md);
    padding: 16px;
    max-height: 300px;
    overflow-y: auto;
    font-family: 'Consolas', monospace;
    font-size: 13px;
  }
  
  .log-item {
    display: flex;
    gap: 12px;
    margin-bottom: 8px;
    
    .log-time {
      color: var(--text-muted);
    }
    
    .log-level {
      width: 60px;
    }
    
    &.info .log-level { color: var(--info); }
    &.warning .log-level { color: var(--warning); }
    &.error .log-level { color: var(--danger); }
    &.success .log-level { color: var(--success); }
  }
  
  .no-logs {
    text-align: center;
    color: var(--text-muted);
    padding: 40px;
  }
  
  .mt-4 {
    margin-top: 24px;
  }
}
</style>
