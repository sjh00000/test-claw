<script setup>
import { Video as VideoIcon, WandSparkles } from '@lucide/vue'

defineProps({
  modelValue: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['update:modelValue'])
</script>

<template>
  <!-- 模式切换只抛出 v-model 变更，表单状态保留在 StudioPage，切换动效由 CSS 控制。 -->
  <div class="mode-switch" :class="{ 'is-video': modelValue === 'video' }" role="tablist" aria-label="选择创作模式">
    <span class="mode-indicator" aria-hidden="true"></span>
    <button
      type="button"
      role="tab"
      :aria-selected="modelValue === 'image'"
      :class="{ active: modelValue === 'image' }"
      @click="emit('update:modelValue', 'image')"
    >
      <WandSparkles :size="17" />
      <span>文生图</span>
    </button>
    <button
      type="button"
      role="tab"
      :aria-selected="modelValue === 'video'"
      :class="{ active: modelValue === 'video' }"
      @click="emit('update:modelValue', 'video')"
    >
      <VideoIcon :size="17" />
      <span>文生视频</span>
    </button>
  </div>
</template>
