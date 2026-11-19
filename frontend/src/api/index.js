import request from './request'

/* ---------------- 认证 ---------------- */
export const login = (data) => request.post('/auth/login', data)
export const getMe = () => request.get('/auth/me')
export const logout = () => request.post('/auth/logout')

/* ---------------- 患者 ---------------- */
export const pagePatients = (params) => request.get('/patients', { params })
export const getPatient = (id) => request.get(`/patients/${id}`)
export const createPatient = (data) => request.post('/patients', data)
export const updatePatient = (id, data) => request.put(`/patients/${id}`, data)
export const getPatientVisits = (id) => request.get(`/patients/${id}/visits`)

/* ---------------- 挂号 / 就诊 ---------------- */
export const registerVisit = (data) => request.post('/visits/register', data)
export const getQueue = (params) => request.get('/visits/queue', { params })
export const pageVisits = (params) => request.get('/visits', { params })
export const getVisit = (id) => request.get(`/visits/${id}`)
export const startVisit = (id) => request.post(`/visits/${id}/start`)
export const finishVisit = (id) => request.post(`/visits/${id}/finish`)
export const cancelVisit = (id) => request.post(`/visits/${id}/cancel`)

/* ---------------- 病历 ---------------- */
export const getRecord = (visitId) => request.get(`/visits/${visitId}/record`)
export const saveRecord = (visitId, data) => request.put(`/visits/${visitId}/record`, data)

/* ---------------- 处方 ---------------- */
export const createPrescription = (data) => request.post('/prescriptions', data)
export const getPrescription = (id) => request.get(`/prescriptions/${id}`)
export const listPrescriptions = (visitId) => request.get('/prescriptions', { params: { visitId } })
export const pendingDispense = () => request.get('/prescriptions/pending-dispense')
export const dispensePrescription = (id, data) => request.post(`/prescriptions/${id}/dispense`, data)
export const cancelPrescription = (id) => request.post(`/prescriptions/${id}/cancel`)

/* ---------------- 收费 ---------------- */
export const pageCharges = (params) => request.get('/charges', { params })
export const getCharge = (id) => request.get(`/charges/${id}`)
export const payCharge = (id, data) => request.post(`/charges/${id}/pay`, data)
export const refundCharge = (id, data) => request.post(`/charges/${id}/refund`, data)
export const chargeSummary = (params) => request.get('/charges/summary', { params })

/* ---------------- 药品 / 库存 ---------------- */
export const pageDrugs = (params) => request.get('/drugs', { params })
export const createDrug = (data) => request.post('/drugs', data)
export const updateDrug = (id, data) => request.put(`/drugs/${id}`, data)
export const getDrugStock = (id) => request.get(`/drugs/${id}/stock`)
export const stockIn = (id, data) => request.post(`/drugs/${id}/stock-in`, data)
export const stockAdjust = (id, data) => request.post(`/drugs/${id}/stock-adjust`, data)
export const pageLedger = (params) => request.get('/stock/ledger', { params })
export const stockAlerts = () => request.get('/stock/alerts')

/* ---------------- 会员 ---------------- */
export const pageMembers = (params) => request.get('/members', { params })
export const createMember = (data) => request.post('/members', data)
export const getMember = (id) => request.get(`/members/${id}`)
export const rechargeMember = (id, data) => request.post(`/members/${id}/recharge`, data)
export const pageMemberTxns = (id, params) => request.get(`/members/${id}/txns`, { params })

/* ---------------- 报表 ---------------- */
export const dailyReport = (params) => request.get('/reports/daily', { params })
export const rangeReport = (params) => request.get('/reports/range', { params })
export const doctorPerformance = (params) => request.get('/reports/doctor-performance', { params })
export const drugSales = (params) => request.get('/reports/drug-sales', { params })

/* ---------------- 医保扩展点 ---------------- */
export const insuranceProviders = () => request.get('/insurance/providers')
export const insuranceSettle = (data) => request.post('/insurance/settle', data)
export const insuranceSettlements = (params) => request.get('/insurance/settlements', { params })

/* ---------------- 系统 ---------------- */
export const listDoctors = () => request.get('/users/doctors')
export const pageUsers = (params) => request.get('/users', { params })
export const createUser = (data) => request.post('/users', data)
export const updateUser = (id, data) => request.put(`/users/${id}`, data)
export const updateUserStatus = (id, status) => request.put(`/users/${id}/status`, { status })
export const getClinic = () => request.get('/config/clinic')
export const updateClinic = (data) => request.put('/config/clinic', data)

/* ---------------- 操作审计（v2） ---------------- */
export const pageAuditLogs = (params) => request.get('/audit-logs', { params })
export const auditModules = () => request.get('/audit-logs/modules')

/* ---------------- 代煎（v2） ---------------- */
export const pageDecoctions = (params) => request.get('/decoctions', { params })
export const getDecoction = (id) => request.get(`/decoctions/${id}`)
export const cookDecoction = (id) => request.post(`/decoctions/${id}/cook`)
export const readyDecoction = (id) => request.post(`/decoctions/${id}/ready`)
export const deliverDecoction = (id, data) => request.post(`/decoctions/${id}/deliver`, data)
export const cancelDecoction = (id) => request.post(`/decoctions/${id}/cancel`)

/* ---------------- 预约（v2） ---------------- */
export const pageAppointments = (params) => request.get('/appointments', { params })
export const arriveAppointment = (id) => request.post(`/appointments/${id}/arrive`)
export const cancelAppointment = (id) => request.post(`/appointments/${id}/cancel`)
export const appointmentSlots = (params) => request.get('/appointments/slots', { params })

/* ---------------- 打印（v3） ---------------- */
export const fetchReceiptHtml = (chargeOrderId) =>
  request.get(`/print/receipt/${chargeOrderId}`, { responseType: 'text' })
export const fetchPrescriptionHtml = (prescriptionId) =>
  request.get(`/print/prescription/${prescriptionId}`, { responseType: 'text' })

/* ---------------- 管理设置（v3） ---------------- */
export const getPrintSettings = () => request.get('/settings/print')
export const updatePrintSettings = (data) => request.put('/settings/print', data)
export const getPaymentMethods = () => request.get('/settings/payment-methods')
export const updatePaymentMethods = (data) => request.put('/settings/payment-methods', data)
export const batchDrugLimits = (data) => request.put('/settings/drug-limits/batch', data)

/* ---------------- 追溯码（v3） ---------------- */
export const bindTraces = (data) => request.post('/traces/bind', data)
export const pageTraces = (params) => request.get('/traces', { params })
export const getTraceByCode = (code) => request.get(`/traces/code/${encodeURIComponent(code)}`)
export const listPrescriptionTraces = (id) => request.get(`/traces/prescription/${id}`)
export const uploadTraces = (ids) => request.post('/traces/upload', { ids })
export const returnTraces = (ids) => request.post('/traces/return', { ids })

/* ---------------- 数据导出（v3） ---------------- */
export const downloadExport = (type, params) =>
  request.get(`/export/${type}.csv`, { params, responseType: 'blob' })

/* ---------------- 医保政策库（v3） ---------------- */
export const pagePolicies = (params) => request.get('/policies', { params })
export const getPolicy = (id) => request.get(`/policies/${id}`)
export const createPolicy = (data) => request.post('/policies', data)
export const updatePolicy = (id, data) => request.put(`/policies/${id}`, data)
export const deletePolicy = (id) => request.delete(`/policies/${id}`)
export const policyReminders = () => request.get('/policies/reminders')

/* ---------------- 医保统计（v3） ---------------- */
export const insuranceStats = (params) => request.get('/reports/insurance', { params })
export const insuranceDaily = (params) => request.get('/reports/insurance/daily', { params })

/* ---------------- 病历与用药安全（v4） ---------------- */
export const suggestTerms = (params) => request.get('/tcm/suggest', { params })
export const checkRecord = (data) => request.post('/tcm/qc', data)
export const aiAssist = (data) => request.post('/ai/assist', data)
export const getAiSettings = () => request.get('/ai/settings')
export const updateAiSettings = (data) => request.put('/ai/settings', data)
export const checkPrescription = (data) => request.post('/prescriptions/check', data)
export const updateDrugClinical = (id, data) => request.put(`/drugs/${id}/clinical`, data)

/* ---------------- 数据导入（v5） ---------------- */
export const downloadImportTemplate = (type) =>
  request.get(`/import/templates/${type}`, { responseType: 'blob' })
export const previewImport = (type, file) => {
  const form = new FormData()
  form.append('type', type)
  form.append('file', file)
  return request.post('/import/preview', form)
}
export const executeImport = (type, file, mode) => {
  const form = new FormData()
  form.append('type', type)
  form.append('file', file)
  form.append('mode', mode || 'CREATE_ONLY')
  return request.post('/import/execute', form)
}

/* ---------------- 扫码发药 / 扫码出库（v6） ---------------- */
export const pharmacyScan = (data) => request.post('/pharmacy/scan', data)
export const scanProgress = (prescriptionId) => request.get(`/pharmacy/scan/progress/${prescriptionId}`)
export const pharmacyScanOut = (data) => request.post('/pharmacy/scan-out', data)

/* ---------------- 开方效率（v7） ---------------- */
export const suggestDrugs = (params) => request.get('/drugs/suggest', { params })
export const commonTerms = (params) => request.get('/tcm/common', { params })
