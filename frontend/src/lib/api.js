const API_BASE = '/api'

async function request(path, options = {}) {
  // API 层集中注入 JWT，页面和组件不需要关心 Authorization 头如何拼接。
  const savedUser = JSON.parse(localStorage.getItem('studioUser') || 'null')
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
      localStorage.removeItem('studioUser')
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
