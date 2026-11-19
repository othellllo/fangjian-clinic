# 开源版与商业版的分层（open-core）

目标：**开源版是完整可用的门诊系统**（不是阉割试用版），商业版/定制版在它之上叠加，而不是 fork 一份改到分家。

## 一、边界建议

| 能力 | 开源版 | 商业版 / 定制版 |
| --- | --- | --- |
| 门诊闭环（挂号/病历/处方/收费/发药） | ✅ | 复用 |
| 中医特色（中西医病历、联想、质控、AI 草稿） | ✅ | 复用 |
| 用药安全规则、特殊煎法 | ✅ | 复用 |
| 库存/批次/效期/扫码发药/追溯码采集 | ✅ | 复用 |
| 报表、审计、导出、Excel 迁移 | ✅ | 复用 |
| 便携版与部署脚本 | ✅ | 复用 |
| **地区医保接口实现**（结算报文、对账、退费） | SPI 桩 | 按地区实现（闭源） |
| **追溯码上传平台直连/自动补传** | SPI 桩 | 实现 + 运维（闭源） |
| **托管部署、远程运维、数据托管备份** | — | 服务（闭源） |
| **定制报表、第三方检验/影像对接** | — | 项目制（闭源） |
| **多租户 SaaS、计费** | 未实现 | 闭源 |

原则：**涉及地区资质、专有报文、长期运维的部分闭源；通用门诊能力全部开源。**

## 二、叠加方式（不改 OSS 主干）

### 1. 后端：独立 jar + SPI 实现

开源版已预留扩展点：

- `integration.InsuranceProvider`（医保结算）
- `integration.TraceUploadProvider`（追溯码上传）
- `ai` 相关（AI 模型接入，通过配置切换）

商业版做成独立 Maven 模块，依赖 OSS 的 jar，实现上述接口并注册为 Spring Bean：

```xml
<dependency>
  <groupId>com.fangjian</groupId>
  <artifactId>fangjian-clinic</artifactId>
  <version>0.1.0</version>
</dependency>
```

打包时把 OSS jar 与 pro jar 一起放进 classpath（Spring Boot 的 `PropertiesLauncher` 或自定义启动类）。
**不要复制 OSS 源码进 pro 仓库**，否则每次上游升级都要手工合并。

### 2. 数据库：独立迁移目录

OSS 的 Flyway 只跑 `classpath:db/migration`。商业版新增表不要改 OSS 迁移脚本，而是：

```yaml
spring:
  flyway:
    locations: classpath:db/migration,classpath:db/pro-migration
```

pro 迁移从 `V9001__pro_init.sql` 起编号，避免与 OSS 版本号冲突。

### 3. 前端：独立页面 + 路由叠加

OSS 前端按路由表驱动菜单。商业版页面单独构建，构建产物放进 `static/pro/`，
在 OSS 的菜单配置里按 `import.meta.glob` 动态注册（或由后端下发菜单，需小幅改造）。
不建议直接改 OSS 的 `router/index.js`，冲突成本高。

### 4. 版本策略

- OSS 打语义化 tag（`v0.1.0`），商业版依赖固定 tag 构建。
- 接口契约（`docs/api-contract.md`）向后兼容；破坏性变更走大版本并在 CHANGELOG 标注。
- OSS 修 bug → 打补丁版本 → 商业版升级依赖即可。

## 三、开源运营注意

1. **不要承诺"开源版也能做医保"**：SPI 是接口不是实现，接医保需要地区资质与专有报文。
2. **医疗宣称口径**：只描述为信息管理/病历书写辅助，不宣传诊断能力，避免触发医疗器械软件监管。
3. **商标**：项目名「方笺」不等于商标；商用请在 9/42/44 类做检索与注册。
4. **许可证**：OSS 用 Apache-2.0，商业模块可闭源（不与 Apache-2.0 冲突），但不得移除 OSS 部分的版权与许可声明。
5. **贡献回流**：商业客户提出的通用改进，回填到 OSS 主干，减少双份维护。
