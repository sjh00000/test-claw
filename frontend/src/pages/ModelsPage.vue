<script setup>
import { Settings2, RefreshCw } from '@lucide/vue'
import { onMounted, reactive, ref } from 'vue'
import PanelHeading from '../components/PanelHeading.vue'
import { listModelConfigs, saveModelConfig } from '../lib/api'

const loading = ref(false)
const message = ref('')
const modelConfigs = ref([])
const modelForms = reactive({
  IMAGE: {
    serviceType: 'IMAGE',
    title: '图片服务',
    baseUrl: '',
    apiKey: '',
    apiKeyMask: '',
    model: '',
    enabled: true
  },
  VIDEO: {
    serviceType: 'VIDEO',
    title: '视频服务',
    baseUrl: '',
    apiKey: '',
    apiKeyMask: '',
    model: '',
    enabled: true
  }
})

// 模型配置页由管理员维护厂商参数，普通生成页面不再暴露 baseUrl、apikey、model。
async function fetchModelConfigs() {
  loading.value = true
  message.value = ''
  try {
    modelConfigs.value = await listModelConfigs({})
    for (const config of modelConfigs.value) {
      const form = modelForms[config.serviceType]
      if (form) {
        form.baseUrl = config.baseUrl || ''
        form.apiKey = config.apiKey || ''
        form.apiKeyMask = config.apiKeyMask || ''
        form.model = config.model || ''
        form.enabled = config.enabled !== false
      }
    }
  } catch (error) {
    message.value = error.message
  } finally {
    loading.value = false
  }
}

async function submitModelConfig(serviceType) {
  // 密钥输入为空时由后端按已有密钥处理，前端只负责展示脱敏占位和提交显式修改。
  const form = modelForms[serviceType]
  loading.value = true
  message.value = ''
  try {
    const saved = await saveModelConfig({
      serviceType,
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey.trim(),
      model: form.model.trim(),
      enabled: form.enabled
    })
    form.apiKey = saved.apiKey || form.apiKey
    form.apiKeyMask = saved.apiKeyMask || ''
    await fetchModelConfigs()
    message.value = `${form.title}已保存`
  } catch (error) {
    message.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(fetchModelConfigs)
</script>

<template>
  <section class="panel admin-panel">
    <div class="admin-panel-head">
      <PanelHeading eyebrow="模型配置" title="服务参数">
        <template #icon>
          <Settings2 :size="20" />
        </template>
      </PanelHeading>
      <button class="secondary-action icon-action" type="button" :disabled="loading" @click="fetchModelConfigs">
        <RefreshCw :size="16" />
        刷新
      </button>
    </div>
    <p v-if="message" class="hint-message">{{ message }}</p>
    <div class="model-config-grid">
      <article v-for="form in modelForms" :key="form.serviceType" class="model-config-card">
        <h3>{{ form.title }}</h3>
        <label class="field">
          服务地址
          <input v-model.trim="form.baseUrl" type="text" placeholder="https://provider.example.com" />
        </label>
        <label class="field">
          密钥
          <input
            v-model.trim="form.apiKey"
            type="text"
            inputmode="text"
            autocomplete="new-password"
            autocapitalize="off"
            autocorrect="off"
            spellcheck="false"
            data-lpignore="true"
            data-1p-ignore="true"
            :name="`${form.serviceType.toLowerCase()}-provider-key`"
            :placeholder="form.apiKeyMask || '请输入服务密钥'"
          />
        </label>
        <label class="field">
          模型名称
          <input v-model.trim="form.model" type="text" placeholder="模型名称" />
        </label>
        <label class="toggle">
          <input v-model="form.enabled" type="checkbox" />
          启用
        </label>
        <button class="primary-action" type="button" :disabled="loading" @click="submitModelConfig(form.serviceType)">
          <Settings2 :size="17" />
          保存配置
        </button>
      </article>
    </div>
  </section>
</template>
