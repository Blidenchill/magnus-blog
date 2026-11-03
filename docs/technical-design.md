# Magnus Blog 技术开发方案

## # 1. 设计基线

- 作者：Ethan V P Zhao；方案整理：GitHub Copilot。
- 日期：2026-11-03。
- Design ID: `8B16bbb01-5ea6-4f20-885F-11af643422Fc`。
- Requirement ID: `Magnus blog V1`, `"REQ-001"` 至 `"REQ-012"`。
- 产品基线：[产品方案](product-plan.md)。
- 当前阶段：定义阶段，无页面或数据库落地需求。
- 目标：Spring Boot + Thymeleaf 单体，部署到 Linux 虚拟机。所有业务数据独立于容器生命周期。
- 本文是后续实现契约，不是当前已生成的代码或已经执行的部署说明。


## ## 2. 选型研究与决策

### ## 2.1 技术栈

| 层次 | 推荐技术 | 说明 |
| ---- | -------- | ---- |
| Java | Java 21 LTS | 工具链成熟；开发、CI、运行时使用相同大版本 |
| 框架 | Spring Boot 4.x | 当前受维护的长期稳定主干；项目创建时检查 Java 21 兼容性和支持前，并指定具体版本；不使用 SNAPSHOT |
| HTTP 与页面 | Spring MVC + Thymeleaf | 一个页面端同时提供公开页面、后台页面少量 350N 接口 |
| 安全 | Spring Security | 表单登录、会话、CSRF、授权和安全页面 |
| 数据访问 | Spring Data JPA + hibernate | 普通 CRUD、事务与乐观锁；列表查询使用投影避免 N+1 |
| 数据库 | PostgreSQL | 主存储数据、成熟的元数据、索引和事务 |
| 迁移 | Flyway + PostgreSQL | 支持转换 | SQL 迁移版本控制；生产启用自动建表和 update |
| 参数校验 | Jakarta Bean Validation | DTO 字段校验与模板级服务校验 |
| 图文编辑 | Quill 2 配合 | RSS 变更允许许可方向，开发时输入具体许可即可；不需要 SPA 或付费编辑器服务 |
| HTML 安全 | Jsoup + OWASP Java HtmlSanitizer | 服务端白名单清洗；不允许任何模板生成的 HTML |
| 图片处理 | Java ImageIO | 当前实现校验转换 JPEG/PNG；重新编码为 JPEG 展示原始编码 |
| 前端 | 自托管 CSS、少量 ES modules、Lucide | 不依赖运行时 CDN，不使用 React/Vue |
| 构建 | Maven Wrapper | 依赖由 Spring Boot BOM 管理，新增库需要版本 |
| 容器化 | Java Wrapper | 支持 Docker、NodeDev、TartContainer、Playwright | 单元、权限、数据库和端到端分层验证 |
| 部署 | Docker Compose + Nginx | 单机部署拓扑，TLS 终止、代理和路径映射 |

不预先增加 Redis、消息队列、Elasticsearch、微服务或 Kubernetes。首版会话及登录缓存在单应用进程内维护；使用重启后管理员需要重新登录。

Spring Boot 4.x 的 starter 名称、测试模块和生态兼容性须按实际版本锁定本校核，不需擅自依赖依赖。若生态依赖验证失败，再评估受维护的其他 Boot 版本，记录原因，不做默然堆栈。

### ## 2.2 后端存储选型

| 方案 | 优点 | 成本或风险 | 结论 |
| --- | --- | --- | --- |
| H2 文件存储 | 启动简单，无数据库连接 | 生产运维、并发和后续迁移能力有限；易误用内存模式 | 不作为生产存储，也不替代数据库选 PostgreSQL |
| SQLite | 单文件部署，适合迁移 | 不支持并发写入、不支持高并发，不推荐作为高并发场景 | 小测试可运行，但不作为项目默认 |
| PostgreSQL | 完善兼容，可靠并发、pg_dump/pg_restore、清场运维能力强 | 增加一个容器，需要备份与维护 | 推荐 |
| MySQL | 成熟且常见 | 没有现有运维偏好，增加另一种选择无明显收益 | 现有服务器和标准许可后再评估 |

图片不存放数据库 BLOB。数据库存描述、大小、哈希、尺寸和相对路径；文件存外部数据磁盘。相对路径不包含域名及宿主绝对路径，便于换服务器。

### ## 2.3 图片存储磁盘

首选使用本地文件存储，而不置 MinIO：省去额外部署、密钥和链路，足以支持一个管理员和一个应用实例。使用 `MediaStorage` 边界封装存储操作，未来可替换为 S3 兼容存储。

重要指标：应用必须保持单实例，且文件目录与数据库必须对应备份。单独外挂磁盘不会自动提供备份、容灾或多节点共享能力。

### ## 2.4 官方依赖入口

- [Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot 文档说明](https://spring.io/projects/spring-boot#support)
- [PostgreSQL 版本维护/支持](https://www.postgresql.org/support/versioning/)
- [Quill 2 文档](https://quilljs.com/docs/quickstart)
- [OWASP Java HTML Sanitizer](https://github.com/OWASP/java-html-sanitizer)
- [Hugo PaperMod](https://github.com/nicetynow/hugo-PaperMod)
- [Ghost Casper](https://github.com/TryGhost/Casper)

本次须获取 Java、Spring、PostgreSQL 和官方页面内容。实施时检查许可证、CVE、维护周期和依赖兼容性，提交 Maven 锁定版本和容器镜像 digest。@参考第三方布局不等于依赖锁定生产。

## ## 3. 系统架构

```mermaid
flowchart LR
  Visitor[访客浏览器] --> Proxy[Nginx / HTTPS]
  Admin[管理员浏览器] --> Proxy
  Proxy --> App[Spring Boot 单实例]
  App --> Security[Spring Security]
  App --> Views[Thymeleaf 页面]
  App --> Services[内容 / 发布 / 媒体服务]
  Services --> DB

[(PostgreSQL)]


  Services --> Files[外部数据盘上的图片文件]
  DB --> DBMount[挂载到 PostgreSQL 目录]
  DBMount --> Backup[定时备份]
  Files --> Backup
  ```

  3.1 职责拆分
-        PublicController ：只接受当前公开版本，不抛出 JPA 实体直接交给页面。
-        AdminController ：只接受后台权限检查，认证由 Spring Security 处理。
-        ContentService ：草稿保存、模块校验、预览和软删除。
-        PublishingService ：生成发布快照、回收站公共恢复，可恢复处理。
-       MediaService ：上传接收、裁剪、存储、相对路径、权限校验和路径。
-        Repository ：显示公开条件、分页、唯一性和乐观锁。
-       Thymeleaf 是展示已授权的 ViewModel；只允许指定文本变量和使用过清洗的 HTML。
页面路由是主要对外契约，留级不提供通用 REST API。后台只有图片上传和部分同步交互回 350N，其余操作使用表单或 Post/Redirect/Get。


### ## 3.2 建设目录

以下是后续创建的项目规划，当前仅文档存在。

```text
docs/
pom.xml
mvnw
mvnw.cmd
Dockerfile
compose.yaml
.env.example
.dockerignore
deploy/
  nginx.conf
  backup.sh
  restore.md
src/main/java/com/hubc/magnusblog/
  MagnusblogApplication.java
  config/
  security/
  profile/
  content/
    photo/
    blog/
    food/
    travel/
  media/
  common/
src/main/resources/
  application.yaml
  application-local.yaml
  application-prod.yaml
  db/migration/
  templates/
    fragments/
    public/
    admin/
    errors/
  static/
    css/
    js/
    vendor/
    fonts/
src/test/java/com/hubc/magnusblog/
src/test/resources/
tests/e2e/
```

按业务模块组织，不拆前后端两个项目。不生成与需求无关的通用框架；控制器、DTO、服务和实体写于各模块内部。

## ## 4. 数据模型

### ## 4.1 模型原则

采用“内容主表 + 可编辑草稿 + 不可变的当前发布快照”。各模块明细继承于修订版本，而不是直接挂在内容主体上，避免修改草稿时连带删除或更新原已发布快照。

所有时间字段使用 PostgreSQL `timestamptz`，内部 UTC。页面按 `Asia/Shanghai` 显示；前台日期和按日期显示 `date`。主键使用 UUID。排序使用独立整数，文字字段长路径由迁移和 DTO 映射控制。

### ## 4.2 实体字段

每个可发布的内容分为内容修订，修订和公开的快照各自存在同一个 `content_entry` 行的 `draft_*` 和 `published_*` 字段；草稿与公开媒体 UUID 聚合也临时对应存储，它代表当前被编辑和修改。

表不维护多态字段，不直接挂在主体上。草稿与公开明细体继承字段描述后映射为快照模型。代码当前没有注册已经全部关联；恢复历史版本或支持跨类型结构化编辑时，应再先实对应迁移。

| 字段 | 关键字段 | 作用与说明 |
| --- | --- | --- |
| `site_profile` | `id`, `name`, `bio`, `avatar`, `contact`, `version`, `updated_at` | 单行配置；首页介绍公开产出。不允许内部配置项绑定。 |
| `content_entry` | `id`, `type`, `slug`, `draft_revision_id`, `published_revision_id`, `first_published_at`, `deleted_at`, `version` | 创建创建时间 / 内容绑定分类: type 为 PHOTO/FOOD/TRAVEL | 同一内容只能有一行。 |
| `content_revision` | `id`, `entry_id`, `kind`, `title`, `content`, `cover_media_id`, `featured`, `body_html`, `created_at`, `updated_at`, `version` | 参数草稿；`kind` 为 DRAFT/PUBLISHED；一个主体最多一个快照版本，草稿版本可多个。 |
| `photo_item` | `id`, `revision_id`, `media_id`, `sort_order`, `alt_text`, `caption`, `sort_order` | 相册的有序结构，排序在版本内唯一。 |
| `recipe_detail` | `revision_id`, `title`, `duration_minutes`, `servings`, `intro` | 一个 FOOD 版本一条详情；数量字段均为必填。 |
| `recipe_ingredient` | `id`, `revision_id`, `name`, `amount`, `unit`, `text`, `sort_order` | 材料明细字段，数量单位文本字段存为纯文本。 |
| `recipe_step` | `id`, `revision_id`, `text`, `media_id`, `alt_text`, `sort_order` | 制作步骤，允许纯文本或图文。 |
| `travel_destination` | `id`, `revision_id`, `name`, `summary`, `media_id`, `alt_text`, `sort_order` | 一个旅行版本包含多个景点；不使用外部 API。 |
| `travel_detail` | `id`, `destination_id`, `media_id`, `alt_text`, `sort_order`, `sort_order` | 景点所属版本版本约束。 |
| `media_asset` | `id`, `filename`, `mime`, `hash`, `width`, `height`, `processed_at`, `status` | 不存原始绝对路径；状态字段为 PROCESSING/READY/FAILED。 |
| `media_variant` | `id`, `media_id`, `variant_type`, `width`, `height`, `path`, `mime`, `size` | 存储 JPEG/PNG/WebP 缩略图/原图；缓存唯一。 |
| `revision_media_ref` | `revision_id`, `media_id`, `role` | 同一引用数，包含正文、封面绑定到媒体字段；版本唯一；发布后冻结使用。 |
| `auth_event` | `id`, `username`, `ip`, `success`, `timestamp`, `user_agent`, `type` | 登录、登出、锁定、修改、尝试记录失败登录；不记密码、Cookie、正文和密码。 |

`revision_media_ref` 是媒体绑定关联的对应规则，不是第二独立表来存储；同一事务里已经绑定关联时，富文本字段和媒体绑定，测试约束再一致。个人身份引用由 `site_profile` 单独插入路径和对应绑定。

不建立管理员或同数据表；配置固定用户和密码 BCrypt 密码，交给 Spring Security 的参数号 `UserDetailsService`；将来增加多人角色时再转为数据库源账户。

### ## 4.3 关系约束

```mermaid
erDiagram
  CONTENT_ENTRY ||--|{ CONTENT_REVISION : owns
  CONTENT_REVISION ||--|{ CONTENT_TAG : tags
  CONTENT_REVISION ||--|| RECIPE_DETAIL : details
  CONTENT_REVISION ||--|{ RECIPE_INGREDIENT : ingredients
  CONTENT_REVISION ||--|{ RECIPE_STEP : steps
  CONTENT_REVISION ||--|{ TRAVEL_DESTINATION : destinations
  CONTENT_REVISION ||--|{ REVISION_MEDIA_REF : references
  MEDIA_ASSET ||--|{ REVISION_MEDIA_REF : referencedBy
  MEDIA_ASSET ||--|{ MEDIA_VARIANT : variants
  ```

- 数据库外键约束主体与修订归属；公开与草稿媒体不能指向其他主体的修订。
- 使用 `(entry_id, id)` 组合约束与复合外键避免扫表；指针类型规则由服务校验，必要时增加数据库约束及触发器。
- 前端在保存时指定，防止草稿与发布混淆指针；删除主体时与修订同步软删除。
- 删除前执行前更新主体和修订，不做跨实体级联回收体文件。
- `content_entry` 和可编辑版本使用 `@Version`；表单提交版本号，冲突返回 409。
- 为 `type`, `first_published_at`, `deleted_at`, 排序名称和引用媒体 ID 建索引。
- 列表查询条件使用 `first_published_at DESC, id DESC`；查询一次得封面与必要摘要，禁止返回关联信息。
- 直接搜索标题、摘要和标签，使用参数化查询、转义 LIKE 通配符；不搜索 HTML 原文，不引入检索服务。

### 4.4 发布与状态流转

不同时刻维护容易混淆的“已发布”和“公开”版本指针。

| 条件 | 状态 | 公开内容 |
| --- | --- | --- |
| `deleted_at` 非空 | 回收站 | 否 |
| 未删除且公开指针为空 | 草稿/已编辑 | 否 |
| 未删除且公开指针非空 | 已发布 | 是，依指针指向的版本 |

已发布内容的草稿保存编辑策略：“有待发布变更”通过规范化字段和图片引用对比判断，或缓存为可直接发布的草稿摘要给编辑。编辑时间变化本身不代表有内容变化。

再次发布后保留新公开快照和当前草稿。旧快照按事务清理引用并删除，不受任何历史保存。删除后的图片进入延迟清理，不在发布事务中立刻删除文件。

### 5. 发布与图片控制路径

#### 5.1 发布事务

```mermaid
sequenceDiagram
  participant A as 管理员
  participant C as 后台控制器
  participant S as 发布服务
  participant D as PostgreSQL
  A->>C: POST publish + CSRF + 版本号
  C->>S: 认证发布申请
  S->>D: 锁定主体并校验版本
  S->>S: 校验字段、HTML 和 READY 图片引用
  S->>D: 复制草稿及所有细节摘要为发布快照
  S->>D: 更新公开指针与首次发布时间
  S->>D: 清理旧快照引用，记录审计
  D-->>S: 提交事务
  S-->>A: 301 跳转并显示公开地址
```

事务失败则撤销快照并取消草稿，不能出现“正文新版、图片旧版”的部分发布。所有内容变更和媒体引用变更遵循统一顺序，降低无锁风险。

### ## 5.2 图片上传与路径

1. 管理员上传图片，认证和 CSRF 必须通过。
2. 校验扩展名、实际文件类型、文件大小、宽度和像素，源站文件名称不参与对应路径。
3. 最大 10,000,000 像素，宽度最高 12,000px；解码前端验证内存，防止压缩时消耗内存。
4. 临时文件写在同一进程磁盘；清理后丢弃元数据并重命名路径。
5. 生成预览图不超过 8,000px 的预览图与不超过 800px 的缩略图；保留原始缩略图。
6. 生成缩略图 JPEG，明确拒绝 WebP, SVG, GIF, HEIC 和 视频，不保存失败操作。
7. 文件采用 UUID 存储，例如 `media/2026/11/uuid0/display.jpg`；通过原于手动完成逻辑。
8. 数据库等待资源为 `READY` 后返回媒体 ID、尺寸和受保护预览地址。

数据库先写入系统无法共享事务：先完成文件，再确认数据库 `READY`；失败遗留的临时文件或非记录文件由对应任务在定期清理。数据库返回不报错描述为自动返回已写入文件。

限制同时运行的媒体刷新任务，例如 2 个；超过返回 429 和可重试反馈，避免并发上传 4 GB 虚拟内存耗尽。

### ## 5.3 公开图片访问

不把图片目录映射为 Spring 静态资源，也不由 Nginx 直接匿名存取磁盘。

公开接口：`GET /media/{id}/{variant}`，只允许 `display` 和 `thumb`。
后台接口：`GET /admin/media/{id}/{variant}`，请求管理员登录。
公开接口检查媒体 READY，并确认被未删除主体的当前公开版本引用，或被公共个人引用；否则 404。
已经被另一部分公开内容引用的图片不会因某个草稿撤消变成私密，这是共享可用规则，不是漏洞。
公开图片，不缓存，已清理的资源统一 404，不暴露文件名路径和可能敏感元数据。
HTML 内部使用 `/media/{id}/display` 地址；后台预览或修改时改为后台媒体地址，不改存储路径的正文。
公共地址使用 `Cache-Control: no-cache`，每次请求含条件验证也必须先验证公开权限，再决定是 304；后台响应使用 `no-store`。
设置真实 MIME、`X-Content-Type-Options: nosniff`；不提供缩略图文件下载。

缓存不能保证已发布的图片被替换后，私密图片不应公开后再开放被缓存撤回保护隐私。

### ## 5.4 富文本安全

Quill 负责输入体验，不负责最终安全。只开放产品级安全的工具栏：段落、有序列表、加粗、斜体、列表、引用、链接和本站图片。

保存时调用服务器端 HTML Sanitizer 和 DOM 解析校验，允许所有属性；Quill 所需的常规格式以及属性；禁止脚本、事件属性、iframe, style 任意值、data URL、外部图片和 JavaScript 协议。

从清洗后的 DOM 提取图片 ID，验证本站相对路径、资源 READY 和数量限制，重新组合引用。组合自名单不等于 URL 可随意放行；链接仅允许 HTTP/HTTPS，并增加合适的 `rel` 属性。

不使用正则表达式代替 HTML 解析。页面普通文本由 `th:text` 自动转义，富文本仅在指定正文区域通过 `th:utext` 展示已清洗结果。

前端不将 Quill Delta 为第二份正文文档；编辑确认后规范化 HTML 加载，再行验证 `保存 -> 再次编辑` 的排版和图片注意一致性。粘贴外部内容只保留自名单排版；外部图片要求本地上传。

## ## 6. 页面与路由约束

### ## 6.1 公开 GET 路由

| 路径 | 参数和返回 | 约束 |
| --- | --- | --- |
| `/` | 个人简介及每个模块最多 4 项最新/精选内容 | 只取公开版本 |
| `/photos` | `/slug` | `page`, `size`, `tag` \| 200 HTML \| 页码从 0 开始, size 为 1..48, 默认 12 |
| `/life/food` | | `category`, `tag` | 200 HTML, 分类名称公开白名单 |
| `/life/travel` | | 分页, `region`, `tag` | 200 HTML, 地区来自公开站点 |
| 详情页 | `/model/{slug}` | 200 HTML 或 404 HTML \| 具体类型与产品统一入口；模块类型不匹配为 404 |
| `/search` | `q`, `type`, `tag` | 200 HTML, 字段，范围：type 200 HTML \| 根据/类型/搜索参数，全文搜索不返回后台信息 |
| `/media/{id}/{variant}` | | 二值图片, 200/304/404 \| 先授权后返回内容 |
| `/robots.txt`, `/sitemap.xml` | | 文本/URL \| robots 只包含公开内容；sitemap 只包含公开内容 |

非法分页及格式返回 400；有效但超出范围的列表返回 200 空状态。公开路由不得接受内容变更请求，错误方法返回 405。

### ## 6.2 管理页面与后台

| 方法与路径 | 输入 | 成功结果 |
| --- | --- | --- |
| `GET /admin` | 当会话 | 未登录跳转 `/admin/login`；已登录跳首页 |
| `GET /admin/login` | 无 | 登录表单 |
| `POST /admin/login` | `username`, `password`, `CSRF` \| Spring Security 建立会话跳转后台首页 |
| `POST /admin/logout` | CSRF \| 清理会话和 Cookie 后跳转登录页 |
| `GET /admin/dashboard` | 无 \| 管理概览 |
| `GET /admin/contents` | `type`, `status`, `q`, `page` \| 后台内容列表 |
| `GET /admin/contents/new` | `type` \| 模块表单 |
| `GET /admin/contents/{id}/edit` | UUID, DTO, CSRF \| 编辑页面 |
| `POST /admin/contents/{id}/edit` | `entryVersion`, `revisionVersion`, `formData`, `CSRF` \| 保存草稿, 303 跳转；不更新公开快照 |
| `GET /admin/contents/{id}/preview` | UUID, DTO, CSRF \| 预览页面 |
| `POST /admin/contents/{id}/publish` | `entryVersion`, `revisionVersion`, `CSRF` \| 首次公开, 303 跳转 |
| `POST /admin/contents/{id}/unpublish` | `entryVersion`, `CSRF` \| 撤销公开, 303 跳转 |
| `POST /admin/contents/{id}/trash` | `entryVersion`, `CSRF` \| 软删除取消公开, 303 跳转 |
| `GET /admin/trash` | `type`, `page` \| 回收站 |
| `POST /admin/contents/{id}/restore` | `entryVersion`, `CSRF` \| 恢复为草稿, 303 跳转 |
| `GET /admin/media` | 分页, `status` | 媒体列表 |
| `POST /admin/media/upload` | `multipart file`, `CSRF` \| 201 JSON, 媒体 ID 与后台预览地址 |
| `POST /admin/media/{id}/delete` | `UUID`, `version` | 后台请求删除 |
| `GET /admin/settings` | 无 \| 个人资料编辑 |
| `POST /admin/settings` | `version`, `formData`, `CSRF` \| 校验保存并立即公开, 303 跳转 |

上传 JSON 示例:

```json
{
  "id": "",
  "previewURL": "/admin/media/a5a0b778-815b-4063-bc7b-ff4a9b5aba00/display",
  "publishedURL": "/media/a5a0b778-815b-4063-bc7b-ff4a9b5aba00/display",
  "width": 1920,
  "height": 1280
}
```
返回 "publicDir" 不代表资源已公开。编辑器插入公开相对地址但通过后台路径查看草稿图片。

### 6.3 输入规则与错误

- 标题 1..120 字符，摘要最多 500 字符；标签最多 8 个，每个 1..24 字符。
- slug 为 1..80 个小写 ASCII 字母、数字和连字符；新建自动给出带 ID 的标识，可在首发前修改。
- 富文本请求上限 200 KiB，按 UTF-8 字节校验；正文有效性在清洗后检查。
- 请求 ID 纳入页面错误提示与日志，方便定位；不返回堆栈和 SQL。
- 表单校验失败回显输入字段错误，返回 422；版本冲突 409，提示刷新后合并，不自动覆盖。
- 跨站脚本入口被过滤 404；已认证无权限 CSRF 失败返回 403；普通后台 GET 未登录到 302 登录跳转。
- 上传过大 413，格式不支持 415，校验失败 422，繁忙或限流 429，内部错误 500。
- 创建草稿使用一次性表单令牌或创建请求 ID 防止重复创建；其他变更由版本号控制重复提交。

## 7. 安全设计

### 7.1 管理员配置

- 部署时指定 'APP_ADMIN_USERNAME'，例如站长自行选择的用户名；不写死公开测试账号。
- 用受控本地命令生成 BCrypt 散列，成本参数从 12 起并按硬件实测调整；只存散列，不部署明文密码。
- secret 文件采用 Spring Boot configtree 读取：'spring.config.import=optional:configtree:/run/secrets/'。
- secret 文件命名 'magnus_admin_password-hash'；数据库密码文件名使用 'spring.datasource.password'，与配置属性精确对应。
- 使用配置项权校验、缺少用户名、密码散列、数据库配置或生产环境路径时拒绝启动，不回退到默认密码。
- 密码轮换：生成新散列 -> 替换 secret 文件 -> 重建应用容器 -> 原有会话会失效 -> 验证新密码可用，旧密码不可用。
- 不得在后端"修改密码"页面；控制台不打印散列或 secret 内容。

### 7.2 会话与访问规则

- /admin/** 除登录所需端点外要求 ADMIN；后台媒体也在规则内。
- Spring Security 默认保留 CSRF；图片上传通过显式 csrf token 发送或请求头，不为 multipart 关闭 CSRF。
- 成功登录更换 session ID；会话空闲 30 分钟过期，cookie 使用 HttpOnly、Secure、SameSite=Lax，本地 HTTP 开发用 profile 配置。
- 生产会话 Cookie 为 HttpOnly、Secure、SameSite=Lax；本地 HTTP 开发用 profile 配置。
- 后台登录页 no-store；限制暴力登录，例如源 IP 和账号维度 15 分钟内 5 次失败后临时拒绝，实测调节。
- 只有 nginx 对公网开放；限制可信代理，不能直接相信客户端提交的 X-Forwarded-For 或 Proto。

### 7.3 防护与隐私

- TLS、HSTS、禁止嵌入后、MIME 嗅探、参数化 SQL、文件白名单化及路径规范化。
- 数据初始化账号与应用账号分离：应用角色 'magnus' 不是超级用户，不能创建角色或其他数据库，仅拥有本项目数据库及迁移所需的 schema 权限。
- CORS report-only 验证后收紧为同源本/图片/字体；禁用危险内联脚本，编辑器样式需求用明确最小规则处理，不直接加入通配符。
- 不对外开发 actuator、env、heapdump、threaddump、logfile；服务端解析最终路径必须在受控存储根目录内。
- 文件上传只接受白名单内图片类型，验证真实类型与尺寸，重编码剥离元数据，避免 SVG 上传。
- 生产不开放 Actuator 环境、配置、堆转储和线程转储；如需健康检查，仅在内部网络且不在公网。
- 日志不记录 Authorization、Cookie、登录请求体和密码；IP 用于限流和安全审计，设定短保留期限。
- 联系方式公开前明确脱敏提示；图片处理去除非定位信息，但照片本身仍可能暴露位置和身份。

## 8. Docker 与远程数据部署

### 8.1 部署拓扑与资源

- Linux 虚拟机，建议 2 vCPU、4 GB RAM，独立 SSD 数据盘；1 GB 内存不作为图片处理生产基线。
- nginx 监听 80/443，Spring Boot 和应用、PostgreSQL 只监听内部网络。
- 应用约 1.5 GB 内存预算，数据库约 1 GB，其余供代理、操作系统和临时峰值；JVM 堆按容器预算实测调整。
- 镜像构建采用 Java 21 多阶段构建和非 root 运行用户；固定用户 UID/GID，例如 10001。
- PostgreSQL 使用 17.x 镜像并锁定 patch/digest；本设计针对 17 的 '/var/lib/postgresql/data' 布局，不套用其他大版本的数据目录规则。

### 8.2 宿主机持久化布局

```text
/srv/magnus/         真正挂载的数据盘根目录
  postgres/          PostgreSQL 17 PGDATA
  app-data/          处理后的展示图和缩略图
  media/             原始图片，同盘易于移动
  tmp/               临时暂存，不用于备份
  backups/ |         本地备份暂存，不等于异地备份
/etc/magnus/         宿主机受保护配置，不对外提供
  secrets/
    postgres-bootstrap-password
    app-database-password
    admin-password-hash
  tls/
    fullchain.pem
    privkey.pem
/opt/magnus/         Compose 与部署配置，不存业务数据
```

先挂载真实数据盘，再创建子目录及启动容器。'/srv/magnus' 只是示例挂载点；如果磁盘没有挂载，不能就默写入系统盘下的同名目录。

部署脚本检查 'mountpoint'、剩余空间、可写权限以及配置文件存在。可用 'systemd' RequisiteMountsFor=/srv/magnus 约束部署服务启动；挂载失败时失败，不创建空目录。

宿主机路径 容器内目标 用途
/srv/magnus/postgres /var/lib/postgresql/data 数据库数据
/srv/magnus/app-data /var/lib/magnus 图片和附件文件
/etc/magnus/secrets/admin-password-hash /run/secrets/magnus_admin_password-hash 管理员 BCrypt 散列
/etc/magnus/secrets/postgres-bootstrap-password /run/secrets/postgres_bootstrap_password 初始化受控运维账号密码，不挂载给应用
/etc/magnus/secrets/app-database-password /run/secrets/spring_datasource_password 应用数据库密码
/etc/magnus/tls /etc/nginx/tls TLS 证书与私钥

挂载目录必须使用长语法：'create_host_path: false'，避免源路径缺失自动创建。Docker Compose secrets 本质仍是主机文件挂载，并不是专门加密的 secret 管理平台。


### 8.3 Compose 核心要点

下面只表达关键数据挂载与配置，不是可直接发布的完整文件。占位镜像必须替换为已构建、已扫描的 tag/digest；后续补齐 nginx、证书、资源限额和健康检查。

```yaml
services:
  db:
    image: postgres:17-bookworm
    environment:
      POSTGRES_DB: magnus
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD_FILE: /run/secrets/postgres_bootstrap_password
    secrets:
      - postgres_bootstrap_password
    volumes:
      - type: bind
        source: ${DATA_ROOT:?DATA_ROOT must be set}/postgres
        target: /var/lib/postgresql/data
        bind:
          create_host_path: false
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d magnus"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  app:
    image: ${APP_IMAGE:?APP_IMAGE must be set}
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/magnus
      SPRING_DATASOURCE_USERNAME: magnus
      SPRING_CONFIG_IMPORT: optional:configtree:/run/secrets/
      APP_ADMIN_USERNAME: ${APP_ADMIN_USERNAME:?APP_ADMIN_USERNAME must be set}
      APP_STORAGE_ROOT: /var/lib/magnus
    secrets:
      - source: app_database_password
        target: spring.datasource.password
      - source: admin_password_hash
        target: magnus_admin_password-hash
    volumes:
      - type: bind
        source: ${DATA_ROOT:?DATA_ROOT must be set}/app-data
        target: /var/lib/magnus
        bind:
          create_host_path: false
    depends_on:
      db:
        condition: service_healthy
    restart: unless-stopped

secrets:
  postgres_bootstrap_password:
    file: /etc/magnus/secrets/postgres-bootstrap-password
  app_database_password:
    file: /etc/magnus/secrets/app-database-password
  admin_password_hash:
    file: /etc/magnus/secrets/admin-password-hash
```

首次启动数据库后，用受控初始化脚本创建“magnus”应用角色，设置其密码并授予项目数据库/schema 权限，再启动应用。脚本从 secret 安全读取密码，不在源码、命令回显或日志中暴露。仅配置上述 Compose 不会自动创建应用角色，也不会自动轮换已有 PGDATA 中的密码。

后续完整 Compose 不为 db/app 添加公网 "ports"；nginx 通过内部 "app:8080" 访问。图片大小限制三层一致：nginx 请求上限 12 MiB，Spring multipart 文件 10 MiB、请求 12 MiB，业务校验文件 10 MiB。

"APP_STORAGE_ROOT" 对应项目自定义 "app.storage.root"；本地配置也必须指向持久化文件目录，禁止生产回退到容器临时目录。

### 8.4 权限与继承

- 预先为 app-data 设置应用非 root UID/GID；PostgreSQL 目录权限按实际镜像用户确认，不假定不同镜像 UID 完全相同。
- secret 文件最小权限并允许指定容器运行用户读取；Compose 本地 secret 的 UID/mode 行为按实际版本验证。
- 数据库文件目录与图片目录不能互绑；不要通过 "chmod 777" 解决权限问题。
- 应用 "readOnly" 做文件系统、"tmpfs /tmp"、去除额外 "capabilities" 的配置在图片插件验证后启用；持久化写操作仅在挂载目录内。
- "liveness" 只表示进程可服务；"readiness" 验证数据库和存储连接可用，不暴露详细状态。
- 上传前检查剩余空间；不足时拒绝新上传并提示，不夸写满数据库盘后才报警。

### 8.5 构建、上线与更新

1. 在受信任的 Maven 环境中构建并测试可执行 JAR，再使用仅含 JRE 的 Dockerfile 构建运行镜像；不把 Maven "settings.xml"、仓库凭据或业务图片复制进镜像。
2. 以版本号和 Git SHA 标记镜像，扫描漏洞并推送镜像仓库；记录实际 digest。
3. 远程安装 Docker Engine 和 Compose，准备域名及 TLS，挂载盘并验证权限。
4. 写入受保护 secrets，设置 "DATA_ROOT=/srv/magnus"、"APP_IMAGE" 和管理员用户名；配置示例不含真实密码。
5. 验证完整 Compose 配置后启动数据库；首次部署创建应用角色并授权，再启动应用和 nginx；Flyway 校验/执行迁移。
6. 检查健康、HTTPS、后台登录、上传草稿图片、发布和匿名访问，确认公开/草稿边界。
7. 更新前备份；拉取固定新镜像，滚动重启应用容器；此处是短暂停机更新，不宣称零停机。
8. 完成后健康与回滚检查；失败时按兼容性范围回退镜像或恢复备份。

重建或移除容器不会删除 bind mount 数据，但手工删除挂载目录、格式化磁盘和错误恢复会造成丢失。禁止把 "docker system prune" 当作业务数据备份措施。

PostgreSQL 大版本升级是独立运维任务，先演练 pg_dump/pg_restore 或 pg_upgrade；不能把镜像从 17 直接换成新大版本继续挂载旧 PGDATA。

## 9. 备份、恢复与回滚

### 9.1 备份对象与目标

同时备份：PostgreSQL 逻辑快照、app-data 的媒体文件、应用/数据库镜像版本、迁移版本和校验清单。secret 单独加密备份，密钥不要与备份放在同一位置。

初期目标：每天一次备份并在升级前备份，RPO 不超过 24 小时；RTO 目标 2 小时，需按实际数据规模演练验证。建议保留 7 份日备份、4 份周备份，并复制到另一台主机或对象存储。

同一数据盘的 backups 目录只是暂存区；磁盘损坏时它会一起丢失。

### 9.2 首版一致性备份流程

1. 让公开网站进入短时维护页，停止唯一应用容器，阻止上传、发布、清理和资料修改；数据库保持运行。
2. 使用 PostgreSQL 17 工具执行 "pg_dump --format=custom"，检查退出码、文件大小和清单。
3. 归档 app-data 媒体目录，忽略可重建的临时文件；生成文件哈希清单。
4. 保存应用镜像 digest、postgresql 版本和 Flyway 当前迁移版本，形成一个备份批次。
5. 重启应用，验证 "readiness" 并下载下维护页；备份步骤失败也要恢复业务并报警。
6. 将备份复制到异地并验证校验和；定期在独立环境恢复演练。

停应用实现单站写入静止，不需要首版开发分布式写锁。不能在数据库和图片均持续变化时随意分开复制，然后承诺一致性。

不要直接 "tar" 正在运行的 PGDATA 作为可靠数据库备份；逻辑备份与数据库物理备份是两套不同流程。

### 9.3 恢复步骤

1. 在隔离环境或新空目录准备相同数据库大版本和对应的应用镜像；保留原数据，不覆盖唯一现存副本。
2. 恢复 secret 和受保护配置，启动空 PostgreSQL 受控配置的应用角色和权限；"pg_dump" 不含全局角色，不得假定数据库备份会恢复账号。
3. 以同版本 "pg_restore" 恢复数据库，再恢复 app-data 文件并校验哈希；不让应用先启动执行意外的新迁移。
4. 验证迁移版本及文件校验和，应用启动后验证登录和公开读，再确认外部入口路由。
5. 对账所有 REQ 模块的文件渲染、发布与未发布边界及媒体权限。
6. 恢复流量前检查：网站内容、登录、发布和图片；确认恢复的草稿未公开。
7. 验收通过后切换代理或域名；记录实际恢复耗时及可接受的数据回退范围。

### 9.4 应用回滚

数据库迁移采用向后兼容的扩展优先策略。上一版兼容新 schema 时才能只回退镜像；不兼容或破坏性迁移时，停止应用并恢复数据库与图片的同批次备份，明确会丢失备份后变更。

Flyway 不承担自动业务回滚；上线前必须记录 "可只退镜像" 还是 "需要恢复数据"。

## 10. 测试、运维与质量门禁

### 10.1 测试层次

| 层次 | 重点 |
| --- | --- |
| 单元测试 | 模块必填校验、状态推导、slug、HTML 清洗、路径检查、媒体公开引用判定 |
| MockMvc | 匿名与认证路由、CSRF、上传错误、登录退出、管理 404、后台预检、镜像限制 |
| PostgreSQL 集成测试 | Flyway 迁移、唯一约束、并发草稿、版本冲突、搜索、匿名、引用溯源 |
| 图片处理测试 | 解码/尺寸/格式/重编码、EXIF 清理、生成哈希与缩略图、大图输入限制 |
| 浏览器 E2E | 登录、发布、编辑、上传、搜索、标签、预览、草稿/非公开隐私、内容恢复 |
| 灾备演练 | 从备份恢复至隔离环境，核对关键路径、媒体、顺序/日志、错误不泄露 |

集成测试使用 PostgreSQL Testcontainers，不以 H2 模拟 PostgreSQL 的行为；E2E 使用普通账户及本地图片夹具，不接触用户真实密码和私人照片。

### 10.2 需求到验证追踪

| 需求 | 实现后端 | 关键验证 |
| --- | --- | --- |
| REQ-001 | profile、公开主页渲染 | 匿名资料渲染与公开最新内容 |
| REQ-002 | 认证 | 安全登录、退出、会话过期、退出失效 |
| REQ-003 | 安全过滤器、媒体控制器 | 未认证拒绝、草稿/私密媒体匿名不可访问、匿名操作失败 |
| REQ-004 | photo、media | 多图排序、放大查看、相册发布 |
| REQ-005 | blog、HtmlSanitizer | 正文安全、XSS 清洗结果一致 |
| REQ-006 | food | 食材步骤、成品/步骤图片 |
| REQ-007 | travel | 多地点排序、地图外链、外链校验 |
| REQ-008 | 搜索 | 保存不改变公开内容、未发布排除回原、楼中楼返回准确请求 |
| REQ-009 | PublishingService、revision | 保存不改变首次发布日期、并发编辑 409 |
| REQ-010 | media、路径检查、响应头 | 路径穿越拒绝、伪装图片拒绝 |
| REQ-011 | compose、PostgreSQL | 数据挂载使用、重建后数据仍可读 |
| REQ-012 | viewport、/assets | 手机/屏宽 320px 与 1440px、横屏与触屏导航、键盘操作、空状态、SEO |






