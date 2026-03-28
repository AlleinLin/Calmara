import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    theme: 'dark',
    sidebarCollapsed: false,
    loading: false,
    notifications: [],
    systemStatus: {
      ollama: false,
      chroma: false,
      whisper: false,
      mediapipe: false
    }
  }),
  
  getters: {
    isDark: (state) => state.theme === 'dark',
    unreadNotifications: (state) => 
      state.notifications.filter(n => !n.read).length
  },
  
  actions: {
    toggleTheme() {
      this.theme = this.theme === 'dark' ? 'light' : 'dark'
      document.documentElement.setAttribute('data-theme', this.theme)
    },
    
    setTheme(theme) {
      this.theme = theme
      document.documentElement.setAttribute('data-theme', theme)
    },
    
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
    },
    
    setSidebarCollapsed(collapsed) {
      this.sidebarCollapsed = collapsed
    },
    
    setLoading(loading) {
      this.loading = loading
    },
    
    addNotification(notification) {
      this.notifications.unshift({
        id: Date.now(),
        ...notification,
        read: false,
        timestamp: new Date().toISOString()
      })
    },
    
    markNotificationRead(id) {
      const notification = this.notifications.find(n => n.id === id)
      if (notification) {
        notification.read = true
      }
    },
    
    clearNotifications() {
      this.notifications = []
    },
    
    updateSystemStatus(status) {
      this.systemStatus = { ...this.systemStatus, ...status }
    }
  },
  
  persist: {
    key: 'calmara-app-store',
    paths: ['theme', 'sidebarCollapsed']
  }
})
