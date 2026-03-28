import request from './request'

export const adminApi = {
  getDashboard() {
    return request.get('/admin/dashboard')
  },
  
  getStudents(params) {
    return request.get('/admin/students', { params })
  },
  
  getStudentDetail(studentId) {
    return request.get(`/admin/students/${studentId}`)
  },
  
  updateStudentStatus(studentId, status) {
    return request.put(`/admin/students/${studentId}/status`, { status })
  },
  
  getAlerts(params) {
    return request.get('/admin/alerts', { params })
  },
  
  getAlertDetail(alertId) {
    return request.get(`/admin/alerts/${alertId}`)
  },
  
  updateAlertStatus(alertId, status, handleNote) {
    return request.put(`/admin/alerts/${alertId}/status`, { status, handleNote })
  },
  
  getEmotionStatistics(startDate, endDate) {
    return request.get('/admin/emotion-statistics', {
      params: { startDate, endDate }
    })
  },
  
  getHighRiskUsers() {
    return request.get('/admin/high-risk-users')
  },
  
  getRecentAlerts(limit = 10) {
    return request.get('/admin/recent-alerts', { params: { limit } })
  }
}
