# 课友（Keyou）

班级同学录 + 班级群聊 App：Android 原生（Java + XML Views，Material 3）× SSM 后端
（Spring 5.3 + SpringMVC + MyBatis + MySQL 8）。

- 开发规格：[docs/SPEC.md](docs/SPEC.md)（功能映射、API 契约、UI 设计）
- 课程要求对照：[docs/实验一-要求整理.md](docs/实验一-要求整理.md)

## 仓库结构

```
app/        主 App（com.offblink.keyou）—— 登录 / 同学录 / 课友圈 / 我的
reader/     第二个 App「读取器」（com.offblink.keyou.reader）：进入直接显示课友通讯录（跨 App ContentProvider）
server/     SSM 后端（Maven WAR：Spring 5.3 + MyBatis + MySQL 8）
scripts/    deploy.sh（打包→ROOT 部署→8081 起停→探活）
docs/       规格、要求对照、报告素材
tools/      本地 Tomcat 副本（不进 git）
```

主 App 的 Java 按功能分包（`app/src/main/java/com/offblink/keyou/`）：

```
ui/        入口与主界面：LoginActivity、MainActivity（三页宿主）、KeyouActivity（二级页基类）
contacts/  同学录：ContactsPage、分组适配器与字母索引条、ContactDetailActivity、
           SQLite（ContactDBHelper/ContactDao/Contact）、班级成员（Mate*）、数据自检页
chat/      课友圈：ChatPanel、ChatAdapter/ChatMessage、公告 RssParseActivity、草稿页
download/  下载：DownloadActivity + 前台服务 DownloadService
mine/      我的：ProfileStore/UserStore、PlaceStore + ProvinceLocator、StartupSound
log/       AppLog 与日志页 LogActivity
debug/     诊断页、服务自检页、绑定式 BoundNoteService
net/       HttpUtil、ServerConfig（10.0.2.2 / 127.0.0.1:8081 与回退）
provider/  ContactProvider（跨 App 供 reader 读取）
lab/       任务 2/4 的实验：四种启动模式 Activity、广播接收器与 action 常量
```

## 功能一览

底部三页：**同学录 / 课友圈 / 我的**。

| 模块 | 功能 | 入口 |
|---|---|---|
| 登录与账号 | 账号密码校验、记住密码、注册（本地账号库，密码存 SHA-256 摘要） | 启动页；「注册」→ 自动回填 → 登录 |
| 同学录 | 本地联系人与班级成员合并成一个分组列表（拼音分组 + 右侧字母索引 + 吸顶字母），每行带「本地 / 班级」来源角标与头像；点行按来源编辑（本地写 SQLite、班级 PUT 服务端），长按删除，FAB 新建时选来源；右上「自检」用 org.json / Gson 双解析校验班级数据 | 底部「同学录」tab |
| 课友圈 | 左右气泡聊天室（RecyclerView + 输入行，键盘发送键直接发）；会话头「班级公告 / 群文件 / 草稿」：分别接 RSS 公告、前台服务下载、输入草稿（自动落到 `files/chat_draft.txt`） | 底部「课友圈」tab |
| 我的 | 头像（相册选择，无头像显示昵称首字）、昵称点击改名；**所在省份**（进入页面自动定位，显示在昵称右边）、启动音（固定开启）；**服务器地址**（真机连本机局域网用，见下）、日志、退出登录 | 底部「我的」tab |
| 日志 | 应用内日志实时滚动（级别过滤 / 清空），数据来自 AppLog 环形缓冲；底部「调试面板 / 服务自检」接四种启动模式、广播、隐式 Intent 与绑定式 Service | 我的 → 日志 |

进入「我的」自动定位（5 分钟内的结果直接复用），省份显示在昵称右边：坐标 → 省份是**本地枚举**
（34 个省级行政区的近似矩形，命中多块取面积最小的，港澳/直辖市就不会被广东/河北盖掉；
不联网、不接地图 SDK），结果连同经纬度、来源与时间存进 SP。启动音固定开启，
每个 App 进程冷启动时响一次（登录页进入时播放），没有开关。

## 快速开始

支持 Android 8.0（API 26）及以上；真机装包失败先看版本——低于 8.0 会直接报「解析包时出现问题」。

```bash
# 1. 数据库（MySQL 8 系统服务；schema 与账号见 server/docs/schema.sql）
#    专用 schema: keyou_e1，低权限账号 keyou（口令在 server/src/main/resources/db.properties，不进 git）

# 2. 服务端：打包 WAR → ROOT 部署 → Tomcat 8081 → 探活 /api/ping
bash scripts/deploy.sh

# 3. 客户端（双模块）
gradlew.bat assembleDebug -I aliyun-init.gradle
gradlew.bat testDebugUnitTest -I aliyun-init.gradle   # 省份枚举 5 + 地址规范化 6
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r reader/build/outputs/apk/debug/reader-debug.apk
```

- 后端基址：模拟器内 `http://10.0.2.2:8081`（首选），**失败自动回退 `http://127.0.0.1:8081`**
  （需 `adb reverse tcp:8081 tcp:8081`，`scripts/start-server.cmd` 会在有设备时自动执行，
  重连/重启模拟器后跑一次 `scripts/connect-device.cmd`）
- **真机（Wi-Fi 直连）**：手机与电脑同一局域网 → 电脑上 `ipconfig` 查 WLAN 的 IPv4 →
  手机「我的 → 服务器地址」填这个 IP（只填 `192.168.1.5` 也行，端口默认 8081）→ 保存即生效，
  同学录会立刻按新地址重拉。debug 包已放开明文 HTTP（`app/src/debug/res/xml/network_security_config.xml`
  覆盖 main 的同名白名单），release 包仍只放行 10.0.2.2 / 127.0.0.1 / localhost。
  连不上先查三处：Tomcat 是否在跑（`curl http://127.0.0.1:8081/api/ping`）、
  Windows 防火墙是否放行 java.exe 入站（当前机器已有「公用」配置文件的入站允许规则）、
  路由是否开了 AP 隔离。
- 真机也可以插 USB 走隧道（不改任何配置）：`adb reverse tcp:8081 tcp:8081`，
  首次请求仍会先撞 10.0.2.2 等 4 秒再回退到 127.0.0.1，之后就直连。
- 接口约定：统一 `{code, message, data}`；`GET /api/mates`、`POST/PUT /api/mate`、`DELETE /api/mate/{id}`、`GET /api/rss`、`GET /files/demo-note.txt`
- 一键接口冒烟（server 起着时）：

```bash
curl "http://127.0.0.1:8081/api/mates?page=1&size=2"
curl -X POST -H "Content-Type: application/json" \
     -d '{"name":"测试","phone":"139","gender":"男"}' http://127.0.0.1:8081/api/mate
```

## UI 设计

取材自 [mobile-design](https://github.com/Offblink/Mobile-Design) 的 android-m3 组件（Material 3 Expressive）：
底部胶囊导航（弹射 pill + 平移切页）、expressive 统计大卡、M3 紫罗兰角色色 + values-night 深色模式；
动效双轨——位移 spring（OvershootInterpolator 0.7）、颜色直切不回弹。

**返回**：二级页顶栏统一由 `KeyouActivity` 基类在 `onContentChanged()` 里绑定 `finish()`；
主界面按系统返回键先回「首页」tab，再按才退出。**退出登录**在「我的」页。
头像走系统相册（Photo Picker，无需存储权限），落盘到应用私有目录，Glide 圆裁加载。

## 排错

| 现象 | 原因与处理 |
|---|---|
| App 报「网络异常：failed to connect to /10.0.2.2」 | ① 服务端没起：双击 `scripts\start-server.cmd`；② 该 AVD 的 App 侧网络栈异常（`adb shell` 里 nc/curl 正常、App 却超时，属假阳性）：已内置**地址自动回退**到 `127.0.0.1`，只需保证 `adb reverse tcp:8081 tcp:8081` 生效（`scripts/connect-device.cmd`） |
| 回退后仍报 `Cleartext HTTP traffic to 127.0.0.1 not permitted` | `network_security_config.xml` 的 domain 列表要含 `127.0.0.1`；注意 **不能写 CIDR**（如 `192.168.0.0/16`），非法条目会让整份配置失效 |
| 模拟器 Wi-Fi 显示开启但一直 Disconnected | 该 AVD 没有虚拟 Wi-Fi 硬件（只有 eth0），保持 Wi-Fi 关闭、走以太网即可 |
| 端口 8080 被占用 | 课堂08 的旧 Tomcat 还开着；本项目固定用 **8081**，两者互不影响 |
