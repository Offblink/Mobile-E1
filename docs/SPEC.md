# SPEC — 课友（Keyou）· 实验一开发规格

> 状态：已确认（主题 A 课友 / MySQL 8 / android-m3 原生翻译 / Java+Views 不用 Compose）。按 §8 增量开发，每个任务一个提交。
> 要求依据：`docs/实验一-要求整理.md`（忠实提取自实验指导书）。

## 1. 定位与主题

**课友（Keyou）——班级同学录 + 班级聊天 App。** 把指导书 9 个任务串成一个完整产品，而不是 9 个孤立实验页：

- 指导书任务 9 的「员工管理 REST CRUD」→ 平替为「班级成员管理」，接口语义、验收点完全等价；
- 任务 5 的「SQLite 通讯录」→ App 内「本地同学录」，并被任务 6 的 ContentProvider 复用；
- 任务 3 的「登录页 / 聊天室」→ App 的真实入口与「课友圈聊天室」；
- 任务 1/2/4/7/8 的演示性内容 → App 内自然承载 + 一个「实验室」页收纳纯演示项。

**原则：在老师要求范围内，每个任务的验收点一个不少；但呈现为一个产品，不出现「实验 1 页面 / 实验 2 页面」式的裸试剂页。**

## 2. 技术栈与版本（全部为本机已验证组合）

| 项 | 选型 | 依据 |
|---|---|---|
| 客户端语言 | **Java**（强制），XML Views | 指导书样例全为 Java；现场提问要讲代码逻辑 |
| Compose | **不使用** | 指导书口径为 Views；避免答辩额外解释 |
| minSdk / targetSdk / compileSdk | 34 / 37 / 37 | 与本机 Andriod01 已验证组合一致（AGP 9.3.3 + Gradle 9.5.0）；指导书「SDK 34」为环境建议，功能口径不受影响，报告注明 |
| 构建 | Gradle wrapper 9.5.0 + AGP 9.3.3，`-I aliyun-init.gradle` 走阿里云镜像 | Andriod01 / 课堂08 实测 |
| 后端 | **SSM**：Spring 5.3 + Spring MVC + MyBatis 3.5 + Jackson，Maven（mvnw 3.8.5）打包 WAR | 用户指定；骨架复用课堂08 HttpUrlServer 实测配方 |
| 数据库 | **MySQL 8.0.43**（本机系统服务 `MySQL80`，127.0.0.1:3306，utf8mb4）；专用 schema **`keyou_e1`** + 低权限账号 `keyou`（口令只写本机 `server/src/main/resources/db.properties`，**不进 git**，仓库只放 `db.properties.example`） | 与指导书口径一致（MySQL 8）；root 口令不进仓库；可视化直接用 MySQL 生态（mysql CLI / 任意 GUI 客户端）截图进报告 |
| Web 容器 | 便携 Tomcat 9.0.123 独立副本 `tools/tomcat/`（源：`Desktop\mobile\课堂\08\unpacked\apache-tomcat-9.0.123`），**ROOT 上下文、端口 8081**（8080 被课堂08 的旧服务占用，不动它） | 实测配方；客户端写 `10.0.2.2:8081/api/...` |
| 第二 App | 同仓库第二 application 模块 `reader`（任务 6 跨 App 验证） | 指导书要求「新建第二个 App」 |

## 3. 仓库结构

```
Mobile-E1/
├── settings.gradle            # include ':app', ':reader'
├── gradle/ + gradlew          # wrapper 9.5.0（从 Andriod01 拷，已缓存）
├── aliyun-init.gradle         # 镜像 init script
├── app/                       # 主 App：com.offblink.keyou
├── reader/                    # 任务6 第二 App：com.offblink.keyou.reader
├── server/                    # SSM 后端（Maven WAR）
│   ├── pom.xml
│   └── src/main/{java,resources,webapp}
├── scripts/                   # 启动服务器 / 构建 / 验收脚本
├── docs/                      # 要求整理、SPEC、报告素材
└── README.md
```

## 4. 任务 → 功能映射（验收点对账表）

| 任务 | 验收点（指导书原文口径） | 落在 App 哪里 |
|---|---|---|
| 1 环境与 Log | 环境验证、项目结构、Log 分级输出与 Logcat 过滤 | 全局 Log 约定（统一 TAG 前缀 `Keyou/`）；「我的 → 关于」页有 Log 自检按钮；结构说明进报告 |
| 2 生命周期与 Intent | 全生命周期日志（正常/Home/旋转/finish）、Bundle 多类型传参、registerForActivityResult 回传、四种启动模式、隐式 Intent | 登录页→主页（显式 Intent + Bundle + 结果回传改昵称）；「实验室 → 启动模式演示」4 个 Manifest 变体 Activity；「实验室 → 隐式 Intent」开浏览器 |
| 3 UI 与控件 | 四大布局登录页、7+ 控件、RecyclerView 列表、左右气泡聊天室 | 登录页（ConstraintLayout + 七控件）；本地同学录列表；「课友圈」聊天室（气泡 RecyclerView） |
| 4 广播 | 动态注册网络变化、静态注册自定义广播、有序广播 priority/abort、8.0+ 适配说明 | 全局网络变化监听（横幅提示）；同步完成自定义广播（显式 setPackage 过 8.0+ 限制）；「实验室 → 有序广播演示」 |
| 5 持久化 | 文件读写、SP 记住密码、SQLite 通讯录（VERSION=2 升级加列、参数化 CRUD、事务、模糊查询、RecyclerView） | 登录记住密码（SP）；草稿箱（文件）；本地同学录（SQLite contact.db 全套） |
| 6 Provider 与权限 | READ_CONTACTS 运行时读取、自定义 Provider + UriMatcher + exported/permissions、第二 App 跨 App 读取 | 「本地同学录 → 导入系统联系人」；ContactProvider；`reader` 模块跨 App 读取验证 |
| 7 网络 | HttpURLConnection 子线程 GET/POST、org.json 与 Gson 双解析对比、通用网络工具类、XmlPullParser RSS、明文 HTTP 适配 | `HttpUtil` 工具类；「实验室 → JSON 双解析对比」；RSS 经 server `/api/rss`；network_security_config 放行 10.0.2.2 |
| 8 服务与定位媒体 | startService/bindService 生命周期、后台下载（前台服务+进度通知+POST_NOTIFICATIONS）、一次定位显示、MediaPlayer、相机缩略图 | 「我的 → 下载中心」；「我的 → 位置」（定位+地图）；「我的 → 音乐盒」；「我的 → 拍照」 |
| 9 SSM 整合 | REST 分页列表/新增/PUT 编辑/长按删除、POST JSON、加载中对话框、空态、异常统一提示、Postman 先行、≥3 联调问题记录 | 「班级成员」主页面（启动即加载）；server 提供全部接口 |

**实验室页（「我的 → 实验室」）** 收纳：启动模式演示、隐式 Intent、有序广播、JSON 双解析、生命周期日志观察——这些是指导书明确要求"观察/对比"的演示项，集中收纳比散落各处更利于现场讲解。

## 5. 后端 API 契约（server）

统一响应体：`{ "code": 0, "message": "ok", "data": [...] }`（data 恒为数组，客户端解析简单）。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/mates?page=1&size=10&kw=` | 分页 + 姓名模糊查询，返回 `{list, total}` |
| POST | `/api/mate` | JSON 新增班级成员 |
| PUT | `/api/mate` | JSON 修改 |
| DELETE | `/api/mate/{id}` | 删除 |
| GET | `/api/rss` | RSS XML（任务 7 XmlPullParser 练习用） |
| GET | `/files/demo-note.txt` | 静态文件（任务 8 后台下载演示用） |

- 实体 `Mate`：`id, name, phone, email, gender, remark`（对应指导书 Contact 的 id/name/phone/email/gender）。
- MySQL schema `keyou_e1` + `DatabaseInitializer`（InitializingBean）建表灌种子数据；表结构 DDL 落 `server/docs/schema.sql`（可重放），可视化用 `mysql` CLI 或任意 GUI 客户端连 `127.0.0.1:3306` 截图进报告。
- 路径 `/api/mates` 而非指导书字面 `/api/emps`：主题平替（班级成员≠员工），报告附映射说明；REST 形态、分页、CRUD 验收点逐一对应。
- CORS：同源 ROOT 部署，App 直连，不需要跨域；报告联调问题从真实踩坑记录（编码/字段/线程等）取材。

## 6. UI 设计（mobile-design android-m3 → 原生）

取材 `C:\Users\Blinvo\Desktop\myProject\mobile-design\components\android-m3\`，按 `mobile-design-android-m3-recipe` 配方翻译（该配方已在 Andriod01 落地过 nav-pill-bar + expressive-card）：

| 组件 | 用途 |
|---|---|
| nav-pill-bar | 底部胶囊导航：首页 / 课友圈 / 同学录 / 我的（4 格，弹射 pill + OvershootInterpolator） |
| expressive-card | 首页统计卡（成员数/未读/今日）+ 关键信息卡 |
| fab-menu | 同学录页右下 FAB 展开（新增/导入/查询） |
| spring-switch | 设置项开关（记住密码等），弹簧位移 + 轨道色直切 |
| button-group | 分段切换（如本地同学录 ↔ 系统联系人导入视图） |
| morph-loader | 加载中对话框的形变装载器 |

- 色彩：M3 紫罗兰角色色，取 `shared/tokens.css`；深色模式跟随系统（colors + values-night）。
- 动效双轨铁律：位移/形变走 spring（OvershootInterpolator 0.7 / 350ms），颜色/透明度直切不回弹。
- **不追求花哨**：只用上述 6 组件 + 常规 M3 控件，每个组件都要能在现场讲出"为什么这么设计"。

## 7. 验证口径

- 每任务提交前：`gradlew assembleDebug` 通过（客户端）；server 改动则 `mvnw package` + curl 冒烟（GET 列表、POST 中文、缺参 400）。
- 端到端（任务 7/9 完成时）：Tomcat 起 → Postman/curl 验接口 → 模拟器 `10.0.2.2` 联调 → `adb install` + `am start` → 用户实测（按用户口径：开 emu、装 APK、拉起即止，交互验证归用户）。
- 报告截图（Logcat/界面/数据库/联调）由用户在验收时采集；素材位留 `docs/report/`。
- 每任务一次有效提交，提交说明写清"本任务验收点 + 对应指导书条目"。

## 8. 增量开发顺序

| 阶段 | 内容 | 提交 |
|---|---|---|
| P0 | 仓库骨架：Gradle 双模块空壳编译通过 + server 空壳 WAR 起 Tomcat + curl 通 | chore: 仓库骨架 |
| P1 | 任务1+2：Log 约定、登录→主页生命周期日志、Bundle/回传、启动模式实验室、隐式 Intent | feat(task1-2) |
| P2 | 任务3：登录页四大布局七控件、同学录 RecyclerView、聊天室气泡 | feat(task3) |
| P3 | 任务4：网络变化动态广播、自定义广播（显式）、有序广播演示 | feat(task4) |
| P4 | 任务5：文件草稿箱、SP 记住密码、SQLite 同学录全套（升级/事务/模糊查） | feat(task5) |
| P5 | 任务6：READ_CONTACTS 导入、ContactProvider、reader 跨 App | feat(task6) |
| P6 | 任务7：HttpUtil、双 JSON 解析对比、RSS 解析、明文 HTTP 配置 | feat(task7) |
| P7 | 任务8：前台服务下载+通知、定位+地图、MediaPlayer、相机 | feat(task8) |
| P8 | 任务9：SSM CRUD 全套 + 班级成员页整合（分页/表单/删除/编辑/对话框/空态/异常） | feat(task9) |
| P9 | UI 收尾（m3 组件落地 + 深色模式）、README、报告素材归档 | chore: polish |

## 9. 风险与已定口径

- **地图**：高德/百度需注册 Key（包名+SHA1），超出课内闭环 → 默认用 **osmdroid（OpenStreetMap，免 Key）** 显示定位点；若瓦片加载失败降级为坐标+地名卡片，报告注明取舍。若你有高德 Key 可换。
- **compose可选**：已按你口径定为不用（Java 强制、Views）；若你想某页用 Compose 练手，说一声我单开模块，但默认不做。
- **指导书样例的过时 API**（CONNECTIVITY_ACTION / IntentService / startActivityForResult）：实现用现代等价物，报告里写"适配说明"，这本身是加分的"甄别 AI/旧代码"素材。
- **AVD**：本机 Small_Phone 是 API 37（指导书写 API 34 Pixel），功能无影响，报告注明环境差异。
