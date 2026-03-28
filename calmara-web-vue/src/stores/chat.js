import { defineStore } from 'pinia'

export const useChatStore = defineStore('chat', {
  state: () => ({
    currentSessionId: null,
    messages: [],
    chatHistory: [],
    emotion: {
      label: '正常',
      score: 0,
      riskLevel: 'LOW'
    },
    isLoading: false,
    isStreaming: false,
    inputText: '',
    error: null
  }),
  
  getters: {
    currentMessages: (state) => state.messages,
    hasMessages: (state) => state.messages.length > 0,
    lastMessage: (state) => state.messages[state.messages.length - 1],
    emotionIndicator: (state) => {
      const { label, riskLevel } = state.emotion
      if (riskLevel === 'HIGH' || label === '高风险') {
        return { class: 'high-risk', text: '高风险 - 请关注' }
      } else if (label === '低落') {
        return { class: 'depressed', text: '情绪低落' }
      } else if (label === '焦虑') {
        return { class: 'anxious', text: '轻微焦虑' }
      }
      return { class: 'normal', text: '情绪正常' }
    }
  },
  
  actions: {
    addMessage(role, content) {
      this.messages.push({
        id: Date.now(),
        role,
        content,
        timestamp: new Date().toISOString()
      })
    },
    
    updateLastAssistantMessage(content) {
      const lastMessage = this.messages[this.messages.length - 1]
      if (lastMessage && lastMessage.role === 'assistant') {
        lastMessage.content = content
      } else {
        this.addMessage('assistant', content)
      }
    },
    
    setEmotion(emotion) {
      this.emotion = emotion
    },
    
    setSessionId(sessionId) {
      this.currentSessionId = sessionId
    },
    
    setLoading(loading) {
      this.isLoading = loading
    },
    
    setStreaming(streaming) {
      this.isStreaming = streaming
    },
    
    setInputText(text) {
      this.inputText = text
    },
    
    clearMessages() {
      this.messages = []
      this.currentSessionId = null
      this.emotion = { label: '正常', score: 0, riskLevel: 'LOW' }
    },
    
    loadChatHistory() {
      const history = JSON.parse(localStorage.getItem('calmara_history') || '[]')
      this.chatHistory = history
    },
    
    saveToHistory(message) {
      const historyItem = {
        sessionId: this.currentSessionId || 'new',
        title: message.substring(0, 30) + (message.length > 30 ? '...' : ''),
        timestamp: Date.now()
      }
      
      this.chatHistory.unshift(historyItem)
      localStorage.setItem(
        'calmara_history',
        JSON.stringify(this.chatHistory.slice(0, 50))
      )
    },
    
    setError(error) {
      this.error = error
    },
    
    clearError() {
      this.error = null
    }
  }
})
