<script setup>
import { RefreshCw, UserRound } from '@lucide/vue'
import { onMounted, reactive, ref } from 'vue'
import PanelHeading from '../components/PanelHeading.vue'
import { listAdminUsers, updateAdminUser } from '../lib/api'

const loading = ref(false)
const message = ref('')
const adminUsers = ref([])
const userSearch = reactive({ keyword: '' })

// 用户页面独立管理筛选和刷新状态，避免多个管理页共用 loading 导致按钮互相锁住。
async function fetchAdminUsers() {
  loading.value = true
  message.value = ''
  try {
    adminUsers.value = await listAdminUsers({
      keyword: userSearch.keyword.trim()
    })
  } catch (error) {
    message.value = error.message
  } finally {
    loading.value = false
  }
}

async function saveUserSettings(item) {
  // 管理员修改的是剩余次数，保存后用后端返回值覆盖本地行，避免展示旧额度。
  loading.value = true
  message.value = ''
  try {
    const updated = await updateAdminUser({
      userId: item.userId,
      imageRemainingCount: Number(item.imageRemainingCount),
      videoRemainingCount: Number(item.videoRemainingCount)
    })
    const index = adminUsers.value.findIndex((userItem) => userItem.userId === updated.userId)
    if (index >= 0) {
      adminUsers.value.splice(index, 1, updated)
    }
    message.value = '用户设置已保存'
  } catch (error) {
    message.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(fetchAdminUsers)
</script>

<template>
  <section class="panel admin-panel">
    <div class="admin-panel-head">
      <PanelHeading eyebrow="用户设置" title="用户与额度">
        <template #icon>
          <UserRound :size="20" />
        </template>
      </PanelHeading>
      <div class="admin-toolbar">
        <input v-model.trim="userSearch.keyword" type="text" placeholder="搜索用户名" />
        <button class="secondary-action" type="button" :disabled="loading" @click="fetchAdminUsers">查询</button>
        <button class="secondary-action icon-action" type="button" :disabled="loading" @click="fetchAdminUsers">
          <RefreshCw :size="16" />
          刷新
        </button>
      </div>
    </div>
    <p v-if="message" class="hint-message">{{ message }}</p>
    <div class="data-table">
      <div class="table-row table-head">
        <span>用户</span>
        <span>图片剩余次数</span>
        <span>视频剩余次数</span>
        <span>操作</span>
      </div>
      <div v-for="item in adminUsers" :key="item.userId" class="table-row">
        <strong data-label="用户">{{ item.username }}</strong>
        <label class="table-field" data-label="图片剩余次数">
          <input v-model.number="item.imageRemainingCount" class="table-input" type="number" min="0" />
        </label>
        <label class="table-field" data-label="视频剩余次数">
          <input v-model.number="item.videoRemainingCount" class="table-input" type="number" min="0" />
        </label>
        <button class="secondary-action" type="button" :disabled="loading" @click="saveUserSettings(item)">保存</button>
      </div>
    </div>
  </section>
</template>
