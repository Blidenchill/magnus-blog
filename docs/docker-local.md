# 本机 Docker 运行指南（镜像制作 + 初始化 + 启动）

本文档说明如何在本机用 Docker 完整跑起 magnus-blog（应用 + PostgreSQL 17），
用于验证生产部署形态。与生产的差异见文末对照表。

## 0. 前置条件

- Docker Desktop 已安装并启动（`docker version` 能显示 Server 信息）
- 项目代码已克隆，以下命令均在项目根目录执行
- 磁盘预留约 2 GB（基础镜像 + 构建产物）

## 1. 一次性初始化（密钥与数据目录）

生产部署约定 secret 放在 `/etc/magnus/secrets/`；本机为避免 sudo，统一放在
`~/.magnus-local/`，由 `compose.local.yaml` 重新指向。

```bash
# 1.1 创建目录
mkdir -p ~/.magnus-local/secrets ~/.magnus-local/data/postgres ~/.magnus-local/data/app-data

# 1.2 生成两个数据库密码（随机值，注意用 printf：secret 文件不能带换行）
printf '%s' "$(openssl rand -hex 24)" > ~/.magnus-local/secrets/postgres-bootstrap-password
printf '%s' "$(openssl rand -hex 24)" > ~/.magnus-local/secrets/app-database-password

# 1.3 生成管理员密码的 BCrypt 散列（成本 12）。下面用项目依赖里的 spring-security-crypto
#     生成 "test-password" 的散列——仅本机约定，生产务必换成自己的强密码散列：
export JAVA_HOME=<你的JDK21路径>
cat > /tmp/GenHash.java <<'EOF'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class GenHash {
    public static void main(String[] a) {
        System.out.println(new BCryptPasswordEncoder(12).encode(a[0]));
    }
}
EOF
CP=$(echo ~/.m2/repository/org/springframework/security/spring-security-crypto/*/spring-security-crypto-*.jar):$(echo ~/.m2/repository/org/springframework/spring-jcl/*/spring-jcl-*.jar)
printf '%s' "$(cd /tmp && "$JAVA_HOME/bin/java" -cp "$CP" GenHash.java 'test-password')" \
    > ~/.magnus-local/secrets/admin-password-hash

# 1.4 收紧权限
chmod 700 ~/.magnus-local/secrets && chmod 600 ~/.magnus-local/secrets/*
```

> ⚠️ 三个 secret 文件**都不能有多余的尾随换行**（所以用 `printf '%s'` 而不是 `echo`）。
> 应用经 configtree 原样读取文件内容；多一个 `\n` 会导致数据库认证失败、
> 管理员密码校验永远不匹配。

`compose.local.yaml` 引用上述 secret；`.env.local` 提供三个非机密参数
（`DATA_ROOT` 需为绝对路径，`.env` 文件不会展开 `$HOME`）：

```
DATA_ROOT=/Users/<你>/.magnus-local/data
APP_IMAGE=magnus-blog:0.1.0
APP_ADMIN_USERNAME=admin
```

## 2. 制作镜像

```bash
docker build -t magnus-blog:0.1.0 .
```

Dockerfile 是多阶段构建：`maven:3.9-eclipse-temurin-21` 阶段拉依赖、打包出
可执行 jar，再复制到 `eclipse-temurin:21-jre` 运行镜像（以非 root 用户
10001 运行）。首次构建需下载基础镜像与 Maven 依赖，约 5–15 分钟；之后有层缓存会快很多。

验证：

```bash
docker images magnus-blog        # 应看到 magnus-blog:0.1.0，约 350MB
```

> 拉取基础镜像太慢时，可在 Docker Desktop → Settings → Docker Engine 里配置
> `registry-mirrors` 加速器后重启 Docker。

## 3. 启动服务

**顺序不可乱：先数据库 → 初始化应用角色 → 再起应用。**

```bash
# 3.1 启动数据库（首次会自动初始化数据目录）
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml up -d db

# 3.2 等数据库健康（STATUS 变为 healthy）
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml ps

# 3.3 创建应用角色 magnus（幂等，可重复执行；密码从 secret 文件注入，不出现在命令历史里）
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml exec -T db \
    psql -U postgres -d magnus \
    -v app_password="$(cat ~/.magnus-local/secrets/app-database-password)" \
    -f - < deploy/init-app-role.sql

# 3.4 启动应用（Flyway 自动建表，首次约 20 秒）
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml up -d app
```

## 4. 验证

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/            # 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost/admin/login # 200
docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml logs --tail 50 app
```

浏览器打开 http://localhost ；后台 http://localhost/admin/login ，
用户名 `admin`，密码为上一步散列对应的明文（本机约定 `test-password`）。

## 5. 日常操作

```bash
COMPOSE="docker compose --env-file .env.local -f compose.yaml -f compose.local.yaml"

$COMPOSE ps                 # 状态
$COMPOSE logs -f app        # 跟随应用日志
$COMPOSE restart app        # 重启应用
$COMPOSE stop               # 停止（数据保留）
$COMPOSE start              # 再启动
$COMPOSE down               # 停止并删除容器（数据卷仍保留在 ~/.magnus-local/data）
```

代码改动后重新上线：

```bash
docker build -t magnus-blog:0.1.0 . && $COMPOSE up -d app
```

## 6. 彻底重置 / 卸载

```bash
$COMPOSE down
rm -rf ~/.magnus-local          # 删除全部数据与 secret（不可恢复！）
docker rmi magnus-blog:0.1.0
```

重置后想再跑，从第 1 节重做即可。

## 7. 与生产环境的差异

| 项 | 本机（compose.local.yaml） | 生产（compose.yaml） |
|---|---|---|
| secret 位置 | `~/.magnus-local/secrets/` | `/etc/magnus/secrets/`（configtree） |
| 反向代理 | 无，应用直接暴露 80（容器内仍 8080） | nginx 终结 TLS，80/443 |
| 访问方式 | http://localhost | https:// 域名 |
| Cookie Secure | 关闭（http 直测需要） | 开启 |
| 管理员密码 | 本机约定弱密码 | 强密码散列，成本 ≥ 12 |
| 数据目录 | `~/.magnus-local/data` | `/srv/magnus`（独立数据盘） |

## 8. 故障排查

- **80 被占用**：`lsof -nP -iTCP:80 -sTCP:LISTEN` 找到进程停掉
  （本地 `local-dev.sh app` 用的是 8080，与 Docker 的 80 不冲突，可并存）。
- **app 一直重启、日志报数据库认证失败**：检查 1.3 的散列/密码文件是否带换行；
  或 3.3 的角色初始化没执行。
- **登录总是跳回登录页**：确认用的是 http://localhost 而不是 https；
  `SESSION_COOKIE_SECURE=false` 只应出现在本机覆盖文件里。
- **init-app-role.sql 重复执行报错**：脚本本身幂等；若报语法错误，确认 `-v app_password=...`
  参数在 `-f -` 之前。
- **看数据库内容**：`$COMPOSE exec db psql -U postgres -d magnus`。
