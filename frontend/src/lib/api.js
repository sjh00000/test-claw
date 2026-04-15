export async function fetchJson(url) {
  const response = await fetch(url);
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload.message || '请求失败');
  }
  return payload;
}

export const getOverview = () => fetchJson('/api/review-admin/overview');
export const getRepoKeys = () => fetchJson('/api/review-admin/repo-keys');
export const getHighRiskRanking = (limit = 8) => fetchJson(`/api/review-admin/rankings/unqualified-authors?limit=${limit}`);
export const getRecordDetail = (id) => fetchJson(`/api/review-admin/records/${id}`);

export function getRecords(params = {}) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value));
    }
  });
  return fetchJson(`/api/review-admin/records?${query.toString()}`);
}
