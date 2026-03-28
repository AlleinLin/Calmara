<template>
  <div class="students-view">
    <div class="page-header">
      <h1 class="page-title">学生管理</h1>
      <div class="page-actions">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索学生..."
          :prefix-icon="Search"
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="filterRisk" placeholder="风险等级" clearable style="width: 140px">
          <el-option label="高风险" value="HIGH" />
          <el-option label="中风险" value="MEDIUM" />
          <el-option label="低风险" value="LOW" />
        </el-select>
      </div>
    </div>
    
    <div class="card">
      <el-table
        :data="students"
        v-loading="loading"
        style="width: 100%"
        @row-click="viewStudent"
      >
        <el-table-column label="学生" min-width="200">
          <template #default="{ row }">
            <div class="student-cell">
              <div class="student-avatar">{{ row.username?.charAt(0).toUpperCase() }}</div>
              <div class="student-info">
                <div class="student-name">{{ row.username }}</div>
                <div class="student-email">{{ row.email }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="studentId" label="学号" width="120" />
        
        <el-table-column prop="realName" label="姓名" width="100" />
        
        <el-table-column label="风险等级" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="getRiskTagType(row.riskLevel)"
              size="small"
            >
              {{ getRiskLabel(row.riskLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="最近情绪" width="100" align="center">
          <template #default="{ row }">
            <span class="emotion-label">{{ row.lastEmotion || '-' }}</span>
          </template>
        </el-table-column>
        
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click.stop="viewStudent(row)">
              查看
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
          @size-change="fetchStudents"
          @current-change="fetchStudents"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api'
import { Search } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

const router = useRouter()

const loading = ref(false)
const students = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchKeyword = ref('')
const filterRisk = ref('')

const fetchStudents = async () => {
  loading.value = true
  try {
    const response = await adminApi.getStudents({
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchKeyword.value,
      riskLevel: filterRisk.value
    })
    
    if (response.success) {
      students.value = response.data.records
      total.value = response.data.total
    }
  } catch (error) {
    console.error('获取学生列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchStudents()
}

const viewStudent = (row) => {
  router.push({ name: 'StudentDetail', params: { id: row.id } })
}

const formatDate = (date) => {
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

const getRiskTagType = (level) => {
  const types = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'success'
  }
  return types[level] || 'info'
}

const getRiskLabel = (level) => {
  const labels = {
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低'
  }
  return labels[level] || '未知'
}

watch(filterRisk, () => {
  currentPage.value = 1
  fetchStudents()
})

onMounted(() => {
  fetchStudents()
})
</script>

<style lang="scss" scoped>
.students-view {
  .student-cell {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  
  .student-avatar {
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
  
  .student-info {
    .student-name {
      font-weight: 500;
      color: var(--text-primary);
    }
    
    .student-email {
      font-size: 12px;
      color: var(--text-muted);
    }
  }
  
  .emotion-label {
    font-size: 13px;
    color: var(--text-secondary);
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
    
    .el-table__row {
      cursor: pointer;
    }
  }
}
</style>
