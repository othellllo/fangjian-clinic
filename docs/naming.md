# 项目命名说明

## 为什么改名

原名「医馆 HIS」过于通用，且"HIS"是行业术语，无法形成项目识别度；商用前也存在商标风险。

现名：**方笺（Fangjian）**

- 「方」= 处方；「笺」= 处方笺。医馆最核心的动作是"开方"，名字直接指向业务。
- 两字组合词，简短、易记、易检索，商标冲突概率低于"青囊""诊籍""悬壶"等传统中医词
  （这些词在第 44 类多有注册）。
- 英文 id 统一为 `fangjian`，便于 GitHub 仓库名、包名与命令行使用。

## 命名对照表

| 位置 | 旧 | 新 |
| --- | --- | --- |
| 产品名 | 医馆 HIS | 方笺 |
| Java 包 | `com.yiguan.his` | `com.fangjian.clinic` |
| Maven 坐标 | `com.yiguan:yiguan-his` | `com.fangjian:fangjian-clinic` |
| jar | `yiguan-his-0.1.0.jar` | `fangjian-clinic-0.1.0.jar` |
| 数据库 | `yiguan_his` / `data/yiguan` | `fangjian_clinic` / `data/fangjian` |
| 备份文件 | `yiguan-*.zip` | `fangjian-*.zip` |
| 导出文件 | `yiguan-*.csv` | `fangjian-*.csv` |
| 容器 | `yiguan-mysql` 等 | `fangjian-mysql` 等 |
| 日志 | `logs/yiguan-his.log` | `logs/fangjian-clinic.log` |
| 便携包目录 | `release/医馆系统` | `release/方笺系统` |

## 商标与合规提醒

- 项目名不等于商标。若计划商用，请在目标类别（第 9 类软件、第 42 类 SaaS、第 44 类医疗服务）
  做**商标检索与注册**，本项目的命名选择不构成商标可用性保证。
- 备选名（若"方笺"已被占用）：`杏笺`、`药录`、`脉册`、`方脉`。改名成本很低——
  全仓标识已集中替换过一轮，按 `docs/naming.md` 的对照表再跑一遍即可。

## 数据兼容性

改名后数据库文件名从 `data/yiguan.mv.db` 变为 `data/fangjian.mv.db`。
已有实例升级时，停止服务后把 `data/` 下的 `yiguan.*` 重命名为 `fangjian.*` 即可（或继续用旧配置）。
本项目此前未对外发布，无存量用户，故未做自动迁移。
