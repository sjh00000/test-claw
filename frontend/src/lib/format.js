export function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '-';
}

export function shortSha(value) {
  return value ? String(value).slice(0, 10) : '-';
}

export function summarizeAdvice(value, limit = 60) {
  if (!value) return '无审查建议';
  const text = String(value).replace(/\s+/g, ' ').trim();
  return text.length > limit ? `${text.slice(0, limit)}...` : text;
}

export function riskClass(result) {
  if (result === '高风险') return 'risk-high';
  if (result === '中风险') return 'risk-medium';
  if (result === '低风险') return 'risk-low';
  if (result === '无风险') return 'risk-safe';
  if (result === '待回传') return 'risk-pending';
  return 'risk-default';
}

export function statLabel(key) {
  const mapping = {
    high: '高风险',
    medium: '中风险',
    low: '低风险',
    safe: '无风险',
    pending: '待回传',
    today: '今日新增'
  };
  return mapping[key] || '全部记录';
}
