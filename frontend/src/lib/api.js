const API_BASE = '/api'

async function request(path, options = {}) {
  // 后端统一返回 R<T>，这里集中拆包，页面只处理业务数据。
  const response = await fetch(`${API_BASE}${path}`, {
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

export function login(data) {
  return request('/auth/login', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function generateImage(data) {
  return request('/generation/images', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function generateVideo(data) {
  return request('/generation/videos', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function queryVideoStatus(data) {
  return request('/generation/videos/status', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}
