<template>
  <div>
    <div class="sf-card">
      <div class="sf-card-title">
        近 14 天蒸发量 · 降雨标注
        <span class="sf-muted">绿线＝蒸发量（左轴 mm）／蓝虚线＝降雨量（右轴 mm）／雨日轴上打蓝色竖标，点圆点看当天明细</span>
        <div style="margin-left: auto; display: flex; gap: 8px">
          <el-button size="small" type="primary" @click="dlg = true">登记气象</el-button>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </div>

      <div v-loading="loading">
        <svg :viewBox="`0 0 ${W} ${H}`" class="wt-svg">
          <!-- 网格 -->
          <line
            v-for="g in gridLines"
            :key="'g' + g.y"
            :x1="PAD_L"
            :y1="g.y"
            :x2="W - PAD_R"
            :y2="g.y"
            stroke="#eef2ee"
            stroke-width="1"
          />
          <text v-for="g in gridLines" :key="'gl' + g.y" :x="PAD_L - 8" :y="g.y + 4" text-anchor="end" class="wt-axis-text">
            {{ g.value }}
          </text>

          <!-- 雨日蓝色竖标 -->
          <g v-for="(p, i) in points" :key="'rk' + i">
            <line
              v-if="p.rainfall > 0"
              :x1="p.x"
              :y1="H - PAD_B"
              :x2="p.x"
              :y2="PAD_T - 6"
              stroke="#1e88e5"
              stroke-width="1.4"
              stroke-dasharray="3 3"
              opacity="0.55"
            />
            <text
              v-if="p.rainfall > 0"
              :x="p.x"
              :y="PAD_T - 10"
              text-anchor="middle"
              class="wt-rain-flag"
            >雨 {{ p.rainfall }}mm</text>
          </g>

          <!-- 降雨量折线（次） -->
          <polyline :points="rainPoints" fill="none" stroke="#1e88e5" stroke-width="1.6" stroke-dasharray="5 4" />
          <!-- 蒸发量折线（主） -->
          <polyline :points="evapPoints" fill="none" stroke="#1b5e20" stroke-width="2.6" />
          <polygon :points="evapArea" fill="rgba(27,94,32,.10)" />

          <!-- 数据点 -->
          <g v-for="(p, i) in points" :key="'p' + i" @click="pick(p)" class="wt-point">
            <circle :cx="p.x" :cy="p.evapY" r="5" :fill="p.rainfall > 0 ? '#1e88e5' : '#1b5e20'" />
            <circle v-if="p.rainfall > 0" :cx="p.x" :cy="p.rainY" r="4" fill="#1e88e5" />
            <circle
              v-if="selected && selected.recordDate === p.recordDate"
              :cx="p.x"
              :cy="p.evapY"
              r="9"
              fill="none"
              stroke="#f9a825"
              stroke-width="2"
            />
          </g>

          <!-- X 轴 -->
          <line :x1="PAD_L" :y1="H - PAD_B" :x2="W - PAD_R" :y2="H - PAD_B" stroke="#c8d6c9" />
          <text
            v-for="(p, i) in points"
            :key="'x' + i"
            :x="p.x"
            :y="H - PAD_B + 18"
            text-anchor="middle"
            class="wt-axis-text"
          >{{ shortDate(p.recordDate) }}</text>
          <text :x="PAD_L - 8" :y="PAD_T - 6" text-anchor="end" class="wt-axis-title">蒸发 mm</text>
          <text :x="W - PAD_R + 6" :y="PAD_T - 6" class="wt-axis-title" fill="#1e88e5">降雨 mm</text>
        </svg>

        <div class="wt-detail" v-if="selected">
          <div class="wt-detail-title">{{ selected.recordDate }}</div>
          <div class="wt-detail-grid">
            <div><span class="sf-muted">蒸发量</span><b>{{ selected.evaporation }} mm</b></div>
            <div><span class="sf-muted">降雨量</span><b style="color: #1e88e5">{{ selected.rainfall }} mm</b></div>
            <div><span class="sf-muted">平均气温</span><b>{{ Number(selected.temperature || 0).toFixed(1) }} ℃</b></div>
            <div><span class="sf-muted">平均卤度</span><b>{{ Number(selected.avgBrine || 0).toFixed(2) }} °Bé</b></div>
            <div><span class="sf-muted">记录条数</span><b>{{ selected.recordCount }}</b></div>
            <div>
              <span class="sf-muted">可否收盐</span>
              <el-tag :type="selected.harvestable ? 'success' : 'danger'" size="small">
                {{ selected.harvestable ? '可收盐' : (selected.rainy ? '雨后禁收' : '卤度不足') }}
              </el-tag>
            </div>
          </div>
        </div>
        <div v-else class="sf-muted" style="margin-top: 10px">点击折线上的圆点查看当天数值与是否可收盐。</div>
      </div>
    </div>

    <el-dialog v-model="dlg" title="登记气象与卤度记录" width="460px">
      <el-form :model="form" label-width="100px" size="small">
        <el-form-item label="池号">
          <el-select v-model="form.pondId" filterable style="width: 100%">
            <el-option v-for="p in ponds" :key="p.id" :label="`${p.pondNo} ${p.name}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.recordDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="卤度 °Bé">
          <el-input-number v-model="form.brineDegree" :min="0" :max="32" :step="0.1" />
        </el-form-item>
        <el-form-item label="蒸发量 mm">
          <el-input-number v-model="form.evaporation" :min="0" :max="40" :step="0.1" />
        </el-form-item>
        <el-form-item label="降雨量 mm">
          <el-input-number v-model="form.rainfall" :min="0" :max="200" :step="0.1" />
        </el-form-item>
        <el-form-item label="气温 ℃">
          <el-input-number v-model="form.temperature" :min="-20" :max="50" :step="0.5" />
        </el-form-item>
        <el-form-item label="风力">
          <el-input-number v-model="form.windLevel" :min="0" :max="12" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const W = 1040
const H = 400
const PAD_L = 56
const PAD_R = 56
const PAD_T = 40
const PAD_B = 46

const series = ref([])
const loading = ref(false)
const selected = ref(null)
const dlg = ref(false)
const ponds = ref([])
const form = reactive({
  pondId: null,
  recordDate: new Date().toISOString().slice(0, 10),
  brineDegree: 25,
  evaporation: 6,
  rainfall: 0,
  temperature: 27,
  windLevel: 3,
  recorder: '吴气象'
})

const maxEvap = computed(() => {
  const m = Math.max(...series.value.map((s) => Number(s.evaporation || 0)), 10)
  return Math.ceil(m / 5) * 5
})
const maxRain = computed(() => {
  const m = Math.max(...series.value.map((s) => Number(s.rainfall || 0)), 5)
  return Math.ceil(m / 5) * 5
})

const gridLines = computed(() => {
  const max = maxEvap.value
  return [0, 0.25, 0.5, 0.75, 1].map((r) => ({
    value: (max * r).toFixed(0),
    y: PAD_T + (H - PAD_T - PAD_B) * (1 - r)
  }))
})

const points = computed(() => {
  const list = series.value
  const n = list.length || 1
  const innerW = W - PAD_L - PAD_R
  const step = n > 1 ? innerW / (n - 1) : innerW / 2
  const plotH = H - PAD_T - PAD_B
  return list.map((s, i) => {
    const x = PAD_L + step * i + (n > 1 ? 0 : innerW / 2)
    const evapY = PAD_T + plotH * (1 - Number(s.evaporation || 0) / maxEvap.value)
    const rainY = PAD_T + plotH * (1 - Number(s.rainfall || 0) / maxRain.value)
    return { ...s, x, evapY, rainY }
  })
})

const evapPoints = computed(() => points.value.map((p) => `${p.x},${p.evapY}`).join(' '))
const rainPoints = computed(() => points.value.map((p) => `${p.x},${p.rainY}`).join(' '))
const evapArea = computed(() => {
  if (!points.value.length) return ''
  const base = H - PAD_B
  return `${PAD_L},${base} ` + evapPoints.value + ` ${points.value[points.value.length - 1].x},${base}`
})

function shortDate(d) {
  return String(d || '').slice(5)
}

function pick(p) {
  selected.value = p
}

async function submit() {
  try {
    const res = await api.createWeather({ ...form })
    if (res && res.rainActive) {
      ElMessageBox.alert(
        `降雨 ${Number(res.rainfall || 0).toFixed(1)}mm 已登记（起算 ${fmtDT(res.stopStartedAt)}）。` +
        `同池 ${res.stoppedTaskCount || 0} 单未完成收盐作业已立即进入「雨情停工」，` +
        `最早 ${fmtDT(res.resumeAt)} 且卤度 ≥25°Bé、池板非需修时，由调度员在班次条确认复工。`,
        '雨后停工已触发',
        { confirmButtonText: '知道了', type: 'warning' }
      ).catch(() => {})
    } else {
      ElMessage.success('气象与卤度记录已登记')
    }
    dlg.value = false
    await load()
    if (res && res.rainActive) {
      window.dispatchEvent(new Event('salt:data-change'))
    }
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function fmtDT(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

async function load() {
  loading.value = true
  try {
    series.value = await api.dailyWeather(14)
    if (series.value.length && !selected.value) selected.value = series.value[series.value.length - 1]
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  selected.value = series.value.length ? series.value[series.value.length - 1] : null
  try {
    const p = await api.ponds()
    ponds.value = p || []
    if (ponds.value.length) form.pondId = ponds.value[0].id
  } catch (e) {
    /* ignore */
  }
})
</script>

<style scoped>
.wt-svg {
  width: 100%;
  height: auto;
  min-height: 380px;
  background: #fbfdfb;
  border: 1px solid #dfe6e4;
  border-radius: 6px;
}

.wt-point {
  cursor: pointer;
}

.wt-axis-text {
  font-size: 11px;
  fill: #6b7280;
}

.wt-axis-title {
  font-size: 11px;
  font-weight: 700;
  fill: #1b5e20;
}

.wt-rain-flag {
  font-size: 10px;
  fill: #1e88e5;
}

.wt-detail {
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px solid #dfe6e4;
  border-radius: 6px;
  background: #f7faf7;
}

.wt-detail-title {
  font-size: 14px;
  font-weight: 700;
  color: #1b5e20;
  margin-bottom: 8px;
}

.wt-detail-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
}

.wt-detail-grid div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.wt-detail-grid b {
  font-size: 15px;
  font-family: "SFMono-Regular", Consolas, monospace;
}
</style>
