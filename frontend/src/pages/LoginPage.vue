<script setup>
import { KeyRound, LogIn } from '@lucide/vue'
import { reactive, ref } from 'vue'
import PanelHeading from '../components/PanelHeading.vue'
import { login } from '../lib/api'

const emit = defineEmits(['login-success', 'error'])

const loginLoading = ref(false)
const loginForm = reactive({
  username: '',
  password: ''
})

// 登录成功后把用户信息交给 App 统一持久化，登录页本身不直接管理全局登录态。
async function submitLogin() {
  if (!loginForm.username.trim() || !loginForm.password) {
    emit('error', '用户名和密码不能为空')
    return
  }
  loginLoading.value = true
  emit('error', '')
  try {
    const user = await login({
      username: loginForm.username.trim(),
      password: loginForm.password
    })
    loginForm.password = ''
    emit('login-success', user)
  } catch (error) {
    emit('error', error.message)
  } finally {
    loginLoading.value = false
  }
}
</script>

<template>
  <section class="auth-card auth-surface">
    <div class="login-panel">
      <PanelHeading eyebrow="登录" title="用户登录">
        <template #icon>
          <KeyRound :size="20" />
        </template>
      </PanelHeading>
      <div class="login-grid">
        <label class="field">
          用户名
          <input v-model.trim="loginForm.username" type="text" autocomplete="username" />
        </label>
        <label class="field">
          密码
          <input v-model="loginForm.password" type="password" autocomplete="current-password" />
        </label>
        <button class="primary-action" type="button" :disabled="loginLoading" @click="submitLogin">
          <LogIn :size="17" />
          {{ loginLoading ? '登录中' : '登录 / 首次创建' }}
        </button>
      </div>
    </div>
    <div class="showcase-panel" aria-hidden="true">
      <div class="showcase-caption">
        <span>轻量创作空间</span>
        <strong>从一句话开始生成画面</strong>
      </div>
    </div>
  </section>
</template>
