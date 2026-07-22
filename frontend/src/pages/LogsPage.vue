<script setup>
import { RefreshCw } from '@lucide/vue'
import { onMounted, reactive, ref } from 'vue'
import PanelHeading from '../components/PanelHeading.vue'
import { listAdminLogs } from '../lib/api'

const loading = ref(false)
const message = ref('')
const operationLogs = ref([])
const logFilter = reactive({
  userId: '',
  username: '',
  operationType: ''
})

// 操作日志只展示用户主动生成行为，任务状态和结果仍以任务中心为准。
async function fetchOperationLogs() {
  loading.value = true
  message.value = ''
  try {
    operationLogs.value = await listAdminLogs({
      userId: logFilter.userId ? Number(logFilter.userId) : null,
      username: logFilter.username.trim(),
      operationType: logFilter.operationType
    })
  } catch (error) {
    message.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(fetchOperationLogs)
</script>

<template>
  <section class="panel admin-panel">
    <div class="admin-panel-head">
      <PanelHeading eyebrow="使用日志" title="操作记录">
        <template #icon>
          <RefreshCw :size="20" />
        </template>
      </PanelHeading>
      <div class="admin-toolbar log-toolbar">
        <input v-model.trim="logFilter.username" type="text" placeholder="用户名" />
        <select v-model="logFilter.operationType">
          <option value="">全部类型</option>
          <option value="TEXT_TO_IMAGE">文生图</option>
          <option value="TEXT_TO_VIDEO">文生视频</option>
        </select>
        <button class="secondary-action" type="button" :disabled="loading" @click="fetchOperationLogs">筛选</button>
        <button class="secondary-action icon-action" type="button" :disabled="loading" @click="fetchOperationLogs">
          <RefreshCw :size="16" />
          刷新
        </button>
      </div>
    </div>
    <p v-if="message" class="hint-message">{{ message }}</p>
    <div class="data-table log-table">
      <div class="table-row table-head">
        <span>时间</span>
        <span>用户</span>
        <span>操作</span>
        <span>请求内容</span>
        <span>返回内容</span>
      </div>
      <div v-for="item in operationLogs" :key="item.id" class="table-row">
        <span data-label="时间">{{ new Date(item.createdAt).toLocaleString() }}</span>
        <strong data-label="用户">{{ item.username || '-' }}</strong>
        <span data-label="操作">{{ item.operationName }}</span>
        <span data-label="请求内容" class="summary-cell">{{ item.requestBody }}</span>
        <span data-label="返回内容" class="summary-cell">{{ item.responseBody }}</span>
      </div>
    </div>
  </section>
</template>
