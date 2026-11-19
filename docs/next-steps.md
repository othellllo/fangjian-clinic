# 下一步待办（2026-11-18 收工存档）

当前版本：v0.1.0，功能到 v6（门诊闭环 / 代煎 / 小程序 / 审计 / 打印 / 追溯码 / 设置 / 导出 / 政策库 / 中西医病历+联想+质控+AI助写 / 用药安全 / Excel 迁移 / 扫码发药）。
验证：六套端到端冒烟合计 91 项全过；便携包 `release\方笺系统`（142.4MB，zip 118.4MB）从零启动可跑通。

## 一、需要用户提供信息才能推进（外部依赖）

1. **打印机型号与纸宽**（58mm / 80mm / A5 处方纸），决定小票套打和处方笺版式微调。
2. **是否医保定点**、当地医保经办机构接口文档与测试环境 —— 医保结算与追溯码上传都要靠这个，现在只有 SPI 桩。
3. **旧系统真实导出的 Excel（脱敏 5~10 行）** —— 按实际列名适配导入模板，比猜列名可靠。
4. **医馆常用药清单** —— 批量补齐煎法/剂量范围/妊娠风险（现在只种了 20 多味）。

## 二、功能缺口（按优先级）

1. **扫码发药撤销**：扫错药品/数量后目前无法撤销（库存已扣），需要「撤销本次扫码」回补库存并退回追溯码状态。
2. **药品条码标签打印**：饮片包装无条码，扫码发药要落地必须先能打药品编码/条码标签。
3. **收费票据套打**：若当地要求财政/税务监制票据，需要票号管理与套打模板。
4. 小程序：真机未验证（无微信开发者工具），首次导入可能需小修。
5. 扫码发药目前对「需追溯品种」要求逐件扫码，一粒装/散装拆零场景需要「拆零豁免」配置。
6. 患者妊娠状态字段已加实体但未开放建档维护，目前靠处方区手动选。

## 三、工程注意事项

- 打包脚本 `scripts\build-portable.ps1` 会重建前端+jlink+zip；**打包前必须先停掉正在运行的后端**，否则 jar 被占用会构建失败。
- 冒烟脚本共 6 个，跑之前先启动后端（便携包或 `java -jar` 均可）：
  `smoke.mjs`(34) → `smoke-v3.mjs`(19) → `smoke-v4.mjs`(17) → `smoke-v5.mjs`(12) → `smoke-v6.mjs`(9)
  注意 v3 依赖 v1/v2 先造数据；v5 需要 `scripts` 目录下的 `xlsx`（已装）。
- Windows 上 `npm run build` 可能报 esbuild `Access is denied`，绕法见 `scripts\README.md`。
- 演示账号密码均为 `123456`，正式交付前必须改；`JWT_SECRET` 必须替换（见 `docs\deploy.md`）。

## 四、开源发布（已完成代码侧，待用户推进）

- 已初始化 git 仓库并完成首个 commit（v0.1.0，322 个文件跟踪，.git 约 2.6MB）
- 已补齐：LICENSE(Apache-2.0)、CONTRIBUTING、CODE_OF_CONDUCT、SECURITY、CHANGELOG、
  .gitattributes、.editorconfig、CI（后端测试 + 前端构建）、Issue/PR 模板、docs/publishing.md
- 已脱敏：移除机器专用运行说明与截图脚本里的本机绝对路径
- 验证：干净克隆后 `mvn test` 与 `npm ci && npm run build` 均通过
- 待办（需要用户账号）：
  1. 在 GitHub 建空仓库并 `git remote add origin ... && git push -u origin main`
  2. 建 Release v0.1.0，把 `release/方笺系统-v0.1.0-便携版.zip`（约 118MB）作为附件上传
     （注意：单文件提交上限 100MB，便携包不能进 git，只能走 Release 附件）
  3. 仓库设置：Description/Topics/Discussions/分支保护/Dependabot/Secret scanning
  4. 决定 open-core 边界（医保地区实现、托管运维是否闭源），见 docs/publishing.md