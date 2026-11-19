<template>
  <div class="page-card drug-catalog">
    <div class="page-title">药品字典</div>

    <div class="page-toolbar">
      <el-input v-model="query.keyword" class="filter-keyword" clearable placeholder="编码 / 名称 / 拼音码"
        @keyup.enter="onSearch" />
      <el-select v-model="query.category" class="filter-category" clearable placeholder="全部分类">
        <el-option v-for="(text, key) in DRUG_CATEGORY_TEXT" :key="key" :label="text" :value="key" />
      </el-select>
      <el-button type="primary" @click="onSearch">
        <el-icon><Search /></el-icon>
        <span>查询</span>
      </el-button>
      <el-button @click="onReset">
        <el-icon><Refresh /></el-icon>
        <span>重置</span>
      </el-button>
      <div class="flex-spacer" />
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        <span>新增药品</span>
      </el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe class="drug-table" empty-text="暂无药品数据">
      <el-table-column prop="code" label="编码" width="120" show-overflow-tooltip />
      <el-table-column prop="name" label="药品名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="pinyin" label="拼音码" width="110" show-overflow-tooltip />
      <el-table-column label="分类" width="110">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="categoryTag(row.category)">
            {{ DRUG_CATEGORY_TEXT[row.category] || row.category || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="spec" label="规格" min-width="130" show-overflow-tooltip />
      <el-table-column prop="unit" label="单位" width="80" />
      <el-table-column label="售价" width="100" align="right">
        <template #default="{ row }">
          <span class="money">{{ formatMoney(row.price) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="进价" width="100" align="right">
        <template #default="{ row }">
          <span class="money">{{ formatMoney(row.costPrice) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="库存预警值" width="110" align="right">
        <template #default="{ row }">
          {{ row.stockWarn ?? 0 }}
        </template>
      </el-table-column>
      <el-table-column label="当前库存" width="110" align="right">
        <template #default="{ row }">
          <el-tag v-if="Number(row.stockQty || 0) === 0" size="small" type="danger" effect="dark">
            0 · 缺货
          </el-tag>
          <el-tag v-else-if="Number(row.stockQty) < Number(row.stockWarn || 0)" size="small" type="warning" effect="plain">
            {{ row.stockQty }} · 偏低
          </el-tag>
          <span v-else>{{ row.stockQty ?? 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="isEnabled(row) ? 'success' : 'info'">
            {{ isEnabled(row) ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" background
        @size-change="onSizeChange" @current-change="load" />
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑药品' : '新增药品'" width="680px"
      :close-on-click-modal="false" @closed="onDialogClosed">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="药品编码" prop="code">
              <el-input v-model="form.code" placeholder="如 YP0001" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="药品名称" prop="name">
              <el-input v-model="form.name" placeholder="药品通用名" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="拼音码" prop="pinyin">
              <el-input v-model="form.pinyin" placeholder="如 BZH" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-select v-model="form.category" class="full-width" placeholder="请选择分类">
                <el-option v-for="(text, key) in DRUG_CATEGORY_TEXT" :key="key" :label="text" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格" prop="spec">
              <el-input v-model="form.spec" placeholder="如 10g/袋" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位" prop="unit">
              <el-input v-model="form.unit" placeholder="如 袋 / 盒 / 克" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="售价" prop="price">
              <el-input-number v-model="form.price" :min="0" :precision="2" :step="0.5" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="进价" prop="costPrice">
              <el-input-number v-model="form.costPrice" :min="0" :precision="2" :step="0.5" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存预警值" prop="stockWarn">
              <el-input-number v-model="form.stockWarn" :min="0" :precision="0" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createDrug, pageDrugs, updateDrug } from '@/api'
import { DRUG_CATEGORY_TEXT, formatMoney } from '@/utils/format'

const CATEGORY_TAG = {
  HERB: 'success',
  PATENT: 'warning',
  WESTERN: 'primary',
  MATERIAL: 'info'
}

const query = reactive({ keyword: '', category: '' })
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref([])
const loading = ref(false)

const dialogVisible = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)

const defaultForm = () => ({
  code: '',
  name: '',
  pinyin: '',
  category: 'HERB',
  spec: '',
  unit: '',
  price: 0,
  costPrice: 0,
  stockWarn: 10
})

const form = reactive(defaultForm())

const rules = {
  code: [{ required: true, message: '请输入药品编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入药品名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择药品分类', trigger: 'change' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
  price: [
    { required: true, type: 'number', message: '请输入售价', trigger: 'blur' },
    { type: 'number', min: 0, message: '售价不能小于 0', trigger: 'blur' }
  ],
  costPrice: [{ type: 'number', min: 0, message: '进价不能小于 0', trigger: 'blur' }],
  stockWarn: [{ type: 'number', min: 0, message: '库存预警值不能小于 0', trigger: 'blur' }]
}

function categoryTag(category) {
  return CATEGORY_TAG[category] || 'info'
}

function isEnabled(row) {
  const status = row?.status
  if (status === undefined || status === null) return true
  if (typeof status === 'number') return status !== 0
  if (typeof status === 'boolean') return status
  return !['0', 'DISABLED', 'INACTIVE', 'OFF'].includes(String(status).toUpperCase())
}

async function load() {
  loading.value = true
  try {
    const data = await pageDrugs({
      keyword: query.keyword.trim() || undefined,
      category: query.category || undefined,
      page: page.value,
      size: size.value
    })
    list.value = data?.list || []
    total.value = Number(data?.total ?? 0)
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function onReset() {
  query.keyword = ''
  query.category = ''
  page.value = 1
  load()
}

function onSizeChange() {
  page.value = 1
  load()
}

function openCreate() {
  editingId.value = null
  Object.assign(form, defaultForm())
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    code: row.code || '',
    name: row.name || '',
    pinyin: row.pinyin || '',
    category: row.category || 'HERB',
    spec: row.spec || '',
    unit: row.unit || '',
    price: Number(row.price ?? 0),
    costPrice: Number(row.costPrice ?? 0),
    stockWarn: Number(row.stockWarn ?? 0)
  })
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function onDialogClosed() {
  editingId.value = null
  formRef.value?.resetFields()
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  const payload = {
    code: form.code.trim(),
    name: form.name.trim(),
    pinyin: form.pinyin.trim() || null,
    category: form.category,
    spec: form.spec.trim() || null,
    unit: form.unit.trim(),
    price: Number(form.price ?? 0),
    costPrice: Number(form.costPrice ?? 0),
    stockWarn: Number(form.stockWarn ?? 0)
  }
  try {
    if (editingId.value) {
      await updateDrug(editingId.value, payload)
      ElMessage.success('药品已更新')
    } else {
      await createDrug(payload)
      ElMessage.success('药品已新增')
    }
    dialogVisible.value = false
    await load()
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.drug-catalog {
  min-height: calc(100vh - 88px);
  display: flex;
  flex-direction: column;
}

.filter-keyword {
  width: 240px;
}

.filter-category {
  width: 150px;
}

.flex-spacer {
  flex: 1;
}

.drug-table {
  width: 100%;
  flex: 1;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.full-width {
  width: 100%;
}
</style>
