<template>
  <div class="alerts-view">
    <div class="page-header">
      <h1 class="page-title">预警管理</h1>
      <div class="page-actions">
        <el-select v-model="filterStatus" placeholder="处理状态" clearable style="width: 120px">
          <el-option label="待处理" value="PENDING" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="已处理" value="RESOLVED" />
        </el-select>
        <el-select v-model="filterRisk" placeholder="风险等级" clearable style="width: 120px">
          <el-option label="高风险" value="HIGH" />
          <el-option label="中风险" value="MEDIUM" />
          <el-option label="低风险" value="LOW" />
        </el-select>
      </div>
    </div>
    
    <div class="card">
      <el-table :data="alerts" v-loading="loading" style="width: 100%">
        <el-table-column label="用户" min-width="150">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar">{{ row.userName?.charAt(0) || '?' }}</div>
              <span>{{ row.userName || '匿名用户' }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="风险等级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getRiskTagType(row.riskLevel)" size="small">
              {{ getRiskLabel(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="情绪" width="100" align="center">
          <template #default="{ row }">
            <span class="emotion-badge">{{ row.emotion || '-' }}</span>
          </template>
        </el-table-column>
        
        <el-table-column label="内容" min-width="200">
          <template #default="{ row }">
            <div class="content-cell">
              {{ row.content?.substring(0, 50) }}{{ row.content?.length > 50 ? '...' : '' }}
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="viewAlert(row)">
              详情
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              text
              type="success"
              @click="handleAlert(row)"
            >
              处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchAlerts"
          @current-change="fetchAlerts"
        />
      </div>
    </div>
    
    <el-dialog
      v-model="dialogVisible"
      title="预警详情"
      width="600px"
    >
      <div class="alert-detail" v-if="currentAlert">
        <div class="detail-row">
          <span class="label">用户：</span>
          <span>{{ currentAlert.userName || '匿名用户' }}</span>
        </div>
        <div class="detail-row">
          <span class="label">风险等级：</span>
          <el-tag :type="getRiskTagType(currentAlert.riskLevel)" size="small">
            {{ getRiskLabel(currentAlert.riskLevel) }}
          </el-tag>
        </div>
        <div class="detail-row">
          <span class="label">情绪状态：</span>
          <span>{{ currentAlert.emotion }}</span>
        </div>
        <div class="detail-row">
          <span class="label">情绪分数：</span>
          <span>{{ currentAlert.emotionScore }}</span>
        </div>
        <div class="detail-row">
          <span class="label">对话内容：</span>
          <div class="content-box">{{ currentAlert.content }}</div>
        </div>
        <div class="detail-row">
          <span class="label">创建时间：</span>
          <span>{{ formatDate(currentAlert.createdAt) }}</span>
        </div>
        
        <el-divider />
        
        <el-form :model="handleForm" label-width="80px">
          <el-form-item label="处理状态">
            <el-select v-model="handleForm.status" style="width: 100%">
              <el-option label="处理中" value="PROCESSING" />
              <el-option label="已处理" value="RESOLVED" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理备注">
            <el-input
              v-model="handleForm.handleNote"
              type="textarea"
              :rows="3"
              placeholder="请输入处理备注..."
            />
          </el-form-item>
        </el-form>
      </div>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitHandle" :loading="submitting">
          提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { adminApi } from '@/api'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'

const loading = ref(false)
const alerts = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filterStatus = ref('')
const filterRisk = ref('')

const dialogVisible = ref(false)
const currentAlert = ref(null)
const submitting = ref(false)
const handleForm = ref({
  status: 'PROCESSING',
  handleNote: ''
})

const fetchAlerts = async () => {
  loading.value = true
  try {
    const response = await adminApi.getAlerts({
      page: currentPage.value,
      size: pageSize.value,
      status: filterStatus.value,
      riskLevel: filterRisk.value
    })
    
    if (response.success) {
      alerts.value = response.data.records
      total.value = response.data.total
    }
  } catch (error) {
    console.error('获取预警列表失败:', error)
  } finally {
    loading.value = false
  }
}

const viewAlert = (alert) => {
  currentAlert.value = alert
  handleForm.value = {
    status: alert.status === 'PENDING' ? 'PROCESSING' : alert.status,
    handleNote: alert.handleNote || ''
  }
  dialogVisible.value = true
}

const handleAlert = (alert) => {
  viewAlert(alert)
}

const submitHandle = async () => {
  if (!currentAlert.value) return
  
  submitting.value = true
  try {
    const response = await adminApi.updateAlertStatus(
      currentAlert.value.id,
      handleForm.value.status,
      handleForm.value.handleNote
    )
    
    if (response.success) {
      ElMessage.success('处理成功')
      dialogVisible.value = false
      fetchAlerts()
    }
  } catch (error) {
    ElMessage.error('处理失败')
  } finally {
    submitting.value = false
  }
}

const formatDate = (date) => {
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

const getStatusTagType = (status) => {
  const types = { PENDING: 'danger', PROCESSING: 'warning', RESOLVED: 'success' }
  return types[status] || 'info'
}

const getStatusLabel = (status) => {
  const labels = { PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已处理' }
  return labels[status] || '未知'
}

watch([filterStatus, filterRisk], () => {
  currentPage.value = 1
  fetchAlerts()
})

onMounted(() => {
  fetchAlerts()
})
</script>

<style lang="scss" scoped>
.alerts-view {
  .user-cell {
    display: flex;
    align-items: center;
    gap: 8px;
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
    font-size: 12px;
    font-weight: 600;
  }
  
  .emotion-badge {
    padding: 4px 8px;
    background: var(--bg-tertiary);
    border-radius: 4px;
    font-size: 12px;
  }
  
  .content-cell {
    color: var(--text-secondary);
    font-size: 13px;
  }
  
  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    padding: 16px 0;
  }
  
  :deep(.el-table) {
    --el-table-bg-color: transparent;
    --el-table-tr-bg-color: transparent;
    --el-table-header-bg-color: var(--bg-tertiary);
    --el-table-row-hover-bg-color: var(--bg-tertiary);
    --el-table-border-color: var(--border);
    --el-table-text-color: var(--text-primary);
  }
  
  .alert-detail {
    .detail-row {
      display: flex;
      margin-bottom: 16px;
      
      .label {
        width: 80px;
        color: var(--text-secondary);
        flex-shrink: 0;
      }
    }
    
    .content-box {
      flex: 1;
      padding: 12px;
      background: var(--bg-tertiary);
      border-radius: var(--radius-md);
      line-height: 1.6;
    }
  }
}
</style>
