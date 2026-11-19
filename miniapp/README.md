# 医馆 HIS —— 微信小程序患者端

原生微信小程序（WXML / WXSS / JS），覆盖患者从**登录 → 预约挂号 → 我的预约 → 就诊记录 → 就诊详情（病历 / 处方 / 费用）**的完整闭环。不依赖 Taro / uni-app，不引入任何 npm 依赖。

后端接口唯一依据：`../docs/api-contract.md` 的 **§15 患者端（`/api/patient/**`）**。

---

## 一、快速开始

### 1. 导入微信开发者工具

1. 打开**微信开发者工具**，选择「导入项目」。
2. 目录选择本文件夹：`yiguan-his/miniapp`。
3. AppID 选择「**游客模式**」或直接使用工程内已配置的 `touristappid`（无需真实 AppID）。
4. 点击导入即可编译预览；`project.config.json` 中已设置 `urlCheck: false`，本地 `http://localhost` 不会报域名校验错误。

### 2. 启动后端

```bash
cd yiguan-his/backend
mvn spring-boot:run
```

默认地址 `http://localhost:8080`，接口前缀 `/api`。

### 3. 登录

- 手机号：后端患者档案中**已存在的手机号**（联调前可在员工端「患者档案」建档）。
- 验证码：开发环境固定 **`123456`**（页面上「获取验证码」不会真的发短信，页脚也有提示）。
- 登录成功后进入「预约」首页，token 存在 `patient_token`，患者资料存在 `patient_info`。

> 患者端使用**独立 JWT**（角色 `PATIENT`），与员工端 token 不通用。

### 4. 准备一个可登录的患者（首次联调必做）

后端初始化只创建员工账号（`admin / 123456` 等），**不会预置患者档案**。所以首次联调需要先建档：

1. 启动后端与员工端 Web（`frontend`），用 `admin / 123456` 登录；
2. 进入「患者档案 → 新建患者」，填写姓名、性别、**手机号**（如 `13900000001`）并保存；
3. 回到小程序，用该手机号 + 验证码 `123456` 登录。

也可以直接用接口建档（需员工 token）：

```bash
# 1) 员工登录拿 token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# 2) 建档（手机号即小程序登录账号）
curl -X POST http://localhost:8080/api/patients \
  -H "Authorization: Bearer <上一步的 token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"测试患者","gender":"MALE","birthDate":"1995-06-01","phone":"13900000001"}'
```

---

## 二、修改接口地址（baseURL）

打开根目录 `config.js`：

```js
module.exports = {
  BASE_URL: 'http://localhost:8080/api'
  // ...
}
```

| 调试方式 | BASE_URL 示例 | 说明 |
| --- | --- | --- |
| 开发者工具模拟器 | `http://localhost:8080/api` | 默认值，开箱即用 |
| 真机预览 / 真机调试 | `http://192.168.1.10:8080/api` | 换成电脑局域网 IP，手机与电脑同一 WiFi |
| 线上 | `https://your-domain.com/api` | 需在小程序后台「开发管理 → 服务器域名」配置 request 合法域名 |

真机注意：

- 微信不允许真机访问 `localhost`，必须用局域网 IP；同时确认电脑防火墙放行 8080 端口。
- 正式发布必须使用 **HTTPS + 已备案域名**；本地 http 仅用于开发调试。
- 开发者工具「详情 → 本地设置」中的「不校验合法域名…」对真机不生效。

---

## 三、目录结构

```
miniapp/
├─ app.js                  # 小程序入口，预读登录态
├─ app.json                # 页面注册、窗口样式、底部 tabBar
├─ app.wxss                # 全局样式：卡片、标签、按钮、空状态
├─ project.config.json     # 工程配置（appid=touristappid，urlCheck=false）
├─ sitemap.json            # 微信索引配置
├─ config.js               # ★ 接口地址、验证码提示、分页大小等常量
├─ README.md
├─ assets/
│  └─ tab/                 # tabBar 图标（本地 PNG，无外部图片依赖）
│     ├─ book.png / book-active.png
│     ├─ list.png / list-active.png
│     └─ user.png / user-active.png
├─ utils/
│  ├─ request.js           # 请求封装：baseURL、Bearer token、code!==0、401、loading
│  ├─ auth.js              # token / 患者资料存取、登录守卫、退出登录
│  ├─ api.js               # /patient/** 接口定义 + 后端字段容错归一化
│  └─ format.js            # 金额 / 日期格式化、状态文案与颜色映射
└─ pages/
   ├─ login/               # 手机号 + 验证码登录（60s 倒计时）
   ├─ index/               # 预约挂号（tab）：医生 / 日期 / 时段 / 主诉 / 提交
   ├─ appointments/        # 我的预约（tab）：即将就诊 / 历史，取消预约
   ├─ visits/              # 我的就诊记录：分页列表
   ├─ visit-detail/        # 就诊详情：患者信息 / 病历 / 处方 / 费用
   └─ mine/                # 我的（tab）：资料、会员余额、就诊记录入口、退出登录
```

每个页面均包含 `.js / .json / .wxml / .wxss` 四件套。

---

## 四、页面与接口依赖

| 页面 | 路由 | 调用接口 | 关键返回字段（按契约） |
| --- | --- | --- | --- |
| 登录 | `pages/login/login` | `POST /patient/auth/login` | `{ token, patient }`；开发环境验证码固定 `123456` |
| 预约挂号 | `pages/index/index`（tab） | `GET /patient/doctors` | `[{ id, realName/name, title, dept }]` |
| | | `GET /patient/slots?doctorId=&date=` | `[{ timeSlot, booked, capacity }]` |
| | | `POST /patient/appointments` | body `{ doctorId, appointDate, timeSlot, complaint }` |
| 我的预约 | `pages/appointments/appointments`（tab） | `GET /patient/appointments` | `AppointmentDto`：`{ id, appointNo, doctorName, appointDate, timeSlot, complaint, status, visitId }`；状态 `BOOKED/ARRIVED/CANCELLED` |
| | | `POST /patient/appointments/{id}/cancel` | 仅 `BOOKED` 展示取消按钮 |
| 就诊记录 | `pages/visits/visits` | `GET /patient/visits?page=&size=` | 分页 `{ list, total, page, size }`，列表项同 `VisitDto` |
| 就诊详情 | `pages/visit-detail/visit-detail?id=` | `GET /patient/visits/{id}` | 聚合 `{ visit, patient, record, prescriptions, charges }` |
| 我的 | `pages/mine/mine`（tab） | `GET /patient/profile` | `PatientDto`：`{ id, patientNo, name, gender, age, phone, memberId }`（暂无余额字段，见第六节） |

> 上表已与后端 `PatientPortalController` 实现对照（2026-11-18）：`/patient/doctors` 返回 `MemberDto.User`（`role=DOCTOR` 且 `status=1`，按姓名排序）；`/patient/slots` 返回 `[{timeSlot,booked,capacity}]`（每个时段容量 5）；`/patient/appointments` 返回 `AppointmentDto` 数组（无需分页）；`/patient/visits` 返回 `{list,total,page,size}`；`/patient/visits/{id}` 返回 `{visit,patient,record,prescriptions,charges}`。

### 统一响应与错误处理

所有接口返回 `{ code, message, data }`：

- `code === 0`：取 `data` 交给页面。
- `code !== 0`：`utils/request.js` 统一 `wx.showToast(message)`，页面只处理 loading / 空态。
- **401**（HTTP 状态码或 `code` 任一为 401）：清除 `patient_token` / `patient_info`，`wx.reLaunch` 回登录页。
- 网络异常（后端未启动、超时等）：toast「网络异常，请检查网络或后端服务」。

### 本工程对返回结构的兼容

后端联调期若字段命名与契约略有出入，`utils/api.js` 已做容错，页面无需改动：

- 列表既支持数组，也支持 `{ list, records, rows, items }` 分页对象。
- 医生姓名兼容 `realName` / `name` / `doctorName`；科室兼容 `dept` / `department`。
- 时段兼容 `timeSlot` / `slot` / `time`，已满兼容 `full` / `available=false` / `status=FULL` / `booked>=capacity`。
- 就诊详情兼容 `record` / `medicalRecord`，`charges` / `chargeOrders` / 单个对象。
- 会员余额兼容 `patient.memberBalance` / `balance` / `member.balance`；都缺失时按是否有 `memberId` 显示「—」或「未开通」。
- 金额一律按字符串十进制处理，展示时统一保留两位小数；数量 / 剂量会去掉多余的小数（`3.00` → `3`）。

---

## 五、交互与视觉约定

- **主色** `#2f7d5d`（中医馆绿），页面底色 `#f5f7f6`，白色卡片、圆角 8px（16rpx）、轻阴影。
- 状态彩色标签：已预约-蓝、已到店-绿、已取消-灰；就诊状态：候诊中-橙、就诊中-蓝、已完成-绿、已作废-灰。
- 空状态：纯 CSS 圆形图标 + 中文占位字（医 / 约 / 诊 / !），不引用外部图片。
- 加载态：`wx.showLoading`（请求封装统一开关），成功/失败用 `wx.showToast`；请求失败均有兜底文案与重试入口。
- 日期选择：今天起 7 天横向滚动，今天 / 明天 / 周几 标签；时段 3 列网格，已满置灰不可点。
- 底部 tabBar：预约 / 我的预约 / 我的（本地图标，选中变主色）。

---

## 六、与后端实现的差异 / 待确认

小程序严格按 `docs/api-contract.md` §15 实现，并按同期后端 `PatientPortalController` 的实际返回做了字段兼容。仍有一个**需求与后端字段的缺口**需要确认：

1. **「我的 - 会员余额」缺数据源（建议后端补充）**
   - 现状：`GET /patient/profile` 返回 `PatientDto`，字段为 `{id, patientNo, name, gender, birthDate, age, phone, idCard, address, allergy, chronicHistory, memberId, createdAt}`，**没有余额字段**。
   - 小程序处理：兼容 `memberBalance` / `balance` / `member.balance` 三种字段名；三者都不存在时，有 `memberId` 显示「—（会员余额请到前台查询）」，没有 `memberId` 显示「未开通」。
   - 建议：在 `/patient/profile` 增加 `memberBalance`（或返回 `member: MemberDto`），前端无需改动即可展示真实余额。

2. **登录只允许已建档患者**
   - 后端行为：手机号未建档时返回「该手机号尚未在医馆建档，请先到前台建档」；验证码错误返回「验证码错误」。小程序直接 toast 后端文案。
   - 待确认：是否允许小程序自助建档 / 注册？若需要，需新增接口。

3. **取消预约规则仅按状态**
   - 后端：`ARRIVED` 已到店不可取消，`CANCELLED` 不可重复取消，无「就诊前 N 小时」限制。小程序只在 `BOOKED` 时展示取消按钮。

4. **可预约日期范围**
   - 后端 `POST /patient/appointments` 只校验「不早于今天」，未限制最远日期；小程序按需求只展示今天起 7 天。若需严格限制，建议后端同步限制。

5. **真实短信验证码**
   - 当前验证码固定为后端配置 `app.patient.login-code`（默认 `123456`），「获取验证码」不发短信。接入短信服务商后需替换后端校验，前端无需改动。

6. **时段余量口径**
   - 后端 `slots` 返回 `{timeSlot,booked,capacity=5}`，已满判定为 `booked >= capacity`；小程序按此展示「余 N 个 / 已满」并禁止点击。

---

## 七、常见问题

**Q：页面提示「网络异常，请检查网络或后端服务」？**

1. 确认后端已在 `8080` 端口启动；
2. 开发者工具「详情 → 本地设置」勾选「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」；
3. 确认 `config.js` 的 `BASE_URL` 与实际后端地址一致。

**Q：真机预览登录不了？**

`localhost` 在手机上指向手机自己。把 `BASE_URL` 改成电脑局域网 IP（如 `http://192.168.1.10:8080/api`），并确保手机与电脑同一 WiFi、电脑防火墙放行端口。

**Q：登录后立刻被踢回登录页？**

说明接口返回了 401：检查 `patient_token` 是否被正确写入、后端患者端 JWT 过滤器是否只接受角色 `PATIENT`、token 是否已过期。

**Q：验证码为什么填 123456？**

演示 / 开发环境后端固定校验 `123456`，页面「获取验证码」只做 60s 倒计时，不真正发送短信。

**Q：登录提示「该手机号尚未在医馆建档」？**

患者端只允许已建档患者登录。请先在员工端「患者档案」用该手机号建档（或使用初始化数据里的演示患者手机号），再回到小程序登录。

**Q：可以不用真实 AppID 吗？**

可以。`project.config.json` 中 `appid` 为 `touristappid`（游客模式），本地编译、预览、真机调试均可使用。

---

## 八、说明

- 本工程为**演示 / 联调**用途，未接入微信支付、订阅消息等能力。
- 所有图标均为本地资源（`assets/tab/` 为脚本生成的 81×81 PNG），不加载任何外部图片。
- 接口若尚未实现，页面会按统一错误处理提示，不会白屏。
