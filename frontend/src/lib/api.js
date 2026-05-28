const BASE_URL = '/api/generation-sessions'

async function request(path, options = {}) {
  // 后端统一返回 R<T>，这里集中拆包和错误处理，页面层只接收 data。
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  })

  const payload = await response.json().catch(() => null)
  if (!response.ok || payload?.code !== 200) {
    throw new Error(payload?.msg || '请求失败')
  }
  return payload.data
}

export function createSession(data) {
  return request('', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function generateReferenceImage(data) {
  return request('/reference-images', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function generateKeyframes(sessionId) {
  return request(`/${sessionId}/keyframes`, { method: 'POST' })
}

export function submitVideo(sessionId) {
  return request(`/${sessionId}/video`, { method: 'POST' })
}

export function submitVideoFromKeyframes(data) {
  return request('/video', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function refreshVideo(sessionId) {
  return request(`/${sessionId}/video/status`, { method: 'POST' })
}
