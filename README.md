# 课友（Keyou）

班级同学录 + 班级群聊 App：Android 原生（Java + XML Views，Material 3）× SSM 后端
（Spring 5.3 + SpringMVC + MyBatis + MySQL 8）。

- 开发规格：[docs/SPEC.md](docs/SPEC.md)（功能映射、API 契约、UI 设计）
- 课程要求对照：[docs/实验一-要求整理.md](docs/实验一-要求整理.md)

## 仓库结构

```
app/        主 App（com.offblink.keyou）—— 登录 / 首页 / 同学录 / 课友圈 / 我的 / 工具箱
reader/     第二个 App（com.offblink.keyou.reader）：跨 App 读 ContentProvider
server/     SSM 后端（Maven WAR：Spring 5.3 + MyBatis + MySQL 8）
scripts/    deploy.sh（打包→ROOT 部署→8081 起停→探活）
docs/       规格、要求对照、报告素材
tools/      本地 Tomcat 副本（不进 git）
```

## 功能一览

| 模块 | 功能 | 入口 |
|---|---|---|
| 登录与账号 | 账号密码校验、记住密码、注册（本地账号库，密码存 SHA-256 摘要） | 启动页；「注册」→ 自动回填 → 登录 |
| 首页 | 欢迎语、本地联系人 / 班级成员统计、班级成员列表（分页、点击编辑、长按删除、右上角添加） | 底部「首页」tab |
| 同学录 | 拼音分组列表 + 右侧字母索引条 + 吸顶字母；详情页可编辑表格；新增/删除；导入系统联系人 | 底部「同学录」tab |
| 课友圈 | 左右气泡聊天室（RecyclerView + 底部输入） | 底部「课友圈」tab，直接进入聊天 |
| 我的 | 头像（相册选择，无头像显示昵称首字）、昵称点击改名、工具箱、关于、退出登录 | 底部「我的」tab |
| 工具箱 · 内容 | 草稿箱（文件存储）、下载中心（前台服务 + 进度通知） | 我的 → 工具箱 |
| 工具箱 · 设备 | 定位、MediaPlayer、绑定式 Service | 我的 → 工具箱 |
| 工具箱 · 网络 | org.json vs Gson 双解析对比、XmlPullParser 解析 RSS | 我的 → 工具箱 → 网络 |
| 工具箱 · 调试 | 四种启动模式、自定义/有序广播、隐式 Intent | 我的 → 工具箱 → 调试面板 |

## 快速开始

```bash
# 1. 数据库（MySQL 8 系统服务；schema 与账号见 server/docs/schema.sql）
#    专用 schema: keyou_e1，低权限账号 keyou（口令在 server/src/main/resources/db.properties，不进 git）

# 2. 服务端：打包 WAR → ROOT 部署 → Tomcat 8081 → 探活 /api/ping
bash scripts/deploy.sh

# 3. 客户端（双模块）
gradlew.bat assembleDebug -I aliyun-init.gradle
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r reader/build/outputs/apk/debug/reader-debug.apk
```

- 后端基址：模拟器内 `http://10.0.2.2:8081`（首选），**失败自动回退 `http://127.0.0.1:8081`**
  （需 `adb reverse tcp:8081 tcp:8081`，`scripts/start-server.cmd` 会在有设备时自动执行，
  重连/重启模拟器后跑一次 `scripts/connect-device.cmd`）
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
