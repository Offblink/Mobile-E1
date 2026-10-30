# 课友（Keyou）· 移动应用开发 实验一

《移动应用开发（AI 赋能版）》实验一「AI 赋能 Android 原生开发综合实验」的个人项目：
**班级同学录 + 班级聊天 App（Android 原生 Java）× SSM 后端（Spring + SpringMVC + MyBatis + MySQL 8）**。

- 要求依据：[docs/实验一-要求整理.md](docs/实验一-要求整理.md)（逐任务提取自实验指导书）
- 开发规格：[docs/SPEC.md](docs/SPEC.md)（任务 → 功能映射、API 契约、UI 设计、增量计划）

## 仓库结构

```
app/        主 App（com.offblink.keyou）—— 登录/主页/同学录/课友圈/实验室/工具箱
reader/     任务 6 的第二 App（com.offblink.keyou.reader，跨 App 读 ContentProvider）
server/     SSM 后端（Maven WAR：Spring 5.3 + MyBatis + MySQL 8）
scripts/    deploy.sh（打包→ROOT 部署→8081 起停→探活）
docs/       要求整理、SPEC、报告素材
tools/      本地 Tomcat 副本（不进 git）
```

## 任务 → 功能对照（现场演示入口）

| 任务 | 功能 | 演示入口 |
|---|---|---|
| 1 | Log 分级（v/d/i/w/e）+ TAG 约定 `Keyou/页面名` | 主页 → 关于 → 「输出五级日志」 |
| 2 | 全生命周期日志 / Bundle 多类型传参 / registerForActivityResult 回传 / 四种启动模式 / 隐式 Intent | 登录→主页观察 Logcat；我的 → 编辑昵称；主页 → 实验室 |
| 3 | 四大布局 + 七控件登录页 / RecyclerView / 左右气泡聊天室 | 登录页；同学录 tab；课友圈 tab |
| 4 | 网络变化动态广播横幅 / 自定义广播（静态+动态对比）/ 有序广播截断 | 切 Wi-Fi 看主页横幅；实验室 → 广播三按钮 |
| 5 | 文件草稿箱 / SP 记住密码 / SQLite 通讯录（版本升级/事务/模糊查询/CRUD） | 同学录 tab → 草稿箱；登录勾记住密码；同学录列表 |
| 6 | READ_CONTACTS 运行时权限三态流程 / 自定义 Provider / 跨 App 读取 | 同学录 → 「导入系统联系人」；装 reader App 点读取 |
| 7 | HttpUtil 封装 / org.json vs Gson 双解析 / XmlPullParser RSS | 实验室 → JSON 双解析对比 / RSS 解析 |
| 8 | 前台服务下载+通知 / 绑定式 Service / 定位 / MediaPlayer / 相机缩略图 | 我的 → 下载中心 / 工具箱 |
| 9 | SSM 整合：分页列表 / POST JSON 新增 / PUT 编辑 / 长按删除 / 加载中/空态/异常提示 | 主页 → 班级成员 |

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

- 后端基址：模拟器内 `http://10.0.2.2:8081`（见 `JsonCompareActivity.BASE_URL`），宿主机 `http://127.0.0.1:8081`
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
