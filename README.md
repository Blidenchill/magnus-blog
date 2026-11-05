# Magnus Blog

个人主页 + 博客 + 相册 + 菜谱 + 旅行记录的单体应用。Spring Boot 4 / Thymeleaf / PostgreSQL。
产品与技术方案见 [docs/product-plan.md](docs/product-plan.md) 与 [docs/technical-design.md](docs/technical-design.md)。

## 本地开发

前置：JDK 21。

**方式一：便携版 PostgreSQL（推荐，零系统安装）**

本机 `~/pg17` 下已放好便携版 PostgreSQL 17（zonky 二进制，非系统安装，删除该目录即完全卸载）：

```bash
scripts/local-dev.sh db-start    # 首次自动初始化并创建 magnus 库，之后仅启动
scripts/local-dev.sh app         # 构建（如需）并启动应用，前台运行，Ctrl+C 停止
scripts/local-dev.sh db-stop     # 停止数据库
scripts/local-dev.sh db-status   # 查看数据库状态
scripts/local-dev.sh reset       # 清空全部数据重新初始化
```

**方式二：已有任意本地 PostgreSQL**

```bash
createdb magnus
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

**方式三：本机 Docker（验证生产部署形态）**

见 [docs/docker-local.md](docs/docker-local.md)：镜像制作、密钥初始化、compose 启动全流程。

本地 profile（`application-local.yaml`）默认：

- 数据库 `jdbc:postgresql://localhost:5432/magnus`，用户/密码 `magnus`/`magnus`（可用 `LOCAL_DB_PASSWORD` 覆盖）
- 管理员 `admin` / `test-password`（仅本地；生产必须走 secret，见下）
- 文件存储 `./data/app-data`

## 测试

```bash
./mvnw test          # 单元测试
./mvnw verify        # 单元 + 集成测试（集成测试需要 Docker，自动使用 Testcontainers/PostgreSQL 17）
```

## 生产部署（概要）

1. 构建镜像：`docker build -t registry.example.com/magnus-blog:0.1.0 .`
2. 准备数据盘：`/srv/magnus/{postgres,app-data}`（先挂载磁盘再建目录）。
3. 准备 secret（`/etc/magnus/secrets/`）：
   - `postgres-bootstrap-password`：postgres 超级用户密码
   - `app-database-password`：应用角色 `magnus` 的密码
   - `admin-password-hash`：管理员密码的 BCrypt 散列（成本 ≥ 12），例如：
     `./mvnw -q exec:java` 或任意 BCrypt 工具生成，只存散列。
4. `cp .env.example .env` 并填 `DATA_ROOT` / `APP_IMAGE` / `APP_ADMIN_USERNAME`。
5. `docker compose up -d db`，首次执行 `deploy/init-app-role.sql` 创建应用角色，再 `docker compose up -d`。
6. TLS 证书放 `/etc/magnus/tls/`（nginx 配置见 `deploy/nginx.conf`）。

备份：`DATA_ROOT=/srv/magnus ./deploy/backup.sh`；恢复演练见 [deploy/restore.md](deploy/restore.md)。

## 结构

```
src/main/java/com/hubc/magnusblog/
  config/     配置属性
  security/   登录、限流、审计
  profile/    站点资料
  content/    内容主体 + 草稿/发布快照 + 搜索
    photo/ blog/ food/ travel/   模块明细
    web/      公开页与后台控制器
  media/      图片上传/处理/存储/授权访问
  common/     异常、分页、slug、HTML 清洗
src/main/resources/
  db/migration/   Flyway
  templates/      Thymeleaf（fragments/public/admin/errors）
  static/         CSS/JS/Quill（自托管）
```
