#!/bin/bash
# 本地开发一键脚本：便携版 PostgreSQL 17 + 应用启动
#
# 用法：
#   scripts/local-dev.sh db-start    初始化（首次）并启动 PostgreSQL
#   scripts/local-dev.sh db-stop     停止 PostgreSQL
#   scripts/local-dev.sh db-status   查看 PostgreSQL 状态
#   scripts/local-dev.sh app         构建（如需）并前台启动应用（local profile）
#   scripts/local-dev.sh reset       清空数据库全部数据并重新初始化（会询问确认）
#
# 便携版 PostgreSQL 位于 ~/pg17（可用 PG_HOME / PGDATA 覆盖），
# 不是系统级安装，不写任何系统路径，删除 ~/pg17 即完全卸载。
set -euo pipefail

PG_HOME="${PG_HOME:-$HOME/pg17/server}"
PGDATA="${PGDATA:-$HOME/pg17/data}"
PGLOG="${PGLOG:-$HOME/pg17/pg.log}"
DB_NAME="${DB_NAME:-magnus}"
DB_USER="${DB_USER:-magnus}"
DB_PASSWORD="${LOCAL_DB_PASSWORD:-magnus}"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

die() { echo "错误: $*" >&2; exit 1; }

need_pg() {
  [ -x "$PG_HOME/bin/pg_ctl" ] || die "未找到便携版 PostgreSQL：$PG_HOME
  请先从 Maven Central 下载 zonky embedded-postgres-binaries 解压，或设置 PG_HOME 指向它。"
}

db_running() { "$PG_HOME/bin/pg_ctl" -D "$PGDATA" status >/dev/null 2>&1; }

init_db() {
  echo "初始化数据库集群: $PGDATA"
  mkdir -p "$(dirname "$PGDATA")"
  "$PG_HOME/bin/initdb" -D "$PGDATA" -U postgres --auth=trust --encoding=UTF-8 >/dev/null
  # 该便携版不含 psql，用单用户模式建角色与库
  "$PG_HOME/bin/postgres" --single -D "$PGDATA" postgres >/dev/null 2>&1 <<SQL
CREATE ROLE $DB_USER LOGIN PASSWORD '$DB_PASSWORD';
CREATE DATABASE $DB_NAME OWNER $DB_USER;
SQL
  echo "已创建角色 $DB_USER 与数据库 $DB_NAME"
}

find_java() {
  if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE '"(21|2[2-9])'; then
    echo "$JAVA_HOME"; return
  fi
  for home in "$HOME"/Library/Java/JavaVirtualMachines/*/Contents/Home /Library/Java/JavaVirtualMachines/*/Contents/Home; do
    if [ -x "$home/bin/java" ] && "$home/bin/java" -version 2>&1 | grep -qE '"(21|2[2-9])'; then
      echo "$home"; return
    fi
  done
  die "未找到 JDK 21+，请设置 JAVA_HOME"
}

find_maven() {
  for mvn in "${MAVEN_HOME:-/nonexistent}/bin/mvn" "$HOME"/.m2/wrapper/dists/*/apache-maven-*/bin/mvn; do
    [ -x "$mvn" ] && { echo "$mvn"; return; }
  done
  echo "$PROJECT_ROOT/mvnw"
}

cmd_db_start() {
  need_pg
  if db_running; then echo "PostgreSQL 已在运行（数据目录 $PGDATA）"; return; fi
  [ -f "$PGDATA/PG_VERSION" ] || init_db
  "$PG_HOME/bin/pg_ctl" -D "$PGDATA" -l "$PGLOG" -w start
  echo "PostgreSQL 17 已启动：jdbc:postgresql://localhost:5432/$DB_NAME（日志 $PGLOG）"
}

cmd_db_stop() {
  need_pg
  db_running || { echo "PostgreSQL 未在运行"; return; }
  "$PG_HOME/bin/pg_ctl" -D "$PGDATA" -m fast stop
}

cmd_db_status() {
  need_pg
  "$PG_HOME/bin/pg_ctl" -D "$PGDATA" status
}

cmd_app() {
  local java_home maven jar
  java_home="$(find_java)"
  maven="$(find_maven)"
  jar="$PROJECT_ROOT/target/magnus-blog-0.1.0.jar"
  if [ ! -f "$jar" ]; then
    echo "jar 不存在，先构建..."
    (cd "$PROJECT_ROOT" && JAVA_HOME="$java_home" "$maven" -q -DskipTests package)
  fi
  db_running || echo "警告: PostgreSQL 未在运行，应用将启动失败。先执行 scripts/local-dev.sh db-start"
  echo "启动应用（local profile）：http://localhost:8080  后台 http://localhost:8080/admin/login（admin / test-password）"
  cd "$PROJECT_ROOT"
  exec env JAVA_HOME="$java_home" "$java_home/bin/java" -jar "$jar" --spring.profiles.active=local
}

cmd_reset() {
  need_pg
  read -r -p "将删除 $PGDATA 下的全部数据，确认？[y/N] " ans
  [ "$ans" = "y" ] || { echo "已取消"; return; }
  db_running && cmd_db_stop
  rm -rf "$PGDATA"
  init_db
  echo "已重置。执行 scripts/local-dev.sh db-start 启动。"
}

case "${1:-}" in
  db-start)  cmd_db_start ;;
  db-stop)   cmd_db_stop ;;
  db-status) cmd_db_status ;;
  app)       cmd_app ;;
  reset)     cmd_reset ;;
  *) sed -n '2,14p' "$0"; exit 1 ;;
esac
