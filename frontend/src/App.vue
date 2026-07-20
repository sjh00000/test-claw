<script setup>
import { computed, reactive, ref } from 'vue'
import { generateImage, generateVideo, login, queryVideoStatus } from './lib/api'

const savedUser = JSON.parse(localStorage.getItem('studioUser') || 'null')

const user = ref(savedUser)
const errorMessage = ref('')
const loginLoading = ref(false)
const imageLoading = ref(false)
const videoLoading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  displayName: ''
})

const providerForm = reactive({
  imageBaseUrl: '',
  imageApiKey: '',
  imageModel: '',
  seedanceBaseUrl: '',
  seedanceApiKey: '',
  seedanceModel: ''
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

const imageResult = ref(null)
const videoResult = ref(null)

const isLoggedIn = computed(() => Boolean(user.value?.userId))

const imageProviderMessage = computed(() => {
  if (!providerForm.imageBaseUrl.trim()) {
    return '请填写 image-provider base-url'
  }
  if (!providerForm.imageApiKey.trim()) {
    return '请填写 image-provider api-key'
  }
  if (!providerForm.imageModel.trim()) {
    return '请填写 image-provider model'
  }
  return ''
})

const seedanceMessage = computed(() => {
  if (!providerForm.seedanceBaseUrl.trim()) {
    return '请填写 Seedance base-url'
  }
  if (!providerForm.seedanceApiKey.trim()) {
    return '请填写 Seedance api-key'
  }
  if (!providerForm.seedanceModel.trim()) {
    return '请填写 Seedance model'
  }
  return ''
})

function buildImageProviderConfig() {
  return {
    baseUrl: providerForm.imageBaseUrl.trim(),
    apiKey: providerForm.imageApiKey.trim(),
    model: providerForm.imageModel.trim()
  }
}

function buildSeedanceConfig() {
  return {
    baseUrl: providerForm.seedanceBaseUrl.trim(),
    apiKey: providerForm.seedanceApiKey.trim(),
    model: providerForm.seedanceModel.trim()
  }
}

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
      password: loginForm.password,
      displayName: loginForm.displayName.trim()
    })
    localStorage.setItem('studioUser', JSON.stringify(user.value))
    loginForm.password = ''
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loginLoading.value = false
  }
}

function logout() {
  user.value = null
  localStorage.removeItem('studioUser')
}

async function submitImage() {
  if (!isLoggedIn.value) {
    errorMessage.value = '请先登录'
    return
  }
  if (imageProviderMessage.value) {
    errorMessage.value = imageProviderMessage.value
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
      userId: user.value.userId,
      prompt: imageForm.prompt.trim(),
      referenceImages: referenceImagePayload(imageForm.referenceImages),
      imageProviderConfig: buildImageProviderConfig(),
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
  if (seedanceMessage.value) {
    errorMessage.value = seedanceMessage.value
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
      userId: user.value.userId,
      prompt: videoForm.prompt.trim(),
      referenceImages: referenceImagePayload(videoForm.referenceImages),
      seedanceConfig: buildSeedanceConfig(),
      duration: Number(videoForm.duration),
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
  if (seedanceMessage.value) {
    errorMessage.value = seedanceMessage.value
    return
  }
  videoLoading.value = true
  errorMessage.value = ''
  try {
    videoResult.value = await queryVideoStatus({
      taskId: videoResult.value.taskId,
      seedanceConfig: buildSeedanceConfig()
    })
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    videoLoading.value = false
  }
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
</script>

<template>
  <main class="studio-shell">
    <section class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">Text Generation Studio</p>
          <h1>文生图 / 文生视频工作台</h1>
        </div>
        <div v-if="isLoggedIn" class="user-chip">
          <span>{{ user.displayName || user.username }}</span>
          <button type="button" @click="logout">退出</button>
        </div>
      </header>

      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

      <section v-if="!isLoggedIn" class="panel login-panel">
        <div class="panel-heading">
          <p class="eyebrow">Login</p>
          <h2>用户登录</h2>
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
          <label class="field">
            展示名
            <input v-model.trim="loginForm.displayName" type="text" placeholder="首次登录可填" />
          </label>
          <button class="primary-action" type="button" :disabled="loginLoading" @click="submitLogin">
            {{ loginLoading ? '登录中' : '登录 / 首次创建' }}
          </button>
        </div>
      </section>

      <template v-else>
        <section class="panel config-panel">
          <div class="panel-heading">
            <p class="eyebrow">Provider Config</p>
            <h2>厂商配置</h2>
          </div>
          <div class="config-grid">
            <div class="config-group">
              <h3>Image Provider</h3>
              <label class="field">
                base-url
                <input v-model.trim="providerForm.imageBaseUrl" type="text" placeholder="https://provider.example.com" />
              </label>
              <label class="field">
                api-key
                <input v-model.trim="providerForm.imageApiKey" type="password" autocomplete="off" placeholder="sk-..." />
              </label>
              <label class="field">
                model
                <input v-model.trim="providerForm.imageModel" type="text" placeholder="gpt-image-2" />
              </label>
            </div>
            <div class="config-group">
              <h3>Seedance</h3>
              <label class="field">
                base-url
                <input v-model.trim="providerForm.seedanceBaseUrl" type="text" placeholder="https://seedance-provider.example.com" />
              </label>
              <label class="field">
                api-key
                <input v-model.trim="providerForm.seedanceApiKey" type="password" autocomplete="off" placeholder="sk-..." />
              </label>
              <label class="field">
                model
                <input v-model.trim="providerForm.seedanceModel" type="text" placeholder="doubao-seedance-2-0" />
              </label>
            </div>
          </div>
        </section>

        <section class="generation-grid">
          <article class="panel generator-panel">
            <div class="panel-heading">
              <p class="eyebrow">Text To Image</p>
              <h2>文生图</h2>
            </div>
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
              <label class="upload-trigger" for="imageRefs">上传参考图（可选）</label>
            </div>
            <div v-if="imageForm.referenceImages.length" class="reference-list">
              <div v-for="item in imageForm.referenceImages" :key="item.id" class="reference-item">
                <img :src="item.url" :alt="item.name" />
                <input v-model.trim="item.name" type="text" />
                <button type="button" @click="removeReference(imageForm.referenceImages, item.id)">移除</button>
              </div>
            </div>
            <button class="primary-action" type="button" :disabled="imageLoading || Boolean(imageProviderMessage)" @click="submitImage">
              {{ imageLoading ? '生成中' : '生成图片' }}
            </button>
            <p v-if="imageProviderMessage" class="hint-message">{{ imageProviderMessage }}</p>
            <div v-if="imageResult?.imageUrl" class="result-preview">
              <img :src="imageResult.imageUrl" alt="生成图片" />
              <button type="button" @click="downloadUrl(imageResult.imageUrl, 'generated-image.png')">下载图片</button>
            </div>
          </article>

          <article class="panel generator-panel">
            <div class="panel-heading">
              <p class="eyebrow">Text To Video</p>
              <h2>文生视频</h2>
            </div>
            <label class="field">
              视频提示词
              <textarea v-model="videoForm.prompt" rows="6" placeholder="描述镜头运动、主体动作、氛围和风格；参考图可选" />
            </label>
            <div class="inline-fields three">
              <label class="field">
                时长
                <input v-model.number="videoForm.duration" type="number" min="4" max="15" />
              </label>
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
              <label class="upload-trigger" for="videoRefs">上传参考图（可选）</label>
            </div>
            <div v-if="videoForm.referenceImages.length" class="reference-list">
              <div v-for="item in videoForm.referenceImages" :key="item.id" class="reference-item">
                <img :src="item.url" :alt="item.name" />
                <input v-model.trim="item.name" type="text" />
                <button type="button" @click="removeReference(videoForm.referenceImages, item.id)">移除</button>
              </div>
            </div>
            <div class="button-row">
              <button class="primary-action" type="button" :disabled="videoLoading || Boolean(seedanceMessage)" @click="submitVideo">
                {{ videoLoading ? '处理中' : '提交视频任务' }}
              </button>
              <button class="secondary-action" type="button" :disabled="videoLoading || !videoResult?.taskId" @click="refreshVideoStatus">
                刷新状态
              </button>
            </div>
            <p v-if="seedanceMessage" class="hint-message">{{ seedanceMessage }}</p>
            <div v-if="videoResult?.taskId" class="video-status">
              <span>任务 ID</span>
              <strong>{{ videoResult.taskId }}</strong>
              <span>状态</span>
              <strong>{{ videoResult.status || '已提交' }}</strong>
            </div>
            <p v-if="videoResult?.failReason" class="error-message compact">{{ videoResult.failReason }}</p>
            <video v-if="videoResult?.videoUrl" class="video-preview" :src="videoResult.videoUrl" controls />
            <button v-if="videoResult?.videoUrl" class="download-link" type="button" @click="downloadUrl(videoResult.videoUrl, 'generated-video.mp4')">
              下载视频
            </button>
          </article>
        </section>
      </template>
    </section>
  </main>
</template>
