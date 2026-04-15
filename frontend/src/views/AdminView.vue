<script setup>
import * as echarts from 'echarts';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import EChartPanel from '../components/EChartPanel.vue';
import { getHighRiskRanking, getOverview, getRecords, getRepoKeys } from '../lib/api';
import { formatTime, riskClass, shortSha, statLabel, summarizeAdvice } from '../lib/format';

const overview = ref({ totalCount: 0, highRiskCount: 0, mediumRiskCount: 0, lowRiskCount: 0, noRiskCount: 0, ignoredCount: 0, pendingCount: 0, todayCount: 0, distinctRepoCount: 0, recentTrend: [] });
const records = ref({ total: 0, page: 1, pageSize: 10, records: [] });
const pagination = ref({ page: 1, pageSize: 10 });
const filters = ref({ keyword: '', reviewResult: '', repoKey: '' });
const repoKeys = ref([]);
const authorRanking = ref([]);
const activeStatKey = ref('');
const recordsLoading = ref(false);
const recordsError = ref('');
const showBackTop = ref(false);

const statCards = computed(() => [
  { key: '', label: '总提交数', value: overview.value.totalCount, note: '点击回到全部记录' },
  { key: 'high', label: '高风险', value: overview.value.highRiskCount, note: '严重缺陷或阻塞问题' },
  { key: 'medium', label: '中风险', value: overview.value.mediumRiskCount, note: '明确问题，需要尽快整改' },
  { key: 'low', label: '低风险', value: overview.value.lowRiskCount, note: '轻微问题，不构成阻塞' },
  { key: 'safe', label: '无风险', value: overview.value.noRiskCount, note: '未发现明确问题' },
  { key: 'today', label: '今日新增', value: overview.value.todayCount, note: '查看今天新增记录' }
]);

const totalPages = computed(() => Math.max(1, Math.ceil((records.value.total || 0) / (records.value.pageSize || pagination.value.pageSize || 10))));

const focusRiskTotal = computed(() => Number(overview.value.highRiskCount || 0) + Number(overview.value.mediumRiskCount || 0));

const peakTrendDay = computed(() => {
  const trend = Array.isArray(overview.value.recentTrend) ? overview.value.recentTrend : [];
  if (!trend.length) {
    return { day: '-', totalCount: 0 };
  }
  return trend.reduce((max, item) => ((item.totalCount || 0) > (max.totalCount || 0) ? item : max), trend[0]);
});

const headlineMetrics = computed(() => [
  { label: '中高风险', value: focusRiskTotal.value, copy: '高风险与中风险的合计数量' },
  { label: '峰值日期', value: peakTrendDay.value.day || '-', copy: `单日峰值 ${peakTrendDay.value.totalCount || 0}` },
  { label: '当前筛选', value: statLabel(activeStatKey.value || '') || '全部', copy: '点击指标卡后这里同步更新' }
]);

const recordsSubtitle = computed(() => {
  const mapping = {
    '': '支持按关键词、风险等级、仓库筛选，并跳转到独立详情页。',
    high: '当前查看“高风险”对应的提交记录。',
    medium: '当前查看“中风险”对应的提交记录。',
    low: '当前查看“低风险”对应的提交记录。',
    safe: '当前查看“无风险”对应的提交记录。',
    pending: '当前查看“待回传”对应的提交记录。',
    today: '当前查看“今日新增”对应的提交记录。'
  };
  return mapping[activeStatKey.value] || mapping[''];
});

const trendOption = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: { trigger: 'axis' },
  legend: { top: 8, textStyle: { color: '#8eafc4' } },
  grid: { left: 24, right: 16, top: 56, bottom: 28, containLabel: true },
  xAxis: { type: 'category', data: overview.value.recentTrend.map(item => item.day), axisLine: { lineStyle: { color: 'rgba(163,205,227,.16)' } }, axisLabel: { color: '#93b6ca' } },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(163,205,227,.08)' } }, axisLabel: { color: '#93b6ca' } },
  series: [
    {
      name: '总提交',
      type: 'line',
      smooth: true,
      symbolSize: 8,
      lineStyle: { color: '#41c6c3', width: 3 },
      itemStyle: { color: '#41c6c3' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(65,198,195,.35)' }, { offset: 1, color: 'rgba(65,198,195,.03)' }]) },
      data: overview.value.recentTrend.map(item => item.totalCount || 0)
    },
    { name: '中高风险', type: 'line', smooth: true, symbolSize: 7, lineStyle: { color: '#ffb347', width: 2 }, itemStyle: { color: '#ffb347' }, data: overview.value.recentTrend.map(item => (item.highRiskCount || 0) + (item.mediumRiskCount || 0)) },
    { name: '无风险', type: 'line', smooth: true, symbolSize: 6, lineStyle: { color: '#2cd39a', width: 2 }, itemStyle: { color: '#2cd39a' }, data: overview.value.recentTrend.map(item => item.noRiskCount || 0) }
  ]
}));

const riskOption = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: { trigger: 'item' },
  legend: { bottom: 8, left: 'center', textStyle: { color: '#8eafc4' } },
  series: [{
    type: 'pie',
    radius: ['48%', '72%'],
    center: ['50%', '45%'],
    label: { color: '#e9f6ff', formatter: '{b}\n{c}' },
    labelLine: { lineStyle: { color: 'rgba(163,205,227,.3)' } },
    itemStyle: { borderColor: '#0d2030', borderWidth: 4 },
    data: [
      { value: overview.value.highRiskCount, name: '高风险', itemStyle: { color: '#f46d6d' } },
      { value: overview.value.mediumRiskCount, name: '中风险', itemStyle: { color: '#ffb347' } },
      { value: overview.value.lowRiskCount, name: '低风险', itemStyle: { color: '#58a6ff' } },
      { value: overview.value.noRiskCount, name: '无风险', itemStyle: { color: '#2cd39a' } },
      { value: overview.value.pendingCount, name: '待回传', itemStyle: { color: '#c59b47' } }
    ]
  }],
  graphic: [{
    type: 'text',
    left: 'center',
    top: '37%',
    style: { text: `总计\n${overview.value.totalCount}`, fill: '#e9f6ff', fontSize: 24, fontWeight: 700, textAlign: 'center' }
  }]
}));

function rankingWidth(value) {
  const max = Math.max(...authorRanking.value.map(item => Number(item.totalCount || 0)), 1);
  return `${Math.max(18, Math.round((Number(value || 0) / max) * 100))}%`;
}

async function loadOverviewData() {
  overview.value = await getOverview();
}

async function loadRankingData() {
  authorRanking.value = await getHighRiskRanking(8);
}

async function loadRepoKeyData() {
  repoKeys.value = await getRepoKeys();
}

async function loadRecordData() {
  recordsLoading.value = true;
  recordsError.value = '';
  try {
    records.value = await getRecords({
      page: pagination.value.page,
      pageSize: pagination.value.pageSize,
      keyword: filters.value.keyword,
      reviewResult: filters.value.reviewResult,
      repoKey: filters.value.repoKey,
      statKey: activeStatKey.value
    });
  } catch (error) {
    recordsError.value = error.message;
  } finally {
    recordsLoading.value = false;
  }
}

async function refreshAll() {
  await Promise.all([loadOverviewData(), loadRankingData(), loadRepoKeyData(), loadRecordData()]);
}

function searchRecords() {
  pagination.value.page = 1;
  loadRecordData();
}

function resetFilters() {
  filters.value = { keyword: '', reviewResult: '', repoKey: '' };
  activeStatKey.value = '';
  pagination.value = { page: 1, pageSize: 10 };
  loadRecordData();
}

function applyStatFilter(statKey) {
  activeStatKey.value = statKey;
  pagination.value.page = 1;
  loadRecordData();
}

function prevPage() {
  if ((records.value.page || 1) <= 1) return;
  pagination.value.page = (records.value.page || 1) - 1;
  loadRecordData();
}

function nextPage() {
  if ((records.value.page || 1) >= totalPages.value) return;
  pagination.value.page = (records.value.page || 1) + 1;
  loadRecordData();
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function handleWindowScroll() {
  showBackTop.value = window.scrollY > 360;
}

onMounted(async () => {
  await refreshAll();
  handleWindowScroll();
  window.addEventListener('scroll', handleWindowScroll, { passive: true });
});

onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleWindowScroll);
});

watch(() => pagination.value.pageSize, () => {
  pagination.value.page = 1;
  loadRecordData();
});
</script>

<template>
  <div class="page-grid">
    <section class="panel hero">
      <div>
        <div class="hero-topbar">
          <span class="eyebrow">OpenClaw BI Dashboard</span>
          <div class="hero-badge-group">
            <span class="hero-badge">总提交 {{ overview.totalCount }}</span>
            <span class="hero-badge">高风险 {{ overview.highRiskCount }}</span>
            <span class="hero-badge">中风险 {{ overview.mediumRiskCount }}</span>
          </div>
        </div>
        <h1>提交审查指挥看板</h1>
        <div class="hero-summary-grid">
          <article v-for="metric in headlineMetrics" :key="metric.label" class="hero-summary-card">
            <div class="hero-summary-label">{{ metric.label }}</div>
            <div class="hero-summary-value">{{ metric.value }}</div>
            <div class="hero-summary-copy">{{ metric.copy }}</div>
          </article>
        </div>
        <div class="hero-actions">
          <button class="btn btn-primary" @click="refreshAll">刷新数据</button>
          <RouterLink class="btn btn-ghost" to="/">返回首页</RouterLink>
        </div>
      </div>
      <div class="hero-side">
        <div class="hero-stat">
          <div class="hero-stat-label">今日新增</div>
          <div class="hero-stat-value">{{ overview.todayCount }}</div>
          <div class="hero-stat-copy">当天新增记录</div>
        </div>
        <div class="hero-stat">
          <div class="hero-stat-label">覆盖仓库</div>
          <div class="hero-stat-value">{{ overview.distinctRepoCount }}</div>
          <div class="hero-stat-copy">当前活跃仓库数</div>
        </div>
      </div>
    </section>

    <section class="kpi-grid">
      <article v-for="card in statCards" :key="card.key" class="kpi-card" :class="{ active: activeStatKey === card.key }" @click="applyStatFilter(card.key)">
        <div class="kpi-label">{{ card.label }}</div>
        <div class="kpi-value">{{ card.value }}</div>
        <div class="kpi-note">{{ card.note }}</div>
      </article>
    </section>

    <section class="dashboard">
      <div class="stack">
        <section class="panel panel-pad">
          <div class="panel-head">
            <div>
              <div class="section-tag">Overview</div>
              <div class="panel-title">风险趋势与结构</div>
              <div class="panel-subtitle">左侧看趋势，右侧看整体结构。</div>
            </div>
          </div>
          <div class="visual-grid">
            <EChartPanel :option="trendOption" :height="360" />
            <EChartPanel :option="riskOption" :height="360" />
          </div>
        </section>

        <section class="panel panel-pad">
          <div class="panel-head">
            <div>
              <div class="section-tag">Records</div>
              <div class="panel-title">提交记录检索</div>
              <div class="panel-subtitle">{{ recordsSubtitle }}</div>
            </div>
          </div>
          <div class="filters">
            <div class="field"><label for="keyword">关键词</label><input id="keyword" v-model.trim="filters.keyword" placeholder="标题 / 作者 / SHA / 路径"></div>
            <div class="field">
              <label for="reviewResult">风险等级</label>
              <select id="reviewResult" v-model="filters.reviewResult">
                <option value="">全部</option>
                <option value="高风险">高风险</option>
                <option value="中风险">中风险</option>
                <option value="低风险">低风险</option>
                <option value="无风险">无风险</option>
                <option value="待回传">待回传</option>
                <option value="已忽略">已忽略</option>
              </select>
            </div>
            <div class="field"><label for="repoKey">仓库标识</label><select id="repoKey" v-model="filters.repoKey"><option value="">全部</option><option v-for="repo in repoKeys" :key="repo" :value="repo">{{ repo }}</option></select></div>
            <div class="field"><label for="pageSize">每页条数</label><select id="pageSize" v-model.number="pagination.pageSize"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select></div>
          </div>
          <div class="filter-actions">
            <button class="btn btn-primary" @click="searchRecords">查询</button>
            <button class="btn btn-ghost" @click="resetFilters">重置</button>
          </div>
        </section>

        <section class="panel panel-pad">
          <div class="panel-head">
            <div>
              <div class="section-tag">Data</div>
              <div class="panel-title">审查明细列表</div>
              <div class="panel-subtitle">跟随指标卡、筛选项和分页状态联动。</div>
            </div>
          </div>
          <div v-if="recordsLoading" class="status status-tip">提交记录加载中...</div>
          <div v-else-if="recordsError" class="status status-error">{{ recordsError }}</div>
          <template v-else>
            <div v-if="!records.records.length" class="empty">当前没有匹配到提交记录</div>
            <div v-else class="table-wrap">
              <table>
                <thead><tr><th>风险</th><th>提交摘要</th><th>作者</th><th>仓库</th><th>统计</th><th>时间</th><th>详情</th></tr></thead>
                <tbody>
                  <tr v-for="record in records.records" :key="record.id">
                    <td><span class="risk-tag" :class="riskClass(record.reviewResult)">{{ record.reviewResult || '未知' }}</span></td>
                    <td><div class="subject">{{ record.commitSubject || '无标题提交' }}</div><div class="subtext mono">SHA {{ shortSha(record.commitSha) }}</div><div class="subtext">风险说明 {{ record.riskLevel || '-' }}</div><div class="subtext">{{ summarizeAdvice(record.reviewAdvice) }}</div></td>
                    <td><div class="subject">{{ record.authorName || '未知作者' }}</div><div class="subtext">{{ record.authorEmail || '-' }}</div></td>
                    <td><div class="subject">{{ record.repoKey || '-' }}</div><div class="subtext">{{ record.repositoryPath || '-' }}</div></td>
                    <td><div class="subject">问题 {{ record.findingCount || 0 }}</div><div class="subtext">改动 {{ record.changedFileCount || 0 }} / 关联 {{ record.relatedFileCount || 0 }}</div></td>
                    <td><div class="subject">{{ formatTime(record.committedAt) }}</div><div class="subtext">入库 {{ formatTime(record.createdAt) }}</div></td>
                    <td><RouterLink class="link-btn" :to="`/review-detail/${record.id}`">查看详情</RouterLink></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </template>
          <div class="pager">
            <div class="pager-text">共 {{ records.total || 0 }} 条，当前第 {{ records.page || 1 }} 页 / {{ totalPages }} 页</div>
            <div class="hero-actions" style="margin-top:0">
              <button class="btn btn-ghost" :disabled="(records.page || 1) <= 1" @click="prevPage">上一页</button>
              <button class="btn btn-ghost" :disabled="(records.page || 1) >= totalPages" @click="nextPage">下一页</button>
            </div>
          </div>
        </section>
      </div>

      <aside class="sidebar-stack">
        <section class="panel ranking-panel compact">
          <div class="side-section">
            <div class="panel-head tight">
              <div>
                <div class="section-tag">Ranking</div>
                <div class="panel-title">高风险人员排行</div>
                <div class="panel-subtitle">只保留高风险作者，方便跟进。</div>
              </div>
            </div>
            <div v-if="authorRanking.length" class="ranking-scroll">
              <div class="ranking-list">
                <article v-for="(item,index) in authorRanking" :key="`${item.name}-${index}`" class="ranking-item">
                  <div class="ranking-top">
                    <div class="ranking-name"><span class="ranking-index">{{ index + 1 }}</span><span>{{ item.name || '未知作者' }}</span></div>
                    <span class="risk-tag risk-high">高风险</span>
                  </div>
                  <div class="bar"><div class="bar-fill" :style="{ width: rankingWidth(item.totalCount) }"></div></div>
                  <div class="ranking-meta"><span class="pill">高风险 {{ item.totalCount }}</span><span class="pill">问题 {{ item.findingCount }}</span></div>
                </article>
              </div>
            </div>
            <div v-else class="empty">当前没有高风险排行数据</div>
          </div>
          <div class="side-section">
            <div class="panel-head tight">
              <div>
                <div class="section-tag">Snapshot</div>
                <div class="panel-title">当前概况</div>
                <div class="panel-subtitle">压缩成一块短摘要。</div>
              </div>
            </div>
            <div class="ranking-list">
              <div class="ranking-item"><div class="subject">总提交 {{ overview.totalCount }}</div><div class="subtext">中高风险合计 {{ overview.highRiskCount + overview.mediumRiskCount }}</div></div>
              <div class="ranking-item"><div class="subject">无风险 {{ overview.noRiskCount }}</div><div class="subtext">待回传 {{ overview.pendingCount }} / 已忽略 {{ overview.ignoredCount }}</div></div>
              <div class="ranking-item"><div class="subject">当前筛选</div><div class="subtext">{{ activeStatKey ? statLabel(activeStatKey) : '全部记录' }}</div></div>
            </div>
          </div>
        </section>
      </aside>
    </section>

    <button class="to-top" :class="{ 'hidden-btn': !showBackTop }" @click="scrollToTop">TOP</button>
  </div>
</template>
