<script setup>
import { computed, ref } from 'vue'
import AppHeader from './components/AppHeader.vue'
import LoginPage from './pages/LoginPage.vue'
import LogsPage from './pages/LogsPage.vue'
import ModelsPage from './pages/ModelsPage.vue'
import StudioPage from './pages/StudioPage.vue'
import TasksPage from './pages/TasksPage.vue'
import UsersPage from './pages/UsersPage.vue'

const savedUser = JSON.parse(localStorage.getItem('studioUser') || 'null')

const user = ref(savedUser)
const currentPage = ref('studio')
const errorMessage = ref('')

const isLoggedIn = computed(() => Boolean(user.value?.userId && user.value?.accessToken))
const isAdmin = computed(() => Boolean(user.value?.admin))

// App 只保留应用壳层状态，业务表单和接口编排下沉到 pages，避免继续膨胀成单文件应用。
function setError(message) {
  errorMessage.value = message
}

function handleLoginSuccess(nextUser) {
  user.value = nextUser
  localStorage.setItem('studioUser', JSON.stringify(nextUser))
  currentPage.value = 'studio'
  errorMessage.value = ''
}

function logout() {
  user.value = null
  currentPage.value = 'studio'
  localStorage.removeItem('studioUser')
}

function openPage(page) {
  // 管理页面只允许初始化管理员进入；这里做前端体验拦截，后端接口仍然负责最终权限校验。
  if (['users', 'logs', 'models'].includes(page) && !isAdmin.value) {
    currentPage.value = 'studio'
    return
  }
  currentPage.value = page
  errorMessage.value = ''
}
</script>

<template>
  <main class="studio-shell">
    <section class="workspace">
      <AppHeader
        :user="user"
        :is-logged-in="isLoggedIn"
        :is-admin="isAdmin"
        :current-page="currentPage"
        @open-page="openPage"
        @logout="logout"
      />

      <div v-if="errorMessage" class="error-message">{{ errorMessage }}</div>

      <LoginPage v-if="!isLoggedIn" @login-success="handleLoginSuccess" @error="setError" />
      <template v-else>
        <StudioPage v-if="currentPage === 'studio'" @error="setError" />
        <TasksPage v-else-if="currentPage === 'tasks'" :is-admin="isAdmin" />
        <UsersPage v-else-if="currentPage === 'users' && isAdmin" />
        <LogsPage v-else-if="currentPage === 'logs' && isAdmin" />
        <ModelsPage v-else-if="currentPage === 'models' && isAdmin" />
      </template>
    </section>
  </main>
</template>
