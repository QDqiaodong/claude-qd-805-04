<template>
  <div>
    <div class="sf-card">
      <div class="sf-card-title">
        盐田平面图 · 卤度热力
        <span class="sf-muted">点击任一池格 → 右侧滑出抽屉登记卤度 / 卤水深度 / 池板状况</span>
      </div>

      <div v-loading="loading">
        <svg :viewBox="`0 0 ${layout.width} ${layout.height}`" class="sf-map" @click="onSvgClick">
          <defs>
            <pattern id="sfWater" width="8" height="8" patternUnits="userSpaceOnUse">
              <path d="M0 8 L8 0" stroke="rgba(255,255,255,.18)" stroke-width="1" />
            </pattern>
          </defs>

          <g v-for="band in layout.bands" :key="band.area.id">
            <!-- 池区条带 -->
            <polygon
              :points="bandPoly(band)"
              :fill="bandFill(band.area.areaType)"
              stroke="#c8d6c9"
              stroke-width="1"
            />
            <text :x="126" :y="band.y + 20" text-anchor="end" class="sf-map-area">{{ band.area.label }}</text>
            <text :x="126" :y="band.y + 38" text-anchor="end" class="sf-map-area-sub">
              {{ band.area.areaTypeName }} · {{ band.area.areaMu }} 亩
            </text>
            <text :x="126" :y="band.y + 54" text-anchor="end" class="sf-map-area-sub">
              {{ band.area.tideGate }}
            </text>

            <!-- 池格 -->
            <g
              v-for="cell in band.cells"
              :key="cell.pond.id"
              class="sf-map-cell"
              @click.stop="select(cell.pond)"
            >
              <polygon
                :points="cellPoly(cell)"
                :fill="brineColor(cell.pond.brineDegree)"
                :stroke="cell.pond.id === activeId ? '#f9a825' : '#ffffff'"
                :stroke-width="cell.pond.id === activeId ? 3 : 1.5"
              />
              <polygon :points="cellPoly(cell)" fill="url(#sfWater)" />
              <text
                :x="cell.x + cell.w / 2"
                :y="cell.y + 34"
                text-anchor="middle"
                :class="textClass(cell.pond.brineDegree)"
              >{{ cell.pond.pondNo }}</text>
              <text
                :x="cell.x + cell.w / 2"
                :y="cell.y + 56"
                text-anchor="middle"
                :class="textClass(cell.pond.brineDegree) + ' sf-map-deg'"
              >{{ Number(cell.pond.brineDegree || 0).toFixed(1) }}°Bé</text>
              <text
                :x="cell.x + cell.w / 2"
                :y="cell.y + 74"
                text-anchor="middle"
                :class="textClass(cell.pond.brineDegree) + ' sf-map-sub'"
              >{{ cell.pond.rainActive ? `雨情停工 · ${fmtHM(cell.pond.rainAt)}起` : `${cell.pond.pondStatusName} · 深 ${cell.pond.brineDepth}cm` }}</text>
              <!-- 雨后窗口蓝色雨标 -->
              <g v-if="cell.pond.rainActive">
                <circle
                  :cx="cell.x + 14"
                  :cy="cell.y + 14"
                  r="7"
                  fill="#1e88e5"
                />
                <text
                  :x="cell.x + 14"
                  :y="cell.y + 18"
                  text-anchor="middle"
                  style="font-size: 10px; font-weight: 700; fill: #fff; pointer-events: none"
                >雨</text>
              </g>
              <circle
                v-if="cell.pond.boardStatus === 'REPAIR'"
                :cx="cell.x + cell.w - 14"
                :cy="cell.y + 14"
                r="6"
                fill="#e53935"
              />
            </g>
          </g>
        </svg>

        <!-- 色带图例 -->
        <div class="sf-legend">
          <span class="sf-muted">卤度 °Bé</span>
          <div class="sf-legend-bar">
            <div
              v-for="item in BRINE_LEGEND"
              :key="item.min"
              class="sf-legend-cell"
              :style="{ background: item.color }"
            >
              <span>{{ item.label }}</span>
            </div>
          </div>
          <span class="sf-muted">门槛 25°Bé 方可下达收盐作业（红点＝池板需修 · 蓝雨标＝雨后停工窗口内）</span>
        </div>
      </div>
    </div>

    <div class="pm-grid">
      <div class="sf-card">
        <div class="sf-card-title">按池区统计卤度均值</div>
        <el-table :data="areaStats" size="small" stripe>
          <el-table-column prop="areaName" label="池区" />
          <el-table-column prop="areaType" label="类型" width="90">
            <template #default="{ row }">{{ areaTypeName(row.areaType) }}</template>
          </el-table-column>
          <el-table-column prop="pondCount" label="池数" width="70" align="center" />
          <el-table-column label="卤度均值 °Bé" width="130" align="right">
            <template #default="{ row }">
              <b :style="{ color: brineColor(row.avgBrine) }">{{ Number(row.avgBrine || 0).toFixed(2) }}</b>
            </template>
          </el-table-column>
          <el-table-column prop="totalMu" label="面积（亩）" width="110" align="right" />
        </el-table>
      </div>
    </div>

    <!-- 右侧抽屉：登记卤度 -->
    <el-drawer v-model="drawer" :title="active ? `${active.pondNo} ${active.name} · 卤度登记` : '卤度登记'" size="420px">
      <template v-if="active">
        <el-descriptions :column="1" size="small" border style="margin-bottom: 14px">
          <el-descriptions-item label="所属池区">{{ active.areaName }}（{{ active.areaTypeName }}）</el-descriptions-item>
          <el-descriptions-item label="面积">{{ active.areaMu }} 亩</el-descriptions-item>
          <el-descriptions-item label="当前卤度">{{ active.brineDegree }} °Bé</el-descriptions-item>
          <el-descriptions-item label="卤水深度">{{ active.brineDepth }} cm</el-descriptions-item>
          <el-descriptions-item label="池板状况">{{ active.boardStatusName }}</el-descriptions-item>
          <el-descriptions-item label="作业状态">
            {{ active.pondStatusName }}
            <el-tag v-if="active.rainActive" type="warning" size="small" style="margin-left: 6px">
              雨情停工 × {{ active.rainStoppedCount || 0 }}
            </el-tag>
          </el-descriptions-item>
          <template v-if="active.rainActive">
            <el-descriptions-item label="停工起算">{{ fmtFull(active.rainAt) }}（降雨 {{ Number(active.rainfall || 0).toFixed(1) }}mm）</el-descriptions-item>
            <el-descriptions-item label="最早可复工">
              <b style="color: #1565c0">{{ fmtFull(active.rainResumeAt) }}</b>
            </el-descriptions-item>
          </template>
        </el-descriptions>

        <el-alert
          v-if="active.rainActive"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 12px"
          :title="`该池处于雨后 24 小时停工窗口：${active.rainStoppedCount || 0} 单未完成作业已自动停工，最早 ${fmtFull(active.rainResumeAt)} 经调度员确认后方可复工`"
        />

        <el-form label-width="96px" size="default">
          <el-form-item label="卤度 °Bé">
            <el-slider v-model="form.brineDegree" :min="0" :max="32" :step="0.1" show-input :show-input-controls="false" />
          </el-form-item>
          <el-form-item label="卤水深度 cm">
            <el-input-number v-model="form.brineDepth" :min="0" :max="120" :step="1" />
          </el-form-item>
          <el-form-item label="池板状况">
            <el-radio-group v-model="form.boardStatus">
              <el-radio-button value="GOOD">完好</el-radio-button>
              <el-radio-button value="LEAK">渗漏</el-radio-button>
              <el-radio-button value="REPAIR">需修</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="记录人">
            <el-input v-model="form.operator" placeholder="测卤员" />
          </el-form-item>
        </el-form>

        <el-alert
          v-if="form.brineDegree < 25"
          type="warning"
          :closable="false"
          show-icon
          :title="`卤度 ${form.brineDegree.toFixed(1)}°Bé 未达 25°Bé 门槛，登记后池子仍不可下达收盐作业`"
          style="margin-bottom: 12px"
        />
        <el-alert
          v-else
          type="success"
          :closable="false"
          show-icon
          title="卤度已达标，可到「收盐作业班次条」下达作业"
          style="margin-bottom: 12px"
        />

        <el-button type="primary" style="width: 100%" @click="submitBrine">提交登记</el-button>

        <div class="sf-card-title" style="margin-top: 18px">本池最近作业记录</div>
        <el-table :data="logs" size="small" max-height="220">
          <el-table-column prop="logTime" label="时间" width="150">
            <template #default="{ row }">{{ fmt(row.logTime) }}</template>
          </el-table-column>
          <el-table-column prop="logTypeName" label="类型" width="80" />
          <el-table-column prop="content" label="内容" />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { BRINE_LEGEND, api, brineColor } from '../api'
import { currentLocation } from '../router'

const emit = defineEmits(['brine-change'])

const CELL_W = 168
const CELL_H = 92
const GAP_X = 18
const GAP_Y = 14
const LEFT = 140
const TOP = 18
const BAND_GAP = 28

const areas = ref([])
const areaStats = ref([])
const loading = ref(false)
const drawer = ref(false)
const active = ref(null)
const logs = ref([])
const form = reactive({ brineDegree: 0, brineDepth: 0, boardStatus: 'GOOD', operator: '周测卤' })

const activeId = computed(() => active.value?.id || null)

const layout = computed(() => {
  let y = TOP
  const bands = []
  for (const a of areas.value) {
    const pods = a.ponds || []
    if (!pods.length) continue
    const cols = Math.max(...pods.map((p) => p.gridCol || 0)) + 1
    const rows = Math.max(...pods.map((p) => p.gridRow || 0)) + 1
    const w = cols * CELL_W + (cols - 1) * GAP_X
    const h = rows * CELL_H + (rows - 1) * GAP_Y
    const cells = pods.map((p) => ({
      pond: p,
      x: LEFT + (p.gridCol || 0) * (CELL_W + GAP_X),
      y: y + (p.gridRow || 0) * (CELL_H + GAP_Y),
      w: CELL_W,
      h: CELL_H
    }))
    bands.push({ area: a, x: LEFT, y, w, h, cells })
    y += h + BAND_GAP
  }
  const maxW = bands.length ? Math.max(...bands.map((b) => b.w)) : 600
  return { bands, width: LEFT + maxW + 30, height: y + 6 }
})

function bandPoly(band) {
  const s = 10
  return `${band.x + s},${band.y} ${band.x + band.w},${band.y} ${band.x + band.w - s},${band.y + band.h} ${band.x},${band.y + band.h}`
}

function cellPoly(cell) {
  const s = 7
  const { x, y, w, h } = cell
  return `${x + s},${y} ${x + w},${y} ${x + w - s},${y + h} ${x},${y + h}`
}

function bandFill(type) {
  if (type === 'EVAP') return '#eef7ef'
  if (type === 'REG') return '#e6f2ea'
  return '#dfeee4'
}

function textClass(degree) {
  return Number(degree || 0) >= 16 ? 'sf-map-text is-light' : 'sf-map-text'
}

function areaTypeName(t) {
  return { EVAP: '蒸发池区', REG: '调节池区', CRYST: '结晶池区' }[t] || t
}

function fmt(v) {
  return v ? String(v).replace('T', ' ') : ''
}

function fmtHM(v) {
  return v ? String(v).replace('T', ' ').slice(5, 16) : ''
}

function fmtFull(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '-'
}

function onSvgClick() {
  // 点空白处不关闭抽屉，避免误触
}

function select(pond) {
  active.value = pond
  form.brineDegree = Number(pond.brineDegree || 0)
  form.brineDepth = Number(pond.brineDepth || 0)
  form.boardStatus = pond.boardStatus || 'GOOD'
  drawer.value = true
  loadLogs(pond.id)
}

async function loadLogs(pondId) {
  try {
    logs.value = await api.pondLogs(pondId)
  } catch (e) {
    logs.value = []
  }
}

async function submitBrine() {
  try {
    const saved = await api.registerBrine(active.value.id, {
      brineDegree: form.brineDegree,
      brineDepth: form.brineDepth,
      boardStatus: form.boardStatus,
      operator: form.operator
    })
    ElMessage.success('卤度已登记：' + saved.brineDegree + '°Bé，状态 ' + saved.pondStatusName)
    active.value = saved
    await loadAll()
    emit('brine-change')
    window.dispatchEvent(new Event('salt:data-change'))
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadAll() {
  loading.value = true
  try {
    const [tree, stats] = await Promise.all([api.pondTree(), api.areaStats()])
    areas.value = tree || []
    areaStats.value = stats || []
    if (active.value) {
      const flat = areas.value.flatMap((a) => a.ponds || [])
      const hit = flat.find((p) => p.id === active.value.id)
      if (hit) active.value = hit
    }
  } finally {
    loading.value = false
  }
}

watch(
  () => currentLocation.query.pond,
  (val) => {
    const id = Number(val)
    if (!id) return
    const flat = areas.value.flatMap((a) => a.ponds || [])
    const hit = flat.find((p) => p.id === id)
    if (hit) select(hit)
  }
)

onMounted(async () => {
  await loadAll()
  const id = Number(currentLocation.query.pond)
  if (id) {
    const flat = areas.value.flatMap((a) => a.ponds || [])
    const hit = flat.find((p) => p.id === id)
    if (hit) select(hit)
  }
})
</script>

<style scoped>
.sf-map {
  width: 100%;
  height: auto;
  min-height: 420px;
  background: #fbfdfb;
  border: 1px solid #dfe6e4;
  border-radius: 6px;
}

.sf-map-cell {
  cursor: pointer;
}

.sf-map-cell:hover polygon {
  filter: brightness(1.08);
}

.sf-map-area {
  font-size: 15px;
  font-weight: 700;
  fill: #1b5e20;
}

.sf-map-area-sub {
  font-size: 11px;
  fill: #6b7280;
}

.sf-map-text {
  font-size: 14px;
  font-weight: 700;
  fill: #14311a;
  pointer-events: none;
}

.sf-map-text.is-light {
  fill: #ffffff;
}

.sf-map-deg {
  font-size: 15px;
}

.sf-map-sub {
  font-size: 11px;
  font-weight: 500;
}

.sf-legend {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.sf-legend-bar {
  display: flex;
  border: 1px solid #dfe6e4;
  border-radius: 4px;
  overflow: hidden;
}

.sf-legend-cell {
  width: 52px;
  height: 24px;
  font-size: 11px;
  color: #14311a;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pm-grid {
  margin-top: 14px;
}
</style>
