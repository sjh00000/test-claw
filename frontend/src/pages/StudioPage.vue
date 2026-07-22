<script setup>
import { Download, Film, Image as ImageIcon, Minus, Plus, Video as VideoIcon, WandSparkles } from '@lucide/vue'
import { onBeforeUnmount, reactive, ref } from 'vue'
import GenerationStatus from '../components/GenerationStatus.vue'
import ModeSwitch from '../components/ModeSwitch.vue'
import PanelHeading from '../components/PanelHeading.vue'
import ReferenceUploader from '../components/ReferenceUploader.vue'
import {
  IMAGE_QUALITY_OPTIONS,
  IMAGE_SIZE_OPTIONS,
  STATUS_POLL_INTERVAL_MS,
  VIDEO_DURATION_MAX,
  VIDEO_DURATION_MIN,
  VIDEO_RATIO_OPTIONS,
  VIDEO_RESOLUTION_OPTIONS,
  isTerminalGenerationStatus
} from '../constants/generation'
import { generateImage, generateVideo, queryTaskStatus, queryVideoStatus } from '../lib/api'
import { downloadUrl } from '../utils/download'
import { referenceImagePayload } from '../utils/referenceImages'

const emit = defineEmits(['error'])

const activeMode = ref('image')
const imageLoading = ref(false)
const videoLoading = ref(false)
const imageResult = ref(null)
const videoResult = ref(null)
const imageStatusPolling = ref(false)
const videoStatusPolling = ref(false)
let imageStatusTimer = null
let videoStatusTimer = null

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

// 生成接口只返回任务 ID，真实结果通过任务轮询回填，避免同步等待厂商长耗时响应。
async function submitImage() {
  if (!imageForm.prompt.trim()) {
    emit('error', '图片提示词不能为空')
    return
  }
  imageLoading.value = true
  emit('error', '')
  try {
    imageResult.value = await generateImage({
      prompt: imageForm.prompt.trim(),
      referenceImages: referenceImagePayload(imageForm.referenceImages),
      imageSize: imageForm.imageSize,
      imageQuality: imageForm.imageQuality
    })
    startImageStatusPolling(imageResult.value?.taskId)
  } catch (error) {
    emit('error', error.message)
  } finally {
    imageLoading.value = false
  }
}

// 视频提交后进入厂商异步任务，前端只轮询本地任务状态，不直接暴露厂商任务细节。
async function submitVideo() {
  if (!videoForm.prompt.trim()) {
    emit('error', '视频提示词不能为空')
    return
  }
  videoLoading.value = true
  emit('error', '')
  stopVideoStatusPolling()
  try {
    videoResult.value = await generateVideo({
      prompt: videoForm.prompt.trim(),
      referenceImages: referenceImagePayload(videoForm.referenceImages),
      duration: clampVideoDuration(videoForm.duration),
      resolution: videoForm.resolution,
      ratio: videoForm.ratio,
      generateAudio: videoForm.generateAudio
    })
    startVideoStatusPolling(videoResult.value?.taskId)
  } catch (error) {
    emit('error', error.message)
  } finally {
    videoLoading.value = false
  }
}

function startImageStatusPolling(taskId) {
  if (!taskId || isTerminalGenerationStatus(imageResult.value)) {
    return
  }
  stopImageStatusPolling()
  imageStatusPolling.value = true
  imageStatusTimer = window.setTimeout(() => pollImageStatus(taskId), STATUS_POLL_INTERVAL_MS)
}

function stopImageStatusPolling() {
  if (imageStatusTimer) {
    window.clearTimeout(imageStatusTimer)
    imageStatusTimer = null
  }
  imageStatusPolling.value = false
}

async function pollImageStatus(taskId) {
  // 切换任务或页面销毁后立即停止旧轮询，避免旧任务结果覆盖当前画面。
  if (!taskId || imageResult.value?.taskId !== taskId) {
    stopImageStatusPolling()
    return
  }
  try {
    const latestTask = await queryTaskStatus({ taskId })
    if (imageResult.value?.taskId !== taskId) {
      stopImageStatusPolling()
      return
    }
    imageResult.value = {
      taskId: latestTask.taskId,
      status: latestTask.statusName || latestTask.status,
      imageUrl: latestTask.resultUrl,
      failReason: latestTask.failReason
    }
    if (isTerminalGenerationStatus(latestTask)) {
      stopImageStatusPolling()
      return
    }
  } catch (error) {
    emit('error', error.message)
    stopImageStatusPolling()
    return
  }
  imageStatusTimer = window.setTimeout(() => pollImageStatus(taskId), STATUS_POLL_INTERVAL_MS)
}

function startVideoStatusPolling(taskId) {
  if (!taskId || isTerminalGenerationStatus(videoResult.value)) {
    return
  }
  stopVideoStatusPolling()
  videoStatusPolling.value = true
  videoStatusTimer = window.setTimeout(() => pollVideoStatus(taskId), STATUS_POLL_INTERVAL_MS)
}

function stopVideoStatusPolling() {
  if (videoStatusTimer) {
    window.clearTimeout(videoStatusTimer)
    videoStatusTimer = null
  }
  videoStatusPolling.value = false
}

async function pollVideoStatus(taskId) {
  // 视频轮询由后端按需刷新厂商状态，前端始终只关心本地任务 ID。
  if (!taskId || videoResult.value?.taskId !== taskId) {
    stopVideoStatusPolling()
    return
  }
  try {
    const latestStatus = await queryVideoStatus({ taskId: videoResult.value.taskId })
    if (videoResult.value?.taskId !== taskId) {
      stopVideoStatusPolling()
      return
    }
    videoResult.value = latestStatus
    if (isTerminalGenerationStatus(latestStatus)) {
      stopVideoStatusPolling()
      return
    }
  } catch (error) {
    emit('error', error.message)
    stopVideoStatusPolling()
    return
  }
  videoStatusTimer = window.setTimeout(() => pollVideoStatus(taskId), STATUS_POLL_INTERVAL_MS)
}

function clampVideoDuration(value) {
  // 时长限制前后端保持一致，防止浏览器输入异常值后提交非法任务。
  const duration = Number(value)
  const normalized = Number.isFinite(duration) ? duration : VIDEO_DURATION_MIN
  videoForm.duration = Math.min(VIDEO_DURATION_MAX, Math.max(VIDEO_DURATION_MIN, normalized))
  return videoForm.duration
}

function adjustVideoDuration(delta) {
  clampVideoDuration(videoForm.duration + delta)
}

onBeforeUnmount(() => {
  stopImageStatusPolling()
  stopVideoStatusPolling()
})
</script>

<template>
  <section class="panel creation-panel">
    <div class="creation-header">
      <PanelHeading eyebrow="创作模式" :title="activeMode === 'image' ? '文生图' : '文生视频'">
        <template #icon>
          <WandSparkles v-if="activeMode === 'image'" :size="20" />
          <VideoIcon v-else :size="20" />
        </template>
      </PanelHeading>
      <ModeSwitch v-model="activeMode" />
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
              <option v-for="item in IMAGE_SIZE_OPTIONS" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>
          <label class="field">
            质量
            <select v-model="imageForm.imageQuality">
              <option v-for="item in IMAGE_QUALITY_OPTIONS" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>
        </div>
        <ReferenceUploader input-id="imageRefs" :items="imageForm.referenceImages" />
        <button class="primary-action" type="button" :disabled="imageLoading" @click="submitImage">
          <WandSparkles :size="17" />
          {{ imageLoading ? '生成中' : '生成图片' }}
        </button>
        <GenerationStatus :task-id="imageResult?.taskId" :status="imageResult?.status" :polling="imageStatusPolling" />
        <p v-if="imageResult?.failReason" class="error-message compact">{{ imageResult.failReason }}</p>
        <div v-if="imageResult?.imageUrl" class="result-preview compact-preview">
          <img :src="imageResult.imageUrl" alt="生成图片" />
          <button type="button" @click="downloadUrl(imageResult.imageUrl, 'generated-image.png')">
            <Download :size="17" />
            下载图片
          </button>
        </div>
        <div v-else class="empty-preview image-empty">
          <ImageIcon :size="34" />
          <strong>图片任务将在这里更新</strong>
          <span>提交后自动刷新状态，完成后可下载结果。</span>
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
              <option v-for="item in VIDEO_RESOLUTION_OPTIONS" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>
          <label class="field">
            比例
            <select v-model="videoForm.ratio">
              <option v-for="item in VIDEO_RATIO_OPTIONS" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>
        </div>
        <label class="toggle">
          <input v-model="videoForm.generateAudio" type="checkbox" />
          生成音频
        </label>
        <ReferenceUploader input-id="videoRefs" :items="videoForm.referenceImages" />
        <div class="button-row">
          <button class="primary-action" type="button" :disabled="videoLoading" @click="submitVideo">
            <VideoIcon :size="17" />
            {{ videoLoading ? '处理中' : '提交视频任务' }}
          </button>
        </div>
        <GenerationStatus :task-id="videoResult?.taskId" :status="videoResult?.status" :polling="videoStatusPolling" />
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
          <span>提交任务后会自动刷新状态并下载最终视频。</span>
        </div>
      </article>
    </Transition>
  </section>
</template>
