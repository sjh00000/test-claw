const API_BASE = '/api'
export const AUTH_EXPIRED_EVENT = 'studio-auth-expired'

function getSavedUser() {
  return JSON.parse(localStorage.getItem('studioUser') || 'null')
}

function notifyAuthExpired(message) {
  // JWT 过期属于全局登录态变化，统一通知应用壳层切回登录页，避免各页面只展示错误条。
  localStorage.removeItem('studioUser')
  window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT, { detail: { message } }))
}

async function request(path, options = {}) {
  // API 层集中注入 JWT，页面和组件不需要关心 Authorization 头如何拼接。
  const savedUser = getSavedUser()
  const headers = {
    'Content-Type': 'application/json',
    ...(savedUser?.accessToken ? { Authorization: `Bearer ${savedUser.accessToken}` } : {}),
    ...(options.headers || {})
  }

  // 后端统一返回 R<T>，这里集中拆包，页面只处理业务数据。
  const response = await fetch(`${API_BASE}${path}`, {
    headers,
    ...options
  })

  const payload = await response.json().catch(() => null)
  if (!response.ok || payload?.code !== 200) {
    if (response.status === 401) {
      notifyAuthExpired(payload?.msg || '登录已过期，请重新登录')
    }
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

export function queryTaskStatus(data) {
  return request('/generation/tasks/status', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function getActiveTask() {
  return request('/generation/tasks/active', {
    method: 'POST',
    body: JSON.stringify({})
  })
}

export function listTasks(data) {
  return request('/generation/tasks', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function listAdminUsers(data) {
  return request('/admin/users', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function updateAdminUser(data) {
  return request('/admin/users/update', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function listAdminLogs(data) {
  return request('/admin/logs', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function listModelConfigs(data) {
  return request('/admin/model-configs', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}

export function saveModelConfig(data) {
  return request('/admin/model-configs/save', {
    method: 'POST',
    body: JSON.stringify(data)
  })
}
