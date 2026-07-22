<script setup>
import { Download, ListChecks, RefreshCw } from '@lucide/vue'
import { onMounted, reactive, ref } from 'vue'
import PanelHeading from '../components/PanelHeading.vue'
import { listTasks, queryTaskStatus } from '../lib/api'
import { downloadUrl } from '../utils/download'

defineProps({
  isAdmin: {
    type: Boolean,
    default: false
  }
})

const loading = ref(false)
const message = ref('')
const generationTasks = ref([])
const taskDownloadingId = ref(null)
const taskFilter = reactive({
  username: '',
  taskType: '',
  status: ''
})

// 列表接口只返回轻量任务摘要，是否可下载由最终状态判断，避免拉取大体积结果 URL。
function canDownloadTask(item) {
  return String(item?.status || '').toUpperCase() === 'SUCCEEDED'
}

function taskResultText(item) {
  if (item.failReason) {
    return item.failReason
  }
  if (canDownloadTask(item)) {
    return item.taskType === 'TEXT_TO_IMAGE' ? '图片已生成' : '视频已生成'
  }
  return '等待生成'
}

async function fetchGenerationTasks() {
  loading.value = true
  message.value = ''
  try {
    generationTasks.value = await listTasks({
      username: taskFilter.username.trim(),
      taskType: taskFilter.taskType,
      status: taskFilter.status
    })
  } catch (error) {
    message.value = error.message
  } finally {
    loading.value = false
  }
}

async function downloadTaskResult(item) {
  // 下载时再查单条任务详情拿 resultUrl，任务中心列表不会因为图片/视频地址过大而变慢。
  if (!canDownloadTask(item) || taskDownloadingId.value) {
    return
  }
  taskDownloadingId.value = item.taskId
  message.value = ''
  try {
    const detail = await queryTaskStatus({ taskId: item.taskId })
    if (!detail?.resultUrl) {
      message.value = '任务结果暂不可下载'
      return
    }
    downloadUrl(detail.resultUrl, item.taskType === 'TEXT_TO_IMAGE' ? 'generated-image.png' : 'generated-video.mp4')
  } catch (error) {
    message.value = error.message
  } finally {
    taskDownloadingId.value = null
  }
}

onMounted(fetchGenerationTasks)
</script>

<template>
  <section class="panel admin-panel">
    <div class="admin-panel-head">
      <PanelHeading eyebrow="任务中心" title="生成任务">
        <template #icon>
          <ListChecks :size="20" />
        </template>
      </PanelHeading>
      <div class="admin-toolbar log-toolbar">
        <input v-if="isAdmin" v-model.trim="taskFilter.username" type="text" placeholder="用户名" />
        <select v-model="taskFilter.taskType">
          <option value="">全部类型</option>
          <option value="TEXT_TO_IMAGE">文生图</option>
          <option value="TEXT_TO_VIDEO">文生视频</option>
        </select>
        <select v-model="taskFilter.status">
          <option value="">全部状态</option>
          <option value="SUBMITTED">已提交</option>
          <option value="RUNNING">生成中</option>
          <option value="SUCCEEDED">已完成</option>
          <option value="FAILED">生成失败</option>
        </select>
        <button class="secondary-action" type="button" :disabled="loading" @click="fetchGenerationTasks">筛选</button>
        <button class="secondary-action icon-action" type="button" :disabled="loading" @click="fetchGenerationTasks">
          <RefreshCw :size="16" />
          刷新
        </button>
      </div>
    </div>
    <p v-if="message" class="hint-message">{{ message }}</p>
    <div class="data-table task-table">
      <div class="table-row table-head">
        <span>时间</span>
        <span>用户</span>
        <span>类型</span>
        <span>状态</span>
        <span>结果</span>
        <span>操作</span>
      </div>
      <div v-for="item in generationTasks" :key="item.taskId" class="table-row">
        <span data-label="时间">{{ new Date(item.createdAt).toLocaleString() }}</span>
        <strong data-label="用户">{{ item.username || '-' }}</strong>
        <span data-label="类型">{{ item.taskName }}</span>
        <span data-label="状态">{{ item.statusName || item.status }}</span>
        <span data-label="结果" class="result-cell">{{ taskResultText(item) }}</span>
        <button
          class="secondary-action"
          type="button"
          :disabled="!canDownloadTask(item) || taskDownloadingId === item.taskId"
          @click="downloadTaskResult(item)"
        >
          <Download :size="16" />
          {{ taskDownloadingId === item.taskId ? '准备中' : '下载' }}
        </button>
      </div>
    </div>
  </section>
</template>
