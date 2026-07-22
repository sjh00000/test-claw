<script setup>
import { Trash2, UploadCloud } from '@lucide/vue'
import { appendReferenceFiles, removeReference } from '../utils/referenceImages'

const props = defineProps({
  inputId: {
    type: String,
    required: true
  },
  items: {
    type: Array,
    required: true
  }
})
</script>

<template>
  <!-- 参考图以 data URL 暂存在前端表单中，提交时由页面转换成后端需要的 referenceImages。 -->
  <div class="upload-row">
    <input :id="inputId" type="file" accept="image/*" multiple @change="appendReferenceFiles($event, props.items)" />
    <label class="upload-trigger" :for="inputId">
      <UploadCloud :size="17" />
      上传参考图
    </label>
  </div>
  <div v-if="items.length" class="reference-list">
    <div v-for="item in items" :key="item.id" class="reference-item">
      <img :src="item.url" :alt="item.name" />
      <input v-model.trim="item.name" type="text" />
      <button type="button" aria-label="移除参考图" title="移除参考图" @click="removeReference(items, item.id)">
        <Trash2 :size="16" />
      </button>
    </div>
  </div>
</template>
