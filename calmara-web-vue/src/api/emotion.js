import request from './request'

export const emotionApi = {
  analyzeText(text) {
    return request.post('/emotion/analyze/text', null, {
      params: { text }
    })
  },
  
  analyzeAudio(file) {
    const formData = new FormData()
    formData.append('audio', file)
    return request.post('/emotion/analyze/audio', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  
  analyzeImage(file) {
    const formData = new FormData()
    formData.append('image', file)
    return request.post('/emotion/analyze/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  
  analyzeVideo(file) {
    const formData = new FormData()
    formData.append('video', file)
    return request.post('/emotion/analyze/video', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  
  transcribe(file) {
    const formData = new FormData()
    formData.append('audio', file)
    return request.post('/emotion/transcribe', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}
