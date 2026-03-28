<template>
  <div class="knowledge-view">
    <div class="page-header">
      <h1 class="page-title">知识库管理</h1>
      <div class="page-actions">
        <el-button type="primary" @click="showAddDialog">
          <el-icon><Plus /></el-icon>
          添加文档
        </el-button>
        <el-button @click="showImportDialog">
          <el-icon><Upload /></el-icon>
          导入
        </el-button>
      </div>
    </div>
    
    <div class="stats-row">
      <div class="stat-item">
        <span class="stat-value">{{ stats.totalDocuments || 0 }}</span>
        <span class="stat-label">文档总数</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ stats.totalCategories || 0 }}</span>
        <span class="stat-label">分类数</span>
      </div>
      <div class="stat-item">
        <span class="stat-value">{{ stats.lastUpdateTime || '-' }}</span>
        <span class="stat-label">最后更新</span>
      </div>
    </div>
    
    <div class="card">
      <div class="filter-bar">
        <el-select v-model="filterCategory" placeholder="选择分类" clearable style="width: 200px">
          <el-option
            v-for="cat in categories"
            :key="cat"
            :label="cat"
            :value="cat"
          />
        </el-select>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文档..."
          :prefix-icon="Search"
          clearable
          style="width: 300px"
          @keyup.enter="fetchDocuments"
        />
      </div>
      
      <el-table :data="documents" v-loading="loading" style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column label="内容预览" min-width="250">
          <template #default="{ row }">
            <div class="content-preview">
              {{ row.content?.substring(0, 80) }}{{ row.content?.length > 80 ? '...' : '' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="editDocument(row)">编辑</el-button>
            <el-button text type="danger" @click="deleteDocument(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchDocuments"
          @current-change="fetchDocuments"
        />
      </div>
    </div>
    
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑文档' : '添加文档'"
      width="700px"
    >
      <el-form :model="documentForm" :rules="formRules" ref="formRef" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="documentForm.title" placeholder="请输入文档标题" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="documentForm.category" placeholder="选择分类" allow-create filterable>
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="documentForm.tags" multiple placeholder="选择标签" allow-create filterable>
            <el-option v-for="tag in documentForm.tags" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="documentForm.content"
            type="textarea"
            :rows="10"
            placeholder="请输入文档内容"
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDocument" :loading="submitting">
          确定
        </el-button>
      </template>
    </el-dialog>
    
    <el-dialog v-model="importDialogVisible" title="导入知识库" width="500px">
      <el-tabs>
        <el-tab-pane label="JSON导入">
          <el-upload
            drag
            accept=".json"
            :auto-upload="false"
            :on-change="handleJsonFile"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              拖拽JSON文件到此处，或<em>点击上传</em>
            </div>
          </el-upload>
        </el-tab-pane>
        <el-tab-pane label="批量导入">
          <el-input
            v-model="batchJson"
            type="textarea"
            :rows="10"
            placeholder="请粘贴JSON格式的文档数组..."
          />
          <el-button type="primary" class="mt-3" @click="importBatch" :loading="importing">
            导入
          </el-button>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { knowledgeApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Search, UploadFilled } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

const loading = ref(false)
const documents = ref([])
const categories = ref([])
const stats = ref({})
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filterCategory = ref('')
const searchKeyword = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const documentForm = reactive({
  id: null,
  title: '',
  category: '',
  content: '',
  tags: []
})

const formRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

const importDialogVisible = ref(false)
const batchJson = ref('')
const importing = ref(false)

const fetchDocuments = async () => {
  loading.value = true
  try {
    const response = await knowledgeApi.listDocuments(
      currentPage.value,
      pageSize.value,
      filterCategory.value
    )
    
    if (response.success) {
      documents.value = response.data.records
      total.value = response.data.total
    }
  } catch (error) {
    console.error('获取文档列表失败:', error)
  } finally {
    loading.value = false
  }
}

const fetchCategories = async () => {
  try {
    const response = await knowledgeApi.getCategories()
    if (response.success) {
      categories.value = response.data
    }
  } catch (error) {
    console.error('获取分类失败:', error)
  }
}

const fetchStats = async () => {
  try {
    const response = await knowledgeApi.getStats()
    if (response.success) {
      stats.value = response.data
    }
  } catch (error) {
    console.error('获取统计失败:', error)
  }
}

const showAddDialog = () => {
  isEdit.value = false
  Object.assign(documentForm, {
    id: null,
    title: '',
    category: '',
    content: '',
    tags: []
  })
  dialogVisible.value = true
}

const editDocument = (doc) => {
  isEdit.value = true
  Object.assign(documentForm, {
    id: doc.id,
    title: doc.title,
    category: doc.category,
    content: doc.content,
    tags: doc.tags || []
  })
  dialogVisible.value = true
}

const submitDocument = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    submitting.value = true
    try {
      const api = isEdit.value
        ? knowledgeApi.updateDocument(documentForm.id, documentForm)
        : knowledgeApi.addDocument(documentForm)
      
      const response = await api
      
      if (response.success) {
        ElMessage.success(isEdit.value ? '更新成功' : '添加成功')
        dialogVisible.value = false
        fetchDocuments()
        fetchStats()
      }
    } catch (error) {
      ElMessage.error('操作失败')
    } finally {
      submitting.value = false
    }
  })
}

const deleteDocument = async (doc) => {
  try {
    await ElMessageBox.confirm('确定要删除该文档吗？', '确认删除', { type: 'warning' })
    
    const response = await knowledgeApi.deleteDocument(doc.id)
    
    if (response.success) {
      ElMessage.success('删除成功')
      fetchDocuments()
      fetchStats()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const showImportDialog = () => {
  importDialogVisible.value = true
}

const handleJsonFile = async (file) => {
  importing.value = true
  try {
    const response = await knowledgeApi.importFromFile(file.raw)
    if (response.success) {
      ElMessage.success(`成功导入 ${response.data} 条文档`)
      importDialogVisible.value = false
      fetchDocuments()
      fetchStats()
    }
  } catch (error) {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}

const importBatch = async () => {
  if (!batchJson.value.trim()) {
    ElMessage.warning('请输入JSON内容')
    return
  }
  
  importing.value = true
  try {
    const documents = JSON.parse(batchJson.value)
    const response = await knowledgeApi.batchImport(documents)
    
    if (response.success) {
      ElMessage.success(`成功导入 ${response.data} 条文档`)
      importDialogVisible.value = false
      batchJson.value = ''
      fetchDocuments()
      fetchStats()
    }
  } catch (error) {
    ElMessage.error('JSON格式错误或导入失败')
  } finally {
    importing.value = false
  }
}

const formatDate = (date) => {
  return dayjs(date).format('YYYY-MM-DD HH:mm')
}

watch(filterCategory, () => {
  currentPage.value = 1
  fetchDocuments()
})

onMounted(() => {
  fetchDocuments()
  fetchCategories()
  fetchStats()
})
</script>

<style lang="scss" scoped>
.knowledge-view {
  .stats-row {
    display: flex;
    gap: 32px;
    margin-bottom: 24px;
    padding: 20px;
    background: var(--bg-card);
    border: 1px solid var(--border);
    border-radius: var(--radius-lg);
  }
  
  .stat-item {
    display: flex;
    flex-direction: column;
    
    .stat-value {
      font-size: 24px;
      font-weight: 600;
      color: var(--text-primary);
    }
    
    .stat-label {
      font-size: 13px;
      color: var(--text-secondary);
    }
  }
  
  .filter-bar {
    display: flex;
    gap: 16px;
    margin-bottom: 16px;
  }
  
  .content-preview {
    color: var(--text-secondary);
    font-size: 13px;
    line-height: 1.5;
  }
  
  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    padding: 16px 0;
  }
  
  .mt-3 {
    margin-top: 16px;
  }
  
  :deep(.el-table) {
    --el-table-bg-color: transparent;
    --el-table-tr-bg-color: transparent;
    --el-table-header-bg-color: var(--bg-tertiary);
    --el-table-row-hover-bg-color: var(--bg-tertiary);
    --el-table-border-color: var(--border);
    --el-table-text-color: var(--text-primary);
  }
}
</style>
