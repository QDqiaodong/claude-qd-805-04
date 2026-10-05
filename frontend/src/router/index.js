import { computed, reactive } from 'vue'

import PondMap from '../views/PondMap.vue'
import HarvestShift from '../views/HarvestShift.vue'
import WeatherTrend from '../views/WeatherTrend.vue'
import PlatformStock from '../views/PlatformStock.vue'
import DispatchBoard from '../views/DispatchBoard.vue'

export const routes = [
  {
    path: '/ponds',
    key: 'ponds',
    name: '盐田平面 · 卤度热力',
    desc: '三条滩带 · 按卤度深浅着色 · 抽屉登记',
    component: PondMap
  },
  {
    path: '/harvest',
    key: 'harvest',
    name: '收盐作业班次条',
    desc: '班次条按时长比例铺开 · 当日吨数进度环',
    component: HarvestShift
  },
  {
    path: '/weather',
    key: 'weather',
    name: '气象蒸发量 · 降雨标注',
    desc: '近 14 天蒸发量与降雨量 · 雨日蓝标',
    component: WeatherTrend
  },
  {
    path: '/platforms',
    key: 'platforms',
    name: '盐坨库存立方体',
    desc: '3D 立方体按库存缩放 · 登记出仓',
    component: PlatformStock
  },
  {
    path: '/dispatch',
    key: 'dispatch',
    name: '派工任务调度',
    desc: '待派 → 进行中 → 已完成 · 合法下一步 · 异常历史留痕',
    component: DispatchBoard
  }
]

function parseQuery(search) {
  const query = {}
  const raw = (search || '').replace(/^\?/, '')
  if (!raw) return query
  raw.split('&').forEach((pair) => {
    if (!pair) return
    const idx = pair.indexOf('=')
    const k = idx < 0 ? pair : pair.slice(0, idx)
    const v = idx < 0 ? '' : pair.slice(idx + 1)
    query[decodeURIComponent(k)] = decodeURIComponent(v)
  })
  return query
}

function toSearch(query) {
  const keys = Object.keys(query || {}).filter((k) => query[k] !== undefined && query[k] !== null && query[k] !== '')
  if (!keys.length) return ''
  return '?' + keys.map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(query[k])}`).join('&')
}

export const currentLocation = reactive({
  path: normalize(window.location.pathname),
  query: parseQuery(window.location.search)
})

function normalize(path) {
  if (!path || path === '/' || path === '/index.html') return routes[0].path
  return routes.some((r) => r.path === path) ? path : routes[0].path
}

function sync() {
  currentLocation.path = normalize(window.location.pathname)
  currentLocation.query = parseQuery(window.location.search)
}

window.addEventListener('popstate', sync)

export function navigate(path, query = {}) {
  const target = normalize(path)
  const search = toSearch(query)
  if (window.location.pathname !== target || window.location.search !== search) {
    window.history.pushState({}, '', target + search)
  }
  sync()
}

export const currentRoute = computed(
  () => routes.find((r) => r.path === currentLocation.path) || routes[0]
)

export default routes
