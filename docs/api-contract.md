# 方笺 — 接口契约（前后端唯一事实来源）

基准：`http://localhost:8080/api`　开发期前端走 Vite 代理 `/api` → `http://localhost:8080`。

## 0. 通用约定

- 鉴权：除登录外的所有接口需 `Authorization: Bearer <token>`；401 表示登录失效。
- 统一响应体，HTTP 状态码恒为 200（401 除外）：

```json
{ "code": 0, "message": "ok", "data": {} }
```

- 业务失败：`code != 0`（400 参数错误、401 未登录、403 无权限、404 不存在、500 服务端错误），`message` 为可直接展示的中文。
- 分页返回：`{ "list": [], "total": 0, "page": 1, "size": 10 }`。
- 时间格式一律 `yyyy-MM-dd HH:mm:ss`，日期 `yyyy-MM-dd`。金额为字符串十进制（如 `"128.50"`），避免前端浮点误差。
- 枚举：`gender` `MALE|FEMALE`；`userRole` `ADMIN|DOCTOR|PHARMACIST|CASHIER`；`visitType` `FIRST|FOLLOWUP`；
  `visitStatus` `WAITING|IN_SERVICE|FINISHED|CANCELLED`；`prescriptionType` `HERB|PATENT|WESTERN`；
  `prescriptionStatus` `DRAFT|PENDING|DISPENSED|CANCELLED`；`chargeStatus` `UNPAID|PAID|REFUNDED`；
  `payMethod` `CASH|WECHAT|ALIPAY|BANK_CARD|MEMBER_BALANCE|INSURANCE`。

## 1. 认证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/login` | body `{username,password}` → `{token, user}` |
| GET | `/auth/me` | 当前用户 |
| POST | `/auth/logout` | 登出（无状态，前端删 token 即可） |

`user`：`{id, username, realName, role, title, phone}`

## 2. 患者

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/patients?keyword=&page=&size=` | 关键字匹配姓名/手机号/病历号 |
| POST | `/patients` | 建档 `{name,gender,birthDate,phone,idCard,address,allergy,chronicHistory}` |
| GET | `/patients/{id}` | 详情 |
| PUT | `/patients/{id}` | 修改 |
| GET | `/patients/{id}/visits` | 该患者全部就诊记录 |

`PatientDto`：`{id,patientNo,name,gender,birthDate,age,phone,idCard,address,allergy,chronicHistory,memberId,createdAt}`

## 3. 挂号 / 就诊

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/visits/register` | `{patientId,doctorId,type,complaint}` → VisitDto，同时生成待收费挂号单 |
| GET | `/visits/queue?doctorId=` | 该医生候诊队列（WAITING + IN_SERVICE，按挂号时间） |
| GET | `/visits?date=&status=&doctorId=&page=&size=` | 就诊记录查询 |
| GET | `/visits/{id}` | `{visit, patient, record, prescriptions, charges}` |
| POST | `/visits/{id}/start` | 接诊，状态 → IN_SERVICE |
| PUT | `/visits/{id}/record` | 保存病历，见 §4 |
| POST | `/visits/{id}/finish` | 完成就诊 → FINISHED |
| POST | `/visits/{id}/cancel` | 作废 → CANCELLED |

`VisitDto`：`{id,visitNo,queueNo,patientId,patientName,patientGender,patientAge,doctorId,doctorName,type,status,registerFee,registerTime,startTime,endTime,complaint,chargeOrderId}`

## 4. 中医病历

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/visits/{id}/record` | 取病历（可能为 null） |
| PUT | `/visits/{id}/record` | 保存（幂等 upsert，记录版本号） |

body / `MedicalRecordDto`：
`{id,visitId,chiefComplaint,presentIllness,pastHistory,inspection,auscultation,inquiry,palpation,tongue,pulse,syndrome,treatmentPrinciple,advice,version,updatedAt}`
（`inspection` 望、`auscultation` 闻、`inquiry` 问、`palpation` 切、`tongue` 舌象、`pulse` 脉象、`syndrome` 辨证、`treatmentPrinciple` 治则）

## 5. 处方

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/prescriptions` | `{visitId,type,decoctionMethod,doses,remark,items:[{drugId,dose,quantity,usageText}]}`，自动算价并生成收费单 |
| GET | `/prescriptions/{id}` | 详情 |
| GET | `/prescriptions?visitId=` | 某次就诊的处方 |
| GET | `/prescriptions/pending-dispense` | 药房待发药（已收费未发药） |
| POST | `/prescriptions/{id}/dispense` | 发药，扣减批次库存并写流水 |
| POST | `/prescriptions/{id}/cancel` | 作废（仅 DRAFT/PENDING） |

`PrescriptionDto`：`{id,prescriptionNo,visitId,patientName,doctorName,type,decoctionMethod,doses,totalAmount,status,remark,createdAt,chargeOrderId,items:[{id,drugId,drugName,spec,unit,dose,quantity,price,amount,usageText}]}`

## 6. 收费

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/charges?status=&date=&page=&size=` | 收费单列表 |
| GET | `/charges/{id}` | `{order, items, patient}` |
| POST | `/charges/{id}/pay` | `{method,remark}`；MEMBER_BALANCE 自动扣会员余额 |
| POST | `/charges/{id}/refund` | `{reason}` |
| GET | `/charges/summary?date=` | 日结：`{total,count,byMethod:[{method,amount,count}]}` |

`ChargeOrderDto`：`{id,orderNo,visitId,patientId,patientName,type,totalAmount,discountAmount,payableAmount,paidAmount,status,createdAt,paidAt,items:[{id,itemType,itemName,quantity,unitPrice,amount}]}`

## 7. 药品与库存

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/drugs?keyword=&category=&page=&size=` | 药品字典，兼容按拼音码/编码搜索 |
| POST | `/drugs` | `{code,name,pinyin,category,spec,unit,price,costPrice,stockWarn}` |
| PUT | `/drugs/{id}` | 修改 |
| GET | `/drugs/{id}/stock` | `{drug,totalQty,batches:[{batchNo,productionDate,expiryDate,quantity}]}` |
| POST | `/drugs/{id}/stock-in` | `{batchNo,productionDate,expiryDate,quantity,costPrice,remark}` 入库 |
| POST | `/drugs/{id}/stock-adjust` | `{batchNo,quantity,remark}` 盘点调整（quantity 为调整后数量） |
| GET | `/stock/ledger?drugId=&page=&size=` | 出入库流水 |
| GET | `/stock/alerts` | `{lowStock:[],expiring:[]}` 低库存 + 90 天内临期 |

`DrugDto`：`{id,code,name,pinyin,category,spec,unit,price,costPrice,stockWarn,stockQty,status}`
`category`：`HERB` 饮片 / `PATENT` 中成药 / `WESTERN` 西药 / `MATERIAL` 耗材

## 8. 会员

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/members?keyword=&page=&size=` | |
| POST | `/members` | `{patientId,phone,name,level}` |
| GET | `/members/{id}` | `{member,txns}` |
| POST | `/members/{id}/recharge` | `{amount,method,remark}` |
| GET | `/members/{id}/txns?page=&size=` | 储值流水 |

`MemberDto`：`{id,memberNo,patientId,name,phone,level,balance,points,status,createdAt}`

## 9. 报表

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/reports/daily?date=` | `{date,revenue,registerCount,visitCount,prescriptionCount,refund,byMethod,byDoctor,topDrugs}` |
| GET | `/reports/range?start=&end=` | 区间每日趋势 `[{date,revenue,visitCount}]` |
| GET | `/reports/doctor-performance?start=&end=` | `[{doctorId,doctorName,visitCount,prescriptionCount,revenue}]` |
| GET | `/reports/drug-sales?start=&end=` | `[{drugName,quantity,amount}]` |

## 10. 医保 / 第三方（扩展点，第一版为桩实现）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/insurance/providers` | `[{code,name,enabled,region}]` |
| POST | `/insurance/settle` | `{chargeOrderId,providerCode}` → `{settlementNo,status,selfPayAmount,insuranceAmount}` |
| GET | `/insurance/settlements?orderId=` | 结算记录 |

规则：`InsuranceProvider` 为 SPI 接口，各地区实现通过 Spring Bean 注入；默认 `NoopInsuranceProvider` 直接返回"未开通"。

## 11. 系统

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/users/doctors` | 挂号台选医生用：`[{id,realName,title,dept}]` |
| GET | `/users?page=&size=` | 员工列表（ADMIN） |
| POST | `/users` | `{username,password,realName,role,title,dept,phone}` |
| PUT | `/users/{id}` | 修改（含改密） |
| PUT | `/users/{id}/status` | `{status}` 1 启用 / 0 停用 |
| GET | `/config/clinic` | 医馆信息 `{name,address,phone,notice}` |
| PUT | `/config/clinic` | 修改（ADMIN） |

---

# 第二版新增接口（v2）

## 12. 操作审计（ADMIN）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/audit-logs?module=&keyword=&start=&end=&page=&size=` | 审计日志分页；`start`/`end` 为 `yyyy-MM-dd` |
| GET | `/audit-logs/modules` | 已有模块名列表，供筛选下拉 |

`AuditLogDto`：`{id,username,realName,role,module,action,targetType,targetId,detail,ip,result,createdAt}`
`result`：`SUCCESS` / `FAIL`

## 13. 代煎管理

处方类型为 `HERB` 且 `decoctionMethod` 为「代煎」的处方，**发药时自动生成代煎单**。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/decoctions?status=&keyword=&page=&size=` | 代煎单分页；`keyword` 匹配代煎号/患者名 |
| GET | `/decoctions/{id}` | 详情（含药品明细） |
| POST | `/decoctions/{id}/cook` | 开始煎制 → `COOKING` |
| POST | `/decoctions/{id}/ready` | 煎制完成 → `READY`（待取） |
| POST | `/decoctions/{id}/deliver` | `{deliverType,remark}` 交付 → `DELIVERED` |
| POST | `/decoctions/{id}/cancel` | 作废 → `CANCELLED` |

状态：`PENDING` 待煎 / `COOKING` 煎制中 / `READY` 待取 / `DELIVERED` 已交付 / `CANCELLED` 已作废
`DecoctionDto`：`{id,decoctionNo,prescriptionId,prescriptionNo,patientId,patientName,doctorName,doses,status,decoctionMethod,expectedTime,cookStartTime,cookEndTime,deliverTime,deliverType,phone,remark,createdAt,items:[{drugName,dose,quantity,unit}]}`

## 14. 预约（员工侧）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/appointments?date=&status=&page=&size=` | 预约列表（默认当天） |
| POST | `/appointments/{id}/arrive` | 到店报到：生成就诊记录（WAITING），预约状态 → `ARRIVED` |
| POST | `/appointments/{id}/cancel` | 取消预约 |
| GET | `/appointments/slots?doctorId=&date=` | 该医生当日可约时段 `[{timeSlot,booked,capacity}]` |

`AppointmentDto`：`{id,appointNo,patientId,patientName,patientPhone,doctorId,doctorName,appointDate,timeSlot,complaint,status,visitId,source,createdAt}`
状态：`BOOKED` 已预约 / `ARRIVED` 已到店 / `CANCELLED` 已取消

## 15. 患者端（微信小程序，`/api/patient/**`）

患者端使用**独立 JWT**（角色 `PATIENT`，subject 为患者 id），与员工端 token 不通用。

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| POST | `/patient/auth/login` | 公开 | `{phone,code}` → `{token,patient}`；开发环境验证码固定 `123456` |
| GET | `/patient/profile` | PATIENT | 我的资料 |
| GET | `/patient/doctors` | PATIENT | 可预约医生列表 |
| GET | `/patient/slots?doctorId=&date=` | PATIENT | 可约时段 |
| GET | `/patient/appointments` | PATIENT | 我的预约 |
| POST | `/patient/appointments` | PATIENT | `{doctorId,appointDate,timeSlot,complaint}` 预约挂号 |
| POST | `/patient/appointments/{id}/cancel` | PATIENT | 取消预约 |
| GET | `/patient/visits?page=&size=` | PATIENT | 我的就诊记录 |
| GET | `/patient/visits/{id}` | PATIENT | 就诊详情（病历摘要 + 处方 + 费用） |

### §15 补充：返回结构说明

- `POST /patient/auth/login` → `{token, patient}`，`patient` 为 §2 的 `PatientDto`。
- `GET /patient/profile` → `PatientProfileDto`：在 `PatientDto` 基础上增加
  `memberBalance`（会员余额，未开卡为 `null`）与 `memberLevel`（1/2/3）。
- `GET /patient/doctors` → `[{id, username, realName, role, title, dept, phone, status}]`（仅启用中的医生）。
- `GET /patient/slots?doctorId=&date=` → `[{timeSlot, booked, capacity}]`，已满（`booked >= capacity`）前端置灰。
- `GET /patient/visits` → 分页 `VisitDto`。
- `GET /patient/visits/{id}` → `{visit, patient, record, prescriptions, charges}`，与员工端 §3 详情一致；
  访问非本人就诊返回 `403`。
- `GET /patient/appointments` → `AppointmentDto[]`（按日期倒序）。

---

# 第三版新增接口（v3）：打印 / 追溯码 / 设置 / 导出 / 政策库 / 医保统计

## 16. 打印

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/print/receipt/{chargeOrderId}` | 返回收费小票 HTML（`text/html`，已含打印样式，打开即打印） |
| GET | `/print/prescription/{prescriptionId}` | 返回处方笺 HTML（A5/A4 版式） |

- 前端用法：`window.open('/api/print/receipt/123')`，页面加载后自动调 `window.print()`。
- 小票宽度、抬头、页脚、份数由 §17 打印设置控制。
- 打印内容：小票含项目明细/合计/支付方式/医保支付与个人自付/收费员；处方含患者、诊断、药品明细、剂数、煎法、医师、金额。

## 17. 管理设置

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/settings/print` | `{receiptWidth:"58"\|"80", header, footer, copies, clinicName, showClinicName}` |
| PUT | `/settings/print` | 同上结构 |
| GET | `/settings/payment-methods` | `[{method,name,enabled,sort}]`，method 取值见 §0 |
| PUT | `/settings/payment-methods` | 传数组整体覆盖（至少保留一种启用） |
| PUT | `/settings/drug-limits/batch` | `{category, stockWarn, stockMax}` 按分类批量设置库存上下限 |

`drug` 新增字段：`stockMax`（库存上限）、`needTrace`（是否需采集追溯码，饮片默认 false）。

## 18. 药品追溯码

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/traces/bind` | `{drugId,batchNo,codes:[...]}` 入库扫码绑定（批量，返回成功/失败明细） |
| GET | `/traces?drugId=&batchNo=&status=&keyword=&page=&size=` | 追溯码台账 |
| GET | `/traces/code/{code}` | 按码反查：药品、批次、入库/发药时间、患者、处方 |
| GET | `/prescriptions/{id}/traces` | 该处方已使用的追溯码 |
| POST | `/traces/upload` | `{ids:[...]}` 上传医保平台（SPI 桩，未接入返回 `UNSUPPORTED`） |
| POST | `/traces/return` | `{ids:[...]}` 退药：状态回退 `IN_STOCK` 并记流水 |

`TraceStatus`：`IN_STOCK` 在库 / `DISPENSED` 已发药 / `RETURNED` 已退药 / `VOID` 作废
`TraceDto`：`{id,drugId,drugName,batchNo,code,status,bindTime,prescriptionId,patientName,dispenseTime,operatorName}`

**发药强校验**：处方中含 `needTrace=true` 的药品时，`POST /prescriptions/{id}/dispense`
必须携带 `traces:[{drugId, codes:[...]}]`，每个需追溯药品的码数量 >= 该药品数量；否则返回业务错误。

## 19. 数据导出（CSV，UTF-8 BOM，Excel 可直接打开）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/export/{type}.csv?start=&end=` | `type`：`patients`/`visits`/`charges`/`prescriptions`/`stock`/`audit`/`traces` |

- `start`/`end` 为日期（可选，按创建时间过滤）；`audit` 仅 ADMIN。
- 导出为写操作审计记录的来源，会记入审计日志。

## 20. 医保政策库

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/policies?keyword=&category=&status=&page=&size=` | 政策列表 |
| GET | `/policies/{id}` | 详情 |
| POST | `/policies` | 新增（字段见下） |
| PUT | `/policies/{id}` | 修改 |
| DELETE | `/policies/{id}` | 删除（仅 ADMIN，建议改状态而非删除） |
| GET | `/policies/reminders` | 生效前 30 天 / 失效前 30 天提醒 |

`category`：`TRACE` 追溯码 / `SETTLEMENT` 结算 / `CATALOG` 目录 / `FEE` 价格 / `SUPERVISION` 监管 / `OTHER`
`status`：`DRAFT` / `EFFECTIVE` / `EXPIRED`
`PolicyDto`：`{id,title,issuer,docNo,category,publishDate,effectiveDate,expireDate,summary,content,sourceUrl,status,createdAt}`

## 21. 医保结算统计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/reports/insurance?start=&end=` | `[{providerCode,count,insuranceAmount,selfPayAmount,total,avgAmount}]` |
| GET | `/reports/insurance/daily?date=` | 当日医保结算明细汇总 + 按支付方式 |

`avgAmount` = 均次费用（医保结算总额 / 结算笔数），口径为 `insurance_settlement` 表。

---

# 第四版新增接口（v4）：病历类型/质控、联想、AI 助写、处方安全审查

## 22. 病历类型与质控

`MedicalRecordRequest` 新增字段：
`recordType`（`TCM`|`WESTERN`）、`tcmDisease`（中医病名）、`physicalExam`（西医体格检查）、
`auxiliaryExam`（辅助检查）、`westernDiagnosis`（西医诊断）、`icdCode`（ICD-10）。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/tcm/suggest?category=&keyword=&limit=` | 联想：标准术语 + 本院历史病历短语 |
| POST | `/tcm/qc` | 质控预检，body 同 `MedicalRecordRequest`，返回 `{passed, issues}` |

`category`：`SYMPTOM` 症状 / `SYNDROME` 证型 / `TONGUE` 舌象 / `PULSE` 脉象 / `TREATMENT` 治法 / `FORMULA` 方名 / `DIAGNOSIS` 西医诊断
`issues[]`：`{level:BLOCK|WARN|INFO, type, field, message, advice}`
`PUT /visits/{id}/record` 保存时自动质控，**存在 BLOCK 级问题时返回业务错误并不保存**。

## 23. AI 助写

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/ai/settings` | `{enabled,provider,baseUrl,model,hasApiKey}` |
| PUT | `/ai/settings` | ADMIN 修改；`provider`：`LOCAL`（离线模板）或 `OPENAI_COMPATIBLE` |
| POST | `/ai/assist` | `{field,recordType,context}` → `{enabled,source:LOCAL|LLM,text,notice}` |

`field`：`presentIllness` / `treatmentPrinciple` / `advice` / `physicalExam`
AI 只返回草稿，不入库；医生确认后随病历保存。

## 24. 处方安全审查

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/prescriptions/check` | `{items:[{drugId,dose,quantity}],pregnant}` → `{passed,issues,annotations}` |

规则：十八反/十九畏（BLOCK）、妊娠禁用（BLOCK）/慎用（WARN）、毒性药（WARN）、
剂量超药典范围（WARN）、特殊煎法自动标注（annotations）。
`POST /prescriptions` 会自动执行同一套审查：BLOCK 级直接拒绝；特殊煎法自动写入该味药的 `usageText`。

## 25. 药品临床属性

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| PUT | `/drugs/{id}/clinical` | `{specialHandling,doseMin,doseMax,pregnancyLevel,toxicityLevel,functions}` |

`specialHandling`：`NORMAL`/`PRE_DECOCT` 先煎/`POST_ADD` 后下/`WRAP_DECOCT` 包煎/
`SEPARATE_DECOCT` 另煎/`MELT` 烊化/`TAKE_WITH` 冲服
`pregnancyLevel`：`NONE`/`CAUTION`/`FORBIDDEN`；`toxicityLevel`：`NONE`/`MILD`/`TOXIC`
`DrugDto` 已包含上述字段与 `functions`。

---

# 第五版新增接口（v5）：Excel 数据导入

## 26. 导入模板

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/import/templates/{type}` | 下载 Excel 模板（`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`） |

`type`：`PATIENT` 患者档案 / `DRUG` 药品字典 / `STOCK` 初始库存 / `RECORD` 历史病历
模板含：表头（中文，带 `*` 表示必填）、填写说明行、示例行。

## 27. 导入预览（只校验，不落库）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/import/preview` | `multipart/form-data`：`file`（xlsx/csv）、`type` |

返回 `{type,total,valid,invalid,errors:[{row,field,message}],sample:[{...前 5 行解析结果}]}`
- 单次最多 2000 行，超出报错
- `errors` 最多返回 50 条

## 28. 执行导入

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/import/execute` | `multipart/form-data`：`file`、`type`、`mode`（`CREATE_ONLY` 默认 / `UPSERT`） |

返回 `{inserted,updated,skipped,failed,errors:[{row,field,message}]}`
- 逐行独立事务：某行失败不影响其他行，失败原因回传
- 匹配键：患者 = 手机号 + 姓名；药品 = 编码；病历 = 患者手机号 + 就诊日期
- 病历导入会自动创建「已完成」的历史就诊记录（不可用于当日业务）
- 导库存会写库存流水（`MANUAL` 来源），不直接改批次
- 仅 ADMIN；导入行为进操作审计

## Excel 模板列

| 类型 | 列（`*` 必填） |
| --- | --- |
| PATIENT | 姓名*、性别*（男/女）、出生日期（yyyy-MM-dd）、手机号*、身份证号、地址、过敏史、既往史 |
| DRUG | 药品编码*、名称*、拼音码、分类*（中药饮片/中成药/西药/耗材）、规格、单位*、售价*、进价、库存预警值、库存上限、需追溯码（是/否）、特殊煎法（常规/先煎/后下/包煎/另煎/烊化/冲服）、剂量下限、剂量上限、妊娠风险（无/慎用/禁用）、毒性（无/轻微/毒性）、功效 |
| STOCK | 药品编码*、批号*、生产日期、有效期、数量*、进价、备注 |
| RECORD | 患者手机号*、就诊日期*（yyyy-MM-dd）、医生姓名*、主诉*、现病史*、既往史、望诊、闻诊、问诊、切诊、舌象、脉象、中医病名、证候、治则治法、医嘱 |

---

# 第六版新增接口（v6）：扫码发药 / 扫码出库

药房实际操作是「扫一下 → 库存就减一」，不用先点发药再核销。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/pharmacy/scan` | `{prescriptionId, code, quantity?, traceCode?}` 扫一次扣一次库存，扫完自动完成发药 |
| GET | `/pharmacy/scan/progress/{prescriptionId}` | 发药进度 `[{drugId,drugName,unit,required,scanned,needTrace,done}]` |
| POST | `/pharmacy/scan-out` | `{code, quantity, reason}` 无处方直接出库（领用/报损），写库存流水 `SCAN_OUT` |

## 扫码规则

| 药品类型 | 扫什么 | 行为 |
| --- | --- | --- |
| 中成药 / 西药 / 耗材（`needTrace=1`） | 包装上的 **20 位追溯码** | 核销该码 + 扣 1 件库存 |
| 中药饮片（`needTrace=0`） | 药品条形码或药品编码（`drug.barcode`，为空回退 `code`） | 按 `quantity` 扣库存（不传则发完剩余量） |

- 处方所有明细「已扫 >= 需要」时自动置为 `DISPENSED`；代煎处方自动进入煎药流程。
- 需追溯药品禁止用条码出库；追溯码必须在库（`IN_STOCK`）。
- 同一处方一旦有扫码记录，**禁止再用旧的整单发药接口**（避免重复扣库存）。
- 直接出库必须填写原因，库存流水 `ref_type=SCAN_OUT`，操作人进审计。
