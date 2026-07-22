<script setup>
import { LogOut, UserRound } from '@lucide/vue'

defineProps({
  user: {
    type: Object,
    default: null
  },
  isLoggedIn: {
    type: Boolean,
    default: false
  },
  isAdmin: {
    type: Boolean,
    default: false
  },
  currentPage: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['open-page', 'logout'])
</script>

<template>
  <!-- 顶部导航只负责页面切换入口和登录用户展示，具体页面数据加载由各 page 自己处理。 -->
  <header class="topbar">
    <div class="brand-block">
      <p class="eyebrow">AI 创作工作台</p>
      <h1>文生图 / 文生视频工作台</h1>
    </div>
    <div v-if="isLoggedIn" class="top-actions">
      <nav class="admin-nav" aria-label="页面导航">
        <button type="button" :class="{ active: currentPage === 'studio' }" @click="emit('open-page', 'studio')">工作台</button>
        <button type="button" :class="{ active: currentPage === 'tasks' }" @click="emit('open-page', 'tasks')">任务中心</button>
        <button v-if="isAdmin" type="button" :class="{ active: currentPage === 'users' }" @click="emit('open-page', 'users')">用户设置</button>
        <button v-if="isAdmin" type="button" :class="{ active: currentPage === 'logs' }" @click="emit('open-page', 'logs')">使用日志</button>
        <button v-if="isAdmin" type="button" :class="{ active: currentPage === 'models' }" @click="emit('open-page', 'models')">模型配置</button>
      </nav>
      <div class="user-chip">
        <UserRound :size="16" />
        <span>{{ user?.username }}</span>
        <button type="button" aria-label="退出登录" title="退出登录" @click="emit('logout')">
          <LogOut :size="15" />
        </button>
      </div>
    </div>
  </header>
</template>
