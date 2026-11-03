# E2E 测试(Playwright)

浏览器端到端测试,覆盖 docs/technical-design.md §10.1 的 E2E 层:
登录、发布、编辑、搜索、草稿隐私、内容恢复。

## 运行前提

1. 本地 PostgreSQL 已启动,存在数据库 `magnus`(账号 `magnus` / 密码 `magnus`)。
2. 应用已用 local profile 启动,且管理员密码哈希对应环境变量中的明文:

   ```bash
   export LOCAL_ADMIN_PASSWORD_HASH='<BCrypt(E2E_ADMIN_PASSWORD)>'
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

3. 本目录安装依赖并下载浏览器:

   ```bash
   cd tests/e2e
   npm install
   npx playwright install chromium
   ```

## 运行

```bash
# 默认连 http://localhost:8080,可用 E2E_BASE_URL 覆盖
E2E_ADMIN_PASSWORD=test-password npm test
```

## 约定

- 使用专用测试账号与 `fixtures/` 下的测试图片,**不接触真实密码和私人照片**。
- 单 worker 串行执行:登录限流(15 分钟 5 次失败)会被并发用例误触发。
- 每个用例创建带时间戳的独立数据,不依赖执行顺序、不清空数据库。
