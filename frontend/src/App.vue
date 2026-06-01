<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { cancelVideo, createSession, generateKeyframe, generateReferenceImage, refreshVideo, submitVideoFromKeyframes } from './lib/api'

const statusText = {
  DRAFT: '待生成',
  GENERATING_KEYFRAMES: '生成关键帧',
  KEYFRAMES_READY: '关键帧完成',
  SUBMITTING_VIDEO: '提交视频',
  VIDEO_RUNNING: '视频生成中',
  SUCCEEDED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消'
}

const MIN_KEYFRAME_COUNT = 1
const MAX_KEYFRAME_COUNT = 50

const form = reactive({
  referenceName: '',
  referencePrompt: '',
  videoPrompt: '',
  keyframeCount: 3,
  imageSize: '1024x1024',
  imageQuality: 'medium',
  duration: 5,
  resolution: '720p',
  ratio: 'adaptive',
  generateAudio: true,
  fastMode: false,
  keyframes: []
})

const referenceImages = ref([])
const selectedReferenceIds = ref([])
const keyframeImages = ref([])
const selectedKeyframeIds = ref([])
const session = ref(null)
const referenceLoading = ref(false)
const generatingFrameIndexes = ref([])
const videoLoading = ref(false)
const errorMessage = ref('')
let referenceRequestSeq = 0
const frameRequestSeqMap = new Map()
let videoRequestSeq = 0
let pollTimer = null

function createBlankKeyframe(index) {
  return {
    prompt: index === 0 ? '镜头从城市天台缓慢推进，主角站在晨光里回头' : ''
  }
}

function syncKeyframes() {
  const nextCount = normalizeKeyframeCount(form.keyframeCount)
  const previousCount = form.keyframes.length
  if (form.keyframeCount !== nextCount) {
    form.keyframeCount = nextCount
  }
  // 关键帧数量变化时保留已有提示词，只补齐或删除尾部项。
  while (form.keyframes.length < nextCount) {
    form.keyframes.push(createBlankKeyframe(form.keyframes.length))
  }
  while (form.keyframes.length > nextCount) {
    form.keyframes.pop()
  }
  if (session.value?.id && previousCount !== nextCount && session.value.status !== 'VIDEO_RUNNING' && session.value.status !== 'SUBMITTING_VIDEO') {
    // 关键帧数量改变后旧会话的帧列表已不可信，后续单帧生成需要重新创建会话。
    session.value = null
  }
}

watch(() => form.keyframeCount, syncKeyframes, { immediate: true })

function normalizeKeyframeCount(value) {
  const count = Number.parseInt(value, 10)
  // 关键帧数量限定在 1-50，避免前端生成过多输入框或提交超出后端限制。
  if (Number.isNaN(count)) {
    return MIN_KEYFRAME_COUNT
  }
  return Math.min(Math.max(count, MIN_KEYFRAME_COUNT), MAX_KEYFRAME_COUNT)
}

function handleKeyframeCountInput(event) {
  const nextCount = normalizeKeyframeCount(event.target.value)
  // 用户手输或粘贴超过 50 时立即回写为 50，做到界面层面不可保留超限值。
  event.target.value = nextCount
  form.keyframeCount = nextCount
}

const selectedReferenceImages = computed(() => {
  return referenceImages.value.filter((item) => selectedReferenceIds.value.includes(item.id))
})

const selectedKeyframeImages = computed(() => {
  return keyframeImages.value.filter((item) => selectedKeyframeIds.value.includes(item.id))
})

const isVideoRunning = computed(() => {
  return videoLoading.value || ['SUBMITTING_VIDEO', 'VIDEO_RUNNING'].includes(session.value?.status)
})

function canGenerateSingleKeyframe(index) {
  return selectedReferenceImages.value.length > 0 && Boolean(form.keyframes[index - 1]?.prompt.trim())
}

const videoValidationMessage = computed(() => {
  if (!form.videoPrompt.trim()) {
    return '视频整体描述不能为空'
  }
  if (selectedReferenceImages.value.length === 0) {
    return '至少选择 1 张参考图'
  }
  if (selectedReferenceImages.value.some((item) => !item.title.trim())) {
    return '已选参考图名称不能为空'
  }
  if (selectedKeyframeImages.value.length === 0) {
    return '至少选择 1 张关键帧图'
  }
  if (selectedKeyframeImages.value.length > MAX_KEYFRAME_COUNT) {
    return '关键帧图最多支持 50 张'
  }
  const duration = Number(form.duration)
  if (!Number.isInteger(duration) || duration < 4 || duration > 15) {
    return '视频时长需为 4 到 15 秒之间的整数'
  }
  if (!['480p', '720p'].includes(form.resolution)) {
    return '视频清晰度仅支持 480p 或 720p'
  }
  if (!['16:9', '4:3', '1:1', '3:4', '9:16', '21:9', 'adaptive'].includes(form.ratio)) {
    return '视频比例仅支持 16:9、4:3、1:1、3:4、9:16、21:9 或 adaptive'
  }
  return ''
})

const canGenerateVideo = computed(() => {
  return !videoValidationMessage.value && !isVideoRunning.value
})

const progress = computed(() => {
  if (!session.value) {
    return 0
  }
  if (session.value.status === 'SUCCEEDED') {
    return 100
  }
  if (session.value.status === 'VIDEO_RUNNING') {
    return 80
  }
  const keyframes = session.value.keyframes || []
  const done = keyframes.filter((item) => item.status === 'KEYFRAMES_READY').length
  return Math.round((done / Math.max(keyframes.length, 1)) * 60)
})

function createImageItem(url, source, title) {
  return {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    url,
    source,
    title
  }
}

function addReferenceImage(url, source, title) {
  const item = createImageItem(url, source, title)
  referenceImages.value.push(item)
  selectedReferenceIds.value.push(item.id)
}

function addKeyframeImage(url, source, title) {
  const item = createImageItem(url, source, title)
  keyframeImages.value.push(item)
  selectedKeyframeIds.value.push(item.id)
}

function removeImageFromRefs(collectionRef, selectedIdsRef, id) {
  const index = collectionRef.value.findIndex((item) => item.id === id)
  if (index >= 0) {
    collectionRef.value.splice(index, 1)
  }
  // 移除图片时同步取消选中，避免已删除图片继续参与生成。
  selectedIdsRef.value = selectedIdsRef.value.filter((itemId) => itemId !== id)
}

function toggleSelectionInRef(selectedIdsRef, id) {
  if (selectedIdsRef.value.includes(id)) {
    // 取消使用只影响选中状态，不删除图片本身。
    selectedIdsRef.value = selectedIdsRef.value.filter((itemId) => itemId !== id)
  } else {
    selectedIdsRef.value.push(id)
  }
}

function removeReferenceImage(id) {
  removeImageFromRefs(referenceImages, selectedReferenceIds, id)
}

function removeKeyframeImage(id) {
  removeImageFromRefs(keyframeImages, selectedKeyframeIds, id)
}

function toggleReferenceSelection(id) {
  toggleSelectionInRef(selectedReferenceIds, id)
}

function toggleKeyframeSelection(id) {
  toggleSelectionInRef(selectedKeyframeIds, id)
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

function readImageFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('图片读取失败'))
    reader.readAsDataURL(file)
  })
}

function describeImageList(title, images) {
  if (!images.length) {
    return `${title}：无`
  }
  return `${title}：\n${images.map((image, index) => {
    const name = image.title?.trim() || `${title}${index + 1}`
    return `${index + 1}. ${name}`
  }).join('\n')}`
}

function confirmGeneration(title, lines) {
  // 生成前把本次引用图标签集中展示，避免用户误选旧图就提交厂商。
  return window.confirm(`${title}\n\n${lines.filter(Boolean).join('\n\n')}\n\n确认继续生成吗？`)
}

async function handleReferenceUpload(event) {
  const files = Array.from(event.target.files || [])
  for (const file of files) {
    const url = await readImageFile(file)
    addReferenceImage(url, 'upload', form.referenceName.trim() || file.name)
  }
  event.target.value = ''
}

async function handleKeyframeUpload(event) {
  const files = Array.from(event.target.files || [])
  for (const file of files) {
    const url = await readImageFile(file)
    addKeyframeImage(url, 'upload', file.name)
  }
  event.target.value = ''
}

async function createReferenceImage() {
  if (!form.referencePrompt.trim() || referenceLoading.value) {
    return
  }
  if (!confirmGeneration('即将生成参考图', [
    `实际提示词：${form.referencePrompt.trim()}`,
    '本次不引用已有图片，只根据上述参考图描述生成。'
  ])) {
    return
  }
  const requestSeq = ++referenceRequestSeq
  const referenceNameSnapshot = form.referenceName.trim() || 'image-2 参考图'
  const referencePromptSnapshot = form.referencePrompt.trim()
  const imageSizeSnapshot = form.imageSize
  const imageQualitySnapshot = form.imageQuality
  referenceLoading.value = true
  errorMessage.value = ''
  try {
    const result = await generateReferenceImage({
      prompt: referencePromptSnapshot,
      imageSize: imageSizeSnapshot,
      imageQuality: imageQualitySnapshot
    })
    if (requestSeq === referenceRequestSeq) {
      addReferenceImage(result.imageUrl, 'generated', referenceNameSnapshot)
    }
  } catch (error) {
    if (requestSeq === referenceRequestSeq) {
      errorMessage.value = error.message
    }
  } finally {
    if (requestSeq === referenceRequestSeq) {
      referenceLoading.value = false
    }
  }
}

async function ensureSession() {
  if (session.value?.id) {
    return session.value
  }
  session.value = await createSession({
    videoPrompt: form.videoPrompt.trim() || '关键帧生成',
    referenceImageUrls: selectedReferenceImages.value.map((item) => item.url),
    referenceImages: selectedReferenceImages.value.map((item) => ({
      imageUrl: item.url,
      name: item.title
    })),
    keyframeCount: Number(form.keyframeCount),
    imageSize: form.imageSize,
    imageQuality: form.imageQuality,
    duration: Number(form.duration),
    resolution: form.resolution,
    ratio: form.ratio,
    generateAudio: form.generateAudio,
    fastMode: form.fastMode,
    keyframes: form.keyframes.map((item) => ({ prompt: item.prompt.trim() }))
  })
  return session.value
}

function syncGeneratedKeyframeImages(nextSession) {
  for (const keyframe of nextSession.keyframes || []) {
    if (!keyframe.generatedImageUrl) {
      continue
    }
    const title = `关键帧 ${keyframe.index}`
    const existing = keyframeImages.value.find((item) => item.title === title && item.source === 'generated')
    if (existing) {
      existing.url = keyframe.generatedImageUrl
    } else {
      addKeyframeImage(keyframe.generatedImageUrl, 'generated', title)
    }
  }
}

function isFrameGenerating(index) {
  return generatingFrameIndexes.value.includes(index)
}

function nextFrameRequestSeq(index) {
  const nextSeq = (frameRequestSeqMap.get(index) || 0) + 1
  frameRequestSeqMap.set(index, nextSeq)
  return nextSeq
}

function isLatestFrameRequest(index, requestSeq) {
  return frameRequestSeqMap.get(index) === requestSeq
}

async function createSingleKeyframe(index) {
  if (!canGenerateSingleKeyframe(index) || isFrameGenerating(index) || isVideoRunning.value) {
    return
  }
  if (!confirmGeneration(`即将生成关键帧 ${index}`, [
    `关键帧 ${index} 画面描述：${form.keyframes[index - 1].prompt.trim()}`,
    describeImageList('本次引用参考图', selectedReferenceImages.value)
  ])) {
    return
  }
  const requestSeq = nextFrameRequestSeq(index)
  const requestId = `${Date.now()}-${index}-${requestSeq}-${Math.random().toString(16).slice(2)}`
  const keyframePromptSnapshot = form.keyframes[index - 1].prompt.trim()
  const imageSizeSnapshot = form.imageSize
  const imageQualitySnapshot = form.imageQuality
  const referenceImagesSnapshot = selectedReferenceImages.value.map((item) => ({
    imageUrl: item.url,
    name: item.title
  }))
  errorMessage.value = ''
  generatingFrameIndexes.value.push(index)
  try {
    await ensureSession()
    const nextSession = await generateKeyframe(session.value.id, index, {
      prompt: keyframePromptSnapshot,
      referenceImages: referenceImagesSnapshot,
      imageSize: imageSizeSnapshot,
      imageQuality: imageQualitySnapshot,
      requestId
    })
    if (isLatestFrameRequest(index, requestSeq)) {
      session.value = nextSession
      syncGeneratedKeyframeImages(session.value)
    }
  } catch (error) {
    if (isLatestFrameRequest(index, requestSeq)) {
      errorMessage.value = error.message
    }
  } finally {
    if (isLatestFrameRequest(index, requestSeq)) {
      generatingFrameIndexes.value = generatingFrameIndexes.value.filter((item) => item !== index)
    }
  }
}

async function createVideo() {
  if (!canGenerateVideo.value || isVideoRunning.value) {
    if (videoValidationMessage.value) {
      errorMessage.value = videoValidationMessage.value
    }
    return
  }
  if (!confirmGeneration('即将生成视频', [
    `视频整体描述：${form.videoPrompt.trim()}`,
    describeImageList('本次引用参考图', selectedReferenceImages.value),
    describeImageList('本次引用关键帧图', selectedKeyframeImages.value)
  ])) {
    return
  }
  const requestSeq = ++videoRequestSeq
  videoLoading.value = true
  errorMessage.value = ''
  clearPolling()

  try {
    const nextSession = await submitVideoFromKeyframes({
      videoPrompt: form.videoPrompt.trim(),
      referenceImages: selectedReferenceImages.value.map((item) => ({
        imageUrl: item.url,
        name: item.title
      })),
      keyframeImages: selectedKeyframeImages.value.map((item, index) => ({
        imageUrl: item.url,
        name: `关键帧 ${index + 1}`
      })),
      duration: Number(form.duration),
      resolution: form.resolution,
      ratio: form.ratio,
      generateAudio: form.generateAudio,
      fastMode: form.fastMode
    })
    if (requestSeq === videoRequestSeq) {
      session.value = nextSession
      startPolling()
    }
  } catch (error) {
    if (requestSeq === videoRequestSeq) {
      errorMessage.value = error.message
    }
  } finally {
    if (requestSeq === videoRequestSeq) {
      videoLoading.value = false
    }
  }
}

function cancelReferenceGeneration() {
  // image-2 同步请求已经发出时无法可靠通知厂商取消；这里取消前端等待并忽略后续返回结果。
  referenceRequestSeq += 1
  referenceLoading.value = false
  errorMessage.value = '已取消本次参考图生成等待'
}

function cancelKeyframeGeneration(index) {
  // 单帧 image-2 请求无法可靠中断上游；这里取消前端等待并忽略旧请求返回，用户可立即重写描述后再生成。
  nextFrameRequestSeq(index)
  generatingFrameIndexes.value = generatingFrameIndexes.value.filter((item) => item !== index)
  errorMessage.value = `已取消关键帧 ${index} 生成等待`
}

async function cancelVideoGeneration() {
  if (!session.value?.id || !isVideoRunning.value) {
    return
  }
  if (!window.confirm('确认取消当前视频生成任务吗？已提交到 Seedance 的任务会调用厂商取消接口。')) {
    return
  }
  videoRequestSeq += 1
  videoLoading.value = false
  clearPolling()
  try {
    session.value = await cancelVideo(session.value.id)
  } catch (error) {
    errorMessage.value = error.message
  }
}

function startPolling() {
  if (!session.value?.id) {
    return
  }
  pollTimer = window.setInterval(async () => {
    try {
      session.value = await refreshVideo(session.value.id)
      if (['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(session.value.status)) {
        clearPolling()
      }
    } catch (error) {
      errorMessage.value = error.message
      clearPolling()
    }
  }, 5000)
}

function clearPolling() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}
</script>

<template>
  <main class="studio-shell">
    <section class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">Keyframe Video Studio</p>
          <h1>三步生成关键帧视频</h1>
        </div>
      </header>

      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

      <div class="step-stack">
        <section class="panel step-panel">
          <div class="step-header">
            <span>1</span>
            <div>
              <h2>生成或上传主体参考图</h2>
              <p>参考图会作为角色、主体或风格约束，被后续所有关键帧共用。</p>
            </div>
          </div>

          <div class="two-column">
            <label class="field" for="referencePrompt">
              参考图描述
              <textarea
                id="referencePrompt"
                v-model="form.referencePrompt"
                rows="4"
                placeholder="描述角色、服装、主体外观、风格或场景基调"
              />
            </label>
            <div class="upload-box">
              <input id="referenceUpload" type="file" accept="image/*" multiple @change="handleReferenceUpload" />
              <label class="upload-trigger" for="referenceUpload">上传参考图</label>
              <label class="field compact-field" for="referenceName">
                参考图名称
                <input
                  id="referenceName"
                  v-model.trim="form.referenceName"
                  type="text"
                  placeholder="例如 沈砚参考图"
                />
              </label>
              <label class="field compact-field">
                image-2 尺寸
                <select v-model="form.imageSize">
                  <option value="1024x1024">1024x1024</option>
                  <option value="1024x1536">1024x1536</option>
                  <option value="1536x1024">1536x1024</option>
                </select>
              </label>
              <label class="field compact-field">
                image-2 质量
                <select v-model="form.imageQuality">
                  <option value="low">low</option>
                  <option value="medium">medium</option>
                  <option value="high">high</option>
                </select>
              </label>
              <button
                class="primary-action"
                type="button"
                :disabled="!form.referencePrompt.trim() || referenceLoading"
                @click="createReferenceImage"
              >
                {{ referenceLoading ? '生成中' : '用 image-2 生成参考图' }}
              </button>
              <button v-if="referenceLoading" class="secondary-action" type="button" @click="cancelReferenceGeneration">
                取消生成
              </button>
            </div>
          </div>

          <div class="image-grid">
            <figure v-for="image in referenceImages" :key="image.id" :class="{ selected: selectedReferenceIds.includes(image.id) }">
              <img :src="image.url" :alt="image.title" />
              <figcaption>
                <label class="field image-meta-field">
                  参考图名称
                  <input v-model.trim="image.title" type="text" placeholder="例如 沈砚参考图" />
                </label>
                <span>{{ image.source === 'upload' ? '用户上传' : '模型生成' }}</span>
              </figcaption>
              <div class="image-actions">
                <button type="button" @click="toggleReferenceSelection(image.id)">
                  {{ selectedReferenceIds.includes(image.id) ? '取消使用' : '使用' }}
                </button>
                <button type="button" @click="downloadUrl(image.url, `${image.title}.png`)">下载</button>
                <button type="button" @click="removeReferenceImage(image.id)">移除</button>
              </div>
            </figure>
          </div>
        </section>

        <section class="panel step-panel">
          <div class="step-header">
            <span>2</span>
            <div>
              <h2>通过参考图生成关键帧图</h2>
              <p>选择关键帧数量，逐帧填写画面描述；也可以直接上传关键帧图。</p>
            </div>
          </div>

          <div class="settings-grid">
            <label class="field">
              关键帧数量
              <input
                :value="form.keyframeCount"
                type="number"
                :min="MIN_KEYFRAME_COUNT"
                :max="MAX_KEYFRAME_COUNT"
                @input="handleKeyframeCountInput"
              />
            </label>
          </div>

          <div class="keyframe-list">
            <article v-for="(item, index) in form.keyframes" :key="index" class="keyframe-editor">
              <div class="keyframe-title">
                <strong>关键帧 {{ index + 1 }}</strong>
                <button
                  v-if="!isFrameGenerating(index + 1)"
                  type="button"
                  :disabled="!canGenerateSingleKeyframe(index + 1) || isFrameGenerating(index + 1) || isVideoRunning"
                  @click="createSingleKeyframe(index + 1)"
                >
                  生成本帧
                </button>
                <button v-else type="button" @click="cancelKeyframeGeneration(index + 1)">
                  取消生成
                </button>
              </div>
              <label class="field">
                画面描述
                <textarea v-model="item.prompt" rows="3" placeholder="描述这一帧的画面内容" />
              </label>
            </article>
          </div>

          <div class="toolbar-row">
            <div class="upload-box compact">
              <input id="keyframeUpload" type="file" accept="image/*" multiple @change="handleKeyframeUpload" />
              <label class="upload-trigger" for="keyframeUpload">上传关键帧图</label>
            </div>
          </div>

          <div class="image-grid">
            <figure v-for="image in keyframeImages" :key="image.id" :class="{ selected: selectedKeyframeIds.includes(image.id) }">
              <img :src="image.url" :alt="image.title" />
              <figcaption>
                <strong>{{ `关键帧 ${selectedKeyframeIds.indexOf(image.id) >= 0 ? selectedKeyframeIds.indexOf(image.id) + 1 : keyframeImages.indexOf(image) + 1}` }}</strong>
                <span>{{ image.source === 'upload' ? '用户上传' : '模型生成' }}</span>
              </figcaption>
              <div class="image-actions">
                <button type="button" @click="toggleKeyframeSelection(image.id)">
                  {{ selectedKeyframeIds.includes(image.id) ? '取消使用' : '使用' }}
                </button>
                <button type="button" @click="downloadUrl(image.url, `${image.title}.png`)">下载</button>
                <button type="button" @click="removeKeyframeImage(image.id)">移除</button>
              </div>
            </figure>
          </div>
        </section>

        <section class="panel step-panel">
          <div class="step-header">
            <span>3</span>
            <div>
              <h2>通过关键帧图生成视频</h2>
              <p>Seedance 会把已选关键帧图作为多模态参考图生成视频。</p>
            </div>
          </div>

          <div class="two-column">
            <label class="field" for="videoPrompt">
              视频整体描述
              <textarea
                id="videoPrompt"
                v-model="form.videoPrompt"
                rows="4"
                placeholder="描述最终视频的运动、情绪、镜头语言和风格"
              />
            </label>
            <div class="video-settings">
              <label class="field">
                时长（秒）
                <input v-model.number="form.duration" type="number" min="4" max="15" />
              </label>
              <label class="field">
                清晰度
                <select v-model="form.resolution">
                  <option value="480p">480p</option>
                  <option value="720p">720p</option>
                </select>
              </label>
              <label class="field">
                比例
                <select v-model="form.ratio">
                  <option value="adaptive">adaptive</option>
                  <option value="16:9">16:9</option>
                  <option value="4:3">4:3</option>
                  <option value="9:16">9:16</option>
                  <option value="3:4">3:4</option>
                  <option value="1:1">1:1</option>
                  <option value="21:9">21:9</option>
                </select>
              </label>
              <div class="toggle-row">
                <label><input v-model="form.generateAudio" type="checkbox" /> 生成音频</label>
                <label><input v-model="form.fastMode" type="checkbox" /> 使用快速模型</label>
              </div>
            </div>
          </div>

          <button class="primary-action" type="button" :disabled="!canGenerateVideo || isVideoRunning" @click="createVideo">
            {{ isVideoRunning ? '生成中' : '生成视频' }}
          </button>
          <button v-if="isVideoRunning" class="secondary-action" type="button" @click="cancelVideoGeneration">
            取消视频生成
          </button>
          <p v-if="videoValidationMessage" class="hint-message">
            {{ videoValidationMessage }}
          </p>

          <div v-if="session" class="progress-card">
            <div class="progress-header">
              <div>
                <p class="eyebrow">Session</p>
                <h2>{{ statusText[session.status] }}</h2>
              </div>
              <span>{{ progress }}%</span>
            </div>
            <div class="progress-track">
              <div :style="{ width: `${progress}%` }" />
            </div>
          </div>

          <video v-if="session?.videoUrl" class="video-preview" :src="session.videoUrl" controls />
          <p v-else-if="session?.status === 'SUCCEEDED'" class="hint-message">
            视频已生成成功，但暂未解析到下载地址，请查看后端 Seedance 查询响应日志。
          </p>
          <button
            v-if="session?.videoUrl"
            class="download-link"
            type="button"
            @click="downloadUrl(session.videoUrl, 'seedance-video.mp4')"
          >
            下载视频
          </button>
        </section>
      </div>
    </section>
  </main>
</template>
