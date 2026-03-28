import request from './request'

export const chatApi = {
  streamChat(text, sessionId, onMessage, onEmotion, onDone, onError) {
    const formData = new FormData()
    formData.append('text', text)
    if (sessionId) {
      formData.append('sessionId', sessionId)
    }
    
    const controller = new AbortController()
    let streamEnded = false
    
    fetch('/api/chat/stream', {
      method: 'POST',
      body: formData,
      signal: controller.signal,
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      }
    }).then(async response => {
      if (!response.ok) {
        const errorText = await response.text().catch(() => 'Unknown error')
        throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`)
      }
      
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      
      const processStream = async () => {
        while (true) {
          const { done, value } = await reader.read()
          
          if (done) {
            streamEnded = true
            if (onDone) {
              onDone({ sessionId: sessionId || 'new', reason: 'stream_complete' })
            }
            return
          }
          
          buffer += decoder.decode(value, { stream: true })
          
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''
          
          let currentEvent = ''
          
          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.substring(6).trim()
            } else if (line.startsWith('data:')) {
              const data = line.substring(5).trim()
              if (!data) continue
              
              if (currentEvent === 'emotion' && onEmotion) {
                try {
                  onEmotion(JSON.parse(data))
                } catch (e) {
                  console.error('Failed to parse emotion data:', e)
                }
              } else if (currentEvent === 'message' && onMessage) {
                onMessage(data)
              } else if (currentEvent === 'done' && onDone) {
                streamEnded = true
                try {
                  onDone(JSON.parse(data))
                } catch (e) {
                  onDone({ sessionId: sessionId || 'new' })
                }
                return
              } else if (currentEvent === 'error' && onError) {
                try {
                  const errorData = JSON.parse(data)
                  onError(new Error(errorData.message || 'Server error'))
                } catch (e) {
                  onError(new Error(data))
                }
                return
              }
            }
          }
        }
      }
      
      await processStream()
    }).catch(error => {
      if (error.name === 'AbortError') {
        console.log('Stream aborted by user')
        return
      }
      if (!streamEnded && onError) {
        onError(error)
      }
    })
    
    return {
      close: () => {
        if (!streamEnded) {
          controller.abort()
        }
      },
      isClosed: () => streamEnded
    }
  },
  
  async sendMessage(text, sessionId) {
    const formData = new FormData()
    formData.append('text', text)
    if (sessionId) {
      formData.append('sessionId', sessionId)
    }
    
    return request.post('/chat/stream', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  
  getSession(sessionId) {
    return request.get(`/admin/sessions/${sessionId}`)
  },
  
  getChatHistory(sessionId) {
    return request.get(`/chat/history/${sessionId}`)
  },
  
  deleteSession(sessionId) {
    return request.delete(`/chat/session/${sessionId}`)
  }
}
