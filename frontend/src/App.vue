<script setup>
import {
  Download,
  Film,
  Image as ImageIcon,
  KeyRound,
  LogIn,
  LogOut,
  Minus,
  Plus,
  RefreshCw,
  Settings2,
  Trash2,
  UploadCloud,
  UserRound,
  Video as VideoIcon,
  WandSparkles
} from '@lucide/vue'
import { computed, reactive, ref } from 'vue'
import {
  generateImage,
  generateVideo,
  listAdminLogs,
  listAdminUsers,
  listModelConfigs,
  login,
  queryVideoStatus,
  saveModelConfig,
  updateAdminUser
} from './lib/api'

const VIDEO_DURATION_MIN = 4
const VIDEO_DURATION_MAX = 15

const savedUser = JSON.parse(localStorage.getItem('studioUser') || 'null')

const user = ref(savedUser)
const errorMessage = ref('')
const adminMessage = ref('')
const loginLoading = ref(false)
const imageLoading = ref(false)
const videoLoading = ref(false)
const adminLoading = ref(false)
const activeMode = ref('image')
const currentPage = ref('studio')

const loginForm = reactive({
  username: '',
  password: ''
})

const imageForm = reactive({
  prompt: '',
  imageSize: '1024x1024',
  imageQuality: 'medium',
  referenceImages: []
})

const videoForm = reactive({
  prompt: '',
  duration: 5,
  resolution: '720p',
  ratio: 'adaptive',
  generateAudio: true,
  referenceImages: []
})

const userSearch = reactive({
  keyword: ''
})

const logFilter = reactive({
  userId: '',
  username: '',
  operationType: '',
  status: ''
})

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

const imageResult = ref(null)
const videoResult = ref(null)
const adminUsers = ref([])
const operationLogs = ref([])
const modelConfigs = ref([])

const isLoggedIn = computed(() => Boolean(user.value?.userId && user.value?.accessToken))
const isAdmin = computed(() => Boolean(user.value?.admin))

function referenceImagePayload(items) {
  return items.map((item, index) => ({
    imageUrl: item.url,
    name: item.name.trim() || `参考图 ${index + 1}`
  }))
}

function readImageFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('图片读取失败'))
    reader.readAsDataURL(file)
  })
}

async function handleReferenceUpload(event, targetList) {
  const files = Array.from(event.target.files || [])
  for (const file of files) {
    const url = await readImageFile(file)
    targetList.push({
      id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      name: file.name,
      url
    })
  }
  event.target.value = ''
}

function removeReference(targetList, id) {
  const index = targetList.findIndex((item) => item.id === id)
  if (index >= 0) {
    targetList.splice(index, 1)
  }
}

async function submitLogin() {
  if (!loginForm.username.trim() || !loginForm.password) {
    errorMessage.value = '用户名和密码不能为空'
    return
  }
  loginLoading.value = true
  errorMessage.value = ''
  try {
    user.value = await login({
      username: loginForm.username.trim(),
      password: loginForm.password
    })
    localStorage.setItem('studioUser', JSON.stringify(user.value))
    currentPage.value = 'studio'
    loginForm.password = ''
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loginLoading.value = false
  }
}

function logout() {
  user.value = null
  currentPage.value = 'studio'
  localStorage.removeItem('studioUser')
}

async function submitImage() {
  if (!isLoggedIn.value) {
    errorMessage.value = '请先登录'
    return
  }
  if (!imageForm.prompt.trim()) {
    errorMessage.value = '图片提示词不能为空'
    return
  }
  imageLoading.value = true
  errorMessage.value = ''
  try {
    imageResult.value = await generateImage({
      prompt: imageForm.prompt.trim(),
      referenceImages: referenceImagePayload(imageForm.referenceImages),
      imageSize: imageForm.imageSize,
      imageQuality: imageForm.imageQuality
    })
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    imageLoading.value = false
  }
}

async function submitVideo() {
  if (!isLoggedIn.value) {
    errorMessage.value = '请先登录'
    return
  }
  if (!videoForm.prompt.trim()) {
    errorMessage.value = '视频提示词不能为空'
    return
  }
  videoLoading.value = true
  errorMessage.value = ''
  try {
    videoResult.value = await generateVideo({
      prompt: videoForm.prompt.trim(),
      referenceImages: referenceImagePayload(videoForm.referenceImages),
      duration: clampVideoDuration(videoForm.duration),
      resolution: videoForm.resolution,
      ratio: videoForm.ratio,
      generateAudio: videoForm.generateAudio
    })
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    videoLoading.value = false
  }
}

async function refreshVideoStatus() {
  if (!videoResult.value?.taskId) {
    return
  }
  videoLoading.value = true
  errorMessage.value = ''
  try {
    videoResult.value = await queryVideoStatus({
      taskId: videoResult.value.taskId
    })
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    videoLoading.value = false
  }
}

function clampVideoDuration(value) {
  const duration = Number(value)
  const normalized = Number.isFinite(duration) ? duration : VIDEO_DURATION_MIN
  videoForm.duration = Math.min(VIDEO_DURATION_MAX, Math.max(VIDEO_DURATION_MIN, normalized))
  return videoForm.duration
}

function adjustVideoDuration(delta) {
  clampVideoDuration(videoForm.duration + delta)
}

function downloadUrl(url, filename) {
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.target = '_blank'
  link.rel = 'noreferrer'
  document.body.appendChild(link)
  link.click()
  link.remove()
}

async function openPage(page) {
  currentPage.value = page
  errorMessage.value = ''
  adminMessage.value = ''
  if (page === 'users') {
    await fetchAdminUsers()
  }
  if (page === 'logs') {
    await fetchOperationLogs()
  }
  if (page === 'models') {
    await fetchModelConfigs()
  }
}

async function fetchAdminUsers() {
  adminLoading.value = true
  adminMessage.value = ''
  try {
    adminUsers.value = await listAdminUsers({
      keyword: userSearch.keyword.trim()
    })
  } catch (error) {
    adminMessage.value = error.message
  } finally {
    adminLoading.value = false
  }
}

async function saveUserSettings(item) {
  adminLoading.value = true
  adminMessage.value = ''
  try {
    const updated = await updateAdminUser({
      userId: item.userId,
      imageCallLimit: Number(item.imageCallLimit),
      videoCallLimit: Number(item.videoCallLimit)
    })
    const index = adminUsers.value.findIndex((userItem) => userItem.userId === updated.userId)
    if (index >= 0) {
      adminUsers.value.splice(index, 1, updated)
    }
    adminMessage.value = '用户设置已保存'
  } catch (error) {
    adminMessage.value = error.message
  } finally {
    adminLoading.value = false
  }
}

async function fetchOperationLogs() {
  adminLoading.value = true
  adminMessage.value = ''
  try {
    operationLogs.value = await listAdminLogs({
      userId: logFilter.userId ? Number(logFilter.userId) : null,
      username: logFilter.username.trim(),
      operationType: logFilter.operationType,
      status: logFilter.status
    })
  } catch (error) {
    adminMessage.value = error.message
  } finally {
    adminLoading.value = false
  }
}

async function fetchModelConfigs() {
  adminLoading.value = true
  adminMessage.value = ''
  try {
    modelConfigs.value = await listModelConfigs({})
    for (const config of modelConfigs.value) {
      const form = modelForms[config.serviceType]
      if (form) {
        form.baseUrl = config.baseUrl || ''
        form.apiKey = ''
        form.apiKeyMask = config.apiKeyMask || ''
        form.model = config.model || ''
        form.enabled = config.enabled !== false
      }
    }
  } catch (error) {
    adminMessage.value = error.message
  } finally {
    adminLoading.value = false
  }
}

async function submitModelConfig(serviceType) {
  const form = modelForms[serviceType]
  adminLoading.value = true
  adminMessage.value = ''
  try {
    const saved = await saveModelConfig({
      serviceType,
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey.trim(),
      model: form.model.trim(),
      enabled: form.enabled
    })
    form.apiKey = ''
    form.apiKeyMask = saved.apiKeyMask || ''
    await fetchModelConfigs()
    adminMessage.value = `${form.title}已保存`
  } catch (error) {
    adminMessage.value = error.message
  } finally {
    adminLoading.value = false
  }
}
</script>

<template>
  <main class="studio-shell">
    <section class="workspace">
      <header class="topbar">
        <div class="brand-block">
          <p class="eyebrow">AI 创作工作台</p>
          <h1>文生图 / 文生视频工作台</h1>
        </div>
        <div v-if="isLoggedIn" class="top-actions">
          <nav v-if="isAdmin" class="admin-nav" aria-label="管理员页面">
            <button type="button" :class="{ active: currentPage === 'studio' }" @click="openPage('studio')">工作台</button>
            <button type="button" :class="{ active: currentPage === 'users' }" @click="openPage('users')">用户设置</button>
            <button type="button" :class="{ active: currentPage === 'logs' }" @click="openPage('logs')">使用日志</button>
            <button type="button" :class="{ active: currentPage === 'models' }" @click="openPage('models')">模型配置</button>
          </nav>
          <div class="user-chip">
            <UserRound :size="16" />
            <span>{{ user.username }}</span>
            <button type="button" aria-label="退出登录" title="退出登录" @click="logout">
              <LogOut :size="15" />
            </button>
          </div>
        </div>
      </header>

      <div v-if="errorMessage" class="error-message">{{ errorMessage }}</div>

      <section v-if="!isLoggedIn" class="auth-card auth-surface">
        <div class="login-panel">
          <div class="panel-heading">
            <div class="heading-icon">
              <KeyRound :size="20" />
            </div>
            <div>
              <p class="eyebrow">登录</p>
              <h2>用户登录</h2>
            </div>
          </div>
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
      </section>

      <template v-else>
        <section v-if="currentPage === 'studio'" class="panel creation-panel">
          <div class="creation-header">
            <div class="panel-heading">
              <div class="heading-icon">
                <WandSparkles v-if="activeMode === 'image'" :size="20" />
                <VideoIcon v-else :size="20" />
              </div>
              <div>
                <p class="eyebrow">创作模式</p>
                <h2>{{ activeMode === 'image' ? '文生图' : '文生视频' }}</h2>
              </div>
            </div>
            <div class="mode-switch" :class="{ 'is-video': activeMode === 'video' }" role="tablist" aria-label="选择创作模式">
              <span class="mode-indicator" aria-hidden="true"></span>
              <button
                type="button"
                role="tab"
                :aria-selected="activeMode === 'image'"
                :class="{ active: activeMode === 'image' }"
                @click="activeMode = 'image'"
              >
                <WandSparkles :size="17" />
                <span>文生图</span>
              </button>
              <button
                type="button"
                role="tab"
                :aria-selected="activeMode === 'video'"
                :class="{ active: activeMode === 'video' }"
                @click="activeMode = 'video'"
              >
                <VideoIcon :size="17" />
                <span>文生视频</span>
              </button>
            </div>
          </div>

          <Transition name="mode-panel" mode="out-in">
            <article v-if="activeMode === 'image'" key="image" class="mode-form">
              <label class="field">
                图片提示词
                <textarea v-model="imageForm.prompt" rows="6" placeholder="描述要生成的图片，也可以上传参考图进行约束" />
              </label>
              <div class="inline-fields">
                <label class="field">
                  尺寸
                  <select v-model="imageForm.imageSize">
                    <option value="1024x1024">1024x1024</option>
                    <option value="1024x1536">1024x1536</option>
                    <option value="1536x1024">1536x1024</option>
                  </select>
                </label>
                <label class="field">
                  质量
                  <select v-model="imageForm.imageQuality">
                    <option value="low">low</option>
                    <option value="medium">medium</option>
                    <option value="high">high</option>
                  </select>
                </label>
              </div>
              <div class="upload-row">
                <input id="imageRefs" type="file" accept="image/*" multiple @change="handleReferenceUpload($event, imageForm.referenceImages)" />
                <label class="upload-trigger" for="imageRefs">
                  <UploadCloud :size="17" />
                  上传参考图
                </label>
              </div>
              <div v-if="imageForm.referenceImages.length" class="reference-list">
                <div v-for="item in imageForm.referenceImages" :key="item.id" class="reference-item">
                  <img :src="item.url" :alt="item.name" />
                  <input v-model.trim="item.name" type="text" />
                  <button type="button" aria-label="移除参考图" title="移除参考图" @click="removeReference(imageForm.referenceImages, item.id)">
                    <Trash2 :size="16" />
                  </button>
                </div>
              </div>
              <button class="primary-action" type="button" :disabled="imageLoading" @click="submitImage">
                <WandSparkles :size="17" />
                {{ imageLoading ? '生成中' : '生成图片' }}
              </button>
              <div v-if="imageResult?.imageUrl" class="result-preview compact-preview">
                <img :src="imageResult.imageUrl" alt="生成图片" />
                <button type="button" @click="downloadUrl(imageResult.imageUrl, 'generated-image.png')">
                  <Download :size="17" />
                  下载图片
                </button>
              </div>
              <div v-else class="empty-preview image-empty">
                <ImageIcon :size="34" />
                <strong>图片结果将在这里展开</strong>
                <span>支持纯文本生成，也支持参考图约束。</span>
              </div>
            </article>

            <article v-else key="video" class="mode-form">
              <label class="field">
                视频提示词
                <textarea v-model="videoForm.prompt" rows="6" placeholder="描述镜头运动、主体动作、氛围和风格；参考图可选" />
              </label>
              <div class="inline-fields three">
                <div class="field">
                  <span>时长</span>
                  <div class="duration-stepper">
                    <button type="button" :disabled="videoForm.duration <= VIDEO_DURATION_MIN" @click="adjustVideoDuration(-1)">
                      <Minus :size="16" />
                    </button>
                    <strong>{{ videoForm.duration }} 秒</strong>
                    <button type="button" :disabled="videoForm.duration >= VIDEO_DURATION_MAX" @click="adjustVideoDuration(1)">
                      <Plus :size="16" />
                    </button>
                  </div>
                </div>
                <label class="field">
                  清晰度
                  <select v-model="videoForm.resolution">
                    <option value="480p">480p</option>
                    <option value="720p">720p</option>
                  </select>
                </label>
                <label class="field">
                  比例
                  <select v-model="videoForm.ratio">
                    <option value="adaptive">adaptive</option>
                    <option value="16:9">16:9</option>
                    <option value="4:3">4:3</option>
                    <option value="9:16">9:16</option>
                    <option value="3:4">3:4</option>
                    <option value="1:1">1:1</option>
                    <option value="21:9">21:9</option>
                  </select>
                </label>
              </div>
              <label class="toggle">
                <input v-model="videoForm.generateAudio" type="checkbox" />
                生成音频
              </label>
              <div class="upload-row">
                <input id="videoRefs" type="file" accept="image/*" multiple @change="handleReferenceUpload($event, videoForm.referenceImages)" />
                <label class="upload-trigger" for="videoRefs">
                  <UploadCloud :size="17" />
                  上传参考图
                </label>
              </div>
              <div v-if="videoForm.referenceImages.length" class="reference-list">
                <div v-for="item in videoForm.referenceImages" :key="item.id" class="reference-item">
                  <img :src="item.url" :alt="item.name" />
                  <input v-model.trim="item.name" type="text" />
                  <button type="button" aria-label="移除参考图" title="移除参考图" @click="removeReference(videoForm.referenceImages, item.id)">
                    <Trash2 :size="16" />
                  </button>
                </div>
              </div>
              <div class="button-row">
                <button class="primary-action" type="button" :disabled="videoLoading" @click="submitVideo">
                  <VideoIcon :size="17" />
                  {{ videoLoading ? '处理中' : '提交视频任务' }}
                </button>
                <button class="secondary-action" type="button" :disabled="videoLoading || !videoResult?.taskId" @click="refreshVideoStatus">
                  <RefreshCw :size="17" />
                  刷新状态
                </button>
              </div>
              <div v-if="videoResult?.taskId" class="video-status">
                <span>任务 ID</span>
                <strong>{{ videoResult.taskId }}</strong>
                <span>状态</span>
                <strong>{{ videoResult.status || '已提交' }}</strong>
              </div>
              <p v-if="videoResult?.failReason" class="error-message compact">{{ videoResult.failReason }}</p>
              <div v-if="videoResult?.videoUrl" class="result-preview compact-preview">
                <video class="video-preview" :src="videoResult.videoUrl" controls />
                <button class="download-link" type="button" @click="downloadUrl(videoResult.videoUrl, 'generated-video.mp4')">
                  <Download :size="17" />
                  下载视频
                </button>
              </div>
              <div v-if="!videoResult?.taskId" class="empty-preview video-empty">
                <Film :size="34" />
                <strong>视频任务状态将在这里呈现</strong>
                <span>提交任务后可刷新状态并下载最终视频。</span>
              </div>
            </article>
          </Transition>
        </section>

        <section v-else-if="currentPage === 'users' && isAdmin" class="panel admin-panel">
          <div class="admin-panel-head">
            <div class="panel-heading">
              <div class="heading-icon">
                <UserRound :size="20" />
              </div>
              <div>
                <p class="eyebrow">用户设置</p>
                <h2>用户与额度</h2>
              </div>
            </div>
            <div class="admin-toolbar">
              <input v-model.trim="userSearch.keyword" type="text" placeholder="搜索用户名" />
              <button class="secondary-action" type="button" :disabled="adminLoading" @click="fetchAdminUsers">查询</button>
            </div>
          </div>
          <p v-if="adminMessage" class="hint-message">{{ adminMessage }}</p>
          <div class="data-table">
            <div class="table-row table-head">
              <span>用户</span>
              <span>图片总次数</span>
              <span>视频总次数</span>
              <span>操作</span>
            </div>
            <div v-for="item in adminUsers" :key="item.userId" class="table-row">
              <strong>{{ item.username }}</strong>
              <input v-model.number="item.imageCallLimit" class="table-input" type="number" min="0" />
              <input v-model.number="item.videoCallLimit" class="table-input" type="number" min="0" />
              <button class="secondary-action" type="button" :disabled="adminLoading" @click="saveUserSettings(item)">保存</button>
            </div>
          </div>
        </section>

        <section v-else-if="currentPage === 'logs' && isAdmin" class="panel admin-panel">
          <div class="admin-panel-head">
            <div class="panel-heading">
              <div class="heading-icon">
                <RefreshCw :size="20" />
              </div>
              <div>
                <p class="eyebrow">使用日志</p>
                <h2>操作记录</h2>
              </div>
            </div>
            <div class="admin-toolbar log-toolbar">
              <input v-model.trim="logFilter.username" type="text" placeholder="用户名" />
              <select v-model="logFilter.operationType">
                <option value="">全部类型</option>
                <option value="TEXT_TO_IMAGE">文生图</option>
                <option value="TEXT_TO_VIDEO">文生视频</option>
              </select>
              <select v-model="logFilter.status">
                <option value="">全部状态</option>
                <option value="SUCCESS">成功</option>
                <option value="FAILURE">失败</option>
              </select>
              <button class="secondary-action" type="button" :disabled="adminLoading" @click="fetchOperationLogs">筛选</button>
            </div>
          </div>
          <p v-if="adminMessage" class="hint-message">{{ adminMessage }}</p>
          <div class="data-table log-table">
            <div class="table-row table-head">
              <span>时间</span>
              <span>用户</span>
              <span>操作</span>
              <span>状态</span>
              <span>耗时</span>
              <span>摘要</span>
            </div>
            <div v-for="item in operationLogs" :key="item.id" class="table-row">
              <span>{{ new Date(item.createdAt).toLocaleString() }}</span>
              <strong>{{ item.username || '-' }}</strong>
              <span>{{ item.operationName }}</span>
              <span>{{ item.status }}</span>
              <span>{{ item.durationMs ?? '-' }}ms</span>
              <span class="summary-cell">{{ item.errorMessage || item.responseSummary || item.requestSummary }}</span>
            </div>
          </div>
        </section>

        <section v-else-if="currentPage === 'models' && isAdmin" class="panel admin-panel">
          <div class="admin-panel-head">
            <div class="panel-heading">
              <div class="heading-icon">
                <Settings2 :size="20" />
              </div>
              <div>
                <p class="eyebrow">模型配置</p>
                <h2>服务参数</h2>
              </div>
            </div>
            <button class="secondary-action" type="button" :disabled="adminLoading" @click="fetchModelConfigs">刷新</button>
          </div>
          <p v-if="adminMessage" class="hint-message">{{ adminMessage }}</p>
          <div class="model-config-grid">
            <article v-for="form in modelForms" :key="form.serviceType" class="model-config-card">
              <h3>{{ form.title }}</h3>
              <label class="field">
                服务地址
                <input v-model.trim="form.baseUrl" type="text" placeholder="https://provider.example.com" />
              </label>
              <label class="field">
                密钥
                <input v-model.trim="form.apiKey" type="password" autocomplete="off" :placeholder="form.apiKeyMask || '留空保持原密钥'" />
              </label>
              <label class="field">
                模型名称
                <input v-model.trim="form.model" type="text" placeholder="模型名称" />
              </label>
              <label class="toggle">
                <input v-model="form.enabled" type="checkbox" />
                启用
              </label>
              <button class="primary-action" type="button" :disabled="adminLoading" @click="submitModelConfig(form.serviceType)">
                <Settings2 :size="17" />
                保存配置
              </button>
            </article>
          </div>
        </section>
      </template>
    </section>
  </main>
</template>
