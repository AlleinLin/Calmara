import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import router from '@/router'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: null,
    user: null,
    isAuthenticated: false,
    isAdmin: false,
    loading: false,
    error: null
  }),
  
  getters: {
    userInfo: (state) => state.user,
    username: (state) => state.user?.username || '游客',
    userId: (state) => state.user?.id || null
  },
  
  actions: {
    initializeAuth() {
      const token = localStorage.getItem('calmara_token')
      const userStr = localStorage.getItem('calmara_user')
      
      if (token && userStr) {
        try {
          this.token = token
          this.user = JSON.parse(userStr)
          this.isAuthenticated = true
          this.isAdmin = this.user?.role === 'ADMIN'
        } catch (e) {
          this.logout()
        }
      }
    },
    
    async login(username, password) {
      this.loading = true
      this.error = null
      
      try {
        const response = await authApi.login(username, password)
        
        if (response.success) {
          this.token = response.data.token
          this.user = {
            id: response.data.userId,
            username: response.data.username,
            role: response.data.role
          }
          this.isAuthenticated = true
          this.isAdmin = this.user.role === 'ADMIN'
          
          localStorage.setItem('calmara_token', this.token)
          localStorage.setItem('calmara_user', JSON.stringify(this.user))
          
          return { success: true }
        } else {
          this.error = response.message || '登录失败'
          return { success: false, message: this.error }
        }
      } catch (error) {
        this.error = error.message || '网络错误'
        return { success: false, message: this.error }
      } finally {
        this.loading = false
      }
    },
    
    async register(username, password, email) {
      this.loading = true
      this.error = null
      
      try {
        const response = await authApi.register(username, password, email)
        
        if (response.success) {
          return { success: true }
        } else {
          this.error = response.message || '注册失败'
          return { success: false, message: this.error }
        }
      } catch (error) {
        this.error = error.message || '网络错误'
        return { success: false, message: this.error }
      } finally {
        this.loading = false
      }
    },
    
    logout() {
      this.token = null
      this.user = null
      this.isAuthenticated = false
      this.isAdmin = false
      this.error = null
      
      localStorage.removeItem('calmara_token')
      localStorage.removeItem('calmara_user')
      
      router.push({ name: 'Login' })
    },
    
    guestLogin() {
      this.token = 'guest_token'
      this.user = {
        id: null,
        username: '游客',
        role: 'GUEST',
        email: null
      }
      this.isAuthenticated = true
      this.isAdmin = false
      
      localStorage.setItem('calmara_token', this.token)
      localStorage.setItem('calmara_user', JSON.stringify(this.user))
    },
    
    updateUser(userData) {
      this.user = { ...this.user, ...userData }
      localStorage.setItem('calmara_user', JSON.stringify(this.user))
    }
  },
  
  persist: {
    key: 'calmara-user-store',
    paths: ['token', 'user', 'isAuthenticated', 'isAdmin']
  }
})
