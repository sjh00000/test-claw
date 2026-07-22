export const VIDEO_DURATION_MIN = 4
export const VIDEO_DURATION_MAX = 15
export const STATUS_POLL_INTERVAL_MS = 5000
export const SUBMIT_DEBOUNCE_MS = 1200

export const ACTIVE_STATUS_CODES = ['SUBMITTED', 'RUNNING']

// 前端用这些状态统一判断轮询是否结束；后端仍然是最终状态来源。
export const TERMINAL_STATUS_CODES = [
  'SUCCEEDED',
  'SUCCESS',
  'COMPLETED',
  'FAILED',
  'FAIL',
  'CANCELED',
  'CANCELLED'
]

export const IMAGE_SIZE_OPTIONS = ['1024x1024', '1024x1536', '1536x1024']
export const IMAGE_QUALITY_OPTIONS = ['low', 'medium', 'high']
export const VIDEO_RESOLUTION_OPTIONS = ['480p', '720p']
export const VIDEO_RATIO_OPTIONS = ['adaptive', '16:9', '4:3', '9:16', '3:4', '1:1', '21:9']

export function isTerminalGenerationStatus(result) {
  const status = String(result?.status || '').toUpperCase()
  return Boolean(
    result?.imageUrl
    || result?.videoUrl
    || result?.resultUrl
    || result?.failReason
    || TERMINAL_STATUS_CODES.includes(status)
  )
}

export function isActiveGenerationStatus(result) {
  const status = String(result?.status || '').toUpperCase()
  return ACTIVE_STATUS_CODES.includes(status) || ['已提交', '生成中'].includes(result?.status)
}
