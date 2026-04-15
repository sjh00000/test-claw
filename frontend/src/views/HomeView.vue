<script setup>
import { computed, onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { getOverview, getRecords } from '../lib/api';
import { formatTime, riskClass } from '../lib/format';

const overview = ref({
  totalCount: 0,
  highRiskCount: 0,
  mediumRiskCount: 0,
  lowRiskCount: 0,
  noRiskCount: 0,
  pendingCount: 0,
  ignoredCount: 0,
  todayCount: 0,
  distinctRepoCount: 0
});
const recentRecords = ref([]);

const headlineMetrics = computed(() => {
  const total = Number(overview.value.totalCount || 0);
  const high = Number(overview.value.highRiskCount || 0);
  const ratio = total ? `${Math.round((high / total) * 100)}%` : '0%';
  return [
    { label: '高风险占比', value: ratio, copy: `高风险 ${high} / 总提交 ${total}` },
    { label: '待回传', value: overview.value.pendingCount || 0, copy: '等待消息回传或结果补齐' },
    { label: '覆盖仓库', value: overview.value.distinctRepoCount || 0, copy: '当前已纳入统计的仓库数' }
  ];
});

async function refreshAll() {
  const [overviewData, recordPage] = await Promise.all([
    getOverview(),
    getRecords({ page: 1, pageSize: 4 })
  ]);
  overview.value = overviewData;
  recentRecords.value = recordPage.records || [];
}

onMounted(refreshAll);
</script>

<template>
  <div class="page-grid">
    <section class="panel hero">
      <div>
        <div class="hero-topbar">
          <span class="eyebrow">OpenClaw Frontend</span>
          <div class="hero-badge-group">
            <span class="hero-badge">总记录 {{ overview.totalCount }}</span>
            <span class="hero-badge">今日新增 {{ overview.todayCount }}</span>
          </div>
        </div>
        <h1>审查系统首页</h1>
        <div class="hero-summary-grid">
          <article v-for="metric in headlineMetrics" :key="metric.label" class="hero-summary-card">
            <div class="hero-summary-label">{{ metric.label }}</div>
            <div class="hero-summary-value">{{ metric.value }}</div>
            <div class="hero-summary-copy">{{ metric.copy }}</div>
          </article>
        </div>
        <div class="hero-actions">
          <RouterLink class="btn btn-primary" to="/review-admin">进入 BI 看板</RouterLink>
          <button class="btn btn-ghost" @click="refreshAll">刷新首页数据</button>
        </div>
      </div>
      <div class="hero-side">
        <div class="hero-stat">
          <div class="hero-stat-label">风险分布</div>
          <div class="hero-stat-value">{{ overview.highRiskCount }} / {{ overview.mediumRiskCount }}</div>
          <div class="hero-stat-copy">高风险 / 中风险</div>
        </div>
        <div class="hero-stat">
          <div class="hero-stat-label">稳定记录</div>
          <div class="hero-stat-value">{{ overview.noRiskCount }}</div>
          <div class="hero-stat-copy">当前无风险记录数</div>
        </div>
      </div>
    </section>

    <section class="dashboard compact-home">
      <div class="stack">
        <section class="panel panel-pad">
          <div class="panel-head">
            <div>
              <div class="section-tag">Entry</div>
              <div class="panel-title">快捷入口</div>
              <div class="panel-subtitle">按场景直接跳转。</div>
            </div>
          </div>
          <div class="quick-grid">
            <RouterLink class="quick-link" to="/review-admin">
              <h3>BI 看板</h3>
              <p>集中查看趋势、排行、风险结构和明细表。</p>
            </RouterLink>
            <RouterLink class="quick-link" :to="recentRecords[0] ? `/review-detail/${recentRecords[0].id}` : '/review-admin'">
              <h3>最近一条详情</h3>
              <p>直接进入最近一条提交的审查详情。</p>
            </RouterLink>
          </div>
        </section>

        <section class="panel panel-pad">
          <div class="panel-head">
            <div>
              <div class="section-tag">Snapshot</div>
              <div class="panel-title">当前概况</div>
              <div class="panel-subtitle">只保留首页该看的核心状态。</div>
            </div>
          </div>
          <div class="summary-grid">
            <div class="summary-card">
              <div class="summary-label">高风险</div>
              <div class="summary-value">{{ overview.highRiskCount }}</div>
              <div class="summary-copy">需要优先跟进</div>
            </div>
            <div class="summary-card">
              <div class="summary-label">中风险</div>
              <div class="summary-value">{{ overview.mediumRiskCount }}</div>
              <div class="summary-copy">建议尽快处理</div>
            </div>
            <div class="summary-card">
              <div class="summary-label">待回传</div>
              <div class="summary-value">{{ overview.pendingCount }}</div>
              <div class="summary-copy">结果仍未完成回传</div>
            </div>
            <div class="summary-card">
              <div class="summary-label">覆盖仓库</div>
              <div class="summary-value">{{ overview.distinctRepoCount }}</div>
              <div class="summary-copy">当前纳入统计的仓库数</div>
            </div>
          </div>
        </section>
      </div>

      <aside class="sidebar-stack static">
        <section class="panel ranking-panel">
          <div class="side-section">
            <div class="panel-head tight">
              <div>
                <div class="section-tag">Recent</div>
                <div class="panel-title">最近提交</div>
                <div class="panel-subtitle">固定展示最近 4 条。</div>
              </div>
            </div>
            <div v-if="recentRecords.length" class="ranking-list">
              <article v-for="record in recentRecords" :key="record.id" class="ranking-item">
                <div class="ranking-top">
                  <div style="display:grid;gap:6px">
                    <div class="subject">{{ record.commitSubject || '无标题提交' }}</div>
                    <div class="subtext">{{ record.authorName || '未知作者' }} / {{ record.repoKey || '-' }}</div>
                  </div>
                  <span class="risk-tag" :class="riskClass(record.reviewResult)">{{ record.reviewResult || '未知' }}</span>
                </div>
                <div class="ranking-meta">
                  <span class="pill">{{ formatTime(record.committedAt) }}</span>
                  <RouterLink class="link-btn" :to="`/review-detail/${record.id}`">查看详情</RouterLink>
                </div>
              </article>
            </div>
            <div v-else class="empty">暂无最近提交数据</div>
          </div>
        </section>
      </aside>
    </section>
  </div>
</template>
