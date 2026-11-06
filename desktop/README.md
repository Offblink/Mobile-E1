# 课友班级群 · 电脑端客户端

Android 课程项目配套的桌面客户端：连本机 SSM 服务端，做两件事 —— **发布公告**、**发消息 / 发文件**。

- JDK 17 + Swing + `java.net.http`，**零第三方依赖**（无 Maven / Gradle / jar 包）
- 单一窗口，两个 Tab，3 秒自动刷新，全部网络请求在后台线程
- 失败只在底部状态栏提示，不弹窗

## 目录

```
desktop/
├── build.cmd                    编译（javac，输出到 out/）
├── 打开班级群客户端.cmd            双击启动（javaw，无控制台窗口）
├── README.md
├── .gitignore
├── out/                         编译产物（不入库）
└── src/main/java/com/offblink/keyou/desktop/
    ├── ClassGroupClient.java    界面 + 定时刷新 + --check 入口
    ├── Api.java                 服务端访问层（超时 / {code,message,data} 解包 / 错误文案）
    └── Json.java                极简 JSON 解析与转义（替代 Jackson）
```

## 构建

双击 `build.cmd`，或在 cmd 里：

```bat
cd /d C:\Users\Blinvo\Desktop\mobile\实验\1\desktop
build.cmd
```

等价于（cmd 不会展开通配符，所以脚本里逐个列文件）：

```bat
javac -encoding UTF-8 -d out ^
      src\main\java\com\offblink\keyou\desktop\Json.java ^
      src\main\java\com\offblink\keyou\desktop\Api.java ^
      src\main\java\com\offblink\keyou\desktop\ClassGroupClient.java
```

源码是 UTF-8，必须带 `-encoding UTF-8`，否则中文常量会编译成乱码。
脚本里显式列了三个 `.java`；**新增源文件时记得同步加进 `build.cmd`**。

## 启动

先起服务端（见下一节），再双击 `打开班级群客户端.cmd`。窗口顶部「服务器」默认 `http://127.0.0.1:8081`，
程序启动时会自动连一次；连不上就点「连接」重试，失败原因显示在状态栏。

也可以手动跑：

```bat
java -cp out com.offblink.keyou.desktop.ClassGroupClient
```

想看到控制台输出就用 `java`；`打开班级群客户端.cmd` 用的是 `javaw`，不弹黑窗。

## 服务端

```bat
scripts\start-server.cmd
```

脚本会 `mvnw package` 出 WAR → 部署到本机便携 Tomcat 的 `ROOT.war` → 起在 **8081** 端口，
并等 `/api/ping` 可通（含 MySQL 连通性检查）。验证：

```bat
curl http://127.0.0.1:8081/api/ping
:: {"code":0,"message":"ok","data":{"db":"MySQL 8.0.43"}}
```

客户端只认这个统一响应体 `{code, message, data}`：`code != 0` 时把 `message` 原样显示到状态栏。

## 无界面自检

不建窗口，只打一行结果，用于排「到底是服务端没起还是客户端坏了」：

```bat
java -cp out com.offblink.keyou.desktop.ClassGroupClient --check http://127.0.0.1:8081
```

| 情况 | 输出 | 退出码 |
| --- | --- | --- |
| 服务端可用 | `OK MySQL 8.0.43` | 0 |
| 端口没开 | `FAIL 无法连接服务器，请确认地址与端口` | 1 |
| 接口 404（WAR 没部署或版本旧） | `FAIL HTTP 404` | 1 |
| 地址写错 | `FAIL 服务器地址无效：…` | 1 |

省略 URL 时默认 `http://127.0.0.1:8081`。

## 界面

```
┌────────────────────────────────────────────────────────────┐
│ 服务器：[http://127.0.0.1:8081] [连接]  已连接 · MySQL 8.0.43 │
├────────────────────────────────────────────────────────────┤
│ [发布公告] [班级群]                                          │
│  ┌ 新建公告 ──────────────────────────────────────────────┐ │
│  │ 作者：[电脑端]  标题：[____________________]            │ │
│  │ ┌ 正文 ────────────────────────────────────┐           │ │
│  │ │                                          │           │ │
│  │ └──────────────────────────────────────────┘  [发布]   │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌ 历史公告 ───────────────────────────────────────────────┐│
│  │ 标题            作者      时间                           ││
│  │ 期中考试安排     电脑端    2026-11-06 14:30              ││
│  │ ─────────────────────────────────────────────────────── ││
│  │ 标题：…  作者：…  时间：…                                ││
│  │ 正文…                                                    ││
│  └─────────────────────────────────────────────────────────┘│
├────────────────────────────────────────────────────────────┤
│ [14:31:22] 已连接 http://127.0.0.1:8081（MySQL 8.0.43）     │
└────────────────────────────────────────────────────────────┘
```

**发布公告 Tab**：填作者 / 标题 / 正文 → 发布。下方「历史公告」表格是最近 50 条，
选中某行，下半区显示正文全文。

**班级群 Tab**：上方填发送人 + 消息 → 发送（回车同效），消息区按 `时间 发送人：内容` 追加并自动滚到底；
下方是群文件：`选择文件…` → 文件名旁显示大小 → `上传`，列表显示 `文件名 · 大小 · 上传人 · 时间`。

## 刷新与线程模型

- 一个 `javax.swing.Timer`（3000ms）驱动刷新；上一轮没回来就跳过本轮，不会叠加请求。
- 三个刷新请求（公告 / 文件 / 消息）在 `ExecutorService` 里串行跑，结果一次性交给 EDT 渲染。
- 三个接口**各自独立**：某个接口报错（例如公告表还没建好返回 500）不会拖垮另外两个，
  群消息照常刷新，状态栏只提示一次首个错误。
- 消息用游标增量拉取：`GET /api/messages?after=<已显示的最大 id>&size=100`，只追加新行。
- 数据没变化时不重建表格：选中行、表格滚动位置不会被 3 秒刷新打断。
- 服务端重启期间：状态栏提示「刷新失败：…」，顶部连接标签变「连接中断，重试中…」，
  定时器继续重试，恢复后自动变回「已连接」，不需要重连。
- 连接超时 3 秒，单次请求超时 10 秒。

## 与服务端的接口对应

| 动作 | 请求 | 用到的字段 |
| --- | --- | --- |
| 连接 | `GET /api/ping` | `data.db` |
| 拉公告 | `GET /api/notices?size=50` | `id,title,content,author,createdAt` |
| 发公告 | `POST /api/notice` `{title,content,author}` | 同上 |
| 拉消息 | `GET /api/messages?after=&size=100` | `id,sender,content,createdAt` |
| 发消息 | `POST /api/message` `{sender,content}` | 同上 |
| 拉文件 | `GET /api/files` | `name,size,uploader,uploadedAt` |
| 传文件 | `POST /api/file` multipart：`file` + `uploader` | 同上 |

`POST` 一律 `Content-Type: application/json; charset=UTF-8`；文件上传用 `multipart/form-data`，
文件名与 `uploader` 均按 UTF-8 编码。

> 注意：`POST /api/file` 要求服务端开启 Spring 的 multipart 解析（`StandardServletMultipartResolver`
> 或 `CommonsMultipartResolver`），否则会返回 400/404。拉取列表类接口若返回 404，说明部署的 WAR 还没带上这组 Controller。

## 排错

| 现象 | 原因 |
| --- | --- |
| 状态栏「HTTP 404」 | 服务端 WAR 是旧版本，或没部署到 ROOT（路径少了前缀） |
| 「无法连接服务器，请确认地址与端口」 | Tomcat 没起 / 端口被占 / 地址填错 |
| 中文变问号 | 编译漏了 `-encoding UTF-8`，或运行环境 `file.encoding` 异常 |
| 上传一直失败 | 服务端没配 multipart 解析，或文件被其他进程占用 |
