<template>
  <div>
    <div class="sf-card">
      <div class="sf-card-title">
        收盐作业班次条
        <span class="sf-muted">横条按作业时长比例绘制 · 点条改作业状态 · 右侧环＝当日收盐 / 计划</span>
        <div style="margin-left: auto; display: flex; gap: 8px; align-items: center">
          <el-date-picker
            v-model="workDate"
            type="date"
            value-format="YYYY-MM-DD"
            size="small"
            style="width: 150px"
            @change="loadBoard"
          />
          <el-button size="small" type="primary" @click="openCreate">下达作业</el-button>
          <el-button size="small" @click="loadBoard">刷新</el-button>
        </div>
      </div>

      <div class="hs-wrap" v-loading="loading">
        <div class="hs-board">
          <!-- 雨情停工汇总条：任一单处于雨后停工即置顶展示 -->
          <el-alert
            v-if="rainStoppedCount > 0"
            type="warning"
            show-icon
            :closable="false"
            class="hs-rain-banner"
            :title="`同池 ${rainStoppedCount} 单收盐作业因有效降雨进入「雨情停工」，雨后 24 小时窗口内禁止开始与完成入账；点停工班次条由调度员确认复工`"
          />

          <!-- 时间刻度 -->
          <div class="hs-axis">
            <div class="hs-axis-label">班组 / 池号</div>
            <div class="hs-axis-track">
              <span v-for="h in hourTicks" :key="h" :style="{ left: ((h - 4) / 16) * 100 + '%' }">{{ h }}:00</span>
            </div>
          </div>

          <div v-for="group in groups" :key="group.team" class="hs-row">
            <div class="hs-row-label">
              <div class="hs-team">{{ group.team }}</div>
              <div class="sf-muted">{{ group.tasks.length }} 单 · {{ sumTons(group.tasks) }} 吨</div>
            </div>
            <div class="hs-row-track">
              <div
                v-for="t in group.tasks"
                :key="t.id"
                class="hs-bar"
                :class="['is-' + String(t.taskStatus).toLowerCase()]"
                :style="barStyle(t)"
                :title="barTitle(t)"
                @click="openStatus(t)"
              >
                <span class="hs-bar-pond">{{ t.pondNo }}</span>
                <span class="hs-bar-tons">{{ Number(t.tons || 0).toFixed(0) }}t</span>
                <span class="hs-bar-mode">{{ t.taskStatus === 'RAIN_STOP' ? '雨停 ' + fmtHM(t.stopStartedAt) : t.workModeName }}</span>
              </div>
            </div>
          </div>

          <el-empty v-if="!groups.length" description="当日暂无收盐作业单" />
        </div>

        <!-- 累计进度环 -->
        <div class="hs-ring-panel">
          <div class="hs-ring">
            <svg viewBox="0 0 180 180" width="180" height="180">
              <circle cx="90" cy="90" r="70" fill="none" stroke="#e3ece4" stroke-width="16" />
              <circle
                cx="90"
                cy="90"
                r="70"
                fill="none"
                stroke="#1b5e20"
                stroke-width="16"
                stroke-linecap="round"
                :stroke-dasharray="circumference"
                :stroke-dashoffset="ringOffset"
                transform="rotate(-90 90 90)"
              />
              <text x="90" y="82" text-anchor="middle" class="hs-ring-value">{{ doneTons }}</text>
              <text x="90" y="104" text-anchor="middle" class="hs-ring-label">/ {{ planTons }} 吨</text>
              <text x="90" y="126" text-anchor="middle" class="hs-ring-pct">{{ percent }}%</text>
            </svg>
          </div>
          <div class="sf-muted" style="text-align: center">当日已完成收盐（吨）</div>

          <div class="sf-card-title" style="margin-top: 16px">按班组汇总</div>
          <el-table :data="teamStats" size="small">
            <el-table-column prop="teamName" label="班组" width="80" />
            <el-table-column label="吨数" align="right">
              <template #default="{ row }">{{ Number(row.totalTons || 0).toFixed(1) }}</template>
            </el-table-column>
            <el-table-column label="工时" align="right" width="70">
              <template #default="{ row }">{{ Number(row.totalHours || 0).toFixed(1) }}h</template>
            </el-table-column>
            <el-table-column prop="taskCount" label="单数" width="60" align="center" />
          </el-table>
        </div>
      </div>
    </div>

    <!-- 调整作业状态 / 雨后复工确认 -->
    <el-dialog
      v-model="statusDlg"
      :title="current && current.taskStatus === 'RAIN_STOP' ? '雨后停工 · 复工确认' : '调整作业状态'"
      width="480px"
    >
      <div v-if="current">
        <div class="sf-muted" style="margin-bottom: 10px">
          {{ current.taskNo }} · {{ current.pondNo }} {{ current.pondName }} · {{ current.teamName }} ·
          {{ current.tons }} 吨 → {{ current.platformNo }}
        </div>

        <!-- 雨情停工：原因 / 起算 / 最早复工 + 三条件确认 -->
        <template v-if="current.taskStatus === 'RAIN_STOP'">
          <el-descriptions :column="1" size="small" border style="margin-bottom: 12px">
            <el-descriptions-item label="当前状态">
              <el-tag type="warning" size="small">雨情停工</el-tag>
              （停工前：{{ current.prevStatusName }}）
            </el-descriptions-item>
            <el-descriptions-item label="停工原因">{{ current.stopReason }}</el-descriptions-item>
            <el-descriptions-item>
              <template #label>停工起算</template>
              {{ fmtFull(current.stopStartedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="最早可复工">
              <b style="color: #1565c0">{{ fmtFull(current.resumeAt) }}</b>
            </el-descriptions-item>
          </el-descriptions>

          <div class="sf-card-title" style="margin: 4px 0 8px">复工条件（任一不满足即被拦截）</div>
          <div v-for="(b, i) in resumeChecklist" :key="i" class="hs-check" :class="b.ok ? 'is-ok' : 'is-no'">
            <span>{{ b.ok ? '✓' : '✕' }}</span>{{ b.text }}
          </div>

          <el-alert
            v-if="(current.resumeBlockers || []).length"
            type="error"
            show-icon
            :closable="false"
            style="margin-top: 10px"
            title="当前不能复工，拦截项："
          >
            <div v-for="(b, i) in current.resumeBlockers" :key="i">· {{ b }}</div>
          </el-alert>
          <el-alert
            v-else
            type="success"
            show-icon
            :closable="false"
            style="margin-top: 10px"
            title="三项条件均满足，可确认复工；确认后班次恢复为停工前状态并留下复工作业记录"
          />

          <el-input
            v-model="resumeOperator"
            size="small"
            placeholder="确认人（调度员）"
            style="margin-top: 12px"
          />
        </template>

        <!-- 普通状态调整 -->
        <template v-else>
          <el-radio-group v-model="nextStatus">
            <el-radio-button value="PLANNED">待作业</el-radio-button>
            <el-radio-button value="WORKING">作业中</el-radio-button>
            <el-radio-button value="DONE">已完成</el-radio-button>
            <el-radio-button value="CANCELED">已取消</el-radio-button>
          </el-radio-group>
          <div class="sf-muted" style="margin-top: 10px">
            置为「已完成」时，收盐量会计入去向坨台库存；雨后窗口内开始/完成会被拦截。
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="statusDlg = false">取消</el-button>
        <el-button
          v-if="current && current.taskStatus === 'RAIN_STOP'"
          type="primary"
          :disabled="!(current.canResume === true)"
          @click="submitResume"
        >确认复工</el-button>
        <el-button v-else type="primary" @click="submitStatus">保存</el-button>
      </template>
    </el-dialog>

    <!-- 下达收盐作业 -->
    <el-dialog v-model="createDlg" title="下达收盐作业" width="520px">
      <el-form :model="createForm" label-width="100px" size="small">
        <el-form-item label="池号">
          <el-select v-model="createForm.pondId" filterable style="width: 100%" placeholder="选择池子">
            <el-option
              v-for="p in ponds"
              :key="p.id"
              :label="`${p.pondNo} ${p.name}（${p.brineDegree}°Bé / ${p.boardStatusName}）`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="坨台去向">
          <el-select v-model="createForm.platformId" style="width: 100%">
            <el-option
              v-for="p in platforms"
              :key="p.id"
              :label="`${p.platformNo} ${p.name}（${p.saltTypeName} 存 ${p.stockTons}t）`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="班组">
          <el-select v-model="createForm.teamName" style="width: 100%">
            <el-option v-for="t in teams" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="开工 / 时长">
          <el-input-number v-model="createForm.startHour" :min="0" :max="23" :step="0.5" />
          <span class="sf-muted" style="margin: 0 8px">时</span>
          <el-input-number v-model="createForm.durationHours" :min="0.5" :max="16" :step="0.5" />
          <span class="sf-muted" style="margin-left: 8px">小时</span>
        </el-form-item>
        <el-form-item label="收盐吨数">
          <el-input-number v-model="createForm.tons" :min="1" :max="500" :step="1" />
        </el-form-item>
        <el-form-item label="作业方式">
          <el-radio-group v-model="createForm.workMode">
            <el-radio-button value="MACHINE">机械</el-radio-button>
            <el-radio-button value="MANUAL">人工</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="校验：卤度未达 25°Bé、雨后 24 小时内、池板「需修」均不允许下达；作业后排到降雨会立即自动停工，须满 24 小时且卤度、池板复核合格后由调度员确认复工"
      />
      <template #footer>
        <el-button @click="createDlg = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">下达</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'

const HOUR_START = 4
const HOUR_SPAN = 16
const R = 70
const circumference = 2 * Math.PI * R

const workDate = ref(today())
const board = ref({ tasks: [], teamStats: [], doneTons: 0, planTons: 320 })
const loading = ref(false)
const ponds = ref([])
const platforms = ref([])
const teams = ref(['甲班', '乙班', '丙班', '丁班'])

const statusDlg = ref(false)
const createDlg = ref(false)
const current = ref(null)
const nextStatus = ref('WORKING')
const resumeOperator = ref('王立新')
const createForm = reactive({
  pondId: null,
  platformId: null,
  teamName: '甲班',
  startHour: 6,
  durationHours: 4,
  tons: 60,
  workMode: 'MACHINE',
  operator: '王立新'
})

const hourTicks = [4, 6, 8, 10, 12, 14, 16, 18, 20]

const groups = computed(() => {
  const map = new Map()
  ;(board.value.tasks || []).forEach((t) => {
    if (!map.has(t.teamName)) map.set(t.teamName, { team: t.teamName, tasks: [] })
    map.get(t.teamName).tasks.push(t)
  })
  return [...map.values()]
})

const doneTons = computed(() => Number(board.value.doneTons || 0).toFixed(1))
const planTons = computed(() => Number(board.value.planTons || 320).toFixed(0))
const percent = computed(() => {
  const p = Number(board.value.planTons || 320)
  if (!p) return 0
  return Math.min(100, Math.round((Number(board.value.doneTons || 0) / p) * 100))
})
const ringOffset = computed(() => circumference * (1 - percent.value / 100))
const teamStats = computed(() => board.value.teamStats || [])
const rainStoppedCount = computed(() => Number(board.value.rainStoppedCount || 0))

/** 复工三条件清单，ok 与否由后端按最新雨情/卤度/池板逐条算好返回 */
const resumeChecklist = computed(() => {
  const t = current.value
  if (!t) return []
  const blockers = t.resumeBlockers || []
  const hit = (kw) => blockers.some((b) => b.includes(kw))
  return [
    { ok: !hit('雨后未满'), text: '最近一次有效降雨已满 24 小时（窗口按最新降雨重新起算）' },
    { ok: !hit('最新卤度'), text: '最新卤度仍不低于 25°Bé' },
    { ok: !hit('需修'), text: '池板状况不是「需修」' }
  ]
})

function fmtHM(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').slice(5, 16)
}

function fmtFull(v) {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 16)
}

function barTitle(t) {
  if (t.taskStatus === 'RAIN_STOP') {
    return `${t.pondNo} ${t.teamName} ${t.tons}吨 雨情停工｜${t.stopReason || ''}`
  }
  return `${t.pondNo} ${t.teamName} ${t.tons}吨 ${t.taskStatusName}`
}

function today() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function barStyle(t) {
  const left = ((Number(t.startHour || 0) - HOUR_START) / HOUR_SPAN) * 100
  const width = (Number(t.durationHours || 0) / HOUR_SPAN) * 100
  return { left: Math.max(0, left) + '%', width: Math.max(3, width) + '%' }
}

function sumTons(list) {
  return list.reduce((s, t) => s + Number(t.tons || 0), 0).toFixed(1)
}

function openStatus(t) {
  current.value = t
  nextStatus.value = t.taskStatus === 'RAIN_STOP' ? (t.prevStatus || 'PLANNED') : t.taskStatus
  resumeOperator.value = t.operator || '王立新'
  statusDlg.value = true
}

async function submitResume() {
  try {
    const saved = await api.resumeHarvest(current.value.id, resumeOperator.value || '调度员')
    ElMessage.success(`已确认复工：${saved.taskNo} 恢复为「${saved.taskStatusName}」`)
    statusDlg.value = false
    await loadBoard()
    window.dispatchEvent(new Event('salt:data-change'))
  } catch (e) {
    ElMessage.error(e.message)
    await loadBoard().catch(() => {})
  }
}

async function submitStatus() {
  try {
    await api.updateHarvestStatus(current.value.id, nextStatus.value)
    ElMessage.success('作业状态已更新为 ' + nextStatus.value)
    statusDlg.value = false
    await loadBoard()
  } catch (e) {
    ElMessage.error(e.message)
    // 可能是登记降雨同时发生导致状态被服务端改为雨情停工，刷新以服务端为准
    await loadBoard().catch(() => {})
  }
}

function openCreate() {
  createDlg.value = true
}

async function submitCreate() {
  try {
    const created = await api.createHarvest({ ...createForm, workDate: workDate.value })
    ElMessage.success('作业已下达：' + created.taskNo)
    createDlg.value = false
    await loadBoard()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadBoard() {
  loading.value = true
  try {
    board.value = await api.harvestBoard(workDate.value)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadBoard()
  try {
    const [p, pf] = await Promise.all([api.ponds(), api.platforms()])
    ponds.value = p || []
    platforms.value = pf || []
  } catch (e) {
    /* ignore */
  }
})
</script>

<style scoped>
.hs-wrap {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}

.hs-board {
  flex: 1;
  min-width: 0;
}

.hs-axis,
.hs-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.hs-axis-label,
.hs-row-label {
  width: 110px;
  flex: 0 0 110px;
  font-size: 12px;
  color: #6b7280;
}

.hs-team {
  font-size: 14px;
  font-weight: 700;
  color: #1b5e20;
}

.hs-axis-track,
.hs-row-track {
  position: relative;
  flex: 1;
  height: 26px;
  border-left: 1px solid #dfe6e4;
  border-right: 1px solid #dfe6e4;
  background: repeating-linear-gradient(90deg, #f6faf6 0 6.25%, #ffffff 6.25% 12.5%);
}

.hs-row-track {
  height: 42px;
  background: #fafcf9;
  border: 1px solid #e6ede7;
  border-radius: 4px;
}

.hs-axis-track span {
  position: absolute;
  top: 4px;
  font-size: 11px;
  color: #6b7280;
  transform: translateX(-50%);
}

.hs-bar {
  position: absolute;
  top: 4px;
  height: 34px;
  border-radius: 5px;
  color: #fff;
  font-size: 11px;
  padding: 2px 6px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}

.hs-bar:hover {
  filter: brightness(1.08);
}

.hs-bar.is-planned {
  background: linear-gradient(135deg, #78909c, #546e7a);
}

.hs-bar.is-working {
  background: linear-gradient(135deg, #43a047, #1b5e20);
}

.hs-bar.is-done {
  background: linear-gradient(135deg, #1b5e20, #0d3b12);
}

.hs-bar.is-canceled {
  background: linear-gradient(135deg, #b0bec5, #90a4ae);
  text-decoration: line-through;
}

/* 雨情停工：蓝灰雨丝底纹 + 警示描边，绝不与「作业中」同色 */
.hs-bar.is-rain_stop {
  background:
    repeating-linear-gradient(115deg, rgba(255, 255, 255, 0.18) 0 6px, rgba(255, 255, 255, 0) 6px 12px),
    linear-gradient(135deg, #5b7fa6, #345070);
  border: 1px dashed #ffd54f;
}

.hs-bar.is-rain_stop .hs-bar-mode {
  opacity: 1;
  color: #ffe9a8;
}

.hs-rain-banner {
  margin: 6px 0 12px;
}

.hs-check {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 13px;
  padding: 5px 8px;
  border-radius: 4px;
  margin-bottom: 4px;
}

.hs-check.is-ok {
  background: #edf7ed;
  color: #1b5e20;
}

.hs-check.is-no {
  background: #fdecea;
  color: #b71c1c;
}

.hs-bar-pond {
  font-weight: 700;
  font-size: 12px;
}

.hs-bar-tons {
  font-family: "SFMono-Regular", Consolas, monospace;
}

.hs-bar-mode {
  opacity: 0.8;
}

.hs-ring-panel {
  width: 260px;
  flex: 0 0 260px;
  border-left: 1px solid #e6ede7;
  padding-left: 16px;
}

.hs-ring {
  display: flex;
  justify-content: center;
}

.hs-ring-value {
  font-size: 24px;
  font-weight: 700;
  fill: #1b5e20;
  font-family: "SFMono-Regular", Consolas, monospace;
}

.hs-ring-label {
  font-size: 13px;
  fill: #6b7280;
}

.hs-ring-pct {
  font-size: 14px;
  font-weight: 700;
  fill: #f9a825;
}
</style>
