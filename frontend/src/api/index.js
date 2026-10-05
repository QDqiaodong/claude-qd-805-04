import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const data = err?.response?.data
    const msg = data?.message || err.message || '请求失败'
    const e = new Error(msg)
    // 409 并发冲突：后端回带任务最新快照，失败方据此直接看到最新状态与生效时间
    e.status = err?.response?.status
    e.snapshot = data?.data || null
    return Promise.reject(e)
  }
)

export const unwrap = (res) => res?.data

/** 卤度 → 热力色（0~30 °Bé，越深越绿） */
export function brineColor(degree) {
  const v = Number(degree || 0)
  if (v >= 28) return '#0d3b12'
  if (v >= 25) return '#1b5e20'
  if (v >= 22) return '#2e7d32'
  if (v >= 19) return '#43a047'
  if (v >= 16) return '#7cb342'
  if (v >= 13) return '#9ccc65'
  if (v >= 10) return '#c5e1a5'
  if (v >= 6) return '#dcedc8'
  return '#f1f8e9'
}

export const BRINE_LEGEND = [
  { min: 28, color: '#0d3b12', label: '≥28' },
  { min: 25, color: '#1b5e20', label: '25' },
  { min: 22, color: '#2e7d32', label: '22' },
  { min: 19, color: '#43a047', label: '19' },
  { min: 16, color: '#7cb342', label: '16' },
  { min: 13, color: '#9ccc65', label: '13' },
  { min: 10, color: '#c5e1a5', label: '10' },
  { min: 6, color: '#dcedc8', label: '6' },
  { min: 0, color: '#f1f8e9', label: '<6' }
]

export const POND_STATUS_COLOR = {
  SUN: '#7cb342',
  CRYST: '#43a047',
  WAIT: '#f9a825',
  REPAIR: '#e53935'
}

export const api = {
  // ---- 池区 / 池子 ----
  pondTree: () => http.get('/ponds/tree').then(unwrap),
  ponds: () => http.get('/ponds').then(unwrap),
  areaStats: () => http.get('/ponds/area-stats').then(unwrap),
  pondDetail: (id) => http.get(`/ponds/${id}`).then(unwrap),
  pondLogs: (id) => http.get(`/ponds/${id}/logs`).then(unwrap),
  registerBrine: (id, data) => http.post(`/ponds/${id}/brine`, data).then(unwrap),

  // ---- 气象 / 卤度 ----
  dailyWeather: (days = 14) => http.get('/weather/daily', { params: { days } }).then(unwrap),
  weatherByPond: (pondId) => http.get(`/weather/pond/${pondId}`).then(unwrap),
  recentRain: (pondId) => http.get(`/weather/pond/${pondId}/rain`).then(unwrap),
  createWeather: (data) => http.post('/weather', data).then(unwrap),

  // ---- 收盐作业 ----
  harvestBoard: (date) => http.get('/harvest/board', { params: date ? { date } : {} }).then(unwrap),
  createHarvest: (data) => http.post('/harvest', data).then(unwrap),
  updateHarvestStatus: (id, taskStatus) => http.put(`/harvest/${id}/status`, { taskStatus }).then(unwrap),
  // 雨后复工确认（满 24h + 卤度≥25°Bé + 池板非需修；不满足时后端逐条返回拦截项）
  resumeHarvest: (id, operator) => http.post(`/harvest/${id}/resume`, { operator }).then(unwrap),

  // ---- 坨台 ----
  platforms: () => http.get('/platforms').then(unwrap),
  platformStockStats: () => http.get('/platforms/stock-stats').then(unwrap),

  // ---- 出仓：登记预占 → 复核确认 → 冲正重记 ----
  outboundRegister: (platformId, data) => http.post(`/outbounds/platform/${platformId}`, data).then(unwrap),
  outboundList: (status) => http.get('/outbounds', { params: status ? { status } : {} }).then(unwrap),
  outboundByPlatform: (id) => http.get(`/outbounds/platform/${id}`).then(unwrap),
  outboundConfirm: (id, data) => http.post(`/outbounds/${id}/confirm`, data).then(unwrap),
  outboundReject: (id, data) => http.post(`/outbounds/${id}/reject`, data).then(unwrap),
  outboundCorrectPlate: (id, data) => http.post(`/outbounds/${id}/correct-plate`, data).then(unwrap),
  outboundReverse: (id, data) => http.post(`/outbounds/${id}/reverse-rerecord`, data).then(unwrap),
  outboundCorrections: (id) => http.get(`/outbounds/${id}/corrections`).then(unwrap),

  // ---- 多态作业记录 / 调度 / 概览 ----
  fieldLogs: (targetType) => http.get('/field-logs', { params: targetType ? { targetType } : {} }).then(unwrap),
  logOptions: () => http.get('/field-logs/options').then(unwrap),
  createFieldLog: (data) => http.post('/field-logs', data).then(unwrap),
  dispatchTasks: () => http.get('/dispatch').then(unwrap),
  dispatchDetail: (id) => http.get(`/dispatch/${id}`).then(unwrap),
  dispatchHistory: (id) => http.get(`/dispatch/${id}/history`).then(unwrap),
  createDispatch: (data) => http.post('/dispatch', data).then(unwrap),
  // 生命周期推进：expectedStatus 为页面快照（并发前置条件），不传时后端仅做状态机强校验；
  // 返回完整 {ok,message,data}，便于区分「已推进」与「幂等未变化」
  advanceDispatch: (id, taskStatus, expectedStatus, operator) =>
    http.put(`/dispatch/${id}/status`, { taskStatus, expectedStatus, operator }),
  repairDispatch: (id, data) => http.post(`/dispatch/${id}/repair`, data).then(unwrap),
  overview: () => http.get('/overview').then(unwrap),
  equipments: () => http.get('/equipments').then(unwrap)
}

export default http
