<template>
  <div class="chat-view">
    <header class="chat-header">
      <h1 class="chat-title">{{ chatTitle }}</h1>
      <div class="emotion-indicator">
        <span class="emotion-dot" :class="emotionClass"></span>
        <span>{{ emotionText }}</span>
      </div>
    </header>
    
    <div class="chat-messages" ref="messagesContainer">
      <div v-if="!chatStore.hasMessages" class="empty-state">
        <div class="empty-icon">🌊</div>
        <h2 class="empty-title">欢迎来到 Calmara</h2>
        <p class="empty-desc">
          我是你的智能心理关怀助手。在这里，你可以安全地倾诉心事，
          我会耐心倾听并提供专业的心理支持。
        </p>
        <div class="capabilities">
          <div class="capability">
            <div class="capability-icon">💬</div>
            <div class="capability-title">倾诉倾听</div>
          </div>
          <div class="capability">
            <div class="capability-icon">😊</div>
            <div class="capability-title">情绪识别</div>
          </div>
          <div class="capability">
            <div class="capability-icon">📊</div>
            <div class="capability-title">专业分析</div>
          </div>
        </div>
      </div>
      
      <div
        v-for="message in chatStore.messages"
        :key="message.id"
        class="message"
        :class="message.role"
      >
        <div class="message-avatar">
          {{ message.role === 'user' ? '👤' : '🧠' }}
        </div>
        <div class="message-content">
          <div class="message-role">
            {{ message.role === 'user' ? '你' : 'Calmara' }}
          </div>
          <div class="message-text" v-html="formatMessage(message.content)"></div>
        </div>
      </div>
      
      <div v-if="chatStore.isLoading" class="message assistant">
        <div class="message-avatar">🧠</div>
        <div class="message-content">
          <div class="message-role">Calmara</div>
          <div class="typing-indicator">
            <div class="typing-dot"></div>
            <div class="typing-dot"></div>
            <div class="typing-dot"></div>
          </div>
        </div>
      </div>
    </div>
    
    <div class="chat-input-container">
      <div class="input-wrapper">
        <textarea
          v-model="inputText"
          class="chat-input"
          placeholder="输入你想倾诉的内容..."
          rows="1"
          @keydown="handleKeyDown"
          @input="autoResize"
        ></textarea>
        <div class="input-actions">
          <button class="action-btn" @click="toggleAudio" title="语音输入">
            <el-icon><Microphone /></el-icon>
          </button>
          <button class="action-btn" @click="toggleImage" title="图片/视频">
            <el-icon><Picture /></el-icon>
          </button>
          <button
            class="action-btn send-btn"
            :disabled="!inputText.trim() || chatStore.isLoading"
            @click="sendMessage"
          >
            <el-icon><Promotion /></el-icon>
          </button>
        </div>
      </div>
    </div>
    
    <input
      ref="fileInput"
      type="file"
      accept="image/*,video/*"
      style="display: none"
      @change="handleFileSelect"
    />
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { useChatStore } from '@/stores'
import { chatApi } from '@/api'
import { ElMessage } from 'element-plus'
import { Microphone, Picture, Promotion } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const chatStore = useChatStore()

const messagesContainer = ref(null)
const fileInput = ref(null)
const inputText = ref('')
const streamController = ref(null)
let accumulatedMessage = ''

const chatTitle = computed(() => {
  if (chatStore.currentSessionId) {
    return '对话中'
  }
  return '新的对话'
})

const emotionClass = computed(() => chatStore.emotionIndicator.class)
const emotionText = computed(() => chatStore.emotionIndicator.text)

const formatMessage = (content) => {
  if (!content) return ''
  const html = marked(content)
  return DOMPurify.sanitize(html)
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

const autoResize = (event) => {
  const target = event.target
  target.style.height = 'auto'
  target.style.height = Math.min(target.scrollHeight, 150) + 'px'
}

const handleKeyDown = (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

const sendMessage = () => {
  const text = inputText.value.trim()
  if (!text || chatStore.isLoading) return
  
  if (streamController.value && !streamController.value.isClosed()) {
    streamController.value.close()
    streamController.value = null
  }
  
  inputText.value = ''
  chatStore.addMessage('user', text)
  chatStore.saveToHistory(text)
  chatStore.setLoading(true)
  scrollToBottom()
  
  accumulatedMessage = ''
  
  streamController.value = chatApi.streamChat(
    text,
    chatStore.currentSessionId,
    (data) => {
      accumulatedMessage += data
      chatStore.updateLastAssistantMessage(accumulatedMessage)
      scrollToBottom()
    },
    (emotion) => {
      chatStore.setEmotion(emotion)
    },
    (data) => {
      chatStore.setSessionId(data.sessionId)
      chatStore.setLoading(false)
      streamController.value = null
    },
    (error) => {
      chatStore.setLoading(false)
      chatStore.updateLastAssistantMessage('抱歉，服务暂时不可用，请稍后再试。')
      ElMessage.error('连接失败，请稍后重试')
      streamController.value = null
    }
  )
}

const toggleAudio = () => {
  ElMessage.info('语音功能开发中...')
}

const toggleImage = () => {
  fileInput.value?.click()
}

const handleFileSelect = (event) => {
  const file = event.target.files?.[0]
  if (file) {
    ElMessage.success(`已选择: ${file.name}`)
  }
  event.target.value = ''
}

onMounted(() => {
  scrollToBottom()
})

onUnmounted(() => {
  if (streamController.value && !streamController.value.isClosed()) {
    streamController.value.close()
    streamController.value = null
  }
})
</script>

<style lang="scss" scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-primary);
}

.chat-header {
  padding: 20px 32px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--bg-secondary);
}

.chat-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.emotion-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: var(--bg-tertiary);
  border-radius: 20px;
  font-size: 13px;
  color: var(--text-secondary);
}

.emotion-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--success);
  
  &.anxious { background: var(--warning); }
  &.depressed { background: #f97316; }
  &.high-risk {
    background: var(--danger);
    animation: pulse 1s infinite;
  }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 32px;
  
  &::-webkit-scrollbar {
    width: 6px;
  }
  
  &::-webkit-scrollbar-thumb {
    background: var(--border);
    border-radius: 3px;
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  padding: 40px;
}

.empty-icon {
  width: 120px;
  height: 120px;
  background: var(--gradient-calm);
  border-radius: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  margin-bottom: 24px;
  box-shadow: var(--shadow-glow);
}

.empty-title {
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--text-primary);
}

.empty-desc {
  font-size: 15px;
  color: var(--text-secondary);
  max-width: 400px;
  line-height: 1.6;
}

.capabilities {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-top: 40px;
  max-width: 400px;
}

.capability {
  background: var(--bg-secondary);
  padding: 20px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  text-align: center;
  transition: all var(--transition-fast);
  
  &:hover {
    border-color: var(--primary);
    transform: translateY(-4px);
  }
}

.capability-icon {
  font-size: 28px;
  margin-bottom: 12px;
}

.capability-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.message {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  animation: fadeIn 0.3s ease;
  
  &.user {
    .message-avatar {
      background: var(--gradient-primary);
    }
  }
  
  &.assistant {
    .message-avatar {
      background: var(--gradient-calm);
    }
  }
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.message-content {
  flex: 1;
  max-width: 800px;
}

.message-role {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--text-primary);
}

.message-text {
  font-size: 15px;
  line-height: 1.7;
  color: var(--text-primary);
  
  :deep(p) {
    margin-bottom: 12px;
    
    &:last-child {
      margin-bottom: 0;
    }
  }
  
  :deep(strong) {
    color: var(--primary-light);
  }
  
  :deep(ul), :deep(ol) {
    margin: 12px 0;
    padding-left: 24px;
  }
  
  :deep(li) {
    margin-bottom: 8px;
  }
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 12px;
}

.typing-dot {
  width: 8px;
  height: 8px;
  background: var(--text-muted);
  border-radius: 50%;
  animation: typingBounce 1.4s infinite;
  
  &:nth-child(2) { animation-delay: 0.2s; }
  &:nth-child(3) { animation-delay: 0.4s; }
}

.chat-input-container {
  padding: 24px 32px;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border);
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  background: var(--bg-tertiary);
  border-radius: var(--radius-lg);
  padding: 12px 16px;
  border: 1px solid var(--border);
  transition: all var(--transition-fast);
  
  &:focus-within {
    border-color: var(--primary);
    box-shadow: var(--shadow-glow);
  }
}

.chat-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-primary);
  font-size: 15px;
  font-family: inherit;
  resize: none;
  max-height: 150px;
  line-height: 1.5;
  
  &::placeholder {
    color: var(--text-muted);
  }
}

.input-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
  
  &:hover {
    background: var(--bg-secondary);
    color: var(--text-primary);
  }
  
  &.send-btn {
    background: var(--gradient-primary);
    color: white;
    
    &:hover {
      transform: scale(1.05);
      box-shadow: var(--shadow-glow);
    }
    
    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
      transform: none;
    }
  }
}

@keyframes typingBounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-4px); }
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}

@media (max-width: 768px) {
  .chat-header {
    padding: 16px;
  }
  
  .chat-messages {
    padding: 16px;
  }
  
  .chat-input-container {
    padding: 16px;
  }
  
  .capabilities {
    grid-template-columns: 1fr;
  }
}
</style>
