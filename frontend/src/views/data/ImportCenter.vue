<template>
  <div class="import-page">
    <!-- 流程说明 + 类型卡片 -->
    <div class="page-card">
      <div class="page-title">数据导入</div>
      <div class="page-sub text-muted">
        支持患者档案、药品字典、初始库存与历史病历四类 Excel 数据的批量导入，导入前请先下载并填写对应模板。
      </div>

      <el-alert type="info" :closable="false" show-icon class="import-alert"
        title="导入前会先校验，预览只检查不写库；导入按行独立处理，失败行会列出原因，不影响其他行。" />

      <el-steps :active="stepActive" align-center finish-status="success" class="import-steps">
        <el-step title="下载模板" description="选择类型并下载 Excel 模板" />
        <el-step title="填写数据" description="按表头填写，* 为必填列" />
        <el-step title="上传预览" description="仅校验，不写入数据库" />
        <el-step title="确认导入" description="选择模式后执行导入" />
      </el-steps>

      <div class="type-grid">
        <div v-for="item in IMPORT_TYPES" :key="item.type" class="type-card"
          :class="{ selected: selectedType === item.type }" @click="selectType(item.type)">
          <div class="card-head">
            <div class="card-icon">
              <el-icon><component :is="item.icon" /></el-icon>
            </div>
            <div class="card-title-box">
              <div class="card-title">
                <span>{{ item.name }}</span>
                <span class="type-code text-muted">{{ item.type }}</span>
              </div>
              <div class="card-desc">{{ item.desc }}</div>
            </div>
            <el-tag v-if="selectedType === item.type" size="small" type="success" effect="plain">已选择</el-tag>
          </div>

          <div class="column-summary">
            <span class="summary-label">模板列</span>
            <span v-for="col in item.columns" :key="col.name" class="col-chip"
              :class="{ required: col.required }">
              {{ col.name }}<b v-if="col.required" class="req-star">*</b>
            </span>
          </div>

          <div class="card-actions">
            <el-button size="small" :loading="!!templateLoading[item.type]" @click.stop="onDownloadTemplate(item)">
              <el-icon><Download /></el-icon>
              <span>下载模板</span>
            </el-button>
            <el-button size="small" :type="selectedType === item.type ? 'success' : 'primary'" plain
              @click.stop="selectType(item.type)">
              {{ selectedType === item.type ? '已选择' : '选择此类型' }}
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 上传与预览触发 -->
    <div class="page-card">
      <div class="section-head">
        <span class="section-title">上传文件并预览校验</span>
        <span class="text-muted">支持 .xlsx / .xls / .csv，单个文件不超过 5MB，单次最多 2000 行</span>
      </div>

      <el-form label-width="96px" class="upload-form">
        <el-form-item label="导入类型">
          <el-select v-model="selectedType" placeholder="请先选择导入类型" class="type-select" @change="onTypeChange">
            <el-option v-for="item in IMPORT_TYPES" :key="item.type"
              :label="`${item.name}（${item.type}）`" :value="item.type" />
          </el-select>
          <span v-if="currentType" class="text-muted form-tip">当前模板：{{ currentType.name }}</span>
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload ref="uploadRef" v-model:file-list="fileList" drag class="uploader" :auto-upload="false"
            :limit="1" accept=".xlsx,.xls,.csv" :on-change="onFileChange" :on-remove="onFileRemove"
            :on-exceed="onFileExceed">
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">仅支持 .xlsx / .xls / .csv 单文件，大小不超过 5MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>

      <div class="upload-actions">
        <el-button type="primary" :loading="previewLoading" :disabled="!canPreview || executing" @click="onPreview">
          <el-icon><View /></el-icon>
          <span>上传预览</span>
        </el-button>
        <el-button :disabled="previewLoading || executing" @click="resetAll">重新开始</el-button>
        <span class="text-muted">预览只做校验，不会写入任何数据</span>
      </div>
    </div>

    <!-- 预览结果 -->
    <div v-if="previewResult && !executeResult" class="page-card">
      <div class="section-head">
        <span class="section-title">预览校验结果</span>
        <span class="text-muted">{{ previewTypeName }} · {{ selectedFileName }}</span>
        <div class="flex-spacer" />
        <el-button size="small" :loading="previewLoading" @click="onPreview">重新校验</el-button>
      </div>

      <div class="stat-row">
        <div class="stat-card">
          <div class="stat-label text-muted">总行数</div>
          <div class="stat-value">{{ previewResult.total }}</div>
        </div>
        <div class="stat-card stat-ok">
          <div class="stat-label text-muted">通过校验</div>
          <div class="stat-value">{{ previewResult.valid }}</div>
        </div>
        <div class="stat-card" :class="previewResult.invalid > 0 ? 'stat-bad' : 'stat-ok'">
          <div class="stat-label text-muted">未通过</div>
          <div class="stat-value">{{ previewResult.invalid }}</div>
          <div v-if="previewResult.invalid > 0" class="stat-extra text-muted">错误明细最多展示 50 条</div>
        </div>
      </div>

      <div v-if="previewResult.invalid > 0" class="sub-block">
        <div class="sub-title">错误明细（{{ previewResult.errors.length }} 条）</div>
        <el-table :data="previewResult.errors" border size="small" max-height="320"
          empty-text="暂无错误明细">
          <el-table-column prop="row" label="行号" width="100" align="center" />
          <el-table-column prop="field" label="字段" width="200" show-overflow-tooltip />
          <el-table-column prop="message" label="原因" min-width="260" show-overflow-tooltip />
          <template #empty>
            <el-empty description="暂无错误明细" :image-size="60" />
          </template>
        </el-table>
      </div>

      <div class="sub-block">
        <div class="sub-title">数据样例（前 5 行）</div>
        <el-table :data="previewResult.sample" border size="small" max-height="320"
          empty-text="暂无可展示的样例数据">
          <el-table-column v-for="col in sampleColumns" :key="col.prop" :prop="col.prop" :label="col.label"
            :min-width="col.width" show-overflow-tooltip />
          <template #empty>
            <el-empty description="暂无可展示的样例数据" :image-size="60" />
          </template>
        </el-table>
      </div>

      <div class="preview-actions">
        <el-button type="primary" :loading="executing" @click="openImportConfirm">
          {{ previewResult.invalid > 0 ? '仍然导入有效行' : '确认导入' }}
        </el-button>
        <el-button :disabled="executing" @click="resetAll">重新开始</el-button>
        <span v-if="previewResult.invalid > 0" class="warn-text">
          有 {{ previewResult.invalid }} 行未通过校验，导入时这些行会被跳过并返回原因，有效行正常写入
        </span>
      </div>
    </div>

    <!-- 执行结果 -->
    <div v-if="executeResult" class="page-card result-card">
      <div class="section-head">
        <span class="section-title">导入结果</span>
        <span class="text-muted">{{ previewTypeName }} · {{ selectedFileName }} · {{ executedModeText }}</span>
        <div class="flex-spacer" />
        <el-button type="primary" @click="resetAll">重新开始</el-button>
      </div>

      <div class="stat-row result-stats">
        <div class="stat-card stat-ok">
          <div class="stat-label text-muted">新增</div>
          <div class="stat-value">{{ executeResult.inserted }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label text-muted">更新</div>
          <div class="stat-value">{{ executeResult.updated }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label text-muted">跳过</div>
          <div class="stat-value">{{ executeResult.skipped }}</div>
        </div>
        <div class="stat-card" :class="executeResult.failed > 0 ? 'stat-bad' : 'stat-ok'">
          <div class="stat-label text-muted">失败</div>
          <div class="stat-value">{{ executeResult.failed }}</div>
        </div>
      </div>

      <div v-if="executeResult.errors.length" class="sub-block">
        <div class="sub-title">失败明细（{{ executeResult.errors.length }} 条）</div>
        <el-table :data="executeResult.errors" border size="small" max-height="320" empty-text="无失败明细">
          <el-table-column prop="row" label="行号" width="100" align="center" />
          <el-table-column prop="field" label="字段" width="200" show-overflow-tooltip />
          <el-table-column prop="message" label="原因" min-width="260" show-overflow-tooltip />
          <template #empty>
            <el-empty description="无失败明细" :image-size="60" />
          </template>
        </el-table>
      </div>
      <el-alert v-else type="success" :closable="false" show-icon class="result-ok"
        title="全部行处理成功，无失败明细" />

      <el-alert type="info" :closable="false" show-icon class="result-audit"
        title="本次导入已写入操作审计"
        description="导入行为会记录操作人、时间、类型与模式，可在「操作审计」页面查看；失败行可在上方明细中核对后重新导入。" />
    </div>

    <!-- 导入注意事项 -->
    <div class="page-card notes-card">
      <div class="section-head">
        <span class="section-title">导入注意事项</span>
      </div>
      <ul class="notes-list">
        <li><b>匹配键</b>：患者 = 手机号 + 姓名；药品 = 药品编码；病历 = 患者手机号 + 就诊日期。</li>
        <li><b>病历导入</b>：会自动创建「已完成」的历史就诊记录，不能用于当日业务。</li>
        <li><b>库存导入</b>：会写入库存流水（来源 MANUAL），不直接修改批次。</li>
        <li><b>错误隔离</b>：导入按行独立处理，某行失败不影响其他行，失败原因逐行返回。</li>
        <li><b>数据量限制</b>：单次最多 2000 行，超出会报错，请拆分文件后分批导入。</li>
        <li><b>必填列</b>：模板表头带 <b>*</b> 的列为必填，缺失或格式错误会导致该行校验失败。</li>
        <li><b>审计留痕</b>：导入行为会写入操作审计，便于留存与检查。</li>
      </ul>
    </div>

    <!-- 确认导入弹窗 -->
    <el-dialog v-model="confirmVisible" title="确认导入" width="580px" :close-on-click-modal="false"
      :close-on-press-escape="!executing" :show-close="!executing">
      <div v-if="previewResult" class="confirm-body">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="导入类型">{{ previewTypeName }}（{{ selectedType }}）</el-descriptions-item>
          <el-descriptions-item label="导入文件">{{ selectedFileName }}</el-descriptions-item>
          <el-descriptions-item label="校验情况">
            共 {{ previewResult.total }} 行，通过 {{ previewResult.valid }} 行，未通过
            {{ previewResult.invalid }} 行
          </el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="previewResult.invalid > 0" type="warning" :closable="false" show-icon class="confirm-warn"
          :title="`有 ${previewResult.invalid} 行未通过校验，导入时这些行会失败并列出原因，不影响有效行。`" />

        <div class="mode-block">
          <div class="mode-title">导入模式</div>
          <el-radio-group v-model="importMode" :disabled="executing">
            <el-radio value="CREATE_ONLY">仅新增（已存在跳过）</el-radio>
            <el-radio value="UPSERT">存在则更新</el-radio>
          </el-radio-group>
          <div class="text-muted mode-tip">
            {{ importMode === 'UPSERT'
              ? '按匹配键更新已有记录，请确认文件中的数据为最新版本。'
              : '已存在的记录会被跳过，不会修改现有数据。' }}
          </div>
        </div>
      </div>
      <template #footer>
        <el-button :disabled="executing" @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="executing" @click="onExecute">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, UploadFilled, View } from '@element-plus/icons-vue'
import { downloadImportTemplate, executeImport, previewImport } from '@/api'

/* ================= 类型配置（列来自接口契约 §26 模板列） ================= */
const IMPORT_TYPES = [
  {
    type: 'PATIENT',
    name: '患者档案',
    icon: 'User',
    desc: '批量建立患者档案，按「手机号 + 姓名」识别已有患者。',
    columns: [
      { name: '姓名', required: true },
      { name: '性别', required: true },
      { name: '出生日期', required: false },
      { name: '手机号', required: true },
      { name: '身份证号', required: false },
      { name: '地址', required: false },
      { name: '过敏史', required: false },
      { name: '既往史', required: false }
    ]
  },
  {
    type: 'DRUG',
    name: '药品字典',
    icon: 'Collection',
    desc: '批量导入药品字典及临床属性，按「药品编码」匹配。',
    columns: [
      { name: '药品编码', required: true },
      { name: '名称', required: true },
      { name: '拼音码', required: false },
      { name: '分类', required: true },
      { name: '规格', required: false },
      { name: '单位', required: true },
      { name: '售价', required: true },
      { name: '进价', required: false },
      { name: '库存预警值', required: false },
      { name: '库存上限', required: false },
      { name: '需追溯码', required: false },
      { name: '特殊煎法', required: false },
      { name: '剂量下限', required: false },
      { name: '剂量上限', required: false },
      { name: '妊娠风险', required: false },
      { name: '毒性', required: false },
      { name: '功效', required: false }
    ]
  },
  {
    type: 'STOCK',
    name: '初始库存',
    icon: 'Box',
    desc: '按药品编码与批号导入期初库存，会写入库存流水。',
    columns: [
      { name: '药品编码', required: true },
      { name: '批号', required: true },
      { name: '生产日期', required: false },
      { name: '有效期', required: false },
      { name: '数量', required: true },
      { name: '进价', required: false },
      { name: '备注', required: false }
    ]
  },
  {
    type: 'RECORD',
    name: '历史病历',
    icon: 'Document',
    desc: '导入历史病历，自动生成「已完成」的历史就诊记录，不用于当日业务。',
    columns: [
      { name: '患者手机号', required: true },
      { name: '就诊日期', required: true },
      { name: '医生姓名', required: true },
      { name: '主诉', required: true },
      { name: '现病史', required: true },
      { name: '既往史', required: false },
      { name: '望诊', required: false },
      { name: '闻诊', required: false },
      { name: '问诊', required: false },
      { name: '切诊', required: false },
      { name: '舌象', required: false },
      { name: '脉象', required: false },
      { name: '中医病名', required: false },
      { name: '证候', required: false },
      { name: '治则治法', required: false },
      { name: '医嘱', required: false }
    ]
  }
]

const MAX_FILE_SIZE = 5 * 1024 * 1024
const ALLOWED_EXTENSIONS = ['xlsx', 'xls', 'csv']

/* ================= 状态 ================= */
const selectedType = ref('')
const uploadRef = ref(null)
const fileList = ref([])
const selectedFile = ref(null)

const previewLoading = ref(false)
const executing = ref(false)
const previewResult = ref(null)
const executeResult = ref(null)
const templateLoading = reactive({})

const confirmVisible = ref(false)
const importMode = ref('CREATE_ONLY')
const lastExecutedMode = ref('CREATE_ONLY')

const currentType = computed(
  () => IMPORT_TYPES.find((item) => item.type === selectedType.value) || null
)
const previewTypeName = computed(() => currentType.value?.name || '未选择类型')
const selectedFileName = computed(() => selectedFile.value?.name || '-')
const canPreview = computed(() => Boolean(selectedType.value && selectedFile.value))
const executedModeText = computed(() =>
  lastExecutedMode.value === 'UPSERT' ? '存在则更新（UPSERT）' : '仅新增（CREATE_ONLY）'
)

const stepActive = computed(() => {
  if (executeResult.value) return 4
  if (previewResult.value) return 3
  if (selectedFile.value) return 2
  if (selectedType.value) return 1
  return 0
})

const sampleColumns = computed(() => {
  const sample = previewResult.value?.sample || []
  const keys = []
  const seen = new Set()
  for (const row of sample) {
    for (const key of Object.keys(row || {})) {
      if (!seen.has(key)) {
        seen.add(key)
        keys.push(key)
      }
    }
  }
  return keys.map((key) => {
    const maxLen = Math.max(key.length, ...sample.map((row) => String(row?.[key] ?? '').length))
    return { prop: key, label: key, width: Math.min(240, Math.max(100, maxLen * 14)) }
  })
})

/* ================= 类型 / 文件 ================= */
function selectType(type) {
  if (selectedType.value === type) return
  selectedType.value = type
  onTypeChange()
}

function onTypeChange() {
  previewResult.value = null
  executeResult.value = null
}

function validateFile(file) {
  const name = String(file?.name || '')
  const dot = name.lastIndexOf('.')
  const ext = dot >= 0 ? name.slice(dot + 1).toLowerCase() : ''
  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    return '仅支持 .xlsx / .xls / .csv 格式的文件'
  }
  if (Number(file?.size || 0) > MAX_FILE_SIZE) {
    return '文件大小不能超过 5MB'
  }
  if (!file?.size) {
    return '文件内容为空，请检查后重新选择'
  }
  return ''
}

function onFileChange(uploadFile) {
  const raw = uploadFile?.raw
  if (!raw) {
    selectedFile.value = null
    return
  }
  const error = validateFile(raw)
  if (error) {
    ElMessage.error(error)
    selectedFile.value = null
    fileList.value = []
    uploadRef.value?.clearFiles()
    return
  }
  selectedFile.value = raw
  previewResult.value = null
  executeResult.value = null
}

function onFileRemove() {
  selectedFile.value = null
  previewResult.value = null
  executeResult.value = null
}

function onFileExceed() {
  ElMessage.warning('一次只能上传一个文件，请先移除已选择的文件')
}

/* ================= 模板下载 ================= */
function saveBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.setTimeout(() => URL.revokeObjectURL(url), 1500)
}

/* 后端若以 HTTP 200 + JSON 返回业务错误，blob 里是 JSON，这里解析出来提示 */
async function rejectJsonBlob(blob) {
  if (!blob || typeof blob.text !== 'function') return false
  const isJson = blob.type && blob.type.toLowerCase().includes('json')
  if (!isJson) return false
  try {
    const body = JSON.parse(await blob.text())
    if (body && body.code !== 0) {
      ElMessage.error(body.message || '模板下载失败')
      return true
    }
  } catch {
    return false
  }
  return false
}

async function onDownloadTemplate(item) {
  if (templateLoading[item.type]) return
  templateLoading[item.type] = true
  try {
    const result = await downloadImportTemplate(item.type)
    const blob = result instanceof Blob
      ? result
      : new Blob([result ?? ''], {
          type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        })
    if (!blob.size) {
      ElMessage.warning('模板内容为空，请稍后重试')
      return
    }
    if (await rejectJsonBlob(blob)) return
    saveBlob(blob, `方笺系统-${item.name}导入模板.xlsx`)
    ElMessage.success(`${item.name}导入模板已开始下载`)
  } catch {
    /* 错误提示由 request 拦截器统一处理 */
  } finally {
    templateLoading[item.type] = false
  }
}

/* ================= 预览 / 导入 ================= */
function toCount(value) {
  const num = Number(value)
  return Number.isFinite(num) && num >= 0 ? num : 0
}

function normalizeErrors(raw) {
  if (!Array.isArray(raw)) return []
  return raw
    .filter((item) => item && typeof item === 'object')
    .map((item) => ({
      row: item.row ?? item.rowNum ?? item.line ?? '-',
      field: item.field || '-',
      message: item.message || item.reason || '未通过校验'
    }))
}

function normalizePreview(data) {
  const total = toCount(data?.total)
  const valid = toCount(data?.valid)
  const invalid = data?.invalid === undefined || data?.invalid === null
    ? Math.max(0, total - valid)
    : toCount(data.invalid)
  const errors = normalizeErrors(data?.errors)
  const sample = Array.isArray(data?.sample)
    ? data.sample.filter((item) => item && typeof item === 'object')
    : []
  return { total, valid, invalid, errors, sample }
}

async function onPreview() {
  if (previewLoading.value || executing.value) return
  if (!selectedType.value) {
    ElMessage.warning('请先选择导入类型')
    return
  }
  if (!selectedFile.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }
  previewLoading.value = true
  try {
    const data = await previewImport(selectedType.value, selectedFile.value)
    previewResult.value = normalizePreview(data)
    executeResult.value = null
    if (previewResult.value.total === 0 && previewResult.value.errors.length === 0) {
      ElMessage.warning('未解析到数据行，请确认文件内容与模板一致')
    } else {
      ElMessage.success('文件校验完成，请核对预览结果')
    }
  } catch {
    previewResult.value = null
  } finally {
    previewLoading.value = false
  }
}

async function openImportConfirm() {
  if (!previewResult.value) {
    ElMessage.warning('请先上传预览，确认校验结果后再导入')
    return
  }
  if (previewResult.value.invalid > 0) {
    try {
      await ElMessageBox.confirm(
        `当前有 ${previewResult.value.invalid} 行未通过校验，导入时这些行会被跳过并返回失败原因，其他有效行会正常写入。是否继续？`,
        '仍有未通过校验的行',
        { type: 'warning', confirmButtonText: '继续导入', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  importMode.value = 'CREATE_ONLY'
  confirmVisible.value = true
}

async function onExecute() {
  if (executing.value) return
  if (!selectedType.value || !selectedFile.value) {
    ElMessage.warning('请先选择导入类型和文件')
    return
  }
  executing.value = true
  try {
    const data = await executeImport(selectedType.value, selectedFile.value, importMode.value)
    lastExecutedMode.value = importMode.value
    executeResult.value = {
      inserted: toCount(data?.inserted),
      updated: toCount(data?.updated),
      skipped: toCount(data?.skipped),
      failed: toCount(data?.failed),
      errors: normalizeErrors(data?.errors)
    }
    confirmVisible.value = false
    ElMessage.success(
      `导入完成：新增 ${executeResult.value.inserted}，更新 ${executeResult.value.updated}，跳过 ${executeResult.value.skipped}，失败 ${executeResult.value.failed}`
    )
  } catch {
    /* 错误提示由 request 拦截器统一处理，弹窗保持打开便于重试 */
  } finally {
    executing.value = false
  }
}

function resetAll() {
  if (previewLoading.value || executing.value) return
  selectedFile.value = null
  fileList.value = []
  uploadRef.value?.clearFiles()
  previewResult.value = null
  executeResult.value = null
  importMode.value = 'CREATE_ONLY'
  ElMessage.success('已清空当前文件与预览结果，可重新选择文件')
}
</script>

<style scoped>
.import-page {
  min-height: calc(100vh - 88px);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.page-sub {
  margin: -4px 0 12px;
  line-height: 1.6;
}

.import-alert {
  margin-bottom: 16px;
}

.import-steps {
  margin-bottom: 20px;
  padding: 4px 0;
}

/* ---------- 类型卡片 ---------- */
.type-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.type-card {
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 16px;
  background: #fafcff;
  box-sizing: border-box;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
}

.type-card:hover {
  border-color: #c6e2ff;
  box-shadow: 0 2px 10px rgba(64, 158, 255, 0.08);
}

.type-card.selected {
  border-color: #67c23a;
  background: #f6fcf3;
  box-shadow: 0 0 0 1px #67c23a inset;
}

.card-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
}

.card-icon {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: #ecf5ff;
  color: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  flex-shrink: 0;
}

.card-title-box {
  min-width: 0;
  flex: 1;
}

.card-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  line-height: 1.4;
}

.type-code {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
}

.card-desc {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.6;
  color: #606266;
}

/* ---------- 模板列摘要 ---------- */
.column-summary {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  margin-bottom: 12px;
  border-radius: 4px;
  background: #f5f7fa;
}

.summary-label {
  font-size: 12px;
  color: #909399;
  margin-right: 2px;
}

.col-chip {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e4e7ed;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
  white-space: nowrap;
}

.col-chip.required {
  border-color: #f5dab1;
  background: #fdf6ec;
  color: #b88230;
}

.req-star {
  color: #f56c6c;
  margin-left: 1px;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* ---------- 上传区 ---------- */
.section-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  padding-left: 8px;
  border-left: 3px solid #1f8a6d;
  line-height: 1.15;
}

.flex-spacer {
  flex: 1;
}

.upload-form {
  max-width: 720px;
}

.type-select {
  width: 260px;
}

.form-tip {
  margin-left: 10px;
  line-height: 1.6;
}

.uploader {
  width: 100%;
  max-width: 560px;
}

.uploader :deep(.el-upload) {
  width: 100%;
}

.uploader :deep(.el-upload-dragger) {
  padding: 22px 10px;
}

.upload-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 4px;
}

/* ---------- 预览 ---------- */
.stat-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.result-stats {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.stat-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 16px;
  background: #fafcff;
  box-sizing: border-box;
  min-height: 88px;
}

.stat-ok {
  border-color: #c2e7b0;
  background: #f6fcf3;
}

.stat-bad {
  border-color: #fbc4c4;
  background: #fef6f6;
}

.stat-label {
  margin-bottom: 8px;
}

.stat-value {
  font-size: 26px;
  line-height: 1.2;
  color: #303133;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.stat-extra {
  margin-top: 6px;
}

.sub-block {
  margin-bottom: 16px;
}

.sub-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding-top: 4px;
}

.warn-text {
  color: #e6a23c;
  font-size: 12px;
  line-height: 1.6;
}

/* ---------- 结果 / 注意事项 ---------- */
.result-ok {
  margin-bottom: 12px;
}

.result-audit {
  margin-top: 4px;
}

.notes-card {
  margin-bottom: 4px;
}

.notes-list {
  margin: 0;
  padding-left: 20px;
  color: #606266;
  font-size: 13px;
  line-height: 1.9;
}

.notes-list b {
  color: #303133;
}

/* ---------- 确认弹窗 ---------- */
.confirm-warn {
  margin-top: 12px;
}

.confirm-body :deep(.el-descriptions__label) {
  width: 88px;
}

.mode-block {
  margin-top: 16px;
}

.mode-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.mode-tip {
  margin-top: 6px;
  line-height: 1.6;
}

@media (max-width: 1100px) {
  .type-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .result-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .stat-row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
