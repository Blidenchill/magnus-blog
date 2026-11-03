# 恢复演练步骤（与备份批次一一对应）

前提：隔离环境或新空目录；保留原数据，绝不覆盖唯一现存副本。

1. 准备同大版本 PostgreSQL 17 容器与对应应用镜像（见备份批次 manifest.txt 中的 digest）。
2. 恢复 secret：`/etc/magnus/secrets/`（postgres-bootstrap-password、app-database-password、admin-password-hash）。
3. 启动空数据库，执行 `deploy/init-app-role.sql` 创建 `magnus` 应用角色（pg_dump 不含全局角色）：
   ```bash
   docker compose exec -T db psql -U postgres -d magnus \
       -v app_password="$(cat /etc/magnus/secrets/app-database-password)" \
       < /dev/null  # 占位，实际执行见下
   docker compose exec -T -e APP_DB_PASSWORD="$(cat /etc/magnus/secrets/app-database-password)" db \
       psql -U postgres -d magnus -c "CREATE ROLE magnus LOGIN PASSWORD :'pw'" # 见 init-app-role.sql
   ```
4. 恢复数据库：`pg_restore --clean --if-exists -U postgres -d magnus magnus.dump`
5. 恢复媒体文件：`tar -C $DATA_ROOT -xf app-data.tar`，校验 `SHA256SUMS`。
6. 启动应用（此时再允许 Flyway 校验迁移版本），验证 readiness。
7. 验证：首页 200、后台登录、至少一个已发布内容详情、图片可加载、草稿不可匿名访问。
8. 验收通过后切换流量；记录恢复耗时与数据回退范围。
