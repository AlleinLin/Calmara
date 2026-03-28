<template>
  <div class="dashboard-view">
    <div class="page-header">
      <h1 class="page-title">仪表盘</h1>
      <div class="page-actions">
        <el-button @click="refreshData" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新数据
        </el-button>
      </div>
    </div>
    
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon gradient-primary">
          <el-icon><User /></el-icon>
        </div>
        <div class="stat-value">{{ stats.totalUsers || 0 }}</div>
        <div class="stat-label">总用户数</div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon gradient-danger">
          <el-icon><Warning /></el-icon>
        </div>
        <div class="stat-value">{{ stats.highRiskCount || 0 }}</div>
        <div class="stat-label">高风险用户</div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon gradient-warning">
          <el-icon><Bell /></el-icon>
        </div>
        <div class="stat-value">{{ stats.pendingAlerts || 0 }}</div>
        <div class="stat-label">待处理预警</div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon gradient-calm">
          <el-icon><Calendar /></el-icon>
        </div>
        <div class="stat-value">{{ stats.todayNewUsers || 0 }}</div>
        <div class="stat-label">今日新增用户</div>
      </div>
    </div>
    
    <div class="dashboard-grid">
      <div class="card emotion-distribution">
        <div class="card-header">
          <h3 class="card-title">情绪分布</h3>
        </div>
        <div class="chart-container" v-loading="loading">
          <v-chart
            v-if="emotionChartData.length > 0"
            :option="emotionChartOption"
            autoresize
          />
          <div v-else class="no-data">暂无数据</div>
        </div>
      </div>
      
      <div class="card recent-alerts">
        <div class="card-header">
          <h3 class="card-title">最近预警</h3>
          <el-button text type="primary" @click="goToAlerts">
            查看全部
          </el-button>
        </div>
        <div class="alerts-list" v-loading="loading">
          <div
            v-for="alert in recentAlerts"
            :key="alert.id"
            class="alert-item"
            @click="viewAlert(alert)"
          >
            <div class="alert-icon" :class="getRiskClass(alert.riskLevel)">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="alert-content">
              <div class="alert-title">{{ alert.userName || '匿名用户' }}</div>
              <div class="alert-desc">{{ alert.content?.substring(0, 50) }}...</div>
            </div>
            <div class="alert-time">{{ formatTime(alert.createdAt) }}</div>
          </div>
          <div v-if="recentAlerts.length === 0" class="no-data">
            暂无预警
          </div>
        </div>
      </div>
      
      <div class="card high-risk-users">
        <div class="card-header">
          <h3 class="card-title">高风险用户</h3>
          <el-button text type="primary" @click="goToStudents">
            查看全部
          </el-button>
        </div>
        <div class="users-list" v-loading="loading">
          <div
            v-for="user in highRiskUsers"
            :key="user.id"
            class="user-item"
            @click="viewUser(user)"
          >
            <div class="user-avatar">{{ user.username?.charAt(0).toUpperCase() }}</div>
            <div class="user-info">
              <div class="user-name">{{ user.username }}</div>
              <div class="user-email">{{ user.email }}</div>
            </div>
            <el-tag type="danger" size="small">高风险</el-tag>
          </div>
          <div v-if="highRiskUsers.length === 0" class="no-data">
            暂无高风险用户
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import dayjs from 'dayjs'
import { User, Warning, Bell, Calendar, Refresh } from '@element-plus/icons-vue'

use([CanvasRenderer, PieChart, TitleComponent, TooltipComponent, LegendComponent])

const router = useRouter()

const loading = ref(false)
const stats = ref({})
const recentAlerts = ref([])
const highRiskUsers = ref([])

const emotionChartData = computed(() => {
  const distribution = stats.value.emotionDistribution || {}
  return Object.entries(distribution).map(([name, value]) => ({
    name,
    value
  }))
})

const emotionChartOption = computed(() => ({
  tooltip: {
    trigger: 'item',
    formatter: '{b}: {c} ({d}%)'
  },
  legend: {
    orient: 'vertical',
    right: '5%',
    top: 'center',
    textStyle: {
      color: 'var(--text-secondary)'
    }
  },
  series: [{
    type: 'pie',
    radius: ['40%', '70%'],
    center: ['35%', '50%'],
    avoidLabelOverlap: false,
    itemStyle: {
      borderRadius: 8,
      borderColor: 'var(--bg-card)',
      borderWidth: 2
    },
    label: {
      show: false
    },
    data: emotionChartData.value.map((item, index) => ({
      ...item,
      itemStyle: {
        color: ['#10b981', '#f59e0b', '#06b6d4', '#ef4444'][index] || '#6366f1'
      }
    }))
  }]
}))

const fetchDashboardData = async () => {
  loading.value = true
  try {
    const [dashboardRes, alertsRes, usersRes] = await Promise.all([
      adminApi.getDashboard(),
      adminApi.getRecentAlerts(5),
      adminApi.getHighRiskUsers()
    ])
    
    if (dashboardRes.success) {
      stats.value = dashboardRes.data
    }
    if (alertsRes.success) {
      recentAlerts.value = alertsRes.data
    }
    if (usersRes.success) {
      highRiskUsers.value = usersRes.data.slice(0, 5)
    }
  } catch (error) {
    console.error('获取仪表盘数据失败:', error)
  } finally {
    loading.value = false
  }
}

const refreshData = () => {
  fetchDashboardData()
}

const formatTime = (time) => {
  return dayjs(time).format('MM-DD HH:mm')
}

const getRiskClass = (level) => {
  const classes = {
    HIGH: 'risk-high',
    MEDIUM: 'risk-medium',
    LOW: 'risk-low'
  }
  return classes[level] || 'risk-low'
}

const goToAlerts = () => {
  router.push({ name: 'Alerts' })
}

const goToStudents = () => {
  router.push({ name: 'Students' })
}

const viewAlert = (alert) => {
  router.push({ name: 'Alerts', query: { id: alert.id } })
}

const viewUser = (user) => {
  router.push({ name: 'StudentDetail', params: { id: user.id } })
}

onMounted(() => {
  fetchDashboardData()
})
</script>

<style lang="scss" scoped>
.dashboard-view {
  .stats-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 24px;
    margin-bottom: 24px;
    
    @media (max-width: 1200px) {
      grid-template-columns: repeat(2, 1fr);
    }
    
    @media (max-width: 768px) {
      grid-template-columns: 1fr;
    }
  }
  
  .stat-card {
    background: var(--bg-card);
    border: 1px solid var(--border);
    border-radius: var(--radius-lg);
    padding: 24px;
    
    .stat-icon {
      width: 48px;
      height: 48px;
      border-radius: var(--radius-md);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      color: white;
      margin-bottom: 16px;
      
      &.gradient-primary { background: var(--gradient-primary); }
      &.gradient-danger { background: var(--gradient-danger); }
      &.gradient-warning { background: linear-gradient(135deg, #f59e0b 0%, #f97316 100%); }
      &.gradient-calm { background: var(--gradient-calm); }
    }
    
    .stat-value {
      font-size: 32px;
      font-weight: 700;
      color: var(--text-primary);
      margin-bottom: 4px;
    }
    
    .stat-label {
      font-size: 14px;
      color: var(--text-secondary);
    }
  }
  
  .dashboard-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    grid-template-rows: auto auto;
    gap: 24px;
    
    @media (max-width: 1200px) {
      grid-template-columns: 1fr;
    }
  }
  
  .emotion-distribution {
    grid-row: span 2;
  }
  
  .chart-container {
    height: 300px;
  }
  
  .alerts-list,
  .users-list {
    max-height: 300px;
    overflow-y: auto;
  }
  
  .alert-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: all var(--transition-fast);
    
    &:hover {
      background: var(--bg-tertiary);
    }
    
    .alert-icon {
      width: 36px;
      height: 36px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      
      &.risk-high { background: var(--danger); }
      &.risk-medium { background: var(--warning); }
      &.risk-low { background: var(--success); }
    }
    
    .alert-content {
      flex: 1;
      min-width: 0;
    }
    
    .alert-title {
      font-size: 14px;
      font-weight: 500;
      color: var(--text-primary);
    }
    
    .alert-desc {
      font-size: 12px;
      color: var(--text-muted);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    
    .alert-time {
      font-size: 12px;
      color: var(--text-muted);
    }
  }
  
  .user-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: all var(--transition-fast);
    
    &:hover {
      background: var(--bg-tertiary);
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
    }
    
    .user-info {
      flex: 1;
      min-width: 0;
    }
    
    .user-name {
      font-size: 14px;
      font-weight: 500;
      color: var(--text-primary);
    }
    
    .user-email {
      font-size: 12px;
      color: var(--text-muted);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
  
  .no-data {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 200px;
    color: var(--text-muted);
    font-size: 14px;
  }
}
</style>
