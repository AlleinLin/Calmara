import request from './request'

export const authApi = {
  login(username, password) {
    return request.post('/auth/login', { username, password })
  },
  
  register(username, password, email) {
    return request.post('/auth/register', { username, password, email })
  },
  
  logout() {
    return request.post('/auth/logout')
  },
  
  getCurrentUser() {
    return request.get('/auth/me')
  },
  
  updateProfile(data) {
    return request.put('/auth/profile', data)
  },
  
  changePassword(oldPassword, newPassword) {
    return request.put('/auth/password', { oldPassword, newPassword })
  }
}
