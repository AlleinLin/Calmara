<template>
  <div class="student-detail-view">
    <div class="page-header">
      <el-button @click="goBack" :icon="ArrowLeft">返回</el-button>
      <h1 class="page-title">学生详情</h1>
    </div>
    
    <div class="detail-grid" v-loading="loading">
      <div class="card user-card">
        <div class="user-header">
          <div class="user-avatar">{{ user?.username?.charAt(0).toUpperCase() }}</div>
          <div class="user-info">
            <h2 class="user-name">{{ user?.realName || user?.username }}</h2>
            <p class="user-email">{{ user?.email }}</p>
            <div class="user-tags">
              <el-tag :type="getRiskTagType(user?.riskLevel)" size="small">
                {{ getRiskLabel(user?.riskLevel) }}风险
              </el-tag>
              <el-tag :type="user?.status === 1 ? 'success' : 'danger'" size="small">
                {{ user?.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </div>
          </div>
        </div>
        
        <div class="user-details">
          <div class="detail-item">
            <span class="label">学号</span>
            <span class="value">{{ user?.studentId || '-' }}</span>
          </div>
          <div class="detail-item">
            <span class="label">注册时间</span>
            <span class="value">{{ formatDate(user?.createdAt) }}</span>
          </div>
          <div class="detail-item">
            <span class="label">最近登录</span>
            <span class="value">{{ formatDate(user?.lastLoginAt) }}</span>
          </div>
        </div>
        
        <div class="user-actions">
          <el-button type="primary" @click="openChat">发起对话</el-button>
          <el-button
            :type="user?.status === 1 ? 'danger' : 'success'"
            @click="toggleStatus"
          >
            {{ user?.status === 1 ? '禁用账号' : '启用账号' }}
          </el-button>
        </div>
      </div>
      
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">情绪记录</h3>
        </div>
        <div class="emotion-list">
          <div
            v-for="record in emotionRecords"
            :key="record.id"
            class="emotion-item"
          >
            <div class="emotion-icon" :class="getEmotionClass(record.emotion)">
              {{ getEmotionEmoji(record.emotion) }}
            </div>
            <div class="emotion-content">
              <div class="emotion-label">{{ record.emotion }}</div>
              <div class="emotion-time">{{ formatDate(record.createdAt) }}</div>
            </div>
            <el-tag :type="getRiskTagType(record.riskLevel)" size="small">
              {{ getRiskLabel(record.riskLevel) }}
            </el-tag>
          </div>
          <div v-if="emotionRecords.length === 0" class="no-data">
            暂无情绪记录
          </div>
        </div>
      </div>
      
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">最近对话</h3>
        </div>
        <div class="messages-list">
          <div
            v-for="message in recentMessages"
            :key="message.id"
            class="message-item"
            :class="message.role"
          >
            <div class="message-role">
              {{ message.role === 'user' ? '用户' : '助手' }}
            </div>
            <div class="message-content">{{ message.content }}</div>
            <div class="message-time">{{ formatDate(message.createdAt) }}</div>
          </div>
          <div v-if="recentMessages.length === 0" class="no-data">
            暂无对话记录
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const user = ref(null)
const emotionRecords = ref([])
const recentMessages = ref([])

const fetchStudentDetail = async () => {
  loading.value = true
  try {
    const response = await adminApi.getStudentDetail(route.params.id)
    
    if (response.success) {
      user.value = response.data.user
      emotionRecords.value = response.data.emotionRecords || []
      recentMessages.value = response.data.recentMessages || []
    }
  } catch (error) {
    console.error('获取学生详情失败:', error)
    ElMessage.error('获取学生详情失败')
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.back()
}

const openChat = () => {
  ElMessage.info('功能开发中')
}

const toggleStatus = async () => {
  const newStatus = user.value?.status === 1 ? 0 : 1
  const action = newStatus === 0 ? '禁用' : '启用'
  
  try {
    await ElMessageBox.confirm(
      `确定要${action}该学生的账号吗？`,
      '确认操作',
      { type: 'warning' }
    )
    
    const response = await adminApi.updateStudentStatus(
      route.params.id,
      newStatus
    )
    
    if (response.success) {
      user.value.status = newStatus
      ElMessage.success(`${action}成功`)
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const formatDate = (date) => {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

const getRiskTagType = (level) => {
  const types = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' }
  return types[level] || 'info'
}

const getRiskLabel = (level) => {
  const labels = { HIGH: '高', MEDIUM: '中', LOW: '低' }
  return labels[level] || '未知'
}

const getEmotionClass = (emotion) => {
  const classes = {
    '正常': 'normal',
    '焦虑': 'anxious',
    '低落': 'depressed',
    '高风险': 'high-risk'
  }
  return classes[emotion] || 'normal'
}

const getEmotionEmoji = (emotion) => {
  const emojis = {
    '正常': '😊',
    '焦虑': '😰',
    '低落': '😢',
    '高风险': '⚠️'
  }
  return emojis[emotion] || '😐'
}

onMounted(() => {
  fetchStudentDetail()
})
</script>

<style lang="scss" scoped>
.student-detail-view {
  .detail-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 24px;
    
    @media (max-width: 1200px) {
      grid-template-columns: 1fr;
    }
  }
  
  .user-card {
    grid-column: span 2;
    
    @media (max-width: 1200px) {
      grid-column: span 1;
    }
  }
  
  .user-header {
    display: flex;
    gap: 24px;
    margin-bottom: 24px;
    padding-bottom: 24px;
    border-bottom: 1px solid var(--border);
  }
  
  .user-avatar {
    width: 80px;
    height: 80px;
    background: var(--gradient-primary);
    border-radius: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: white;
    font-size: 32px;
    font-weight: 600;
  }
  
  .user-info {
    .user-name {
      font-size: 24px;
      font-weight: 600;
      margin-bottom: 4px;
    }
    
    .user-email {
      color: var(--text-secondary);
      margin-bottom: 12px;
    }
    
    .user-tags {
      display: flex;
      gap: 8px;
    }
  }
  
  .user-details {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
    margin-bottom: 24px;
    
    @media (max-width: 768px) {
      grid-template-columns: 1fr;
    }
  }
  
  .detail-item {
    .label {
      display: block;
      font-size: 12px;
      color: var(--text-muted);
      margin-bottom: 4px;
    }
    
    .value {
      font-size: 14px;
      color: var(--text-primary);
    }
  }
  
  .user-actions {
    display: flex;
    gap: 12px;
  }
  
  .emotion-list,
  .messages-list {
    max-height: 400px;
    overflow-y: auto;
  }
  
  .emotion-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    border-radius: var(--radius-md);
    
    &:hover {
      background: var(--bg-tertiary);
    }
    
    .emotion-icon {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      
      &.normal { background: rgba(16, 185, 129, 0.15); }
      &.anxious { background: rgba(245, 158, 11, 0.15); }
      &.depressed { background: rgba(6, 182, 212, 0.15); }
      &.high-risk { background: rgba(239, 68, 68, 0.15); }
    }
    
    .emotion-content {
      flex: 1;
    }
    
    .emotion-label {
      font-weight: 500;
    }
    
    .emotion-time {
      font-size: 12px;
      color: var(--text-muted);
    }
  }
  
  .message-item {
    padding: 12px;
    border-radius: var(--radius-md);
    margin-bottom: 8px;
    
    &.user {
      background: var(--bg-tertiary);
    }
    
    &.assistant {
      background: rgba(99, 102, 241, 0.1);
    }
    
    .message-role {
      font-size: 12px;
      color: var(--text-muted);
      margin-bottom: 4px;
    }
    
    .message-content {
      font-size: 14px;
      line-height: 1.5;
      margin-bottom: 4px;
      display: -webkit-box;
      -webkit-line-clamp: 3;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    
    .message-time {
      font-size: 12px;
      color: var(--text-muted);
    }
  }
  
  .no-data {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 200px;
    color: var(--text-muted);
  }
}
</style>
