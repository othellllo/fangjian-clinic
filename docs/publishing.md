# 开源发布指南（维护者用）

仓库已按开源项目整理好：许可证、贡献指南、行为准则、安全策略、Issue/PR 模板、CI、更新日志。
下面是把它推上 GitHub 并正式发布的步骤。

## 一、推送到 GitHub

在 GitHub 新建一个**空仓库**（不要勾选初始化 README/.gitignore/LICENSE），然后：

```bash
cd J:\医馆系统
git remote add origin https://github.com/<你的账号>/yiguan-his.git
git push -u origin main
```

> 如果推送需要认证：HTTPS 用 Personal Access Token（不要用账号密码），或配置 SSH key。

## 二、仓库设置（10 分钟）

| 项目 | 建议 |
| --- | --- |
| Description | 开源中医馆/诊所门诊管理系统：挂号、中医病历、处方、药房扫码、收费、报表 |
| Topics | `tcm` `clinic` `his` `medical` `spring-boot` `vue3` `mysql` `tcm-clinic` `healthcare` |
| Website | 暂空，或放文档站 |
| Discussions | 开启，分类：问答 / 功能想法 / 展示 |
| Issues | 模板已内置（`.github/ISSUE_TEMPLATE/`） |
| Releases | 按下方第三节发布 v0.1.0 |

建议在 **Settings → Branches** 给 `main` 加保护：要求 PR、要求 CI 通过、禁止 force push。

建议开启 **Settings → Code security**：Dependabot alerts、Secret scanning、Private vulnerability reporting。

## 三、发布 v0.1.0

1. 确认 CI 绿（`.github/workflows/ci.yml`：后端测试 + 前端构建）。
2. 打标签并发布：

```bash
git tag -a v0.1.0 -m "v0.1.0 首个可运行版本"
git push origin v0.1.0
```

3. 在 GitHub Releases 用 `v0.1.0` 创建 Release，标题与说明可直接用 `CHANGELOG.md` 对应段落。
4. **便携版 zip 作为 Release 附件上传**（`release/医馆系统-v0.1.0-便携版.zip`，约 117MB）。

> 注意：GitHub 单文件提交上限 100MB，所以**便携包不要提交进 git**（`.gitignore` 已忽略 `release/`），
> 只作为 Release 附件分发（Release 附件上限 2GB）。

## 四、README 首屏建议

开源项目的第一屏决定别人是否继续看：

1. 一句话定位 + 三张截图（挂号台、医生工作台、药房扫码）——仓库里 `screenshots/` 已就绪。
2. "为什么做这个"：面向中医馆/小型诊所，单机私有化，数据不出店。
3. 30 秒跑起来：便携版双击 / Docker Compose / 源码三种方式各给一条命令。
4. 功能清单（表格）+ 已验证项（91 项冒烟）+ 明确的"已知边界"。

## 五、版本与分支策略

- 语义化版本：`0.x` 阶段允许不兼容改动，但需在 CHANGELOG 标注。
- 分支：`main` 稳定，功能走 `feat/*` 分支 + PR。
- 数据库迁移只增不改：已发布的 `V*.sql` 不得修改，否则已部署实例 Flyway 校验失败。

## 六、开源范围建议（open-core）

如果后续要做商业化，建议明确边界，避免"开源版被商用又要背锅"：

| 保持开源 | 可以商业化（闭源/付费） |
| --- | --- |
| 门诊闭环、病历、处方、收费、库存 | 各地区医保接口实现（地区差异大、需资质与运维） |
| 追溯码采集与台账 | 追溯码上传平台直连、自动补传服务 |
| 便携版与部署脚本 | 托管服务、远程运维、数据托管备份 |
| 打印与报表 | 定制报表、与第三方检验/影像对接 |

另外注意：
- **不要宣称诊断能力**。门诊信息系统通常不属于医疗器械软件，但一旦宣传"AI 诊断/辅助诊断"可能触发监管要求。
- "医馆 HIS"作为项目名建议查一下商标；如果商用，注册软件著作权并保留提交记录作为证据。
- 默认账号密码与 JWT 密钥必须在文档里反复强调替换，避免用户直接上生产。

## 七、持续维护

- 依赖升级：Dependabot 自动提 PR，合并前跑 CI。
- 安全问题：按 `SECURITY.md` 私下处理，修复后发布补丁版本并在 Release 说明。
- 每个版本更新 `CHANGELOG.md`；重大功能同步更新 `docs/`。
