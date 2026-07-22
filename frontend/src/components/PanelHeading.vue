<script setup>
defineProps({
  eyebrow: {
    type: String,
    required: true
  },
  title: {
    type: String,
    required: true
  }
})
</script>

<template>
  <!-- 页面和卡片标题统一走该组件，后续替换图标尺寸或标题层级时只改一处。 -->
  <div class="panel-heading">
    <div class="heading-icon">
      <slot name="icon" />
    </div>
    <div>
      <p class="eyebrow">{{ eyebrow }}</p>
      <h2>{{ title }}</h2>
    </div>
  </div>
</template>
