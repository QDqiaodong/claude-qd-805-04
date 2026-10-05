<template>
  <div>
    <!-- 生命周期口径 -->
    <el-alert type="success" :closable="false" show-icon style="margin-bottom: 14px">
      <template #title>
        <b>派工生命周期：待派 → 进行中 → 已完成，只能逐级前进</b>
      </template>
      <div class="dp-policy">
        重复提交同一结果<b>幂等</b>（状态不变、不重复留痕）；跳级、倒退、未知取值以及<b>已完成后的任何修改一律拒绝</b>，
        后端返回可操作原因。两名调度员同时操作时，行锁与状态快照保证<b>已完成结果不会被覆盖</b>，
        冲突方按提示刷新即可看到最新状态与生效时间。历史数据中：旧别名（如 DO、已派）已自动归一化并留痕、保持可用；
        <b>空值 / 非法状态标记异常并冻结流转</b>，原始值原样展示，只能由调度主管核实后「修正异常」。
      </div>
    </el-alert>

    <div class="sf-card">
      <div class="sf-card-title">
        派工任务列表
        <span class="sf-muted">每行只给出唯一合法的下一步 · 异常行带历史提示 · 单号看流转痕迹</span>
        <div style="margin-left: auto; display: flex; align-items: center; gap: 10px">
          <span class="sf-muted">当前操作人</span>
          <el-input v-model="operator" size="small" style="width: 120px" placeholder="调度员姓名" />
          <el-button size="small" type="primary" plain @click="openCreate">下达派工</el-button>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </div>

      <el-table :data="filtered" size="small" stripe v-loading="loading" :row-class-name="rowClass">
        <el-table-column label="单号" width="104">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.taskNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="92">
          <template #default="{ row }">{{ row.taskTypeName }}</template>
        </el-table-column>
        <el-table-column label="对象" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" effect="plain" class="dp-tt">{{ row.targetTypeName }}</el-tag>
            {{ row.targetName || '未关联' }}
          </template>
        </el-table-column>
        <el-table-column prop="crew" label="班组" width="92" show-overflow-tooltip />
        <el-table-column label="计划日期" width="104">
          <template #default="{ row }">{{ fmtDate(row.planDate) }}</template>
        </el-table-column>
        <el-table-column label="当前状态" width="190">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row)">{{ row.taskStatusName }}</el-tag>
            <el-tag v-if="row.alias" size="small" type="warning" effect="plain" class="dp-flag">
              旧值 {{ row.rawTaskStatus }} 已归一
            </el-tag>
            <el-tooltip v-if="row.abnormal" :content="row.abnormalHint" placement="top">
              <el-tag size="small" type="danger" effect="dark" class="dp-flag">
                异常：{{ rawLabel(row.rawTaskStatus) }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="最后操作 / 生效时间" width="178">
          <template #default="{ row }">
            <div v-if="row.statusChangedAt" class="dp-time">
              {{ row.lastOperator || '—' }} · {{ fmt(row.statusChangedAt) }}
            </div>
            <div v-else class="sf-muted dp-time">无生效时间（历史行）</div>
          </template>
        </el-table-column>
        <el-table-column label="操作（仅合法下一步）" width="230" fixed="right">
          <template #default="{ row }">
            <template v-if="row.abnormal">
              <el-button size="small" type="danger" @click="openRepair(row)">修正异常</el-button>
              <el-tooltip :content="row.abnormalHint" placement="top">
                <span class="sf-muted dp-why">为何冻结</span>
              </el-tooltip>
            </template>
            <template v-else-if="row.nextStatus">
              <el-button
                size="small"
                :type="row.nextStatus === 'DONE' ? 'success' : 'primary'"
                :loading="actingId === row.id"
                @click="advance(row)"
              >
                推进为「{{ row.nextStatusName }}」
              </el-button>
              <el-button size="small" text @click="openDetail(row)">痕迹</el-button>
            </template>
            <template v-else>
              <el-tag size="small" type="success" effect="plain">已完成并锁定</el-tag>
              <el-button size="small" text @click="openDetail(row)">痕迹</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="dp-filter">
        <el-radio-group v-model="filter" size="small" @change="() => {}">
          <el-radio-button label="ALL">全部 {{ tasks.length }}</el-radio-button>
          <el-radio-button label="TODO">待派 {{ countOf('TODO') }}</el-radio-button>
          <el-radio-button label="DOING">进行中 {{ countOf('DOING') }}</el-radio-button>
          <el-radio-button label="DONE">已完成 {{ countOf('DONE') }}</el-radio-button>
          <el-radio-button label="ABNORMAL">异常 {{ abnormalCount }}</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 下达派工 -->
    <el-dialog v-model="createDlg" title="下达派工任务（进入「待派」）" width="520px">
      <el-form :model="createForm" label-width="92px" size="small">
        <el-form-item label="任务类型">
          <el-select v-model="createForm.taskType" style="width: 100%">
            <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :value="t.value" :label="t.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务对象">
          <el-select v-model="createForm.targetKey" filterable placeholder="选择池子 / 坨台 / 设备" style="width: 100%">
            <el-option-group
              v-for="g in groupedOptions"
              :key="g.targetType"
              :label="g.targetTypeName"
            >
              <el-option
                v-for="o in g.items"
                :key="g.targetType + '-' + o.targetId"
                :value="g.targetType + ':' + o.targetId"
                :label="o.targetName"
              />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="班组">
          <el-input v-model="createForm.crew" />
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="createForm.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="下达人">
          <el-input v-model="createForm.operator" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDlg = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">下达（待派）</el-button>
      </template>
    </el-dialog>

    <!-- 详情 + 历史 -->
    <el-dialog v-model="detailDlg" title="派工任务详情与流转历史" width="680px">
      <div v-if="detail" v-loading="detailLoading">
        <el-alert
          v-if="detail.abnormal"
          type="error"
          :closable="false"
          show-icon
          class="dp-abn"
          :title="detail.abnormalHint"
        />
        <el-alert
          v-else-if="detail.alias"
          type="warning"
          :closable="false"
          class="dp-abn"
          :title="`历史旧值「${detail.rawTaskStatus}」已归一化为「${detail.taskStatusName}」，归一化动作已在下方留痕`"
        />

        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="单号">{{ detail.taskNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.taskTypeName }}</el-descriptions-item>
          <el-descriptions-item label="对象">
            {{ detail.targetTypeName }} · {{ detail.targetName || '未关联' }}
          </el-descriptions-item>
          <el-descriptions-item label="班组">{{ detail.crew }}</el-descriptions-item>
          <el-descriptions-item label="计划日期">{{ fmtDate(detail.planDate) }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag size="small" :type="statusTag(detail)">{{ detail.taskStatusName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="最后操作人">{{ detail.lastOperator || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态生效时间">
            {{ detail.statusChangedAt ? fmt(detail.statusChangedAt) : '无（历史行）' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="!detail.abnormal" class="dp-next">
          合法下一步：
          <template v-if="detail.nextStatus">
            <el-button
              size="small"
              :type="detail.nextStatus === 'DONE' ? 'success' : 'primary'"
              :loading="actingId === detail.id"
              @click="advance(detail)"
            >推进为「{{ detail.nextStatusName }}」</el-button>
          </template>
          <el-tag v-else size="small" type="success" effect="plain">已完成并锁定，不可再修改</el-tag>
        </div>

        <div class="sf-card-title" style="margin: 14px 0 8px">状态流转历史（前态 → 后态 · 原始值 · 操作人 · 时间）</div>
        <el-timeline>
          <el-timeline-item
            v-for="h in detail.history || []"
            :key="h.id"
            :type="historyType(h.changeType)"
            :timestamp="`${h.operator || '—'} · ${fmt(h.operatedAt)}`"
          >
            <el-tag size="small" :type="historyType(h.changeType)" effect="plain">{{ h.changeTypeName }}</el-tag>
            <span class="dp-flow">
              {{ h.fromStatusName || '（无）' }}
              <b>→</b>
              {{ h.toStatusName }}
            </span>
            <div v-if="h.rawFromStatus && h.rawFromStatus !== h.fromStatus" class="sf-muted">
              数据库原始值：{{ rawLabel(h.rawFromStatus) }}
            </div>
            <div v-if="h.remark" class="sf-muted">{{ h.remark }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="!(detail.history && detail.history.length)" description="暂无流转记录" :image-size="60" />
      </div>
    </el-dialog>

    <!-- 异常修正 -->
    <el-dialog v-model="repairDlg" title="修正历史异常状态（原值留痕）" width="500px">
      <el-alert v-if="repairRow" type="error" :closable="false" show-icon style="margin-bottom: 12px">
        <template #title>{{ repairRow.abnormalHint }}</template>
      </el-alert>
      <el-form label-width="92px" size="small">
        <el-form-item label="任务">
          {{ repairRow && repairRow.taskNo }} · {{ repairRow && repairRow.targetName }}
        </el-form-item>
        <el-form-item label="数据库原值">
          <el-tag size="small" type="danger">{{ repairRow && rawLabel(repairRow.rawTaskStatus) }}</el-tag>
          <span class="sf-muted" style="margin-left: 8px">修正后原值仍保留在流转历史</span>
        </el-form-item>
        <el-form-item label="核实后状态">
          <el-radio-group v-model="repairForm.newStatus">
            <el-radio label="TODO">待派</el-radio>
            <el-radio label="DOING">进行中</el-radio>
            <el-radio label="DONE">已完成</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="修正人">
          <el-input v-model="repairForm.operator" placeholder="调度主管姓名" />
        </el-form-item>
        <el-form-item label="核实理由">
          <el-input v-model="repairForm.reason" type="textarea" :rows="2" placeholder="例如：比对旧纸质派工单，实际已在作业中" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="repairDlg = false">取消</el-button>
        <el-button type="danger" @click="submitRepair">确认修正并留痕</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const TYPE_OPTIONS = [
  { value: 'DREDGE', label: '清淤' },
  { value: 'FIX', label: '修池' },
  { value: 'TRANSFER', label: '转卤' },
  { value: 'UPKEEP', label: '设备保养' }
]

const tasks = ref([])
const loading = ref(false)
const actingId = ref(null)
const operator = ref('调度员')
const filter = ref('ALL')

const createDlg = ref(false)
const targetOptions = ref([])
const createForm = reactive({
  taskType: 'FIX', targetKey: '', crew: '一班',
  planDate: new Date().toISOString().slice(0, 10), remark: '', operator: '调度员'
})

const detailDlg = ref(false)
const detail = ref(null)
const detailLoading = ref(false)

const repairDlg = ref(false)
const repairRow = ref(null)
const repairForm = reactive({ newStatus: 'TODO', operator: '', reason: '' })

const groupedOptions = computed(() => {
  const groups = {}
  for (const o of targetOptions.value) {
    if (!groups[o.targetType]) {
      groups[o.targetType] = { targetType: o.targetType, targetTypeName: o.targetTypeName, items: [] }
    }
    groups[o.targetType].items.push(o)
  }
  return Object.values(groups)
})

const filtered = computed(() => {
  if (filter.value === 'ALL') return tasks.value
  if (filter.value === 'ABNORMAL') return tasks.value.filter((t) => t.abnormal)
  return tasks.value.filter((t) => t.taskStatus === filter.value)
})

const abnormalCount = computed(() => tasks.value.filter((t) => t.abnormal).length)

function countOf(s) {
  return tasks.value.filter((t) => t.taskStatus === s).length
}

function fmt(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

function fmtDate(d) {
  return d || '-'
}

function rawLabel(raw) {
  return raw === null || raw === undefined || String(raw).trim() === '' ? '空值' : String(raw)
}

function statusTag(row) {
  if (row.abnormal) return 'danger'
  return { TODO: 'warning', DOING: 'primary', DONE: 'success' }[row.taskStatus] || 'info'
}

function historyType(t) {
  return { CREATE: 'primary', ADVANCE: 'success', NORMALIZE: 'warning', REPAIR: 'danger' }[t] || 'info'
}

function rowClass({ row }) {
  return row.abnormal ? 'dp-row-abnormal' : ''
}

async function load() {
  loading.value = true
  try {
    tasks.value = (await api.dispatchTasks()) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

/**
 * 唯一的推进入口：携带页面快照 taskStatus 作为 expectedStatus。
 * 并发冲突（409）时已完成结果绝不被覆盖，用后端回带的最新快照更新该行并提示刷新。
 */
async function advance(row) {
  if (!row.nextStatus) return
  try {
    await ElMessageBox.confirm(
      `确认将「${row.taskNo}」由「${row.taskStatusName}」推进为「${row.nextStatusName}」？`,
      '状态推进确认',
      { type: 'info', confirmButtonText: '确认推进', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  actingId.value = row.id
  try {
    const res = await api.advanceDispatch(row.id, row.nextStatus, row.taskStatus, operator.value || '调度员')
    ElMessage.success(res?.message || '状态已推进')
    await load()
    if (detailDlg.value && detail.value && detail.value.id === row.id) {
      await openDetailById(row.id)
    }
  } catch (e) {
    if (e.status === 409) {
      ElMessage.warning(e.message)
      if (e.snapshot) {
        const i = tasks.value.findIndex((t) => t.id === row.id)
        if (i >= 0) tasks.value[i] = e.snapshot
      }
      await load() // 失败方刷新后看到最新状态与生效时间
      if (detailDlg.value) await openDetailById(row.id)
    } else {
      ElMessage.error(e.message)
    }
  } finally {
    actingId.value = null
  }
}

async function openCreate() {
  createForm.operator = operator.value
  try {
    targetOptions.value = (await api.logOptions()) || []
  } catch (e) {
    targetOptions.value = []
  }
  createDlg.value = true
}

async function submitCreate() {
  if (!createForm.targetKey) {
    ElMessage.error('请选择任务对象')
    return
  }
  const [targetType, targetId] = createForm.targetKey.split(':')
  try {
    await api.createDispatch({
      taskType: createForm.taskType,
      targetType,
      targetId: Number(targetId),
      crew: createForm.crew,
      planDate: createForm.planDate,
      remark: createForm.remark,
      operator: createForm.operator
    })
    ElMessage.success('调度任务已下达（待派）')
    createDlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function openDetail(row) {
  detailDlg.value = true
  await openDetailById(row.id)
}

async function openDetailById(id) {
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await api.dispatchDetail(id)
  } catch (e) {
    ElMessage.error(e.message)
    detailDlg.value = false
  } finally {
    detailLoading.value = false
  }
}

function openRepair(row) {
  repairRow.value = row
  repairForm.newStatus = 'TODO'
  repairForm.operator = operator.value
  repairForm.reason = ''
  repairDlg.value = true
}

async function submitRepair() {
  if (!repairForm.operator || !repairForm.operator.trim()) {
    ElMessage.error('请填写修正人（调度主管）')
    return
  }
  try {
    await api.repairDispatch(repairRow.value.id, { ...repairForm })
    ElMessage.success('异常状态已修正并留痕')
    repairDlg.value = false
    await load()
    if (detailDlg.value) await openDetailById(repairRow.value.id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.dp-policy {
  font-size: 12.5px;
  line-height: 1.8;
  margin-top: 2px;
}

.dp-policy b {
  color: #1b5e20;
}

.dp-tt {
  margin-right: 6px;
}

.dp-flag {
  margin-left: 6px;
}

.dp-time {
  font-size: 12px;
  line-height: 1.5;
}

.dp-why {
  margin-left: 8px;
  font-size: 12px;
}

.dp-filter {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.dp-abn {
  margin-bottom: 12px;
}

.dp-next {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.dp-flow {
  margin-left: 10px;
  font-size: 13px;
}

:deep(.dp-row-abnormal) {
  background: #fef0f0 !important;
}
</style>
