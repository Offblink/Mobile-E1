# 实验一报告素材

按报告要求（每任务 ≥1 张截图）预留的素材清单。截图由本人上机验收时采集，命名按任务号。

## 截图清单（每项 1 张起）

| 任务 | 建议截图内容 |
|---|---|
| 1 | Logcat 按 `Keyou` 过滤，显示 v/d/i/w/e 五级日志 |
| 2 | 登录→主页 Logcat 生命周期序列（onCreate→onResume）；启动模式 standard 反复点开后 Logcat 多实例 hashCode vs singleTop onNewIntent |
| 3 | 登录页（七控件）；同学录列表；聊天室左右气泡 |
| 4 | 切 Wi-Fi 后主页横幅；Logcat Keyou/Broadcast 三组 TAG；有序广播截断前后对比（OrderedLow 有/无日志） |
| 5 | 草稿箱保存读回；SP 预填；SQLite 列表查询/编辑/删除；Logcat ContactDao 事务日志 |
| 6 | READ_CONTACTS 权限弹窗与授权后导入；reader App 跨 App 读到数据 |
| 7 | JSON 双解析对比页（两栏结果+耗时）；RSS 解析页三条公告 |
| 8 | 下载中心进度通知；绑定服务生成便签；定位经纬度；相机缩略图 |
| 9 | 班级成员分页列表；新增表单；长按删除确认；加载中对话框；空态提示 |

## 数据库截图

- `mysql -ukeyou -p keyou_e1` 查 mate 表种子数据（或任意 GUI 客户端连 127.0.0.1:3306）

## 联调问题记录（报告「错误与改正」素材，来自真实踩坑）

1. **静态文件 404**：DispatcherServlet 映射 `/` 后接管所有请求，默认 servlet 不再服务静态资源 →
   spring-mvc.xml 显式 `<mvc:resources mapping="/files/**" location="/files/"/>`。
2. **Spring 子容器缺 JdbcTemplate bean**：PingController 启动即 500（NoSuchBeanDefinitionException）→
   根容器声明 jdbcTemplate，子容器继承。
3. **mapperLocations 找不到 mapper/ 目录**：P0 骨架期 MyBatis 尚无 XML，启动报
   "class path resource [mapper/] cannot be resolved" → 任务 9 引入 Mapper 后恢复该配置。
4. **布局 id 少 `@+id/` 前缀**：`android:id="tv_hello"` 直接写名字 → Android resource linking failed，
   改 `@+id/tv_hello`。
5. **INTERNET 权限遗漏**：任务 7 网络页首次联调全线失败（SecurityException）→ Manifest 补
   `android.permission.INTERNET`（ACCESS_NETWORK_STATE 不替代它）。
6. **RecyclerView 没有 setOnItemLongClickListener**：长按删除初版直接编译失败 → 长按事件挂在
   itemView 的 setOnLongClickListener，适配器暴露回调接口。

## AI 使用记录要点

- AI 生成代码均逐行核对：SQL 全参数化（#{} / ContentValues）、Cursor try-with-resources、
  动态广播成对注销、事务 setTransactionSuccessful —— 上线前按指导书排错表逐项检查过。
- 指导书样例的过时 API 均做了现代化适配并保留教学注释：CONNECTIVITY_ACTION（只作触发信号，
  真实连通性用 getNetworkCapabilities）、IntentService → 前台 Service、
  startActivityForResult → registerForActivityResult。
- 教材 SQLite 样例的版本升级坑（onCreate 建 v1 表 + VERSION=2 导致 gender 列永不出现）已修正，
  见 `ContactDBHelper` 注释。
