<template>
  <div class="workbench">
    <!-- 顶部：当前医生 + 今日接诊统计 -->
    <div class="page-card top-bar">
      <div class="doctor-info">
        <el-icon class="doctor-icon"><FirstAidKit /></el-icon>
        <span class="doctor-name">{{ currentDoctorName }}</span>
        <span class="text-muted">{{ isAdmin ? '管理员视角 · 可切换医生' : '医生工作台' }}</span>
      </div>
      <div class="top-stats">
        <el-select v-if="isAdmin" v-model="doctorId" class="doctor-select" placeholder="选择医生"
          :loading="doctorsLoading" @change="onDoctorChange">
          <el-option v-for="d in doctors" :key="d.id"
            :label="d.realName ? d.realName + (d.title ? '（' + d.title + '）' : '') : d.username"
            :value="d.id" />
        </el-select>
        <el-tag v-if="doctorId" type="info" effect="plain">候诊 {{ queue.length }} 人</el-tag>
        <el-tag v-if="doctorId" type="success" effect="plain">今日已接诊 {{ todayVisitCount }} 人</el-tag>
      </div>
    </div>

    <div class="columns">
      <!-- 左栏：候诊队列 -->
      <div class="page-card queue-column">
        <div class="column-header">
          <span class="column-title">候诊队列</span>
          <el-button text size="small" :loading="queueLoading" @click="refreshAll">
            <el-icon><Refresh /></el-icon>
            <span>刷新</span>
          </el-button>
        </div>
        <div v-loading="queueLoading" class="column-body queue-body">
          <el-empty v-if="!queue.length" description="暂无候诊患者" :image-size="72" />
          <div v-for="item in queue" :key="item.id" class="queue-card"
            :class="{ active: item.id === selectedVisitId }" @click="onQueueCardClick(item)">
            <div class="queue-card-top">
              <span class="queue-no">{{ item.queueNo != null ? item.queueNo : '-' }} 号</span>
              <span class="queue-name">{{ item.patientName || '未知患者' }}</span>
              <el-tag size="small" effect="plain" :type="VISIT_STATUS_TAG[item.status] || 'info'">
                {{ VISIT_STATUS_TEXT[item.status] || item.status }}
              </el-tag>
            </div>
            <div class="queue-meta">
              <span>{{ GENDER_TEXT[item.patientGender] || '性别未知' }}</span>
              <span>{{ item.patientAge != null ? item.patientAge + ' 岁' : '年龄未知' }}</span>
              <span>{{ item.type === 'FIRST' ? '初诊' : '复诊' }}</span>
            </div>
            <div class="queue-complaint">主诉：{{ item.complaint || '未填写' }}</div>
            <div class="queue-actions">
              <el-button v-if="item.status === 'WAITING'" type="primary" size="small"
                :loading="startingId === item.id" @click.stop="onStartVisit(item)">接诊</el-button>
              <el-button v-else type="primary" size="small" plain @click.stop="selectVisit(item.id)">继续接诊</el-button>
              <span class="text-muted">{{ formatDateTime(item.registerTime) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 中栏：病历（中医 / 西医分开） -->
      <div class="page-card record-column">
        <div class="column-header record-header">
          <div class="record-header-left">
            <span class="column-title">病历</span>
            <el-radio-group v-model="recordType" size="small" :disabled="!currentVisit">
              <el-radio-button value="TCM">中医病历</el-radio-button>
              <el-radio-button value="WESTERN">西医病历</el-radio-button>
            </el-radio-group>
          </div>
          <div class="header-actions">
            <el-tooltip v-if="currentVisit && qcBadge" :content="qcBadgeTip" placement="bottom">
              <el-tag size="small" effect="plain" :type="qcBadge.type">{{ qcBadge.text }}</el-tag>
            </el-tooltip>
            <el-tag v-if="currentVisit && hasSavedRecord && !recordDirty" size="small" type="success" effect="plain">已保存</el-tag>
            <el-tag v-else-if="currentVisit && recordDirty" size="small" type="warning" effect="plain">未保存</el-tag>
            <el-button type="primary" size="small" :loading="savingRecord" :disabled="!currentVisit || recordLoading"
              @click="saveRecordForm">保存病历</el-button>
          </div>
        </div>

        <div v-loading="visitLoading || recordLoading" class="column-body record-body">
          <el-empty v-if="!currentVisit" description="请从左侧候诊队列选择患者开始接诊" :image-size="110" />

          <template v-else>
            <!-- 患者信息条 -->
            <div class="patient-bar">
              <div class="patient-main">
                <span class="patient-name">{{ currentVisit.patientName || '未知患者' }}</span>
                <span>{{ GENDER_TEXT[currentVisit.patientGender] || '性别未知' }}</span>
                <span>{{ currentVisit.patientAge != null ? currentVisit.patientAge + ' 岁' : '年龄未知' }}</span>
                <el-tag size="small" effect="dark" :type="VISIT_STATUS_TAG[currentVisit.status] || 'info'">
                  {{ VISIT_STATUS_TEXT[currentVisit.status] || currentVisit.status }}
                </el-tag>
              </div>
              <div class="patient-meta">
                <span>就诊号：{{ currentVisit.visitNo || '-' }}</span>
                <span>挂号时间：{{ formatDateTime(currentVisit.registerTime) }}</span>
                <span v-if="currentPatient && currentPatient.allergy" class="allergy">
                  过敏史：{{ currentPatient.allergy }}
                </span>
                <span class="text-muted">病历类型：{{ RECORD_TYPE_TEXT[recordType] }}</span>
              </div>
            </div>

            <!-- 质控结果（实时 + 保存前强制校验） -->
            <div v-if="qcIssues.length" class="qc-panel">
              <el-alert :type="qcPanelType" :closable="false" show-icon>
                <template #title>
                  {{ qcPanelTitle }}（必填 {{ qcBlockCount }} 项 / 建议 {{ qcWarnCount }} 项 / 提示 {{ qcInfoCount }} 项）
                </template>
                <ul class="issue-list">
                  <li v-for="(issue, index) in sortedQcIssues" :key="'qc-' + index" class="issue-item">
                    <el-tag size="small" effect="plain" :type="issueTagType(issue.level)">
                      {{ ISSUE_LEVEL_TEXT[issue.level] || issue.level }}
                    </el-tag>
                    <span class="issue-message">{{ issue.message }}</span>
                    <span v-if="issue.advice" class="issue-advice">建议：{{ issue.advice }}</span>
                  </li>
                </ul>
              </el-alert>
            </div>
            <el-alert v-else-if="qcError" type="warning" :closable="false" show-icon class="qc-panel"
              title="质控服务暂不可用，保存时服务端仍会执行质控" />

            <el-form :model="recordForm" label-position="top" class="record-form">
              <el-row :gutter="12">
                <template v-for="(field, index) in currentFields" :key="field.key || 'group-' + index">
                  <el-col v-if="field.group" :span="24">
                    <el-divider content-position="left">{{ field.group }}</el-divider>
                  </el-col>
                  <el-col v-else :span="field.span || 24">
                    <el-form-item>
                      <template #label>
                        <span class="field-label">
                          {{ field.label }}
                          <span v-if="field.required" class="req-star" title="必填项">*</span>
                        </span>
                        <el-tooltip v-if="field.ai" :content="aiButtonTip" placement="top">
                          <el-button link type="primary" size="small" class="ai-btn"
                            :loading="aiLoadingField === field.ai"
                            :disabled="!!aiLoadingField && aiLoadingField !== field.ai"
                            @click="onAiDraft(field)">AI 助写</el-button>
                        </el-tooltip>
                      </template>
                      <el-autocomplete v-if="field.suggest" v-model="recordForm[field.key]" value-key="term"
                        type="textarea" :rows="field.rows || 2" resize="vertical" clearable class="record-input"
                        :placeholder="field.placeholder" :maxlength="field.maxlength"
                        :show-word-limit="!!field.maxlength" :debounce="250" :trigger-on-focus="false"
                        :fetch-suggestions="suggestFetchers[field.suggest]">
                        <template #default="{ item }">
                          <div class="suggest-row">
                            <span class="suggest-term">{{ item.term }}</span>
                            <el-tag size="small" effect="plain" :type="item.source === 'SYSTEM' ? 'success' : 'info'">
                              {{ SOURCE_TEXT[item.source] || '其他来源' }}
                            </el-tag>
                            <span v-if="item.pinyin" class="suggest-pinyin">{{ item.pinyin }}</span>
                          </div>
                        </template>
                      </el-autocomplete>
                      <el-input v-else v-model="recordForm[field.key]"
                        :type="field.single ? 'text' : 'textarea'" :rows="field.rows || 3"
                        resize="vertical" clearable class="record-input" :placeholder="field.placeholder"
                        :maxlength="field.maxlength" :show-word-limit="!!field.maxlength" />
                    </el-form-item>
                  </el-col>
                </template>
              </el-row>
            </el-form>

            <div class="record-footer">
              <el-button type="success" :loading="finishing" @click="onFinishVisit">完成就诊</el-button>
              <span class="text-muted">完成后该患者将移出候诊队列</span>
            </div>
          </template>
        </div>
      </div>

      <!-- 右栏：处方（含用药安全提示） -->
      <div class="page-card rx-column">
        <div class="column-header">
          <span class="column-title">处方</span>
          <div class="header-actions">
            <el-tag v-if="chargeInfo" size="small" effect="plain" :type="chargeInfo.tag">收费：{{ chargeInfo.text }}</el-tag>
            <el-tag v-if="rxCheck && !rxCheckError" size="small" effect="plain" :type="rxSafetyTag.type">
              用药安全：{{ rxSafetyTag.text }}
            </el-tag>
          </div>
        </div>

        <div v-loading="prescriptionLoading" class="column-body rx-body">
          <el-empty v-if="!currentVisit" description="接诊后可在此开具处方" :image-size="100" />

          <template v-else>
            <el-form label-position="top" size="small" class="rx-form">
              <el-form-item label="处方类型">
                <el-radio-group v-model="rxType">
                  <el-radio-button value="HERB">中药</el-radio-button>
                  <el-radio-button value="PATENT">中成药</el-radio-button>
                  <el-radio-button value="WESTERN">西药</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-row :gutter="12">
                <el-col :span="12">
                  <el-form-item label="剂数">
                    <el-input-number v-model="rxDoses" :min="1" :max="99" size="small" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col v-if="rxType === 'HERB'" :span="12">
                  <el-form-item label="煎法">
                    <el-select v-model="decoctionMethod" size="small" style="width: 100%">
                      <el-option label="代煎" value="代煎" />
                      <el-option label="自煎" value="自煎" />
                      <el-option label="免煎颗粒" value="免煎颗粒" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item v-if="showPregnancy" label="妊娠状态">
                <el-radio-group v-model="pregnantStatus">
                  <el-radio-button value="">未知</el-radio-button>
                  <el-radio-button value="YES">妊娠</el-radio-button>
                  <el-radio-button value="NO">非妊娠</el-radio-button>
                </el-radio-group>
                <div class="text-muted pregnancy-hint">未知时不做妊娠禁忌判定，仅提示核对</div>
              </el-form-item>

              <el-form-item label="添加药品">
                <el-select v-model="selectedDrugId" class="drug-select" filterable remote clearable size="small"
                  placeholder="搜索药品名称 / 拼音码" :remote-method="searchDrugs" :loading="drugSearchLoading"
                  @change="onSelectDrug" @visible-change="onDrugSelectVisible">
                  <el-option v-for="d in drugOptions" :key="d.id"
                    :label="`${d.name} / ${d.spec || '无规格'} / ${d.unit || '-'} / ￥${formatMoney(d.price)}`"
                    :value="d.id" />
                </el-select>
              </el-form-item>
            </el-form>

            <el-table :data="rxItems" size="small" border class="rx-items" empty-text="暂未添加药品">
              <el-table-column label="药名 / 规格" min-width="150">
                <template #default="{ row }">
                  <div class="drug-name">
                    {{ row.drugName }}
                    <el-tooltip v-if="rxAnnotations[row.drugId]" placement="top"
                      :content="rxAnnotations[row.drugId].note || '特殊煎法自动标注'">
                      <el-tag size="small" type="warning" effect="plain" class="handling-tag">
                        {{ handlingShort(rxAnnotations[row.drugId].specialHandling) }}
                      </el-tag>
                    </el-tooltip>
                  </div>
                  <div class="text-muted">
                    {{ [row.spec, row.unit].filter(Boolean).join(' / ') || '-' }} · 单价 ￥{{ formatMoney(row.price) }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="单次剂量" width="100">
                <template #default="{ row }">
                  <el-input v-model="row.dose" size="small" placeholder="如 10g" />
                </template>
              </el-table-column>
              <el-table-column label="数量" width="106">
                <template #default="{ row }">
                  <el-input-number v-model="row.quantity" :min="1" :max="9999" size="small"
                    controls-position="right" class="qty-input" />
                </template>
              </el-table-column>
              <el-table-column label="金额" width="88" align="right">
                <template #default="{ row }">
                  <span class="money">{{ formatMoney(itemAmount(row)) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="用法" min-width="120">
                <template #default="{ row }">
                  <el-input v-model="row.usageText" size="small" placeholder="如 每日一剂" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="64" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" size="small" @click="removeRxItem($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="rx-total">
              <span class="text-muted">共 {{ rxItems.length }} 味 / {{ rxQuantityTotal }} 件</span>
              <span>合计：<span class="money rx-amount">￥{{ formatMoney(rxTotal) }}</span></span>
            </div>

            <el-form label-position="top" size="small" class="rx-form">
              <el-form-item label="备注">
                <el-input v-model="rxRemark" type="textarea" :rows="2" maxlength="200" show-word-limit
                  placeholder="选填，如：饭后温服" />
              </el-form-item>
            </el-form>

            <!-- 处方安全审查结果 -->
            <div v-if="rxCheckError || rxIssues.length" class="rx-check">
              <el-alert v-if="rxCheckError" type="warning" :closable="false" show-icon
                title="处方安全审查暂不可用，开方前请人工核对配伍禁忌与妊娠禁忌" />
              <el-alert v-if="rxBlockIssues.length" type="error" :closable="false" show-icon
                :title="`存在 ${rxBlockIssues.length} 项用药安全禁忌，禁止开方`">
                <ul class="issue-list">
                  <li v-for="(issue, index) in rxBlockIssues" :key="'rx-block-' + index" class="issue-item">
                    <span class="issue-message">{{ issue.message }}</span>
                    <span v-if="issue.advice" class="issue-advice">建议：{{ issue.advice }}</span>
                  </li>
                </ul>
              </el-alert>
              <el-alert v-if="rxWarnIssues.length" type="warning" :closable="false" show-icon
                :title="`${rxWarnIssues.length} 项用药提醒（可继续开方）`">
                <ul class="issue-list">
                  <li v-for="(issue, index) in rxWarnIssues" :key="'rx-warn-' + index" class="issue-item">
                    <span class="issue-message">{{ issue.message }}</span>
                    <span v-if="issue.advice" class="issue-advice">建议：{{ issue.advice }}</span>
                  </li>
                </ul>
              </el-alert>
              <el-alert v-if="rxInfoIssues.length" type="info" :closable="false" show-icon
                :title="`${rxInfoIssues.length} 项提示`">
                <ul class="issue-list">
                  <li v-for="(issue, index) in rxInfoIssues" :key="'rx-info-' + index" class="issue-item">
                    <span class="issue-message">{{ issue.message }}</span>
                    <span v-if="issue.advice" class="issue-advice">建议：{{ issue.advice }}</span>
                  </li>
                </ul>
              </el-alert>
            </div>

            <div class="rx-submit">
              <span v-if="rxChecking" class="text-muted rx-checking">安全审查中…</span>
              <el-button type="primary" :loading="submittingRx" :disabled="!rxItems.length || rxBlockIssues.length > 0"
                @click="submitPrescription">开方</el-button>
            </div>

            <el-divider content-position="left">本次处方</el-divider>
            <el-table :data="prescriptions" size="small" border class="rx-list" empty-text="本次就诊暂无处方">
              <el-table-column label="处方号 / 用法" min-width="150">
                <template #default="{ row }">
                  <div>{{ row.prescriptionNo }}</div>
                  <div class="rx-usage">{{ rxUsageSummary(row) }}</div>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="92">
                <template #default="{ row }">
                  {{ PRESCRIPTION_TYPE_TEXT[row.type] || row.type || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="总额" width="76" align="right">
                <template #default="{ row }">
                  <span class="money">{{ formatMoney(row.totalAmount) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="78">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" :type="rxStatusTag(row.status)">
                    {{ PRESCRIPTION_STATUS_TEXT[row.status] || row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="58" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" :loading="printingRxId === row.id"
                    @click="printPrescriptionRow(row)">打印</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </div>
      </div>
    </div>

    <!-- AI 助写草稿确认：医生点「采用草稿」才写入病历 -->
    <el-dialog v-model="aiDialogVisible" :title="aiDialogTitle" width="640px" @closed="onAiDialogClosed">
      <el-alert v-if="aiNotice" :title="aiNotice" :type="aiNoticeType" :closable="false" show-icon
        class="ai-notice" />
      <div class="ai-source-row">
        <el-tag size="small" effect="plain" :type="aiSource === 'LLM' ? 'warning' : 'info'">
          {{ aiSource === 'LLM' ? '模型生成' : '本地模板生成' }}
        </el-tag>
        <span class="text-muted">AI 只生成草稿，须医生核对修改后「采用」才写入病历</span>
      </div>
      <el-input v-model="aiDraftText" type="textarea" :rows="9" resize="vertical"
        placeholder="AI 草稿内容，可直接编辑后再采用" />
      <div v-if="aiOriginalText.trim()" class="ai-overwrite">
        采用后将覆盖「{{ aiDraftField ? aiDraftField.label : '' }}」原有内容
      </div>
      <template #footer>
        <el-button @click="aiDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="applyAiDraft">采用草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import {
  aiAssist,
  checkPrescription,
  checkRecord,
  createPrescription,
  fetchPrescriptionHtml,
  finishVisit,
  getAiSettings,
  getQueue,
  getRecord,
  getVisit,
  listDoctors,
  listPrescriptions,
  pageDrugs,
  pageVisits,
  saveRecord,
  startVisit,
  suggestTerms
} from '@/api'
import { openPrintWindow } from '@/utils/print'
import {
  CHARGE_STATUS_TAG,
  GENDER_TEXT,
  PRESCRIPTION_STATUS_TEXT,
  PRESCRIPTION_TYPE_TEXT,
  VISIT_STATUS_TAG,
  VISIT_STATUS_TEXT,
  formatDateTime,
  formatMoney,
  todayStr
} from '@/utils/format'

/*
 * v4 临床辅助接口（契约见 docs/api-contract.md §22~§25）：
 * suggestTerms（联想）/ checkRecord（质控）/ aiAssist（AI 助写）/
 * getAiSettings（AI 设置）/ checkPrescription（处方安全审查），均由 @/api 提供。
 */

const RX_STATUS_TAG = {
  DRAFT: 'info',
  PENDING: 'warning',
  DISPENSED: 'success',
  CANCELLED: 'danger'
}

const RECORD_TYPE_TEXT = { TCM: '中医病历', WESTERN: '西医病历' }
const SOURCE_TEXT = { SYSTEM: '标准术语', HISTORY: '本院历史' }
const ISSUE_LEVEL_TEXT = { BLOCK: '必须补充', WARN: '建议完善', INFO: '提示' }
const ISSUE_LEVEL_TAG = { BLOCK: 'danger', WARN: 'warning', INFO: 'info' }
const ISSUE_SORT = { BLOCK: 0, WARN: 1, INFO: 2 }
const HANDLING_SHORT = {
  PRE_DECOCT: '先煎',
  POST_ADD: '后下',
  WRAP_DECOCT: '包煎',
  SEPARATE_DECOCT: '另煎',
  MELT: '烊化',
  TAKE_WITH: '冲服'
}

/* 病历字段（中西医用同一份 form 存储，切换类型时公共字段保留） */
const RECORD_FIELDS = [
  'chiefComplaint',
  'presentIllness',
  'pastHistory',
  'inspection',
  'auscultation',
  'inquiry',
  'palpation',
  'tongue',
  'pulse',
  'tcmDisease',
  'syndrome',
  'treatmentPrinciple',
  'physicalExam',
  'auxiliaryExam',
  'westernDiagnosis',
  'icdCode',
  'advice'
]

/*
 * 字段布局：
 * group  -> 分组标题（el-divider）
 * suggest-> 联想类别（SYMPTOM / TONGUE / PULSE / SYNDROME / TREATMENT / DIAGNOSIS）
 * ai     -> AI 助写字段名（presentIllness / treatmentPrinciple / advice / physicalExam）
 * required -> 必填项（与 docs/clinical-design.md 及后端 RecordQcService 对齐）
 */
const FIELD_LAYOUT = {
  TCM: [
    { group: '主诉及病史' },
    {
      key: 'chiefComplaint', label: '主诉', required: true, span: 24, rows: 2, maxlength: 500,
      suggest: 'SYMPTOM', placeholder: '主要症状 + 持续时间，如：反复胃脘胀痛 3 月'
    },
    {
      key: 'presentIllness', label: '现病史', required: true, span: 12, rows: 4, maxlength: 1000,
      suggest: 'SYMPTOM', ai: 'presentIllness',
      placeholder: '发病诱因、症状演变、诊治经过、十问（寒热、汗、饮食、二便、睡眠）'
    },
    {
      key: 'pastHistory', label: '既往史', required: true, span: 12, rows: 4, maxlength: 1000,
      placeholder: '既往疾病、手术、过敏史（无则写「无」）'
    },
    { group: '中医四诊' },
    { key: 'inspection', label: '望诊', required: true, span: 12, rows: 3, placeholder: '神、色、形、态' },
    { key: 'auscultation', label: '闻诊', span: 12, rows: 3, placeholder: '语声、气息、气味' },
    { key: 'inquiry', label: '问诊', span: 12, rows: 3, placeholder: '寒热、汗、饮食、二便、睡眠' },
    { key: 'palpation', label: '切诊', required: true, span: 12, rows: 3, placeholder: '按诊、肌肤、手足' },
    {
      key: 'tongue', label: '舌象', required: true, span: 12, rows: 2, maxlength: 500,
      suggest: 'TONGUE', placeholder: '舌质、舌苔，如：舌淡红、苔薄白'
    },
    {
      key: 'pulse', label: '脉象', required: true, span: 12, rows: 2, maxlength: 500,
      suggest: 'PULSE', placeholder: '如：脉弦细'
    },
    { group: '辨证论治' },
    {
      key: 'tcmDisease', label: '中医病名', required: true, span: 12, rows: 2, maxlength: 200,
      suggest: 'SYNDROME', placeholder: '如：胃痞、不寐'
    },
    {
      key: 'syndrome', label: '证候诊断', required: true, span: 12, rows: 2, maxlength: 500,
      suggest: 'SYNDROME', placeholder: '如：脾胃气虚证'
    },
    {
      key: 'treatmentPrinciple', label: '治则治法', required: true, span: 12, rows: 3, maxlength: 500,
      suggest: 'TREATMENT', ai: 'treatmentPrinciple', placeholder: '如：健脾益气'
    },
    {
      key: 'advice', label: '医嘱', span: 12, rows: 3, maxlength: 1000,
      ai: 'advice', placeholder: '饮食起居、复诊安排等'
    }
  ],
  WESTERN: [
    { group: '主诉及病史' },
    {
      key: 'chiefComplaint', label: '主诉', required: true, span: 24, rows: 2, maxlength: 500,
      suggest: 'SYMPTOM', placeholder: '主要症状 + 持续时间，如：咳嗽咳痰 1 周'
    },
    {
      key: 'presentIllness', label: '现病史', required: true, span: 24, rows: 4, maxlength: 1000,
      suggest: 'SYMPTOM', ai: 'presentIllness',
      placeholder: '发病诱因、症状演变、伴随症状、诊治经过'
    },
    {
      key: 'pastHistory', label: '既往史', required: true, span: 24, rows: 2, maxlength: 1000,
      placeholder: '既往疾病、手术、过敏史（无则写「无」）'
    },
    { group: '查体与辅助检查' },
    {
      key: 'physicalExam', label: '体格检查', required: true, span: 24, rows: 4, maxlength: 2000,
      ai: 'physicalExam', placeholder: '生命体征（T/P/R/BP）+ 阳性与阴性体征'
    },
    {
      key: 'auxiliaryExam', label: '辅助检查', span: 24, rows: 3, maxlength: 2000,
      placeholder: '化验、影像等检查结果（无则写「无」或「暂缺」）'
    },
    { group: '诊断与处理' },
    {
      key: 'westernDiagnosis', label: '西医诊断', required: true, span: 16, rows: 2, maxlength: 500,
      suggest: 'DIAGNOSIS', placeholder: '疾病名称，如：急性支气管炎'
    },
    { key: 'icdCode', label: 'ICD-10 编码', span: 8, single: true, maxlength: 20, placeholder: '如：J20.900' },
    {
      key: 'advice', label: '处理意见', required: true, span: 24, rows: 3, maxlength: 1000,
      ai: 'advice', placeholder: '用药、检查、随访安排'
    }
  ]
}

const store = useUserStore()

const isAdmin = computed(() => store.role === 'ADMIN')

/* ---------------- 医生 / 候诊队列 ---------------- */
const doctors = ref([])
const doctorsLoading = ref(false)
const doctorId = ref(store.user?.id ?? null)
const queue = ref([])
const queueLoading = ref(false)
const todayVisitCount = ref(0)
const startingId = ref(null)

const currentDoctorName = computed(() => {
  if (!isAdmin.value) return store.displayName || '医生'
  const doctor = doctors.value.find((d) => d.id === doctorId.value)
  return doctor ? doctor.realName || doctor.username : store.displayName || '管理员'
})

async function loadDoctors() {
  if (!isAdmin.value) return
  doctorsLoading.value = true
  try {
    const data = await listDoctors()
    doctors.value = Array.isArray(data) ? data : []
    if (doctors.value.length) {
      const self = doctors.value.find((d) => d.id === store.user?.id)
      doctorId.value = self ? self.id : doctors.value[0].id
    }
  } catch {
    doctors.value = []
  } finally {
    doctorsLoading.value = false
  }
}

async function loadQueue() {
  if (!doctorId.value) {
    queue.value = []
    return
  }
  queueLoading.value = true
  try {
    const data = await getQueue({ doctorId: doctorId.value })
    queue.value = Array.isArray(data) ? data : (data?.list || [])
  } catch {
    queue.value = []
  } finally {
    queueLoading.value = false
  }
}

async function loadTodayVisitCount() {
  if (!doctorId.value) {
    todayVisitCount.value = 0
    return
  }
  try {
    const [inService, finished] = await Promise.all([
      pageVisits({ date: todayStr(), doctorId: doctorId.value, status: 'IN_SERVICE', page: 1, size: 1 }),
      pageVisits({ date: todayStr(), doctorId: doctorId.value, status: 'FINISHED', page: 1, size: 1 })
    ])
    todayVisitCount.value = Number(inService?.total ?? 0) + Number(finished?.total ?? 0)
  } catch {
    todayVisitCount.value = 0
  }
}

function refreshAll() {
  return Promise.all([loadQueue(), loadTodayVisitCount()])
}

function onDoctorChange() {
  clearSelection()
  refreshAll()
}

/* ---------------- 当前就诊 / 病历 ---------------- */
const selectedVisitId = ref(null)
const visitDetail = ref(null)
const visitLoading = ref(false)
const recordLoading = ref(false)
const savingRecord = ref(false)
const finishing = ref(false)
const hasSavedRecord = ref(false)
const recordVersion = ref(null)
const recordSavedSnapshot = ref('')
const recordType = ref('TCM')

const recordForm = reactive(Object.fromEntries(RECORD_FIELDS.map((key) => [key, ''])))

const currentVisit = computed(() => visitDetail.value?.visit || null)
const currentPatient = computed(() => visitDetail.value?.patient || null)
const charges = computed(() => visitDetail.value?.charges || [])
const currentFields = computed(() => FIELD_LAYOUT[recordType.value] || FIELD_LAYOUT.TCM)

function formSnapshot() {
  return JSON.stringify({ recordType: recordType.value, ...recordForm })
}

const recordDirty = computed(() => formSnapshot() !== recordSavedSnapshot.value)

function hasAnyContent() {
  return RECORD_FIELDS.some((key) => String(recordForm[key] ?? '').trim() !== '')
}

const chargeInfo = computed(() => {
  if (!charges.value.length) return null
  const countBy = (status) => charges.value.filter((c) => c.status === status).length
  if (countBy('UNPAID')) return { text: `待收费 ${countBy('UNPAID')} 单`, tag: CHARGE_STATUS_TAG.UNPAID }
  if (countBy('PAID')) return { text: `已收费 ${countBy('PAID')} 单`, tag: CHARGE_STATUS_TAG.PAID }
  return { text: `已退费 ${countBy('REFUNDED')} 单`, tag: CHARGE_STATUS_TAG.REFUNDED }
})

function resetRecordForm() {
  RECORD_FIELDS.forEach((key) => {
    recordForm[key] = ''
  })
  recordType.value = 'TCM'
  recordVersion.value = null
  hasSavedRecord.value = false
  qcIssues.value = []
  qcChecked.value = false
  qcError.value = false
  qcLoading.value = false
  recordSavedSnapshot.value = formSnapshot()
}

function clearSelection() {
  selectedVisitId.value = null
  visitDetail.value = null
  prescriptions.value = []
  resetRecordForm()
  rxItems.value = []
  rxRemark.value = ''
  rxDoses.value = 1
  rxType.value = 'HERB'
  decoctionMethod.value = '代煎'
  drugOptions.value = []
  selectedDrugId.value = null
  pregnantStatus.value = ''
  resetRxCheckState()
  closeAiDialog()
}

/* ---------------- 联想输入 ---------------- */
function makeSuggestFetcher(category) {
  return async (keyword, callback) => {
    const kw = String(keyword ?? '').trim()
    if (!kw) {
      callback([])
      return
    }
    try {
      const data = await suggestTerms({ category, keyword: kw, limit: 10 })
      const list = Array.isArray(data) ? data : []
      callback(list.filter((item) => item && item.term))
    } catch {
      callback([])
    }
  }
}

const suggestFetchers = {
  SYMPTOM: makeSuggestFetcher('SYMPTOM'),
  TONGUE: makeSuggestFetcher('TONGUE'),
  PULSE: makeSuggestFetcher('PULSE'),
  SYNDROME: makeSuggestFetcher('SYNDROME'),
  TREATMENT: makeSuggestFetcher('TREATMENT'),
  DIAGNOSIS: makeSuggestFetcher('DIAGNOSIS')
}

/* ---------------- 实时质控 ---------------- */
const qcIssues = ref([])
const qcChecked = ref(false)
const qcError = ref(false)
const qcLoading = ref(false)
let qcTimer = null
let qcSeq = 0

const qcBlockCount = computed(() => qcIssues.value.filter((item) => item.level === 'BLOCK').length)
const qcWarnCount = computed(() => qcIssues.value.filter((item) => item.level === 'WARN').length)
const qcInfoCount = computed(() => qcIssues.value.filter((item) => item.level === 'INFO').length)

const sortedQcIssues = computed(() =>
  [...qcIssues.value].sort((a, b) => (ISSUE_SORT[a.level] ?? 9) - (ISSUE_SORT[b.level] ?? 9))
)

const qcPanelType = computed(() => {
  if (qcBlockCount.value) return 'error'
  if (qcWarnCount.value) return 'warning'
  return 'info'
})

const qcPanelTitle = computed(() => {
  if (qcBlockCount.value) return `质控未通过，必填项未完成 ${qcBlockCount.value} 项，需补充后才能保存`
  if (qcWarnCount.value) return `质控可保存，有 ${qcWarnCount.value} 项建议完善`
  return '质控提示'
})

const qcBadge = computed(() => {
  if (!currentVisit.value) return null
  if (qcLoading.value) return { text: '质控中…', type: 'info' }
  if (qcError.value) return { text: '质控不可用', type: 'info' }
  if (!qcChecked.value) return { text: '未质控', type: 'info' }
  if (!qcIssues.value.length) return { text: '质控通过', type: 'success' }
  const total = qcIssues.value.length
  return {
    text: `有 ${total} 项待完善`,
    type: qcBlockCount.value ? 'danger' : (qcWarnCount.value ? 'warning' : 'info')
  }
})

const qcBadgeTip = computed(() => {
  if (!currentVisit.value) return '选择患者后可执行病历质控'
  if (qcLoading.value) return '正在按《中医病历书写基本规范》校验必填项与基本逻辑'
  if (qcError.value) return '质控服务暂不可用，保存时服务端仍会执行质控'
  if (!qcChecked.value) return '填写内容后自动执行质控，保存前也会强制质控'
  if (!qcIssues.value.length) return '质控全部通过'
  return `必填未完成 ${qcBlockCount.value} 项，建议完善 ${qcWarnCount.value} 项，提示 ${qcInfoCount.value} 项`
})

function issueTagType(level) {
  return ISSUE_LEVEL_TAG[level] || 'info'
}

function buildRecordPayload() {
  const payload = { recordType: recordType.value }
  RECORD_FIELDS.forEach((key) => {
    payload[key] = String(recordForm[key] ?? '')
  })
  return payload
}

function scheduleQc(delay = 900) {
  if (qcTimer) {
    clearTimeout(qcTimer)
    qcTimer = null
  }
  if (!selectedVisitId.value) return
  if (!hasAnyContent() && !hasSavedRecord.value) {
    qcIssues.value = []
    qcChecked.value = false
    qcError.value = false
    return
  }
  qcTimer = setTimeout(runQc, delay)
}

async function runQc() {
  if (qcTimer) {
    clearTimeout(qcTimer)
    qcTimer = null
  }
  if (!selectedVisitId.value) {
    qcIssues.value = []
    qcChecked.value = false
    return
  }
  const seq = ++qcSeq
  qcLoading.value = true
  try {
    const data = await checkRecord(buildRecordPayload())
    if (seq !== qcSeq) return
    qcIssues.value = Array.isArray(data?.issues) ? data.issues : []
    qcChecked.value = true
    qcError.value = false
  } catch {
    if (seq !== qcSeq) return
    qcIssues.value = []
    qcChecked.value = false
    qcError.value = true
  } finally {
    if (seq === qcSeq) qcLoading.value = false
  }
}

/* ---------------- 加载 / 保存病历 ---------------- */
async function loadRecord(visitId) {
  recordLoading.value = true
  try {
    const data = await getRecord(visitId)
    if (data) {
      recordType.value = data.recordType === 'WESTERN' ? 'WESTERN' : 'TCM'
      RECORD_FIELDS.forEach((key) => {
        recordForm[key] = data[key] ?? ''
      })
      recordVersion.value = data.version ?? null
      hasSavedRecord.value = true
    } else {
      resetRecordForm()
    }
  } catch {
    resetRecordForm()
  } finally {
    recordSavedSnapshot.value = formSnapshot()
    recordLoading.value = false
  }
  scheduleQc(0)
}

async function selectVisit(visitId) {
  if (!visitId) return
  selectedVisitId.value = visitId
  visitLoading.value = true
  try {
    const detail = await getVisit(visitId)
    visitDetail.value = detail || null
  } catch {
    visitDetail.value = null
    selectedVisitId.value = null
  } finally {
    visitLoading.value = false
  }
  if (!visitDetail.value) return
  pregnantStatus.value = ''
  qcIssues.value = []
  qcChecked.value = false
  qcError.value = false
  await Promise.all([loadRecord(visitId), loadPrescriptions(visitId)])
  scheduleRxCheck()
}

function onQueueCardClick(item) {
  if (item.status === 'IN_SERVICE') {
    selectVisit(item.id)
  } else {
    ElMessage.info('请点击「接诊」开始接诊')
  }
}

async function onStartVisit(item) {
  if (!item) return
  startingId.value = item.id
  try {
    await startVisit(item.id)
    ElMessage.success(`已接诊 ${item.patientName || '患者'}`)
    await Promise.all([loadQueue(), loadTodayVisitCount()])
    await selectVisit(item.id)
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    startingId.value = null
  }
}

async function saveRecordForm() {
  if (!selectedVisitId.value) return
  if (!hasAnyContent()) {
    ElMessage.warning('请至少填写一项病历内容')
    return
  }
  savingRecord.value = true
  try {
    // 保存前强制质控：BLOCK 级未通过则不保存
    await runQc()
    if (qcChecked.value && qcBlockCount.value > 0) {
      ElMessage.error(`质控未通过：${qcBlockCount.value} 项必填内容待补充，本次未保存`)
      return
    }
    if (!qcChecked.value) {
      ElMessage.warning('质控服务暂不可用，本次直接保存（服务端保存时仍会执行质控）')
    }
    const payload = buildRecordPayload()
    if (recordVersion.value != null) payload.version = recordVersion.value
    const data = await saveRecord(selectedVisitId.value, payload)
    if (data?.version != null) recordVersion.value = data.version
    recordSavedSnapshot.value = formSnapshot()
    hasSavedRecord.value = true
    ElMessage.success(recordVersion.value != null ? `已保存（版本 ${recordVersion.value}）` : '病历已保存')
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    savingRecord.value = false
  }
}

async function onFinishVisit() {
  if (!currentVisit.value) return
  if (recordDirty.value) {
    ElMessage.warning('病历有未保存的修改，请先保存病历')
    return
  }
  if (!hasSavedRecord.value) {
    ElMessage.warning('尚未保存病历，请先保存病历后再完成就诊')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认完成「${currentVisit.value.patientName || '当前患者'}」的就诊？完成后该患者将移出候诊队列。`,
      '完成就诊',
      { type: 'warning', confirmButtonText: '确认完成', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  finishing.value = true
  try {
    await finishVisit(selectedVisitId.value)
    ElMessage.success('就诊已完成')
    clearSelection()
    await refreshAll()
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    finishing.value = false
  }
}

/* ---------------- AI 助写 ---------------- */
const aiSettings = ref(null)
const aiLoadingField = ref('')
const aiDialogVisible = ref(false)
const aiDraftText = ref('')
const aiDraftField = ref(null)
const aiOriginalText = ref('')
const aiSource = ref('')
const aiNotice = ref('')

const aiButtonTip = computed(() => {
  const settings = aiSettings.value
  if (!settings) return 'AI 助写：设置读取失败，将使用本地模板生成草稿，需医生核对'
  if (settings.enabled === false) return 'AI 助写未开启，当前为本地模板（不联网）；草稿需医生核对后采用'
  if (settings.provider === 'LOCAL') return '当前为本地模板（不联网）生成草稿，需医生核对后采用'
  return `将调用已配置模型「${settings.model || settings.baseUrl || settings.provider}」生成草稿；病历内容会发送到该模型服务，需医生核对后采用`
})

const aiDialogTitle = computed(() => `AI 助写 · ${aiDraftField.value ? aiDraftField.value.label : ''}`)
const aiNoticeType = computed(() => (aiSource.value === 'LLM' ? 'warning' : 'info'))

async function loadAiSettings() {
  try {
    aiSettings.value = await getAiSettings()
  } catch {
    aiSettings.value = null
  }
}

function extractDuration(text) {
  const match = String(text ?? '').match(/(\d+|[一二三四五六七八九十半两几]+)\s*(天|日|周|星期|个月|月|年|小时)/)
  return match ? match[0] : ''
}

function buildAiContext() {
  const context = {}
  RECORD_FIELDS.forEach((key) => {
    const value = String(recordForm[key] ?? '').trim()
    if (value) context[key] = value.length > 300 ? value.slice(0, 300) : value
  })
  const duration = extractDuration(recordForm.chiefComplaint)
  if (duration) context.duration = duration
  return context
}

async function onAiDraft(field) {
  if (!field?.ai || aiLoadingField.value) return
  aiLoadingField.value = field.ai
  try {
    const data = await aiAssist({
      field: field.ai,
      recordType: recordType.value,
      context: buildAiContext()
    })
    aiDraftField.value = field
    aiDraftText.value = String(data?.text ?? '')
    aiOriginalText.value = String(recordForm[field.key] ?? '')
    aiSource.value = data?.source === 'LLM' ? 'LLM' : 'LOCAL'
    aiNotice.value = String(data?.notice ?? '')
    aiDialogVisible.value = true
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    aiLoadingField.value = ''
  }
}

function applyAiDraft() {
  if (!aiDraftField.value) return
  const text = aiDraftText.value.trim()
  if (!text) {
    ElMessage.warning('草稿内容为空，未写入病历')
    return
  }
  recordForm[aiDraftField.value.key] = text
  aiDialogVisible.value = false
  ElMessage.success(`草稿已写入「${aiDraftField.value.label}」，请医生核对修改后保存`)
}

function onAiDialogClosed() {
  aiDraftText.value = ''
  aiDraftField.value = null
  aiOriginalText.value = ''
  aiNotice.value = ''
  aiSource.value = ''
}

function closeAiDialog() {
  aiDialogVisible.value = false
  onAiDialogClosed()
}

/* ---------------- 处方 ---------------- */
const rxType = ref('HERB')
const rxDoses = ref(1)
const decoctionMethod = ref('代煎')
const rxRemark = ref('')
const rxItems = ref([])
const selectedDrugId = ref(null)
const drugOptions = ref([])
const drugSearchLoading = ref(false)
const prescriptions = ref([])
const prescriptionLoading = ref(false)
const submittingRx = ref(false)
const printingRxId = ref(null)
const pregnantStatus = ref('')

const rxTotal = computed(() =>
  rxItems.value.reduce((sum, item) => sum + Number(item.price || 0) * Number(item.quantity || 0), 0)
)
const rxQuantityTotal = computed(() =>
  rxItems.value.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
)

const patientGender = computed(() => currentVisit.value?.patientGender || null)
const showPregnancy = computed(() => patientGender.value !== 'MALE')
const pregnantValue = computed(() => {
  if (patientGender.value === 'MALE') return false
  if (pregnantStatus.value === 'YES') return true
  if (pregnantStatus.value === 'NO') return false
  return null
})

function itemAmount(row) {
  return Number(row?.price || 0) * Number(row?.quantity || 0)
}

function rxStatusTag(status) {
  return RX_STATUS_TAG[status] || 'info'
}

/* 从「10g / 10 克 / 1.5g」中取数值，供剂量范围审查使用 */
function parseDoseNumber(text) {
  const match = String(text ?? '').match(/\d+(?:\.\d+)?/)
  return match ? Number(match[0]) : null
}

async function searchDrugs(keyword) {
  drugSearchLoading.value = true
  try {
    const data = await pageDrugs({ keyword: (keyword || '').trim() || undefined, page: 1, size: 20 })
    drugOptions.value = data?.list || []
  } catch {
    drugOptions.value = []
  } finally {
    drugSearchLoading.value = false
  }
}

function onDrugSelectVisible(visible) {
  if (visible && !drugOptions.value.length) searchDrugs('')
}

function onSelectDrug(drugId) {
  if (!drugId) return
  const drug = drugOptions.value.find((d) => d.id === drugId)
  if (!drug) return
  const existing = rxItems.value.find((item) => item.drugId === drugId)
  if (existing) {
    existing.quantity = Number(existing.quantity || 0) + 1
  } else {
    rxItems.value.push({
      drugId: drug.id,
      drugName: drug.name,
      spec: drug.spec,
      unit: drug.unit,
      price: drug.price,
      dose: '',
      quantity: 1,
      usageText: ''
    })
  }
  selectedDrugId.value = null
}

function removeRxItem(index) {
  rxItems.value.splice(index, 1)
}

async function printPrescriptionRow(row) {
  if (!row?.id) return
  printingRxId.value = row.id
  try {
    const html = await fetchPrescriptionHtml(row.id)
    if (!openPrintWindow(html)) {
      ElMessage.warning('打印窗口被浏览器拦截或打印内容异常，请允许本站弹出窗口后重试')
    }
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    printingRxId.value = null
  }
}

async function loadPrescriptions(visitId) {
  prescriptionLoading.value = true
  try {
    const data = await listPrescriptions(visitId)
    prescriptions.value = Array.isArray(data) ? data : (data?.list || [])
  } catch {
    prescriptions.value = []
  } finally {
    prescriptionLoading.value = false
  }
}

async function refreshVisitDetail() {
  if (!selectedVisitId.value) return
  try {
    const detail = await getVisit(selectedVisitId.value)
    if (detail) visitDetail.value = detail
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  }
}

/* ---------------- 处方安全审查（400ms 防抖） ---------------- */
const rxCheck = ref(null)
const rxCheckError = ref(false)
const rxChecking = ref(false)
let rxTimer = null
let rxSeq = 0

const rxIssues = computed(() => (Array.isArray(rxCheck.value?.issues) ? rxCheck.value.issues : []))
const rxBlockIssues = computed(() => rxIssues.value.filter((item) => item.level === 'BLOCK'))
const rxWarnIssues = computed(() => rxIssues.value.filter((item) => item.level === 'WARN'))
const rxInfoIssues = computed(() => rxIssues.value.filter((item) => item.level === 'INFO'))

const rxAnnotations = computed(() => {
  const map = {}
  const list = Array.isArray(rxCheck.value?.annotations) ? rxCheck.value.annotations : []
  list.forEach((annotation) => {
    if (annotation?.drugId != null) map[annotation.drugId] = annotation
  })
  return map
})

const rxSafetyTag = computed(() => {
  if (rxBlockIssues.value.length) return { text: `禁止开方（${rxBlockIssues.value.length} 项禁忌）`, type: 'danger' }
  if (rxWarnIssues.value.length) return { text: `${rxWarnIssues.value.length} 项提醒`, type: 'warning' }
  if (rxInfoIssues.value.length) return { text: `${rxInfoIssues.value.length} 项提示`, type: 'info' }
  return { text: '未发现禁忌', type: 'success' }
})

function handlingShort(handling) {
  return HANDLING_SHORT[handling] || '特殊煎法'
}

function rxUsageSummary(row) {
  const items = Array.isArray(row?.items) ? row.items : []
  const texts = items.map((item) => String(item.usageText || '').trim()).filter(Boolean)
  return texts.length ? texts.join('；') : '用法未标注'
}

function resetRxCheckState() {
  if (rxTimer) {
    clearTimeout(rxTimer)
    rxTimer = null
  }
  rxSeq += 1
  rxChecking.value = false
  rxCheck.value = null
  rxCheckError.value = false
}

function scheduleRxCheck() {
  if (rxTimer) {
    clearTimeout(rxTimer)
    rxTimer = null
  }
  if (!selectedVisitId.value || !rxItems.value.length) {
    resetRxCheckState()
    return
  }
  rxTimer = setTimeout(runRxCheck, 400)
}

async function runRxCheck() {
  if (rxTimer) {
    clearTimeout(rxTimer)
    rxTimer = null
  }
  if (!selectedVisitId.value || !rxItems.value.length) return
  const seq = ++rxSeq
  rxChecking.value = true
  try {
    const data = await checkPrescription({
      items: rxItems.value.map((item) => ({
        drugId: item.drugId,
        dose: parseDoseNumber(item.dose),
        quantity: Number(item.quantity) || null
      })),
      pregnant: pregnantValue.value
    })
    if (seq !== rxSeq) return
    rxCheck.value = {
      passed: data?.passed !== false,
      issues: Array.isArray(data?.issues) ? data.issues : [],
      annotations: Array.isArray(data?.annotations) ? data.annotations : []
    }
    rxCheckError.value = false
  } catch {
    if (seq !== rxSeq) return
    rxCheck.value = null
    rxCheckError.value = true
  } finally {
    if (seq === rxSeq) rxChecking.value = false
  }
}

async function submitPrescription() {
  if (!selectedVisitId.value) return
  if (!rxItems.value.length) {
    ElMessage.warning('请先添加处方药品')
    return
  }
  if (rxBlockIssues.value.length) {
    ElMessage.error('处方存在用药安全禁忌，请调整药品或剂量后再开方')
    return
  }
  const invalid = rxItems.value.find(
    (item) => !String(item.dose || '').trim() || !Number(item.quantity)
  )
  if (invalid) {
    ElMessage.warning(`请完善「${invalid.drugName}」的单次剂量与数量`)
    return
  }
  if (rxType.value === 'HERB' && !decoctionMethod.value) {
    ElMessage.warning('请选择煎法')
    return
  }
  submittingRx.value = true
  try {
    await createPrescription({
      visitId: selectedVisitId.value,
      type: rxType.value,
      decoctionMethod: rxType.value === 'HERB' ? decoctionMethod.value : null,
      doses: Number(rxDoses.value) || 1,
      remark: rxRemark.value.trim() || null,
      items: rxItems.value.map((item) => ({
        drugId: item.drugId,
        // 后端 ItemRequest.dose 为 BigDecimal（单位取自药品字典），故按数值提交
        dose: parseDoseNumber(item.dose),
        quantity: Number(item.quantity),
        usageText: item.usageText?.trim() || null
      }))
    })
    ElMessage.success('处方已开具')
    rxItems.value = []
    rxRemark.value = ''
    resetRxCheckState()
    await Promise.all([loadPrescriptions(selectedVisitId.value), refreshVisitDetail()])
  } catch {
    /* 失败提示由 request 拦截器统一处理 */
  } finally {
    submittingRx.value = false
  }
}

/* ---------------- 联动 ---------------- */
watch(recordType, () => scheduleQc())
watch(() => JSON.stringify(recordForm), () => scheduleQc())
watch(rxItems, () => scheduleRxCheck(), { deep: true })
watch(pregnantStatus, () => scheduleRxCheck())

onMounted(async () => {
  await Promise.all([loadDoctors(), loadAiSettings()])
  await refreshAll()
})

onBeforeUnmount(() => {
  if (qcTimer) clearTimeout(qcTimer)
  if (rxTimer) clearTimeout(rxTimer)
})
</script>

<style scoped>
.workbench {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: calc(100vh - 88px);
  min-height: 600px;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  flex-shrink: 0;
}

.doctor-info {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.doctor-icon {
  font-size: 20px;
  color: #409eff;
}

.doctor-name {
  font-size: 17px;
  font-weight: 600;
}

.top-stats {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.doctor-select {
  width: 220px;
}

.columns {
  flex: 1;
  display: flex;
  gap: 12px;
  min-height: 0;
}

.page-card.queue-column,
.page-card.record-column,
.page-card.rx-column {
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.queue-column {
  flex: 0 0 280px;
  width: 280px;
}

.record-column {
  flex: 1;
  min-width: 0;
}

.rx-column {
  flex: 0 0 460px;
  width: 460px;
}

.column-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}

.column-title {
  font-size: 15px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.record-header {
  flex-wrap: wrap;
}

.record-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.column-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 14px;
}

.queue-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.queue-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: #fff;
}

.queue-card:hover {
  border-color: #c6e2ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.12);
}

.queue-card.active {
  border-color: #409eff;
  background: #f0f7ff;
}

.queue-card-top {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.queue-no {
  font-size: 12px;
  color: #fff;
  background: #409eff;
  border-radius: 4px;
  padding: 1px 6px;
}

.queue-name {
  font-weight: 600;
  flex: 1;
}

.queue-meta {
  display: flex;
  gap: 10px;
  margin-top: 6px;
  font-size: 12px;
  color: #606266;
}

.queue-complaint {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.queue-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
}

.patient-bar {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 8px;
}

.patient-main {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  font-weight: 600;
}

.patient-name {
  font-size: 17px;
}

.patient-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 6px;
  font-size: 12px;
  color: #606266;
  flex-wrap: wrap;
}

.allergy {
  color: #e6a23c;
}

.record-form {
  margin-top: 4px;
}

.field-label {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.req-star {
  color: #f56c6c;
  font-weight: 700;
  margin-left: 2px;
}

.record-form :deep(.el-form-item__label) {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 6px;
  line-height: 24px;
}

.ai-btn {
  margin-left: auto;
  padding: 0;
  height: auto;
  font-size: 12px;
}

.record-input {
  width: 100%;
}

.suggest-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.suggest-term {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.suggest-pinyin {
  font-size: 12px;
  color: #909399;
}

.qc-panel {
  margin-bottom: 10px;
}

.qc-panel :deep(.el-alert__content),
.rx-check :deep(.el-alert__content) {
  width: 100%;
}

.qc-panel :deep(.el-alert__title),
.rx-check :deep(.el-alert__title) {
  font-size: 13px;
}

.issue-list {
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.issue-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 12px;
}

.issue-message {
  font-weight: 600;
}

.issue-advice {
  color: #909399;
}

.rx-check {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 8px 0;
}

.rx-checking {
  margin-right: 10px;
}

.record-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0 4px;
}

.rx-form {
  margin-top: 4px;
}

.pregnancy-hint {
  margin-top: 4px;
  line-height: 18px;
}

.drug-select {
  width: 100%;
}

.rx-items {
  width: 100%;
}

.drug-name {
  font-weight: 600;
}

.handling-tag {
  margin-left: 6px;
}

.qty-input {
  width: 100%;
}

.rx-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 2px;
  font-size: 13px;
}

.rx-amount {
  color: #f56c6c;
  font-size: 16px;
}

.rx-submit {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.rx-list {
  width: 100%;
}

.rx-usage {
  font-size: 12px;
  color: #909399;
  line-height: 16px;
  word-break: break-all;
}

.ai-notice {
  margin-bottom: 10px;
}

.ai-source-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 10px 0 8px;
}

.ai-overwrite {
  margin-top: 8px;
  font-size: 12px;
  color: #e6a23c;
}

:deep(.el-divider--horizontal) {
  margin: 14px 0 10px;
}

:deep(.el-divider__text) {
  font-weight: 600;
  color: #303133;
}

:deep(.el-form-item) {
  margin-bottom: 12px;
}

@media (max-width: 1360px) {
  .rx-column {
    flex: 0 0 400px;
    width: 400px;
  }
}
</style>
