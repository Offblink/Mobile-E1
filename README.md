# 课友（Keyou）· 移动应用开发 实验一

《移动应用开发（AI 赋能版）》实验一「AI 赋能 Android 原生开发综合实验」的个人项目：
**班级同学录 + 班级聊天 App（Android 原生 Java）× SSM 后端（Spring + SpringMVC + MyBatis + MySQL 8）**。

- 要求依据：[docs/实验一-要求整理.md](docs/实验一-要求整理.md)
- 开发规格：[docs/SPEC.md](docs/SPEC.md)（任务 → 功能映射、API 契约、UI 设计、增量计划）

## 仓库结构

```
app/        主 App（com.offblink.keyou）
reader/     任务 6 的第二 App（跨 App 读 ContentProvider）
server/     SSM 后端（Maven WAR）
scripts/    启动 / 部署脚本
docs/       要求整理、SPEC、报告素材
tools/      本地 Tomcat 副本（不进 git）
```

## 快速开始

```bash
# 服务端（需本机 MySQL 8 服务在跑；口令见 server/src/main/resources/db.properties）
bash scripts/deploy.sh          # 打包 WAR → 部署 ROOT → 起 Tomcat(8081) → 探活 /api/ping

# 客户端
gradlew.bat assembleDebug -I aliyun-init.gradle
```

- 后端基址：`http://127.0.0.1:8081`（模拟器内用 `http://10.0.2.2:8081`）
- 接口约定：统一 `{code, message, data}`，详见 SPEC §5
