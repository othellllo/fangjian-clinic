# 参与贡献（Contributing）

感谢你愿意参与「方笺」。这是一个面向中医馆/小型诊所的开源门诊系统，欢迎代码、文档、规则库、翻译与问题反馈。

## 一、开发环境

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 后端运行与编译 |
| Maven | 3.9+ | 也可用仓库根目录 `.tools/maven` 的便携版 |
| Node.js | 18+（推荐 20） | 前端与验证脚本 |
| MySQL | 8.0（可选） | 默认用 H2 文件库，本地开发无需安装 |

```bash
# 后端（默认 H2，零配置）
cd backend && mvn spring-boot:run

# 前端
cd frontend && npm install && npm run dev
# 打开 http://localhost:5173 ，接口代理到 8080

# 端到端冒烟（另开一个终端，后端需已启动）
node scripts/smoke.mjs
```

默认账号 `admin / 123456`。**请勿在真实数据上使用默认密码。**

## 二、代码结构

```
backend/src/main/java/com/fangjian/his/
├─ common/     统一响应、分页、异常、单号
├─ config/     安全、JWT、Jackson、审计过滤器、启动初始化
├─ domain/     实体与枚举
├─ repository/ Spring Data JPA
├─ service/    业务逻辑（按领域）
├─ controller/ REST 接口
├─ dto/        视图对象
└─ integration/ 医保/追溯码 SPI 扩展点
```

- 数据库变更一律新增 Flyway 迁移（`backend/src/main/resources/db/migration/V*.sql`），**禁止修改已发布的迁移脚本**。
- 金额用 `BigDecimal`，库存只增流水不改历史结存。
- 新增接口需同步更新 `docs/api-contract.md`。

## 三、提交规范

- 分支：`main` 保护，改动走 PR。
- 提交信息建议使用 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：
  `feat: 支持处方扫码撤销`、`fix: 修正追溯码重复核销`、`docs: 补充部署说明`。
- PR 请説明：改了什么、为什么、如何验证；涉及接口的先更新契约文档。
- 新增业务逻辑请补对应的冒烟脚本用例（`scripts/smoke*.mjs`）或后端测试。

## 四、提交前自检

```bash
cd backend && mvn -B test        # 后端测试（H2 内存库）
cd frontend && npm run build     # 前端构建
node scripts/smoke.mjs           # 端到端冒烟（需后端运行）
```

GitHub Actions 会对 push / PR 自动执行上述后端测试与前端构建。

## 五、关于医疗合规

- 本项目是**信息管理工具**，不构成诊疗建议，也不替代医师判断；用药安全提示仅作辅助。
- 贡献涉及临床规则（剂量、配伍、煎法）时，请在 PR 中注明依据（教材/药典/规范条款），便于评审。
- 请勿提交真实患者数据。测试数据请使用明显虚构的姓名与手机号。

## 六、许可

提交贡献即表示你同意以 [Apache License 2.0](LICENSE) 授权该贡献。
建议在提交信息中附带 `Signed-off-by`（DCO），表示你有权提交该代码。
