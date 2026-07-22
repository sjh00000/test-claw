<script setup>
defineProps({
  taskId: {
    type: [String, Number],
    default: ''
  },
  status: {
    type: String,
    default: '已提交'
  },
  polling: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <!-- 图片/视频共用同一套任务状态展示，保证异步生成反馈在两种模式下行为一致。 -->
  <div v-if="taskId" class="video-status">
    <span>任务</span>
    <strong>{{ taskId }}</strong>
    <span>状态</span>
    <strong>{{ polling ? `${status || '已提交'} · 自动刷新中` : (status || '已提交') }}</strong>
  </div>
</template>
