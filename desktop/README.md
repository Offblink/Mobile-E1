# 课友班级群 · 桌面端客户端

Android 课程项目配套的桌面客户端：连本机 SSM 服务端，做两件事 —— **发布公告**、**发消息 / 发文件**。

- **Kotlin + Compose Multiplatform（桌面端）**，JDK 17；构建复用仓库根目录的 Gradle wrapper
- 单一窗口，两个 Tab（发布公告 / 班级群），3 秒自动刷新，网络请求都在 IO 线程
- 失败只在顶部状态栏提示，不弹窗

## 目录

```
desktop/
├── settings.gradle.kts          独立构建（仓库根 gradlew -p desktop 即可）
├── build.gradle.kts             kotlin jvm + compose 插件、checkApi 任务
├── build.cmd                    编译
├── 打开班级群客户端.cmd            启动
├── README.md
└── src/main/
    ├── kotlin/com/offblink/keyou/desktop/Main.kt   Compose 界面 + --check 入口
    └── java/com/offblink/keyou/desktop/            Api.java（HTTP）/ Json.java（极简 JSON）
```

## 构建与运行

```bat
cd /d C:\Users\Blinvo\Desktop\mobile\实验\1\desktop
build.cmd                  :: 等价于 ..\gradlew.bat -p . compileKotlin
打开班级群客户端.cmd         :: 等价于 ..\gradlew.bat -p . run
```

首次构建会从阿里云镜像拉 Compose 依赖（约几分钟），之后走缓存。

无界面自检（不弹窗口，只打一行）：

```bat
cd /d C:\Users\Blinvo\Desktop\mobile\实验\1
gradlew.bat -p desktop checkApi -PbaseUrl=http://127.0.0.1:8081
:: OK MySQL 8.0.43   /   FAIL <原因>
```

## 服务端

先起后端（端口 8081）：

```bat
scripts\start-server.cmd
```

界面顶部填服务器地址（默认 `http://127.0.0.1:8081`），点「连接」；连上后绿灯 + `已连接 · MySQL 8.0.x`。

- **发布公告** → `POST /api/notice`，历史公告列在下面，点条目看正文
- **班级群** → 发消息（`POST /api/message`）、选文件上传（`POST /api/file` 多段表单）
- 消息与文件列表每 3 秒自动刷新；消息只拉增量（`after=最大 id`）

手机端装同一套服务端的 APK：「班级群」里 4 秒内能看到电脑发的消息；
班级公告 / 群文件的查看入口在手机「我的 → 日志 → 调试面板」。
