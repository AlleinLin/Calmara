<template>
  <div class="statistics-view">
    <div class="page-header">
      <h1 class="page-title">统计分析</h1>
      <div class="page-actions">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          @change="fetchStatistics"
        />
      </div>
    </div>
    
    <div class="charts-grid">
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">情绪分布</h3>
        </div>
        <div class="chart-container">
          <v-chart v-if="emotionData.length > 0" :option="emotionOption" autoresize />
          <div v-else class="no-data">暂无数据</div>
        </div>
      </div>
      
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">风险等级分布</h3>
        </div>
        <div class="chart-container">
          <v-chart v-if="riskData.length > 0" :option="riskOption" autoresize />
          <div v-else class="no-data">暂无数据</div>
        </div>
      </div>
      
      <div class="card full-width">
        <div class="card-header">
          <h3 class="card-title">情绪趋势</h3>
        </div>
        <div class="chart-container large">
          <v-chart v-if="trendData.length > 0" :option="trendOption" autoresize />
          <div v-else class="no-data">暂无数据</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminApi } from '@/api'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, LineChart, BarChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import dayjs from 'dayjs'

use([
  CanvasRenderer,
  PieChart,
  LineChart,
  BarChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
])

const dateRange = ref([
  dayjs().subtract(30, 'day').toDate(),
  dayjs().toDate()
])

const statistics = ref({
  dailyEmotions: {},
  riskDistribution: {}
})

const emotionData = computed(() => {
  const emotions = statistics.value.dailyEmotions || {}
  return Object.entries(emotions).map(([name, value]) => ({ name, value }))
})

const riskData = computed(() => {
  const risks = statistics.value.riskDistribution || {}
  return Object.entries(risks).map(([name, value]) => ({ name, value }))
})

const trendData = computed(() => {
  return []
})

const emotionOption = computed(() => ({
  tooltip: {
    trigger: 'item',
    formatter: '{b}: {c} ({d}%)'
  },
  legend: {
    orient: 'vertical',
    right: '5%',
    top: 'center',
    textStyle: { color: 'var(--text-secondary)' }
  },
  series: [{
    type: 'pie',
    radius: ['40%', '70%'],
    center: ['35%', '50%'],
    itemStyle: {
      borderRadius: 8,
      borderColor: 'var(--bg-card)',
      borderWidth: 2
    },
    label: { show: false },
    data: emotionData.value.map((item, index) => ({
      ...item,
      itemStyle: {
        color: ['#10b981', '#f59e0b', '#06b6d4', '#ef4444'][index] || '#6366f1'
      }
    }))
  }]
}))

const riskOption = computed(() => ({
  tooltip: {
    trigger: 'item',
    formatter: '{b}: {c} ({d}%)'
  },
  legend: {
    orient: 'vertical',
    right: '5%',
    top: 'center',
    textStyle: { color: 'var(--text-secondary)' }
  },
  series: [{
    type: 'pie',
    radius: ['40%', '70%'],
    center: ['35%', '50%'],
    itemStyle: {
      borderRadius: 8,
      borderColor: 'var(--bg-card)',
      borderWidth: 2
    },
    label: { show: false },
    data: riskData.value.map((item, index) => ({
      ...item,
      itemStyle: {
        color: ['#10b981', '#f59e0b', '#ef4444'][index] || '#6366f1'
      }
    }))
  }]
}))

const trendOption = computed(() => ({
  tooltip: {
    trigger: 'axis'
  },
  legend: {
    data: ['正常', '焦虑', '低落', '高风险'],
    textStyle: { color: 'var(--text-secondary)' }
  },
  grid: {
    left: '3%',
    right: '4%',
    bottom: '3%',
    containLabel: true
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: [],
    axisLine: { lineStyle: { color: 'var(--border)' } },
    axisLabel: { color: 'var(--text-secondary)' }
  },
  yAxis: {
    type: 'value',
    axisLine: { lineStyle: { color: 'var(--border)' } },
    axisLabel: { color: 'var(--text-secondary)' },
    splitLine: { lineStyle: { color: 'var(--border)' } }
  },
  series: [
    { name: '正常', type: 'line', data: [], itemStyle: { color: '#10b981' } },
    { name: '焦虑', type: 'line', data: [], itemStyle: { color: '#f59e0b' } },
    { name: '低落', type: 'line', data: [], itemStyle: { color: '#06b6d4' } },
    { name: '高风险', type: 'line', data: [], itemStyle: { color: '#ef4444' } }
  ]
}))

const fetchStatistics = async () => {
  try {
    const [startDate, endDate] = dateRange.value || []
    const response = await adminApi.getEmotionStatistics(
      startDate ? dayjs(startDate).format('YYYY-MM-DD') : null,
      endDate ? dayjs(endDate).format('YYYY-MM-DD') : null
    )
    
    if (response.success) {
      statistics.value = response.data
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

onMounted(() => {
  fetchStatistics()
})
</script>

<style lang="scss" scoped>
.statistics-view {
  .charts-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 24px;
    
    @media (max-width: 1200px) {
      grid-template-columns: 1fr;
    }
  }
  
  .full-width {
    grid-column: span 2;
    
    @media (max-width: 1200px) {
      grid-column: span 1;
    }
  }
  
  .chart-container {
    height: 300px;
    
    &.large {
      height: 400px;
    }
  }
  
  .no-data {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: var(--text-muted);
  }
}
</style>
