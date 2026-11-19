# 医馆 HIS — 领域模型与设计说明

本文说明第一版的数据模型、关键业务规则与取舍理由，供二次开发与评审使用。

## 一、领域模型

```
                 ┌──────────┐
                 │ clinic   │  (sys_config 键值对存医馆信息)
                 └──────────┘

 patient 1 ──── n visit 1 ──── 1 medical_record
   │               │ 1
   │               ├──── n prescription 1 ──── n prescription_item ──┐
   │               │           │ 1                                   │
   │               │           └── 1 charge_order 1 ──── n charge_item
   │               └──── n charge_order (REGISTER / PRESCRIPTION)
   │                            └──── n payment
   └──── 0..1 member_card 1 ──── n member_txn

 drug 1 ──── n drug_batch          (库存结存)
 drug 1 ──── n stock_ledger        (库存流水，只增不改)
 drug 1 ──── n prescription_item   (药名/价格做快照)

 charge_order 1 ──── n insurance_settlement   (医保/第三方结算记录)
```

### 表清单

| 表 | 作用 | 关键约束 |
| --- | --- | --- |
| `sys_user` | 员工账号 | `username` 唯一；`role` 四角色；`status` 启停 |
| `sys_config` | 医馆配置 | `config_key` 唯一 |
| `patient` | 患者档案 | `patient_no` 唯一；可关联会员 |
| `visit` | 一次挂号 = 一次就诊 | `visit_no` 唯一；`(doctor_id, register_time)` 索引 |
| `medical_record` | 中医病历 | `visit_id` 唯一（一诊一档） |
| `drug` | 药品/饮片字典 | `code` 唯一；`stock_warn` 低库存阈值 |
| `drug_batch` | 批号+效期库存 | 结存 `quantity`，按效期先进先出 |
| `stock_ledger` | 库存流水 | 记录变动数量与变动前后结存 |
| `prescription` / `prescription_item` | 处方 | `charge_order_id` 关联收费单 |
| `charge_order` / `charge_item` | 收费单 | 状态机 `UNPAID → PAID → REFUNDED` |
| `payment` | 支付流水 | 退费写负数，便于对账 |
| `member_card` / `member_txn` | 会员储值 | 余额与流水同事务更新 |
| `insurance_settlement` | 医保结算记录 | 关联收费单 |

## 二、关键业务规则

### 1. 就诊状态机

```
WAITING ──start──> IN_SERVICE ──finish──> FINISHED
   │                    │
   └──── cancel ────────┴──────────────> CANCELLED
```

- 只有 `WAITING` 才能接诊；只有 `IN_SERVICE` 才能开方、写病历。
- `FINISHED` 不可作废；作废时自动关闭未收费的挂号单。

### 2. 处方与收费解耦

开方即生成 `charge_order(PRESCRIPTION)`，`prescription.status = PENDING`；
**收费后**药房才允许发药（`dispense` 会校验收费单状态），发药后处方变为 `DISPENSED`。
这样杜绝「先发药后收费」导致的漏费。

### 3. 库存：批次 + 流水

- 结存只看 `drug_batch.quantity`，任何变动都追加一条 `stock_ledger`。
- 发药按 **近效期先出（FEFO）** 跨批次扣减，扣减前先校验总可用量，不足则整单回滚。
- 盘点采用「调整到目标值」语义，差异写入流水，不改历史。

### 4. 收费与退费

- `charge_order.payable_amount` 为应收，`paid_amount` 为实收。
- 支付写 `payment` 正数；退费写 `payment` 负数并将单状态置 `REFUNDED`，
  日报营收直接对 `payment` 求和，天然含退费冲减。
- 会员余额支付在同一事务内扣减 `member_card.balance` 并写 `member_txn`。

### 5. 病历可追溯

`medical_record.version` 每次保存 +1，主诉同步回写 `visit.complaint` 便于列表展示。
第一版未做历史版本快照表，路线图中有「操作审计日志」。

## 三、技术取舍

| 选择 | 理由 |
| --- | --- |
| 单体 + 按领域分包 | 单机私有化部署，一套 jar 上线；避免小团队过早拆微服务 |
| Spring Data JPA | 减少手写 SQL 出错面；报表统计用 `JdbcTemplate` 直写聚合 SQL |
| Flyway 迁移 | 医馆现场升级需要可回溯的建表/改表脚本 |
| H2（MySQL 模式）默认 | 开发与演示零安装；生产切 MySQL，Flyway 脚本两边兼容 |
| 统一响应体 `{code,message,data}` | 前端统一拦截器处理错误与 401 |
| JWT 无状态 | 单机部署无需会话共享，前端存 localStorage |

## 四、v2 新增设计

### 1. 操作审计（audit_log）

不靠业务代码到处埋点，而是用 **Servlet 过滤器**（`AuditFilter`，注册在过滤器链最内层）
统一拦截所有非 GET 的 `/api/**` 请求：此时 SecurityContext 仍然有效，因此能自然拿到操作人。

- 记录字段：操作人（id/账号/姓名/角色）、模块（按路径首段映射中文）、动作（`METHOD 路径`）、
  对象（路径中的数字段作为 targetId）、结果（HTTP 状态）、IP（优先 `X-Forwarded-For`）、耗时。
- 登录成功/失败由 `AuthService` 单独记录（登录时还没有 SecurityContext）。
- 写日志失败不影响业务（`AuditService` 内部全部 catch 并只打 warn）。
- 局限：HTTP 层审计拿不到业务语义（例如「改了哪张处方」），需要更细时可在 service 上再补埋点。

### 2. 预约（appointment）

```
小程序选医生/时段 → BOOKED ──arrive──> ARRIVED（同时生成 visit，进入 WAITING 队列）
                        └──cancel──> CANCELLED
```

- 号源：固定 12 个时段 × 每时段 5 人（`AppointmentService.TIME_SLOTS`），先查后写。
- 患者姓名/手机号/医生姓名为**快照字段**，列表查询不联表。
- 预约本身不占用就诊号；到店报到才 `VisitService.register`，排队号依然按当天顺序生成。

### 3. 代煎（decoction_order）

代煎是饮片处方特有的履约流程，放在**发药之后**：

```
处方发药（扣库存）→ 自动建代煎单 PENDING → COOKING → READY → DELIVERED
```

- 触发条件：处方类型 `HERB` 且 `decoctionMethod` 含「代煎」。
- 与发药解耦但仍受控：没发药就没有代煎单；作废代煎单不回滚库存（饮片已实际消耗）。
- 预计完成时间默认 +2 小时，实际以煎药房操作为准。

### 4. 患者端鉴权

患者 token 与管理端 token 同一套 JWT，但**角色为 `PATIENT`**，`subject` 是患者 id：

- `/api/patient/**` 只允许 `ROLE_PATIENT`；`/api/**` 其他接口只允许四个员工角色。
- 患者只能看**自己的**就诊记录：`VisitService.patientVisitDetail` 校验 `visit.patientId`。
- 短信验证码未接服务商前，开发环境固定 `123456`（配置项 `app.patient.login-code`）。

## 五、已知限制与后续演进

1. **多租户**：当前为单医馆单实例。SaaS 化需要给业务表加 `tenant_id` 并挂数据权限拦截器。
2. **病历历史版本**：只有 `version` 递增与审计日志，没有逐版本内容快照（不可否认追溯仍不完整）。
3. **医保**：只留 SPI，未实现任何地区真实报文；接入时新增 `InsuranceProvider` 实现即可。
4. **并发扣库存**：当前依赖数据库事务与行级更新，极端并发下建议给 `drug_batch` 加乐观锁版本号。
5. **患者端短信**：固定验证码仅供演示，生产必须接入短信服务商。
6. **前端体积**：Element Plus 与 ECharts 目前全量引入，可改按需加载优化首屏。
