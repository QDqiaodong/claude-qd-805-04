<template>
  <div class="sf-shell">
    <!-- 左侧常驻池区树：池区 → 池号 -->
    <aside class="sf-side">
      <div class="sf-brand">
        <div class="sf-brand-title">海盐盐场 · 结晶池与收盐作业管理</div>
        <div class="sf-brand-sub">池区 → 池子 → 卤度/作业 → 坨台库存</div>
      </div>

      <div class="sf-side-scroll" v-loading="loadingTree">
        <div class="sf-area" v-for="area in tree" :key="area.id">
          <div class="sf-area-head" @click="toggle(area.id)">
            <span class="sf-area-caret">{{ open[area.id] ? '▾' : '▸' }}</span>
            <span class="sf-area-name">{{ area.code }} {{ area.label }}</span>
            <span class="sf-area-tag">{{ area.areaTypeName }}</span>
          </div>
          <div class="sf-area-meta">
            {{ area.areaMu }} 亩 · 纳潮口 {{ area.tideGate }} · 责任人 {{ area.ownerName }}
          </div>
          <div class="sf-ponds" v-show="open[area.id] !== false">
            <div
              v-for="pond in area.ponds"
              :key="pond.id"
              class="sf-pond"
              :class="{ 'is-active': pond.id === activePondId }"
              @click="pickPond(pond.id)"
            >
              <span class="sf-pond-dot" :style="{ background: statusColor(pond.pondStatus) }"></span>
              <span v-if="pond.rainActive" class="sf-pond-rain" title="雨后停工窗口内">雨</span>
              <span class="sf-pond-no">{{ pond.pondNo }}</span>
              <span class="sf-pond-name">{{ pond.name }}</span>
              <span class="sf-pond-deg">{{ Number(pond.brineDegree || 0).toFixed(1) }}°</span>
            </div>
          </div>
        </div>
      </div>

      <div class="sf-side-foot">
        待收池 {{ overview.waitingPondCount || 0 }} 座 · 修池 {{ overview.repairingPondCount || 0 }} 座<br />
        今日蒸发 {{ overview.todayEvaporation || 0 }}mm · 降雨 {{ overview.todayRainfall || 0 }}mm
      </div>
    </aside>

    <!-- 右侧主体 -->
    <div class="sf-main">
      <div class="sf-topbar">
        <div class="sf-topbar-row">
          <div>
            <div class="sf-page-title">{{ currentRoute.name }}</div>
            <div class="sf-page-desc">{{ currentRoute.desc }}</div>
          </div>
          <div class="sf-kpis">
            <div class="sf-kpi">
              <div class="sf-kpi-value">{{ overview.pondCount || 0 }}</div>
              <div class="sf-kpi-label">池子 / {{ overview.areaCount || 0 }} 池区</div>
            </div>
            <div class="sf-kpi">
              <div class="sf-kpi-value">{{ tons(overview.todayTons) }}</div>
              <div class="sf-kpi-label">今日收盐 / 计划 {{ tons(overview.planTons) }} 吨</div>
            </div>
            <div class="sf-kpi">
              <div class="sf-kpi-value">{{ tons(overview.stockTons) }}</div>
              <div class="sf-kpi-label">坨台库存（吨）</div>
            </div>
            <div class="sf-kpi">
              <div class="sf-kpi-value">{{ overview.waitingPondCount || 0 }}</div>
              <div class="sf-kpi-label">待收池（座）</div>
            </div>
          </div>
        </div>

        <!-- 页面切换 -->
        <div class="sf-tabs">
          <div
            v-for="r in routes"
            :key="r.path"
            class="sf-tab"
            :class="{ 'is-active': r.path === currentLocation.path }"
            @click="navigate(r.path)"
          >
            {{ r.name }}
          </div>
        </div>

        <!-- 顶部池号 chips 条：可横向滚动，点 chip 直接定位到该池 -->
        <div class="sf-chips">
          <div
            v-for="pond in ponds"
            :key="pond.id"
            class="sf-chip"
            :class="{ 'is-active': pond.id === activePondId }"
            :style="{ borderLeft: '4px solid ' + brineColor(pond.brineDegree) }"
            @click="pickPond(pond.id)"
          >
            <span class="sf-chip-no">{{ pond.pondNo }}</span>
            <span class="sf-chip-deg">{{ Number(pond.brineDegree || 0).toFixed(1) }}°Bé</span>
            <span
              class="sf-chip-status"
              :style="{ background: statusColor(pond.pondStatus) + '22', color: statusColor(pond.pondStatus) }"
            >{{ pond.pondStatusName }}</span>
            <span v-if="pond.rainActive" class="sf-chip-rain" title="雨后 24 小时停工窗口内">
              雨停×{{ pond.rainStoppedCount || 0 }}
            </span>
          </div>
        </div>
      </div>

      <div class="sf-body">
        <component :is="currentRoute.component" :key="currentRoute.path" @brine-change="loadAll" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { api, brineColor, POND_STATUS_COLOR } from './api'
import { currentLocation, currentRoute, navigate, routes } from './router'

const tree = ref([])
const ponds = ref([])
const overview = ref({})
const loadingTree = ref(false)
const open = reactive({})

const activePondId = computed(() => {
  const id = Number(currentLocation.query.pond)
  return Number.isFinite(id) && id > 0 ? id : null
})

function statusColor(status) {
  return POND_STATUS_COLOR[status] || '#9e9e9e'
}

function tons(v) {
  return Number(v || 0).toFixed(0)
}

function toggle(areaId) {
  open[areaId] = open[areaId] === false
}

function pickPond(pondId) {
  navigate('/ponds', { pond: pondId })
}

async function loadAll() {
  loadingTree.value = true
  try {
    const [t, p, o] = await Promise.all([api.pondTree(), api.ponds(), api.overview()])
    tree.value = t || []
    ponds.value = p || []
    overview.value = o || {}
  } catch (e) {
    tree.value = []
    ponds.value = []
    overview.value = {}
  } finally {
    loadingTree.value = false
  }
}

onMounted(loadAll)

// 降雨登记 / 雨后复工确认后，池区树、chips、概览与当前页一并按服务端状态刷新
onMounted(() => window.addEventListener('salt:data-change', loadAll))
onBeforeUnmount(() => window.removeEventListener('salt:data-change', loadAll))
</script>
