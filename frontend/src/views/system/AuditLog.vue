<template>
  <div class="audit-page">
    <div class="page-card">
      <div class="page-toolbar">
        <el-select
          v-model="query.module"
          placeholder="全部模块"
          clearable
          style="width: 160px"
          @change="handleQuery"
          @clear="handleQuery"
        >
          <el-option label="全部模块" value="" />
          <el-option v-for="item in modules" :key="item" :label="item" :value="item" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="操作人 / 动作"
          clearable
          style="width: 220px"
          :prefix-icon="Search"
          @keyup.enter="handleQuery"
          @clear="handleQuery"
        />
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 280px"
          @change="handleQuery"
        />
        <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
        <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="loadList">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe class="audit-table">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作人" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="operator">
              <span class="op-name">{{ row.realName || '-' }}</span>
              <span v-if="row.username" class="text-muted op-account">{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="96" align="center">
          <template #default="{ row }">
            {{ ROLE_TEXT[row.role] || row.role || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="模块" width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.module || '-' }}</template>
        </el-table-column>
        <el-table-column label="动作" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.action || '-' }}</template>
        </el-table-column>
        <el-table-column label="对象" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ targetText(row) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="resultTag(row.result)">
              {{ resultText(row.result) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="IP" width="132" show-overflow-tooltip>
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="88" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :image-size="80" description="暂无审计日志" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        class="pagination"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="loadList"
        @current-change="loadList"
      />
    </div>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="审计详情" width="680px" :close-on-click-modal="false">
      <template v-if="currentLog">
        <el-descriptions :column="2" border size="small" class="detail-desc">
          <el-descriptions-item label="时间">{{ formatDateTime(currentLog.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="操作人">
            {{ currentLog.realName || '-' }}
            <span v-if="currentLog.username" class="text-muted">（{{ currentLog.username }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="角色">
            {{ ROLE_TEXT[currentLog.role] || currentLog.role || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="模块">{{ currentLog.module || '-' }}</el-descriptions-item>
          <el-descriptions-item label="动作">{{ currentLog.action || '-' }}</el-descriptions-item>
          <el-descriptions-item label="对象">{{ targetText(currentLog) }}</el-descriptions-item>
          <el-descriptions-item label="结果">
            <el-tag size="small" :type="resultTag(currentLog.result)">
              {{ resultText(currentLog.result) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="IP">{{ currentLog.ip || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-title">详情</div>
        <div class="detail-text">{{ currentLog.detail || '无详情' }}</div>
      </template>
      <el-empty v-else description="暂无详情" />
      <template #footer>
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import { auditModules, pageAuditLogs } from '@/api'
import { ROLE_TEXT, formatDateTime } from '@/utils/format'

const RESULT_TEXT = { SUCCESS: '成功', FAIL: '失败' }
const RESULT_TAG = { SUCCESS: 'success', FAIL: 'danger' }

/* ---------------- 筛选条件 ---------------- */
const query = reactive({ module: '', keyword: '' })
const dateRange = ref(null)

/* ---------------- 列表 ---------------- */
const modules = ref([])
const list = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

function resultText(result) {
  return RESULT_TEXT[result] || result || '-'
}

function resultTag(result) {
  return RESULT_TAG[result] || 'info'
}

function targetText(row) {
  if (!row) return '-'
  const type = row.targetType || ''
  const id = row.targetId
  if (!type && (id === null || id === undefined || id === '')) return '-'
  if (id === null || id === undefined || id === '') return type || '-'
  return `${type || '-'}#${id}`
}

async function loadModules() {
  try {
    const data = await auditModules()
    modules.value = (Array.isArray(data) ? data : data?.list || [])
      .filter((item) => item !== null && item !== undefined && String(item).trim() !== '')
      .map((item) => String(item))
  } catch {
    modules.value = []
  }
}

async function loadList() {
  loading.value = true
  try {
    const data = await pageAuditLogs({
      module: query.module || '',
      keyword: query.keyword.trim(),
      start: dateRange.value?.[0] || '',
      end: dateRange.value?.[1] || '',
      page: page.value,
      size: size.value
    })
    list.value = Array.isArray(data?.list) ? data.list : []
    total.value = Number(data?.total ?? 0)
  } catch {
    /* 错误提示由 request 拦截器统一处理，保留已有列表 */
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  page.value = 1
  loadList()
}

function handleReset() {
  query.module = ''
  query.keyword = ''
  dateRange.value = null
  page.value = 1
  loadList()
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false)
const currentLog = ref(null)

function openDetail(row) {
  currentLog.value = row
  detailVisible.value = true
}

onMounted(() => {
  loadModules()
  loadList()
})
</script>

<style scoped>
.audit-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.audit-table {
  width: 100%;
}

.operator {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.op-name {
  font-weight: 600;
}

.op-account {
  font-size: 12px;
}

.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.detail-desc {
  margin-bottom: 14px;
}

.detail-title {
  font-size: 14px;
  font-weight: 600;
  padding-left: 8px;
  border-left: 3px solid #1f8a6d;
  line-height: 1.1;
  margin-bottom: 8px;
}

.detail-text {
  max-height: 320px;
  overflow: auto;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #f8f9fa;
  color: #303133;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
