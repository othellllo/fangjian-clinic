# 第三方依赖与许可

本项目以 Apache License 2.0 发布。下列为主要第三方依赖及其许可证，供合规审查参考。
（完整清单以 `backend/pom.xml` 与 `frontend/package-lock.json` 为准。）

## 后端

| 依赖 | 版本 | 许可证 | 说明 |
| --- | --- | --- | --- |
| Spring Boot / Spring Framework / Spring Security / Spring Data JPA | 3.3.x / 6.x | Apache-2.0 | 应用框架 |
| Flyway Community | 10.x | Apache-2.0 | 数据库迁移 |
| H2 Database | 2.2.x | MPL-2.0 或 EPL-1.0（双许可） | 开发/便携版内嵌数据库 |
| MySQL Connector/J | 9.x | GPL-2.0 + Universal FOSS Exception | 生产数据库驱动 |
| Apache POI | 5.3.0 | Apache-2.0 | Excel 导入导出 |
| JJWT | 0.12.6 | Apache-2.0 | JWT 签发与解析 |
| springdoc-openapi | 2.6.0 | Apache-2.0 | Swagger 文档 |
| Lombok | 1.18.x | MIT | 编译期样板代码 |

> **注意**：MySQL Connector/J 采用 GPL-2.0 并附带 FOSS Exception。本项目是 FOSS（Apache-2.0），
> 可正常使用；若你将其用于**闭源商业分发**，需向 Oracle 获取商业许可或改用其他 JDBC 驱动。

## 前端

| 依赖 | 版本 | 许可证 |
| --- | --- | --- |
| Vue 3 / Vue Router / Pinia | 3.5 / 4.x / 2.x | MIT |
| Element Plus | 2.8.x | MIT |
| ECharts | 5.5.x | Apache-2.0 |
| Vite / @vitejs/plugin-vue | 5.x | MIT |
| axios | 1.7.x | MIT |

## 微信小程序

原生小程序（WXML/WXSS/JS），无第三方 npm 依赖。

## 内嵌字体与图标

- Element Plus 图标集：MIT（随 Element Plus 分发）
- 界面使用系统字体（微软雅黑/宋体），不内嵌商业字体

## 内容来源声明

- 十八反、十九畏、妊娠禁忌等规则条文引自公开教材与广东省中医药局公开发布内容，仅作医学规则提示。
- 《中医病历书写基本规范》（国中医药医政发〔2010〕29 号）依据公开发布文件实现校验规则。
- 上述内容不构成诊疗建议，最终由执业医师判断并签名负责。
