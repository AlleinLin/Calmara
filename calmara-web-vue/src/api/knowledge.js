import request from './request'

export const knowledgeApi = {
  listDocuments(page = 1, size = 10, category) {
    return request.get('/admin/knowledge-base/list', {
      params: { page, size, category }
    })
  },
  
  getDocument(id) {
    return request.get(`/admin/knowledge-base/get/${id}`)
  },
  
  addDocument(document) {
    return request.post('/admin/knowledge-base/add', document)
  },
  
  updateDocument(id, document) {
    return request.put(`/admin/knowledge-base/update/${id}`, document)
  },
  
  deleteDocument(id) {
    return request.delete(`/admin/knowledge-base/delete/${id}`)
  },
  
  importFromJson(jsonContent) {
    return request.post('/admin/knowledge-base/import/json', jsonContent, {
      headers: { 'Content-Type': 'application/json' }
    })
  },
  
  importFromFile(file) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/admin/knowledge-base/import/file', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  
  batchImport(documents) {
    return request.post('/admin/knowledge-base/batch', documents)
  },
  
  getCategories() {
    return request.get('/admin/knowledge-base/categories')
  },
  
  getStats() {
    return request.get('/admin/knowledge-base/stats')
  },
  
  validateDocuments(documents) {
    return request.post('/admin/knowledge-base/validate', documents)
  },
  
  incrementalUpdate(documents) {
    return request.post('/admin/knowledge-base/incremental-update', documents)
  }
}
