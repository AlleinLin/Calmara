import request from './request'

export const finetuneApi = {
  getStatus() {
    return request.get('/admin/finetune/status')
  },
  
  getState() {
    return request.get('/admin/finetune/state')
  },
  
  triggerFinetune(reason) {
    return request.post('/admin/finetune/trigger', { reason })
  },
  
  stopFinetune() {
    return request.post('/admin/finetune/stop')
  },
  
  getConfig() {
    return request.get('/admin/finetune/config')
  },
  
  updateConfig(config) {
    return request.post('/admin/finetune/config', config)
  },
  
  updatePartialConfig(updates) {
    return request.patch('/admin/finetune/config', updates)
  },
  
  resetConfig() {
    return request.post('/admin/finetune/config/reset')
  },
  
  getTriggerConfig() {
    return request.get('/admin/finetune/config/trigger')
  },
  
  updateTriggerConfig(config) {
    return request.post('/admin/finetune/config/trigger', config)
  },
  
  getResourceConfig() {
    return request.get('/admin/finetune/config/resource')
  },
  
  updateResourceConfig(config) {
    return request.post('/admin/finetune/config/resource', config)
  },
  
  getTrainingConfig() {
    return request.get('/admin/finetune/config/training')
  },
  
  updateTrainingConfig(config) {
    return request.post('/admin/finetune/config/training', config)
  },
  
  getRemoteServerConfig() {
    return request.get('/admin/finetune/config/remote-server')
  },
  
  updateRemoteServerConfig(config) {
    return request.post('/admin/finetune/config/remote-server', config)
  },
  
  testRemoteConnection(config) {
    return request.post('/admin/finetune/remote/test-connection', config)
  },
  
  testCurrentRemoteConnection() {
    return request.post('/admin/finetune/remote/test-connection/current')
  },
  
  executeRemoteCommand(command) {
    return request.post('/admin/finetune/remote/execute', { command })
  },
  
  uploadToRemote(localPath, remotePath) {
    return request.post('/admin/finetune/remote/upload', { localPath, remotePath })
  },
  
  downloadFromRemote(remotePath, localPath) {
    return request.post('/admin/finetune/remote/download', { remotePath, localPath })
  },
  
  getNotificationConfig() {
    return request.get('/admin/finetune/config/notification')
  },
  
  updateNotificationConfig(config) {
    return request.post('/admin/finetune/config/notification', config)
  },
  
  getEvaluationConfig() {
    return request.get('/admin/finetune/config/evaluation')
  },
  
  updateEvaluationConfig(config) {
    return request.post('/admin/finetune/config/evaluation', config)
  },
  
  getModelManagementConfig() {
    return request.get('/admin/finetune/config/model-management')
  },
  
  updateModelManagementConfig(config) {
    return request.post('/admin/finetune/config/model-management', config)
  }
}
