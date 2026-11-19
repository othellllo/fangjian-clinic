# 方笺 — MySQL 生产部署与运维手册

适用于「每家医馆部署一套」的单机私有化形态。数据库用 MySQL 8，应用为单个 jar，
前端为静态文件（Nginx 托管）。全流程不依赖外网。

## 一、环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| 操作系统 | Linux（推荐 Ubuntu 22.04 / CentOS 7+）或 Windows Server | |
| JDK | 21 | 运行后端 |
| MySQL | 8.0+ | 字符集 `utf8mb4`，时区 `+08:00` |
| Nginx | 1.20+ | 托管前端静态文件 + 反代 `/api` |
| 内存 | ≥ 4GB | 后端 1GB + MySQL 1GB + 系统余量 |

## 二、数据库准备

```sql
-- 以 root 执行
CREATE DATABASE fangjian_clinic DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'fangjian'@'localhost' IDENTIFIED BY '换成强密码';
GRANT ALL PRIVILEGES ON fangjian_clinic.* TO 'fangjian'@'localhost';
FLUSH PRIVILEGES;
```

表结构与初始数据由 **Flyway 在应用启动时自动执行**（`backend/src/main/resources/db/migration`），
无需手工导入 SQL。升级版本时同样只需替换 jar 并重启，Flyway 会按版本号增量迁移。

> 重要：升级前先备份（见第六节）。Flyway 迁移一旦执行不可回退，回滚靠备份恢复。

## 三、后端启动

```bash
# 1. 构建（开发机执行，产物只有一个 jar）
cd backend && mvn clean package -DskipTests
# 2. 上传
scp target/fangjian-clinic-0.1.0.jar user@server:/opt/fangjian-clinic/app.jar
```

启动参数（环境变量方式，避免密码写进命令行历史）：

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=fangjian_clinic
export DB_USER=fangjian
export DB_PASSWORD='换成强密码'
export JWT_SECRET='至少 32 位随机字符串，务必替换默认值'
java -Xms512m -Xmx1024m -jar /opt/fangjian-clinic/app.jar
```

### systemd 托管（Linux 推荐）

`/etc/systemd/system/fangjian-clinic.service`：

```ini
[Unit]
Description=Fangjian HIS backend
After=network.target mysql.service

[Service]
User=fangjian
WorkingDirectory=/opt/fangjian-clinic
EnvironmentFile=/opt/fangjian-clinic/his.env
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/fangjian-clinic/app.jar
Restart=always
RestartSec=5
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
```

`/opt/fangjian-clinic/his.env`（权限 `600`）：

```ini
SPRING_PROFILES_ACTIVE=prod
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=fangjian_clinic
DB_USER=fangjian
DB_PASSWORD=换成强密码
JWT_SECRET=至少32位随机字符串
```

```bash
systemctl daemon-reload && systemctl enable --now fangjian-clinic
systemctl status fangjian-clinic
curl -s http://127.0.0.1:8080/api/insurance/providers   # 健康检查
```

## 四、前端部署（Nginx）

```bash
cd frontend && npm ci && npm run build
scp -r dist/* user@server:/var/www/fangjian-clinic/
```

`/etc/nginx/conf.d/fangjian-clinic.conf`：

```nginx
server {
    listen 80;
    server_name his.example.com;   # 医馆内网可写 IP

    root /var/www/fangjian-clinic;
    index index.html;

    location / { try_files $uri $uri/ /index.html; }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 60s;
        client_max_body_size 20m;
    }

    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;
    gzip_min_length 1024;
}
```

```bash
nginx -t && systemctl reload nginx
```

如需 HTTPS，用 `certbot --nginx` 或自签证书（纯内网可只开 HTTP）。

## 五、Docker Compose 方式（备选）

仓库根目录已提供 `docker-compose.yml`（MySQL + 后端 + Nginx）：

```bash
copy .env.example .env      # 修改密码与 JWT_SECRET
docker compose up -d --build
docker compose logs -f backend
```

首次启动会自动等 MySQL 健康检查通过，再执行 Flyway 建表。

## 六、备份与恢复

**备份是方笺系统的生命线**：病历与收费数据丢失无法重建。

```powershell
# Windows 服务器 / 运维机
powershell -File scripts\backup-mysql.ps1 -Password '库密码' -OutDir D:\his-backup -KeepDays 30
```

```bash
# Linux 服务器（每日 02:00 备份）
0 2 * * * mysqldump -ufangjian -p'密码' --single-transaction --routines --events fangjian_clinic \
  | gzip > /backup/fangjian_clinic-$(date +\%Y\%m\%d).sql.gz
# 清理 30 天前
5 2 * * * find /backup -name 'fangjian_clinic-*.sql.gz' -mtime +30 -delete
```

恢复：

```powershell
powershell -File scripts\restore-mysql.ps1 -File D:\his-backup\fangjian_clinic-20261118-020000.sql.gz -Password '库密码'
```

建议再按周把备份拷到 **另一台机器或移动硬盘**，本地备份随机器一起坏的情况很常见。

## 七、上线前安全检查清单

- [ ] 修改所有演示账号密码（admin / doctor01 / cashier01 / pharmacist01 默认都是 `123456`）
- [ ] 替换 `JWT_SECRET`（默认值仅供开发）
- [ ] MySQL 只监听 127.0.0.1，不对公网暴露 3306
- [ ] 关闭 Swagger 公网访问（生产可由 Nginx 拦截 `/swagger-ui` 与 `/v3/api-docs`）
- [ ] 配置每日自动备份，并**实际演练一次恢复**
- [ ] 服务器开启防火墙，仅放行 80/443
- [ ] 患者端短信验证码接入真实服务商（当前为固定码 `123456`，仅演示）

## 八、常见问题

| 现象 | 排查方向 |
| --- | --- |
| 启动报 Flyway 校验失败 | 迁移脚本被改动过；生产库不要手改表结构，恢复备份后重新升级 |
| 页面 502 | 后端没起来，看 `journalctl -u fangjian-clinic -n 200` |
| 登录后立刻 401 | `JWT_SECRET` 变更导致旧 token 失效，重新登录即可 |
| 时间差 8 小时 | MySQL 时区需为 `+08:00`，JVM 加 `-Duser.timezone=Asia/Shanghai` |
| 库存扣减为负 | 检查是否有人手工改过 `drug_batch`，库存以批次结存为准 |
