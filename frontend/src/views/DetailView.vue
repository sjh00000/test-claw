<script setup>
import { onMounted, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { getRecordDetail } from '../lib/api';
import { formatTime, riskClass } from '../lib/format';

const route = useRoute();
const loading = ref(true);
const error = ref('');
const record = ref({});

async function loadDetail() {
  loading.value = true;
  error.value = '';
  try {
    if (!route.params.id) {
      record.value = {};
      return;
    }
    record.value = await getRecordDetail(route.params.id);
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}

onMounted(loadDetail);
</script>

<template>
  <div class="page-grid">
    <section class="panel hero">
      <div>
        <span class="eyebrow">OpenClaw Vue Detail</span>
        <h1>提交审查详情</h1>
        <p class="hero-copy">这里展示单次提交的核心审查信息。左边集中看基础信息和审查建议，右边看结构图和统计摘要。</p>
        <div class="hero-actions">
          <RouterLink class="btn btn-primary" to="/review-admin">返回 BI 看板</RouterLink>
          <RouterLink class="btn btn-ghost" to="/">返回首页</RouterLink>
        </div>
      </div>
      <div class="hero-side">
        <div class="hero-stat">
          <div class="hero-stat-label">风险等级</div>
          <div class="hero-stat-value">{{ record.reviewResult || '-' }}</div>
          <div class="hero-stat-copy">当前记录的最终风险标识。</div>
        </div>
        <div class="hero-stat">
          <div class="hero-stat-label">问题数</div>
          <div class="hero-stat-value">{{ record.findingCount || 0 }}</div>
          <div class="hero-stat-copy">本次审查提炼出的明确问题数量。</div>
        </div>
      </div>
    </section>

    <div v-if="loading" class="status status-tip">详情加载中...</div>
    <div v-else-if="error" class="status status-error">{{ error }}</div>
    <div v-else-if="!record.id" class="empty">没有拿到有效的记录 ID。</div>

    <section v-else class="detail-layout">
      <div class="stack">
        <section class="panel panel-pad">
          <div class="panel-head">
            <div>
              <div class="section-tag">Overview</div>
              <div class="panel-title">基础信息与建议</div>
              <div class="panel-subtitle">把提交身份、仓库位置和审查建议收在同一块主区域里。</div>
            </div>
          </div>
          <div class="detail-grid-two">
            <div class="info-card">
              <div class="kv"><div class="k">标题</div><div class="v">{{ record.commitSubject || '-' }}</div></div>
              <div class="kv"><div class="k">Commit</div><div class="v mono">{{ record.commitSha || '-' }}</div></div>
              <div class="kv"><div class="k">作者</div><div class="v">{{ record.authorName || '-' }} / {{ record.authorEmail || '-' }}</div></div>
              <div class="kv"><div class="k">风险标签</div><div class="v"><span class="risk-tag" :class="riskClass(record.reviewResult)">{{ record.reviewResult || '未知' }}</span></div></div>
            </div>
            <div class="info-card">
              <div class="kv"><div class="k">仓库</div><div class="v">{{ record.repoKey || '-' }}</div></div>
              <div class="kv"><div class="k">仓库路径</div><div class="v">{{ record.repositoryPath || '-' }}</div></div>
              <div class="kv"><div class="k">提交时间</div><div class="v">{{ formatTime(record.committedAt) }}</div></div>
              <div class="kv"><div class="k">入库时间</div><div class="v">{{ formatTime(record.createdAt) }}</div></div>
            </div>
          </div>
          <div class="panel-head" style="margin:18px 0 16px">
            <div>
              <div class="section-tag">Advice</div>
              <div class="panel-title">审查建议</div>
              <div class="panel-subtitle">完整保留当前这次审查输出。</div>
            </div>
          </div>
          <div class="advice-box">{{ record.reviewAdvice || '无审查建议' }}</div>
        </section>
      </div>

      <aside class="sidebar-stack static">
        <section class="panel panel-pad">
          <div class="panel-head tight">
            <div>
              <div class="section-tag">Metrics</div>
              <div class="panel-title">统计摘要</div>
              <div class="panel-subtitle">这里直接展示关键统计，不再用环形图混合没有强关联的维度。</div>
            </div>
          </div>
          <div class="summary-grid" style="margin-bottom:14px">
            <div class="summary-card">
              <div class="summary-label">问题数</div>
              <div class="summary-value">{{ record.findingCount || 0 }}</div>
              <div class="summary-copy">审查过程中识别出的明确问题。</div>
            </div>
            <div class="summary-card">
              <div class="summary-label">改动文件</div>
              <div class="summary-value">{{ record.changedFileCount || 0 }}</div>
              <div class="summary-copy">本次提交直接改动到的文件数。</div>
            </div>
            <div class="summary-card">
              <div class="summary-label">关联文件</div>
              <div class="summary-value">{{ record.relatedFileCount || 0 }}</div>
              <div class="summary-copy">审查时关联参考到的上下文文件数。</div>
            </div>
            <div class="summary-card">
              <div class="summary-label">规范命中</div>
              <div class="summary-value">{{ record.standardCount || 0 }}</div>
              <div class="summary-copy">命中的规范或检查项数量。</div>
            </div>
          </div>
          <div class="info-card">
            <div class="kv"><div class="k">风险结果</div><div class="v"><span class="risk-tag" :class="riskClass(record.reviewResult)">{{ record.reviewResult || '未知' }}</span></div></div>
            <div class="kv"><div class="k">风险说明</div><div class="v">{{ record.riskLevel || '无' }}</div></div>
            <div class="kv"><div class="k">是否立即修复</div><div class="v">{{ record.immediateFix ? '是' : '否' }}</div></div>
            <div class="kv"><div class="k">审查来源</div><div class="v">{{ record.reviewSource || '-' }}</div></div>
          </div>
        </section>
      </aside>
    </section>
  </div>
</template>
