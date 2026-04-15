<script setup>
import * as echarts from 'echarts';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: Number, default: 320 }
});

const chartRef = ref(null);
let chart;

function render() {
  if (!chartRef.value) return;
  if (!chart) chart = echarts.init(chartRef.value);
  chart.setOption(props.option, true);
}

function resize() {
  if (chart) chart.resize();
}

onMounted(() => {
  render();
  window.addEventListener('resize', resize);
});

watch(() => props.option, render, { deep: true });

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize);
  if (chart) {
    chart.dispose();
    chart = null;
  }
});
</script>

<template>
  <div ref="chartRef" class="chart-shell" :style="{ height: `${height}px` }"></div>
</template>
