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

const statCards = computed(() => [
  { label: '高风险', value: overview.value.highRiskCount, note: '严重缺陷或阻塞问题' },
  { label: '中风险', value: overview.value.mediumRiskCount, note: '明确问题，需要尽快整改' },
  { label: '低风险', value: overview.value.lowRiskCount, note: '轻微问题，不构成阻塞' },
  { label: '无风险', value: overview.value.noRiskCount, note: '未发现明确问题' },
  { label: '待回传', value: overview.value.pendingCount, note: '审查结果未完成回传' },
  { label: '覆盖仓库', value: overview.value.distinctRepoCount, note: '当前涉及的仓库数量' }
]);

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
        <span class="eyebrow">OpenClaw Frontend</span>
        <h1>审查系统首页</h1>
        <div class="hero-actions">
          <RouterLink class="btn btn-primary" to="/review-admin">进入 BI 看板</RouterLink>
          <button class="btn btn-ghost" @click="refreshAll">刷新首页数据</button>
        </div>
      </div>
      <div class="hero-side">
        <div class="hero-stat">
          <div class="hero-stat-label">总提交</div>
          <div class="hero-stat-value">{{ overview.totalCount }}</div>
          <div class="hero-stat-copy">当前数据库内的审查记录总数。</div>
        </div>
        <div class="hero-stat">
          <div class="hero-stat-label">今日新增</div>
          <div class="hero-stat-value">{{ overview.todayCount }}</div>
          <div class="hero-stat-copy">今天新增入库的提交审查数量。</div>
        </div>
      </div>
    </section>

    <section class="kpi-grid">
      <article v-for="card in statCards" :key="card.label" class="kpi-card simple">
        <div class="kpi-label">{{ card.label }}</div>
        <div class="kpi-value">{{ card.value }}</div>
        <div class="kpi-note">{{ card.note }}</div>
      </article>
    </section>

    <section class="dashboard compact-home">
      <div class="stack">
        <section class="panel panel-pad">
          <div class="panel-head">
            <div>
              <div class="section-tag">Entry</div>
              <div class="panel-title">快捷入口</div>
              <div class="panel-subtitle">需要分析就进 BI，看具体记录就进详情。</div>
            </div>
          </div>
          <div class="quick-grid">
            <RouterLink class="quick-link" to="/review-admin">
              <h3>BI 看板</h3>
              <p>查看扇形图、折线图、排行和表格，适合日常巡检和汇报。</p>
            </RouterLink>
            <RouterLink class="quick-link" :to="recentRecords[0] ? `/review-detail/${recentRecords[0].id}` : '/review-admin'">
              <h3>最近一条详情</h3>
              <p>直接打开最近一条提交的完整审查详情，查看建议和结构图。</p>
            </RouterLink>
          </div>
        </section>

        <section class="panel panel-pad">
          <div class="panel-head">
            <div>
              <div class="section-tag">Snapshot</div>
              <div class="panel-title">当前概况</div>
              <div class="panel-subtitle">首页保留轻量摘要，不堆重图表，但把关键状态收进来。</div>
            </div>
          </div>
          <div class="ranking-list">
            <div class="ranking-item">
              <div class="subject">风险概况</div>
              <div class="subtext">高风险 {{ overview.highRiskCount }} / 中风险 {{ overview.mediumRiskCount }} / 低风险 {{ overview.lowRiskCount }}</div>
            </div>
            <div class="ranking-item">
              <div class="subject">稳定性</div>
              <div class="subtext">无风险 {{ overview.noRiskCount }} / 待回传 {{ overview.pendingCount }} / 已忽略 {{ overview.ignoredCount }}</div>
            </div>
            <div class="ranking-item">
              <div class="subject">覆盖范围</div>
              <div class="subtext">当前涉及仓库 {{ overview.distinctRepoCount }} 个，今日新增 {{ overview.todayCount }} 条。</div>
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
                <div class="panel-subtitle">保留关键摘要，点进去看完整详情。</div>
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
