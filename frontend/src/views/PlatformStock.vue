<template>
  <div>
    <!-- 库存口径与当前影响说明 -->
    <el-alert type="success" :closable="false" show-icon style="margin-bottom: 14px">
      <template #title>
        <b>库存口径：登记预占，复核确认后才实扣</b>
      </template>
      <div class="ps-policy">
        司磅员登记出仓后，吨数先<b>预占冻结</b>（可用库存立即减少），<b>账面库存不动</b>；
        复核员确认无误后才<b>实扣</b>账面，发运汇总只统计「已确认」。
        待复核单发现错误：直接<b>作废</b>释放预占即可，账面从未变化、无账实差异；
        已确认单不得删除——仅车牌错误可<b>直接更正</b>（坨台/吨数不变），
        吨数、坨台或客户错误走<b>冲正重记</b>：库存先原子恢复、再按新单扣减，禁止重复冲正。
      </div>
    </el-alert>

    <div class="sf-card">
      <div class="sf-card-title">
        盐坨库存立方体
        <span class="sf-muted">立方体按账面库存缩放 · 卡片显示 库存 / 预占中 / 可用 · 点卡片登记出仓</span>
        <div style="margin-left: auto"><el-button size="small" @click="load">刷新</el-button></div>
      </div>

      <div class="ps-grid" v-loading="loading">
        <div
          v-for="p in platforms"
          :key="p.id"
          class="ps-card"
          :class="{ 'is-active': current && current.id === p.id }"
        >
          <!-- CSS 3D 盐坨立方体 -->
          <div class="ps-stage" @click="openOutbound(p)">
            <div class="ps-cube" :style="cubeStyle(p)">
              <div class="ps-face ps-face-top" :style="faceStyle(p, 'top')"></div>
              <div class="ps-face ps-face-front" :style="faceStyle(p, 'front')"></div>
              <div class="ps-face ps-face-right" :style="faceStyle(p, 'right')"></div>
            </div>
            <div class="ps-shadow" :style="{ width: cubeSize(p) + 'px' }"></div>
          </div>

          <div class="ps-info">
            <div class="ps-head">
              <span class="ps-no">{{ p.platformNo }}</span>
              <el-tag size="small" :type="saltTag(p.saltType)">{{ p.saltTypeName }}</el-tag>
              <el-tag v-if="p.pendingCount > 0" size="small" type="warning" style="margin-left: auto">
                待复核 {{ p.pendingCount }}
              </el-tag>
            </div>
            <div class="ps-name">{{ p.name }} · {{ p.pileShape }} · {{ p.location }}</div>
            <div class="ps-metrics">
              <div><span class="sf-muted">账面库存</span><b>{{ n1(p.stockTons) }} t</b></div>
              <div><span class="sf-muted">预占中</span>
                <b :class="{ 'ps-reserved': Number(p.reservedTons || 0) > 0 }">{{ n1(p.reservedTons) }} t</b>
              </div>
              <div><span class="sf-muted">可用库存</span><b class="ps-available">{{ n1(p.availableTons) }} t</b></div>
              <div><span class="sf-muted">累计实发</span><b>{{ n1(p.outTons) }} t</b></div>
            </div>
            <el-progress
              :percentage="fillPercent(p)"
              :stroke-width="8"
              :color="fillPercent(p) > 85 ? '#e53935' : '#1b5e20'"
            />
            <el-button size="small" type="primary" style="width: 100%; margin-top: 8px" @click="openOutbound(p)">
              登记出仓（预占）
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 出仓复核队列 -->
    <div class="sf-card" style="margin-top: 14px">
      <div class="sf-card-title">
        出仓复核
        <span class="sf-muted">待复核单只预占未实扣 · 确认后账面减吨 · 作废释放预占</span>
        <el-radio-group v-model="queueFilter" size="small" style="margin-left: auto" @change="reloadQueue">
          <el-radio-button label="PENDING">待复核 {{ pendingTotal }}</el-radio-button>
          <el-radio-button label="">全部流水</el-radio-button>
        </el-radio-group>
      </div>
      <el-table :data="queue" size="small" stripe v-loading="queueLoading" empty-text="暂无待复核单">
        <el-table-column label="#" width="56">
          <template #default="{ row }">#{{ row.id }}</template>
        </el-table-column>
        <el-table-column label="状态" width="86">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="坨台" width="120">
          <template #default="{ row }">{{ row.platformNo }} {{ row.platformName }}</template>
        </el-table-column>
        <el-table-column prop="customer" label="客户" min-width="130" show-overflow-tooltip />
        <el-table-column label="吨数" width="82" align="right">
          <template #default="{ row }">{{ n1(row.tons) }}</template>
        </el-table-column>
        <el-table-column prop="plateNo" label="车牌" width="116" />
        <el-table-column prop="operator" label="登记人" width="80" />
        <el-table-column label="登记 / 确认" width="150">
          <template #default="{ row }">
            <div class="ps-time">{{ fmt(row.createdAt) }}</div>
            <div v-if="row.confirmAt" class="sf-muted ps-time">
              {{ row.confirmOperator }} {{ fmt(row.confirmAt) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button size="small" type="primary" @click="actConfirm(row)">确认实扣</el-button>
              <el-button size="small" type="warning" @click="actReject(row)">作废</el-button>
            </template>
            <template v-else-if="row.status === 'CONFIRMED'">
              <el-button size="small" @click="openPlate(row)">改车牌</el-button>
              <el-button size="small" type="danger" @click="openReverse(row)">冲正重记</el-button>
            </template>
            <template v-else>
              <span class="sf-muted">
                <span v-if="row.status === 'REVERSED'">已冲正 → #{{ row.reversedById }}</span>
                <span v-else-if="row.reversalOfId">冲正重记自 #{{ row.reversalOfId }}</span>
                <span v-else>已作废</span>
              </span>
            </template>
            <el-button size="small" text @click="openHistory(row)">痕迹</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="sf-card" style="margin-top: 14px">
      <div class="sf-card-title">坨台库存与发运汇总</div>
      <el-table :data="stockStats" size="small" stripe>
        <el-table-column prop="platformNo" label="坨台" width="90" />
        <el-table-column prop="platformName" label="名称" />
        <el-table-column label="盐种" width="100">
          <template #default="{ row }">{{ saltName(row.saltType) }}</template>
        </el-table-column>
        <el-table-column label="账面库存（吨）" width="120" align="right">
          <template #default="{ row }">{{ n1(row.stockTons) }}</template>
        </el-table-column>
        <el-table-column label="预占中（吨）" width="110" align="right">
          <template #default="{ row }">
            <span :class="{ 'ps-reserved': Number(row.reservedTons || 0) > 0 }">{{ n1(row.reservedTons) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可用（吨）" width="110" align="right">
          <template #default="{ row }">
            <b class="ps-available">{{ n1(Number(row.stockTons || 0) - Number(row.reservedTons || 0)) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="累计实发（吨）" width="130" align="right">
          <template #default="{ row }">{{ n1(row.outTons) }}</template>
        </el-table-column>
        <el-table-column prop="pendingCount" label="待复核单" width="90" align="center" />
      </el-table>
    </div>

    <!-- 登记出仓（预占） -->
    <el-dialog v-model="dlg" :title="current ? `${current.platformNo} ${current.name} · 出仓登记（预占）` : '出仓登记'" width="520px">
      <div v-if="current">
        <el-alert type="info" :closable="false" style="margin-bottom: 12px">
          登记后为<b>待复核</b>：{{ n1(form.tons) }} 吨将被<b>预占冻结</b>，账面库存暂不减少；
          复核确认后才实扣。
        </el-alert>
        <el-descriptions :column="2" size="small" border style="margin-bottom: 12px">
          <el-descriptions-item label="盐种">{{ current.saltTypeName }}</el-descriptions-item>
          <el-descriptions-item label="垛形">{{ current.pileShape }}</el-descriptions-item>
          <el-descriptions-item label="账面库存">{{ n1(current.stockTons) }} 吨</el-descriptions-item>
          <el-descriptions-item label="可用库存">
            <b class="ps-available">{{ n1(current.availableTons) }} 吨</b>
            <span class="sf-muted">（已预占 {{ n1(current.reservedTons) }}）</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-form :model="form" label-width="90px" size="small">
          <el-form-item label="客户">
            <el-input v-model="form.customer" placeholder="例如：渤海化工集团" />
          </el-form-item>
          <el-form-item label="出仓吨数">
            <el-input-number v-model="form.tons" :min="1" :max="9999" :step="10" />
            <span class="sf-muted" style="margin-left: 10px">可用 {{ n1(current.availableTons) }} 吨</span>
          </el-form-item>
          <el-form-item label="车牌">
            <el-input v-model="form.plateNo" placeholder="例如：冀B·A1276" />
          </el-form-item>
          <el-form-item label="司磅员">
            <el-input v-model="form.operator" />
          </el-form-item>
        </el-form>

        <div class="sf-card-title" style="margin-top: 6px">本坨台账</div>
        <el-table :data="outbounds" size="small" max-height="220">
          <el-table-column prop="outDate" label="日期" width="100" />
          <el-table-column label="状态" width="74">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTag(row.status)">{{ row.statusName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="customer" label="客户" min-width="110" show-overflow-tooltip />
          <el-table-column label="吨数" width="72" align="right">
            <template #default="{ row }">{{ n1(row.tons) }}</template>
          </el-table-column>
          <el-table-column prop="plateNo" label="车牌" width="106" />
          <el-table-column label="操作" width="64">
            <template #default="{ row }">
              <el-button size="small" text @click="openHistory(row)">痕迹</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="submit">登记并预占</el-button>
      </template>
    </el-dialog>

    <!-- 复核人输入 -->
    <el-dialog v-model="reviewDlg" title="复核操作" width="420px">
      <el-form label-width="80px" size="small">
        <el-form-item label="操作类型">
          <el-tag :type="reviewMode === 'confirm' ? 'success' : 'warning'">
            {{ reviewMode === 'confirm' ? '确认实扣' : '作废（释放预占）' }}
          </el-tag>
          <span class="sf-muted" style="margin-left: 8px">出仓单 #{{ reviewRow && reviewRow.id }}</span>
        </el-form-item>
        <el-form-item label="复核人">
          <el-input v-model="reviewForm.operator" placeholder="复核员姓名" />
        </el-form-item>
        <el-form-item v-if="reviewMode === 'reject'" label="作废原因">
          <el-input v-model="reviewForm.reason" type="textarea" :rows="2" placeholder="例如：磅单与车牌不符" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDlg = false">取消</el-button>
        <el-button :type="reviewMode === 'confirm' ? 'primary' : 'warning'" @click="submitReview">确定</el-button>
      </template>
    </el-dialog>

    <!-- 仅车牌直改 -->
    <el-dialog v-model="plateDlg" title="直接更正车牌" width="440px">
      <el-alert v-if="plateRow" type="info" :closable="false" style="margin-bottom: 12px">
        仅车牌错误时使用：坨台 <b>{{ plateRow.platformNo }}</b>、吨数 <b>{{ n1(plateRow.tons) }} 吨</b>
        均不变，<b>库存不动</b>；原值 / 新值 / 操作人 / 时间会记入更正流水。
      </el-alert>
      <el-form label-width="80px" size="small">
        <el-form-item label="原车牌">
          <el-input :model-value="plateRow && plateRow.plateNo" disabled />
        </el-form-item>
        <el-form-item label="新车牌">
          <el-input v-model="plateForm.newPlateNo" placeholder="输入正确车牌" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="plateForm.operator" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="plateDlg = false">取消</el-button>
        <el-button type="primary" @click="submitPlate">确认更正</el-button>
      </template>
    </el-dialog>

    <!-- 冲正重记 -->
    <el-dialog v-model="reverseDlg" title="冲正后重记" width="560px">
      <el-alert v-if="reverseRow" type="warning" :closable="false" style="margin-bottom: 12px">
        原子流程：原单 #{{ reverseRow.id }} 先冲正、库存恢复 <b>{{ n1(reverseRow.tons) }} 吨</b>，
        再按新单扣减；原单状态变「已冲正」<b>不可删除、不可再次冲正</b>。
      </el-alert>
      <el-descriptions v-if="reverseRow" :column="1" size="small" border style="margin-bottom: 12px">
        <el-descriptions-item label="原值">
          {{ reverseRow.platformNo }} {{ reverseRow.platformName }} · {{ reverseRow.customer }} ·
          {{ n1(reverseRow.tons) }} 吨 · {{ reverseRow.plateNo }}
        </el-descriptions-item>
      </el-descriptions>
      <el-form :model="reverseForm" label-width="90px" size="small">
        <el-form-item label="新坨台">
          <el-select v-model="reverseForm.platformId" style="width: 100%">
            <el-option v-for="p in platforms" :key="p.id" :value="p.id"
              :label="`${p.platformNo} ${p.name}（可用 ${n1(p.availableTons)} 吨）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="新客户">
          <el-input v-model="reverseForm.customer" />
        </el-form-item>
        <el-form-item label="新吨数">
          <el-input-number v-model="reverseForm.tons" :min="1" :max="9999" :step="10" />
        </el-form-item>
        <el-form-item label="新车牌">
          <el-input v-model="reverseForm.plateNo" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="reverseForm.operator" />
        </el-form-item>
        <el-form-item label="冲正原因">
          <el-input v-model="reverseForm.reason" type="textarea" :rows="2" placeholder="例如：坨台选错 / 吨数录错" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reverseDlg = false">取消</el-button>
        <el-button type="danger" @click="submitReverse">冲正并重记</el-button>
      </template>
    </el-dialog>

    <!-- 更正痕迹 -->
    <el-dialog v-model="historyDlg" title="更正痕迹（原值 → 新值）" width="680px">
      <el-table :data="history" size="small" stripe empty-text="该单暂无更正记录">
        <el-table-column prop="operatedAt" label="时间" width="148">
          <template #default="{ row }">{{ fmt(row.operatedAt) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.changeType === 'PLATE' ? '' : 'danger'">
              {{ row.changeTypeName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="原值 → 新值" min-width="280">
          <template #default="{ row }">
            <div v-if="row.changeType === 'PLATE'">
              车牌：<b>{{ row.oldPlateNo || '（空）' }}</b> → <b>{{ row.newPlateNo }}</b>
            </div>
            <div v-else>
              <div>坨台：{{ row.oldPlatformText }} → <b>{{ row.newPlatformText }}</b></div>
              <div>客户：{{ row.oldCustomer }} → <b>{{ row.newCustomer }}</b></div>
              <div>吨数：{{ n1(row.oldTons) }} → <b>{{ n1(row.newTons) }}</b></div>
              <div>车牌：{{ row.oldPlateNo || '（空）' }} → <b>{{ row.newPlateNo || '（空）' }}</b></div>
              <div class="sf-muted">原单 #{{ row.recordId }} → 重记新单 #{{ row.newRecordId }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="80" />
        <el-table-column prop="remark" label="备注" min-width="90" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const platforms = ref([])
const stockStats = ref([])
const loading = ref(false)

const dlg = ref(false)
const current = ref(null)
const outbounds = ref([])
const form = reactive({ customer: '', tons: 100, plateNo: '冀B·A1276', operator: '刘司磅' })

const queue = ref([])
const queueLoading = ref(false)
const queueFilter = ref('PENDING')
const pendingTotal = computed(() => stockStats.value.reduce((s, p) => s + Number(p.pendingCount || 0), 0))

const reviewDlg = ref(false)
const reviewMode = ref('confirm')
const reviewRow = ref(null)
const reviewForm = reactive({ operator: '王复核', reason: '' })

const plateDlg = ref(false)
const plateRow = ref(null)
const plateForm = reactive({ newPlateNo: '', operator: '刘司磅' })

const reverseDlg = ref(false)
const reverseRow = ref(null)
const reverseForm = reactive({ platformId: null, customer: '', tons: 100, plateNo: '', operator: '刘司磅', reason: '' })

const historyDlg = ref(false)
const history = ref([])

function n1(v) {
  return Number(v || 0).toFixed(1)
}

function fmt(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

function statusTag(s) {
  return { PENDING: 'warning', CONFIRMED: 'success', REJECTED: 'info', REVERSED: 'danger' }[s] || 'info'
}

/** 立方体边长：按账面库存吨数在容量中的占比缩放（40px ~ 132px） */
function cubeSize(p) {
  const ratio = Number(p.fillRatio || 0)
  return Math.round(40 + Math.min(1, ratio) * 92)
}

function cubeStyle(p) {
  const s = cubeSize(p)
  return { width: s + 'px', height: s + 'px' }
}

function faceStyle(p, face) {
  const s = cubeSize(p)
  const base = saltColor(p.saltType)
  if (face === 'top') {
    return {
      background: shade(base, 26),
      transform: `rotateX(90deg) translateZ(${s / 2}px)`,
      width: s + 'px',
      height: s + 'px'
    }
  }
  if (face === 'front') {
    return {
      background: base,
      transform: `translateZ(${s / 2}px)`,
      width: s + 'px',
      height: s + 'px'
    }
  }
  return {
    background: shade(base, -22),
    transform: `rotateY(90deg) translateZ(${s / 2}px)`,
    width: s + 'px',
    height: s + 'px'
  }
}

function saltColor(type) {
  if (type === 'REFINED') return '#eceff1'
  if (type === 'SUN') return '#cfd8dc'
  return '#b0bec5'
}

function shade(hex, amount) {
  const n = parseInt(hex.slice(1), 16)
  const r = Math.min(255, Math.max(0, ((n >> 16) & 255) + amount))
  const g = Math.min(255, Math.max(0, ((n >> 8) & 255) + amount))
  const b = Math.min(255, Math.max(0, (n & 255) + amount))
  return `rgb(${r},${g},${b})`
}

function saltName(t) {
  return { INDUSTRIAL: '工业盐', SUN: '日晒盐', REFINED: '精制盐' }[t] || t
}

function saltTag(t) {
  return { INDUSTRIAL: '', SUN: 'warning', REFINED: 'info' }[t] || 'info'
}

function fillPercent(p) {
  return Math.min(100, Math.round(Number(p.fillRatio || 0) * 100))
}

async function openOutbound(p) {
  current.value = p
  form.customer = ''
  form.tons = Math.max(10, Math.round(Number(p.availableTons || 0) * 0.1))
  form.plateNo = '冀B·A1276'
  try {
    outbounds.value = await api.outboundByPlatform(p.id)
  } catch (e) {
    outbounds.value = []
  }
  dlg.value = true
}

async function submit() {
  if (!form.customer || !form.customer.trim()) {
    ElMessage.error('客户名称不能为空')
    return
  }
  try {
    await api.outboundRegister(current.value.id, { ...form })
    ElMessage.success('出仓已登记：吨数已预占，等待复核确认')
    dlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function actConfirm(row) {
  reviewMode.value = 'confirm'
  reviewRow.value = row
  reviewForm.reason = ''
  reviewDlg.value = true
}

function actReject(row) {
  reviewMode.value = 'reject'
  reviewRow.value = row
  reviewForm.reason = ''
  reviewDlg.value = true
}

async function submitReview() {
  if (!reviewForm.operator || !reviewForm.operator.trim()) {
    ElMessage.error('请填写复核人')
    return
  }
  if (reviewMode.value === 'reject' && !reviewForm.reason.trim()) {
    ElMessage.error('作废请填写原因')
    return
  }
  const fn = reviewMode.value === 'confirm' ? api.outboundConfirm : api.outboundReject
  try {
    await fn(reviewRow.value.id, { operator: reviewForm.operator, reason: reviewForm.reason })
    ElMessage.success(reviewMode.value === 'confirm' ? '已确认：预占转实扣，账面库存已减' : '已作废：预占已释放')
    reviewDlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openPlate(row) {
  plateRow.value = row
  plateForm.newPlateNo = row.plateNo || ''
  plateDlg.value = true
}

async function submitPlate() {
  if (!plateForm.newPlateNo.trim()) {
    ElMessage.error('新车牌不能为空')
    return
  }
  if (plateForm.newPlateNo.trim() === (plateRow.value.plateNo || '')) {
    ElMessage.error('新车牌与原车牌相同')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认将车牌由「${plateRow.value.plateNo || '（空）'}」更正为「${plateForm.newPlateNo}」？坨台与吨数不变，库存不动。`,
      '车牌直改确认', { type: 'warning', confirmButtonText: '确认更正', cancelButtonText: '取消' })
  } catch (e) {
    return
  }
  try {
    await api.outboundCorrectPlate(plateRow.value.id, {
      newPlateNo: plateForm.newPlateNo.trim(),
      operator: plateForm.operator
    })
    ElMessage.success('车牌已更正，原值新值已留痕')
    plateDlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openReverse(row) {
  reverseRow.value = row
  reverseForm.platformId = row.platformId
  reverseForm.customer = row.customer || ''
  reverseForm.tons = Number(row.tons)
  reverseForm.plateNo = row.plateNo || ''
  reverseForm.reason = ''
  reverseDlg.value = true
}

async function submitReverse() {
  try {
    await ElMessageBox.confirm(
      `冲正原单 #${reverseRow.value.id} 并按新单重记？库存将先恢复 ${n1(reverseRow.value.tons)} 吨再按新单扣减，
       原单变「已冲正」且不可删除、不可再次冲正。`,
      '冲正重记确认', { type: 'warning', confirmButtonText: '冲正并重记', cancelButtonText: '取消' })
  } catch (e) {
    return
  }
  try {
    await api.outboundReverse(reverseRow.value.id, { ...reverseForm })
    ElMessage.success('冲正重记完成：库存已原子恢复并按新单扣减')
    reverseDlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function openHistory(row) {
  history.value = []
  historyDlg.value = true
  try {
    history.value = await api.outboundCorrections(row.id)
  } catch (e) {
    history.value = []
  }
}

async function reloadQueue() {
  queueLoading.value = true
  try {
    queue.value = await api.outboundList(queueFilter.value || undefined)
  } finally {
    queueLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const [list, stats] = await Promise.all([api.platforms(), api.platformStockStats()])
    platforms.value = list || []
    stockStats.value = stats || []
    if (current.value) {
      const fresh = platforms.value.find((p) => p.id === current.value.id)
      if (fresh) current.value = fresh
    }
  } finally {
    loading.value = false
  }
  await reloadQueue()
  if (dlg.value && current.value) {
    try {
      outbounds.value = await api.outboundByPlatform(current.value.id)
    } catch (e) { /* 弹窗可能已关 */ }
  }
}

onMounted(load)
</script>

<style scoped>
.ps-policy {
  font-size: 12.5px;
  line-height: 1.8;
  margin-top: 2px;
}

.ps-policy b {
  color: #1b5e20;
}

.ps-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.ps-card {
  border: 1px solid #dfe6e4;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.ps-card:hover {
  box-shadow: 0 6px 16px rgba(27, 94, 32, 0.14);
  transform: translateY(-2px);
}

.ps-card.is-active {
  border-color: #1b5e20;
}

.ps-stage {
  height: 190px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: radial-gradient(circle at 50% 60%, #f2f7f2 0%, #e7efe8 100%);
  perspective: 620px;
  cursor: pointer;
}

.ps-cube {
  position: relative;
  transform-style: preserve-3d;
  transform: rotateX(-22deg) rotateY(34deg);
  animation: ps-spin 9s linear infinite;
}

.ps-face {
  position: absolute;
  top: 0;
  left: 0;
  border: 1px solid rgba(255, 255, 255, 0.65);
  box-shadow: inset 0 0 18px rgba(0, 0, 0, 0.08);
}

@keyframes ps-spin {
  0% {
    transform: rotateX(-22deg) rotateY(24deg);
  }
  100% {
    transform: rotateX(-22deg) rotateY(384deg);
  }
}

.ps-shadow {
  height: 10px;
  border-radius: 50%;
  background: rgba(27, 94, 32, 0.16);
  filter: blur(3px);
}

.ps-info {
  padding: 10px 12px 14px;
}

.ps-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ps-no {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 15px;
  font-weight: 700;
  color: #1b5e20;
}

.ps-name {
  font-size: 12px;
  color: #6b7280;
  margin: 4px 0 8px;
}

.ps-metrics {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
  margin-bottom: 8px;
}

.ps-metrics div {
  display: flex;
  flex-direction: column;
}

.ps-metrics b {
  font-size: 14px;
  font-family: "SFMono-Regular", Consolas, monospace;
}

.ps-reserved {
  color: #b26a00;
}

.ps-available {
  color: #1b5e20;
}

.ps-time {
  font-size: 12px;
  line-height: 1.5;
}
</style>
