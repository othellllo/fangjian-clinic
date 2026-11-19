<template>
  <div class="page-card">
    <div class="page-toolbar">
      <el-radio-group v-model="mode">
        <el-radio-button value="daily">日报</el-radio-button>
        <el-radio-button value="range">区间统计</el-radio-button>
        <el-radio-button value="insurance">医保统计</el-radio-button>
      </el-radio-group>

      <template v-if="mode === 'daily'">
        <el-date-picker
          v-model="dailyDate"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          placeholder="选择日期"
          style="width: 160px"
        />
        <el-button type="primary" :loading="loading" @click="loadDaily">查询</el-button>
      </template>

      <template v-else-if="mode === 'range'">
        <el-date-picker
          v-model="rangeStart"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          placeholder="起始日期"
          style="width: 160px"
        />
        <span class="text-muted">至</span>
        <el-date-picker
          v-model="rangeEnd"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          placeholder="结束日期"
          style="width: 160px"
        />
        <el-button type="primary" :loading="loading" @click="loadRange">查询</el-button>
      </template>

      <template v-else>
        <el-date-picker
          v-model="insuranceStart"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          placeholder="起始日期"
          style="width: 160px"
        />
        <span class="text-muted">至</span>
        <el-date-picker
          v-model="insuranceEnd"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          placeholder="结束日期"
          style="width: 160px"
        />
        <el-button type="primary" :loading="insuranceLoading" @click="loadInsurance">查询</el-button>
        <span class="text-muted">口径：医保结算记录（insurance_settlement）</span>
      </template>
    </div>

    <!-- 日报 -->
    <template v-if="mode === 'daily'">
      <div v-loading="loading" class="stat-row">
        <div v-for="card in statCards" :key="card.label" class="stat-card">
          <div class="stat-label text-muted">{{ card.label }}</div>
          <div class="stat-value money">{{ card.value }}</div>
          <div v-if="card.extra" class="stat-extra text-muted">{{ card.extra }}</div>
        </div>
      </div>

      <div class="chart-row">
        <div class="chart-box">
          <div class="chart-title">支付方式分布</div>
          <div v-if="methodRows.length" ref="pieRef" class="chart-canvas"></div>
          <el-empty v-else class="chart-empty" description="暂无数据" :image-size="72" />
        </div>
        <div class="chart-box">
          <div class="chart-title">药品销量 Top10</div>
          <div v-if="drugTop.length" ref="drugRef" class="chart-canvas"></div>
          <el-empty v-else class="chart-empty" description="暂无数据" :image-size="72" />
        </div>
      </div>

      <div class="section">
        <div class="page-title">医生绩效</div>
        <el-table v-loading="loading" :data="doctorRows" border size="small">
          <el-table-column label="医生" min-width="120">
            <template #default="{ row }">{{ row.doctorName || row.name || '-' }}</template>
          </el-table-column>
          <el-table-column label="就诊数" width="110" align="right">
            <template #default="{ row }">{{ row.visitCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="处方数" width="110" align="right">
            <template #default="{ row }">{{ row.prescriptionCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="营收（元）" width="150" align="right">
            <template #default="{ row }">
              <span class="money">{{ formatMoney(row.revenue) }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无数据" :image-size="70" />
          </template>
        </el-table>
      </div>
    </template>

    <!-- 区间统计 -->
    <template v-else-if="mode === 'range'">
      <div class="chart-box range-chart">
        <div class="chart-title">每日营收与就诊量趋势</div>
        <div v-if="rangeRows.length" ref="trendRef" class="chart-canvas"></div>
        <el-empty v-else class="chart-empty" description="暂无数据" :image-size="72" />
      </div>

      <div class="section">
        <div class="page-title">区间明细</div>
        <el-table v-loading="loading" :data="rangeRows" border size="small">
          <el-table-column type="index" label="#" width="60" align="center" />
          <el-table-column label="日期" min-width="140">
            <template #default="{ row }">{{ formatDate(row.date) }}</template>
          </el-table-column>
          <el-table-column label="营收（元）" width="160" align="right">
            <template #default="{ row }">
              <span class="money">{{ formatMoney(row.revenue) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="就诊量" width="120" align="right">
            <template #default="{ row }">{{ row.visitCount ?? 0 }}</template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无数据" :image-size="70" />
          </template>
        </el-table>
      </div>
    </template>

    <!-- 医保统计 -->
    <template v-else>
      <div class="section">
        <div class="page-title">区间医保结算统计</div>
        <el-table v-loading="insuranceLoading" :data="insuranceRows" border size="small"
          empty-text="所选区间暂无医保结算数据">
          <el-table-column label="结算渠道" min-width="180">
            <template #default="{ row }">{{ providerText(row.providerCode) }}</template>
          </el-table-column>
          <el-table-column label="笔数" width="100" align="right">
            <template #default="{ row }">{{ row.count ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="医保支付（元）" width="150" align="right">
            <template #default="{ row }">
              <span class="money">{{ formatMoney(row.insuranceAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="个人自付（元）" width="150" align="right">
            <template #default="{ row }">
              <span class="money">{{ formatMoney(row.selfPayAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="合计（元）" width="140" align="right">
            <template #default="{ row }">
              <span class="money">{{ formatMoney(row.total) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="均次费用（元）" width="150" align="right">
            <template #default="{ row }">
              <span class="money">{{ formatMoney(row.avgAmount) }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="所选区间暂无医保结算数据" :image-size="70" />
          </template>
        </el-table>
      </div>

      <div class="section insurance-daily">
        <div class="page-toolbar daily-toolbar">
          <div class="page-title no-margin">当日医保汇总</div>
          <el-date-picker v-model="insuranceDate" type="date" value-format="YYYY-MM-DD" :clearable="false"
            placeholder="选择日期" style="width: 160px" @change="loadInsuranceDaily" />
          <el-button :loading="insuranceDailyLoading" @click="loadInsuranceDaily">刷新</el-button>
          <span class="text-muted">当日医保结算明细汇总与支付方式分布</span>
        </div>

        <div v-loading="insuranceDailyLoading" class="stat-row">
          <div v-for="card in insuranceDailyCards" :key="card.label" class="stat-card">
            <div class="stat-label text-muted">{{ card.label }}</div>
            <div class="stat-value money">{{ card.value }}</div>
            <div v-if="card.extra" class="stat-extra text-muted">{{ card.extra }}</div>
          </div>
        </div>

        <el-table v-if="insuranceByMethod.length" :data="insuranceByMethod" border size="small" class="method-table">
          <el-table-column label="支付方式" min-width="160">
            <template #default="{ row }">{{ PAY_METHOD_TEXT[row.method] || row.method || '其他' }}</template>
          </el-table-column>
          <el-table-column label="笔数" width="120" align="right">
            <template #default="{ row }">{{ row.count ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="金额（元）" width="180" align="right">
            <template #default="{ row }">
              <span class="money">{{ formatMoney(row.amount) }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="当日暂无医保结算数据" :image-size="70" />
          </template>
        </el-table>
        <el-empty v-else description="当日暂无医保结算数据" :image-size="70" />
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { dailyReport, rangeReport, doctorPerformance, drugSales, insuranceStats, insuranceDaily } from '@/api'
import { formatMoney, formatDate, todayStr, PAY_METHOD_TEXT } from '@/utils/format'

const mode = ref('daily')
const loading = ref(false)

const dailyDate = ref(todayStr())
const rangeStart = ref(daysAgo(6))
const rangeEnd = ref(todayStr())

const daily = ref(null)
const doctorRows = ref([])
const drugRows = ref([])
const rangeRows = ref([])

/* 医保统计 */
const insuranceStart = ref(monthStart())
const insuranceEnd = ref(todayStr())
const insuranceDate = ref(todayStr())
const insuranceRows = ref([])
const insuranceDailyData = ref(null)
const insuranceLoading = ref(false)
const insuranceDailyLoading = ref(false)

/* 图表实例（非响应式） */
const pieRef = ref(null)
const drugRef = ref(null)
const trendRef = ref(null)
let pieChart = null
let drugChart = null
let trendChart = null

function daysAgo(count) {
  const d = new Date()
  d.setDate(d.getDate() - count)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function monthStart() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-01`
}

/* ---------------- 数据加载 ---------------- */
async function loadDaily() {
  if (!dailyDate.value) {
    ElMessage.warning('请选择日报日期')
    return
  }
  loading.value = true
  try {
    const data = await dailyReport({ date: dailyDate.value })
    daily.value = data || null
    await fillDoctorRows(data)
    await fillDrugRows(data)
  } catch (e) {
    daily.value = null
    doctorRows.value = []
    drugRows.value = []
  } finally {
    loading.value = false
    await nextTick()
    syncCharts()
  }
}

async function loadRange() {
  if (!rangeStart.value || !rangeEnd.value) {
    ElMessage.warning('请选择起止日期')
    return
  }
  if (rangeStart.value > rangeEnd.value) {
    ElMessage.warning('起始日期不能晚于结束日期')
    return
  }
  loading.value = true
  try {
    const data = await rangeReport({ start: rangeStart.value, end: rangeEnd.value })
    rangeRows.value = Array.isArray(data) ? data : []
  } catch (e) {
    rangeRows.value = []
  } finally {
    loading.value = false
    await nextTick()
    syncCharts()
  }
}

/* ---------------- 医保统计 ---------------- */
const PROVIDER_TEXT = {
  LOCAL: '本地医保',
  NOOP: '未开通（桩）',
  DEFAULT: '默认渠道'
}

function providerText(code) {
  if (!code) return '未标识渠道'
  return PROVIDER_TEXT[code] || String(code)
}

function normalizeInsuranceRows(data) {
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.list)) return data.list
  if (Array.isArray(data?.items)) return data.items
  return []
}

async function loadInsurance() {
  if (!insuranceStart.value || !insuranceEnd.value) {
    ElMessage.warning('请选择起止日期')
    return
  }
  if (insuranceStart.value > insuranceEnd.value) {
    ElMessage.warning('起始日期不能晚于结束日期')
    return
  }
  insuranceLoading.value = true
  try {
    const data = await insuranceStats({ start: insuranceStart.value, end: insuranceEnd.value })
    insuranceRows.value = normalizeInsuranceRows(data)
  } catch (e) {
    insuranceRows.value = []
  } finally {
    insuranceLoading.value = false
  }
}

async function loadInsuranceDaily() {
  if (!insuranceDate.value) {
    ElMessage.warning('请选择日期')
    return
  }
  insuranceDailyLoading.value = true
  try {
    insuranceDailyData.value = await insuranceDaily({ date: insuranceDate.value })
  } catch (e) {
    insuranceDailyData.value = null
  } finally {
    insuranceDailyLoading.value = false
  }
}

function pickNumber(source, keys) {
  if (!source) return null
  for (const key of keys) {
    const value = source[key]
    if (value === null || value === undefined || value === '') continue
    const num = Number(value)
    if (Number.isFinite(num)) return num
  }
  return null
}

function moneyText(value, { withSymbol = true } = {}) {
  if (value === null || value === undefined || value === '') return '--'
  const num = Number(value)
  if (!Number.isFinite(num)) return '--'
  return withSymbol ? `¥ ${formatMoney(num)}` : formatMoney(num)
}

const insuranceSummary = computed(() => {
  const data = insuranceDailyData.value
  if (!data || Array.isArray(data)) return null
  return data.summary && typeof data.summary === 'object' ? data.summary : data
})

const insuranceDailyCards = computed(() => {
  const summary = insuranceSummary.value
  return [
    {
      label: '结算笔数',
      value: summary ? String(pickNumber(summary, ['count', 'settlementCount', 'totalCount']) ?? 0) : '--',
      extra: ''
    },
    {
      label: '医保支付（元）',
      value: moneyText(pickNumber(summary, ['insuranceAmount', 'insurancePayAmount', 'insuranceTotal'])),
      extra: ''
    },
    {
      label: '个人自付（元）',
      value: moneyText(pickNumber(summary, ['selfPayAmount', 'selfPay', 'personalAmount'])),
      extra: ''
    },
    {
      label: '合计（元）',
      value: moneyText(pickNumber(summary, ['total', 'totalAmount', 'amount'])),
      extra:
        summary && pickNumber(summary, ['avgAmount', 'averageAmount']) !== null
          ? `均次 ${formatMoney(pickNumber(summary, ['avgAmount', 'averageAmount']))} 元`
          : ''
    }
  ]
})

const insuranceByMethod = computed(() => {
  const data = insuranceDailyData.value
  if (!data) return []
  const raw = Array.isArray(data)
    ? data
    : data.byMethod ?? data.byMethods ?? data.paymentMethods ?? []
  const arr = Array.isArray(raw)
    ? raw
    : raw && typeof raw === 'object'
      ? Object.entries(raw).map(([method, amount]) => ({ method, amount }))
      : []
  return arr
    .map((item) => ({
      method: item.method || item.payMethod || item.name || 'OTHER',
      count: Number(item.count ?? item.settlementCount ?? 0),
      amount: Number(item.amount ?? item.insuranceAmount ?? item.total ?? 0)
    }))
    .filter((item) => item.method && (item.count > 0 || item.amount > 0))
})


async function fillDoctorRows(data) {
  const byDoctor = Array.isArray(data?.byDoctor) ? data.byDoctor : []
  if (byDoctor.length) {
    doctorRows.value = byDoctor
    return
  }
  try {
    const perf = await doctorPerformance({ start: dailyDate.value, end: dailyDate.value })
    doctorRows.value = Array.isArray(perf) ? perf : []
  } catch (e) {
    doctorRows.value = []
  }
}

async function fillDrugRows(data) {
  const raw = Array.isArray(data?.byDrugs)
    ? data.byDrugs
    : Array.isArray(data?.topDrugs)
      ? data.topDrugs
      : []
  if (raw.length) {
    drugRows.value = raw
    return
  }
  try {
    const sales = await drugSales({ start: dailyDate.value, end: dailyDate.value })
    drugRows.value = Array.isArray(sales) ? sales : []
  } catch (e) {
    drugRows.value = []
  }
}

/* ---------------- 汇总视图 ---------------- */
const statCards = computed(() => {
  const data = daily.value
  return [
    {
      label: '营收（元）',
      value: data ? `¥ ${formatMoney(data.revenue)}` : '--',
      extra: data ? `退费 ¥ ${formatMoney(data.refund)}` : ''
    },
    { label: '挂号数', value: data ? String(data.registerCount ?? 0) : '--', extra: '' },
    { label: '就诊数', value: data ? String(data.visitCount ?? 0) : '--', extra: '' },
    { label: '处方数', value: data ? String(data.prescriptionCount ?? 0) : '--', extra: '' }
  ]
})

function toMethodRows(raw) {
  if (!raw) return []
  const arr = Array.isArray(raw)
    ? raw
    : typeof raw === 'object'
      ? Object.entries(raw).map(([method, amount]) => ({ method, amount }))
      : []
  return arr
    .map((item) => ({
      name: PAY_METHOD_TEXT[item.method] || item.method || '其他',
      value: Number(item.amount ?? item.value ?? 0)
    }))
    .filter((item) => item.value > 0)
}

const methodRows = computed(() => toMethodRows(daily.value?.byMethod))

const drugTop = computed(() => {
  const arr = Array.isArray(drugRows.value) ? drugRows.value : []
  return arr
    .map((row) => ({
      name: row.drugName || row.name || '-',
      quantity: Number(row.quantity ?? 0),
      amount: Number(row.amount ?? 0)
    }))
    .filter((row) => row.quantity > 0 || row.amount > 0)
    .sort((a, b) => b.quantity - a.quantity || b.amount - a.amount)
    .slice(0, 10)
})

/* ---------------- 图表渲染 ---------------- */
function disposeChart(chart) {
  if (chart) chart.dispose()
}

function syncCharts() {
  if (mode.value === 'daily') {
    renderPieChart()
    renderDrugChart()
    disposeChart(trendChart)
    trendChart = null
  } else if (mode.value === 'range') {
    renderTrendChart()
    disposeChart(pieChart)
    pieChart = null
    disposeChart(drugChart)
    drugChart = null
  } else {
    disposeChart(pieChart)
    disposeChart(drugChart)
    disposeChart(trendChart)
    pieChart = null
    drugChart = null
    trendChart = null
  }
}

function renderPieChart() {
  if (!pieRef.value || !methodRows.value.length) {
    disposeChart(pieChart)
    pieChart = null
    return
  }
  if (!pieChart) pieChart = echarts.init(pieRef.value)
  pieChart.setOption(
    {
      tooltip: {
        trigger: 'item',
        formatter: (params) => `${params.name}<br/>¥ ${formatMoney(params.value)}（${params.percent}%）`
      },
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          type: 'pie',
          radius: ['42%', '64%'],
          center: ['50%', '44%'],
          itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 4 },
          label: { formatter: '{b} {d}%', fontSize: 12 },
          data: methodRows.value.map((item) => ({ name: item.name, value: item.value }))
        }
      ]
    },
    true
  )
  pieChart.resize()
}

function renderDrugChart() {
  if (!drugRef.value || !drugTop.value.length) {
    disposeChart(drugChart)
    drugChart = null
    return
  }
  if (!drugChart) drugChart = echarts.init(drugRef.value)
  const names = drugTop.value.map((item) => item.name)
  const quantities = drugTop.value.map((item) => item.quantity)
  drugChart.setOption(
    {
      grid: { left: 8, right: 48, top: 16, bottom: 8, containLabel: true },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params) => {
          const item = drugTop.value[params[0]?.dataIndex]
          if (!item) return ''
          return `${item.name}<br/>销量：${item.quantity}<br/>金额：¥ ${formatMoney(item.amount)}`
        }
      },
      xAxis: {
        type: 'value',
        splitLine: { lineStyle: { type: 'dashed' } }
      },
      yAxis: {
        type: 'category',
        inverse: true,
        data: names,
        axisTick: { show: false },
        axisLabel: { width: 110, overflow: 'truncate' }
      },
      series: [
        {
          type: 'bar',
          barMaxWidth: 18,
          data: quantities,
          itemStyle: { color: '#409eff', borderRadius: [0, 4, 4, 0] },
          label: { show: true, position: 'right', fontSize: 11 }
        }
      ]
    },
    true
  )
  drugChart.resize()
}

function renderTrendChart() {
  if (!trendRef.value || !rangeRows.value.length) {
    disposeChart(trendChart)
    trendChart = null
    return
  }
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  const dates = rangeRows.value.map((row) => formatDate(row.date))
  const revenues = rangeRows.value.map((row) => Number(row.revenue ?? 0))
  const visits = rangeRows.value.map((row) => Number(row.visitCount ?? 0))
  trendChart.setOption(
    {
      tooltip: { trigger: 'axis' },
      legend: { data: ['营收', '就诊量'], top: 0 },
      grid: { left: 8, right: 8, top: 44, bottom: 8, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: dates,
        axisTick: { show: false }
      },
      yAxis: [
        {
          type: 'value',
          name: '营收（元）',
          splitLine: { lineStyle: { type: 'dashed' } }
        },
        {
          type: 'value',
          name: '就诊量',
          splitLine: { show: false },
          minInterval: 1
        }
      ],
      series: [
        {
          name: '营收',
          type: 'line',
          smooth: true,
          yAxisIndex: 0,
          data: revenues,
          itemStyle: { color: '#409eff' },
          areaStyle: { opacity: 0.08 }
        },
        {
          name: '就诊量',
          type: 'line',
          smooth: true,
          yAxisIndex: 1,
          data: visits,
          itemStyle: { color: '#67c23a' }
        }
      ]
    },
    true
  )
  trendChart.resize()
}

function handleResize() {
  ;[pieChart, drugChart, trendChart].forEach((chart) => {
    if (chart) chart.resize()
  })
}

/* ---------------- 生命周期 ---------------- */
watch(mode, async (value) => {
  await nextTick()
  syncCharts()
  if (value === 'range') {
    await loadRange()
  } else if (value === 'insurance') {
    await Promise.all([loadInsurance(), loadInsuranceDaily()])
  } else {
    await loadDaily()
  }
})

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadDaily()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  disposeChart(pieChart)
  disposeChart(drugChart)
  disposeChart(trendChart)
  pieChart = null
  drugChart = null
  trendChart = null
})
</script>

<style scoped>
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 16px;
  background: #fafcff;
  min-height: 96px;
  box-sizing: border-box;
}

.stat-label {
  margin-bottom: 8px;
}

.stat-value {
  font-size: 26px;
  line-height: 1.2;
  color: #303133;
}

.stat-extra {
  margin-top: 8px;
}

.chart-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.chart-box {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  box-sizing: border-box;
}

.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.chart-canvas {
  width: 100%;
  height: 320px;
}

.chart-empty {
  height: 320px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.range-chart {
  margin-bottom: 16px;
}

.section {
  margin-top: 4px;
}

.insurance-daily {
  margin-top: 20px;
}

.daily-toolbar {
  margin-bottom: 12px;
}

.no-margin {
  margin: 0;
}

.method-table {
  margin-top: 4px;
}

@media (max-width: 1200px) {
  .stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
