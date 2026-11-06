package com.offblink.keyou.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import kotlin.system.exitProcess

/**
 * 课友 · 桌面端班级群客户端（Compose Multiplatform 桌面端）。
 *
 * 只做两件事：发布公告、发消息 / 发文件；服务端是本机 SSM（默认 8081）。
 * 每 3 秒自动刷新公告 / 消息 / 文件列表；网络请求全部在 IO 线程。
 */
private const val POLL_INTERVAL_MS = 3_000L

private val OK_COLOR = Color(0xFF2E7D32)
private val ERR_COLOR = Color(0xFFC62828)

fun main(args: Array<String>) {
    // 无界面自检：--check <baseUrl> 打印 OK <db> / FAIL <原因>，不开窗口
    val checkIndex = args.indexOf("--check").let { if (it < 0) args.indexOf("-c") else it }
    if (checkIndex >= 0) {
        val base = args.getOrNull(checkIndex + 1) ?: "http://127.0.0.1:8081"
        exitProcess(runCheck(base))
    }

    application {
        Window(onCloseRequest = ::exitApplication, title = "课友 · 班级群（桌面端）") {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

private fun runCheck(base: String): Int = try {
    val api = Api(base)
    println("OK " + api.ping().db())
    0
} catch (e: Api.ApiException) {
    println("FAIL " + e.message)
    1
} catch (e: Exception) {
    println("FAIL " + (e.message ?: e.toString()))
    1
}

// ---------------------------------------------------------------- 界面

@Composable
private fun App() {
    var address by remember { mutableStateOf("http://127.0.0.1:8081") }
    var api by remember { mutableStateOf<Api?>(null) }
    var status by remember { mutableStateOf("未连接") }
    var statusOk by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }

    var notices by remember { mutableStateOf<List<Api.Notice>>(emptyList()) }
    var messages by remember { mutableStateOf<List<Api.Message>>(emptyList()) }
    var files by remember { mutableStateOf<List<Api.FileInfo>>(emptyList()) }
    var lastMessageId by remember { mutableStateOf(0L) }
    /** 本机有副本的文件名 → 本地文件（自己传的原件 / 下载过的），点卡片直接打开 */
    val localCopies = remember { mutableStateMapOf<String, File>() }

    val scope = rememberCoroutineScope()

    /**
     * 消息 + 文件合成一条时间线（与手机端 ChatTimeline 同口径）：
     * 两边时间都是 "yyyy-MM-dd HH:mm" 文本，字典序即时间序；同分钟用稳定排序、文本在前。
     */
    val entries: List<ChatEntry> = remember(messages, files) {
        val all = ArrayList<ChatEntry>(messages.size + files.size)
        messages.forEach { all.add(ChatEntry.Text(it)) }
        files.forEach { all.add(ChatEntry.Card(it)) }
        all.sortedBy { it.time }
    }

    suspend fun refreshAll(api: Api) {
        try {
            notices = io { api.notices(50) }
            messages = io { api.messages(lastMessageId, 100) }
            if (messages.isNotEmpty()) lastMessageId = messages.maxOf { it.id }
            files = io { api.files() }
            statusOk = true
        } catch (e: Exception) {
            statusOk = false
            status = "刷新失败：" + (e.message ?: e.toString())
        }
    }

    fun connect() {
        scope.launch {
            try {
                val created = io { Api(address) }
                val ping = io { created.ping() }
                api = created
                statusOk = true
                status = "已连接 · " + ping.db()
                lastMessageId = 0
                refreshAll(created)
            } catch (e: Exception) {
                api = null
                statusOk = false
                status = "连接失败：" + (e.message ?: e.toString())
            }
        }
    }

    // 连上之后每 3 秒刷新一次（增量拉消息）
    LaunchedEffect(api) {
        val current = api ?: return@LaunchedEffect
        while (true) {
            delay(POLL_INTERVAL_MS)
            try {
                val fresh = io { current.messages(lastMessageId, 100) }
                if (fresh.isNotEmpty()) {
                    messages = messages + fresh
                    lastMessageId = fresh.maxOf { it.id }
                }
                notices = io { current.notices(50) }
                files = io { current.files() }
                statusOk = true
            } catch (e: Exception) {
                statusOk = false
                status = "刷新失败：" + (e.message ?: e.toString())
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 顶部：地址 + 连接 + 状态
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("服务器") },
                singleLine = true,
                modifier = Modifier.width(320.dp)
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = { connect() }) { Text("连接") }
            Spacer(Modifier.width(16.dp))
            Box(
                modifier = Modifier.size(10.dp).background(
                    if (statusOk) OK_COLOR else ERR_COLOR, CircleShape
                )
            )
            Spacer(Modifier.width(8.dp))
            Text(status, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("发布公告") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("班级群") })
        }

        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> NoticeTab(
                api = api,
                notices = notices,
                onPublished = { n ->
                    notices = listOf(n) + notices
                    statusOk = true
                    status = "公告已发布"
                },
                onError = { msg ->
                    statusOk = false
                    status = msg
                }
            )
            else -> GroupTab(
                api = api,
                entries = entries,
                localCopies = localCopies,
                onSent = { m ->
                    messages = messages + m
                    lastMessageId = maxOf(lastMessageId, m.id)
                    statusOk = true
                    status = "已发送"
                },
                onUploaded = { f, source ->
                    files = listOf(f) + files
                    localCopies[f.name] = source
                    statusOk = true
                    status = "已上传 " + f.name + "（本机与手机端班级群都看得到这张卡片）"
                },
                onDownloaded = { f, target ->
                    localCopies[f.name] = target
                    statusOk = true
                    status = "已下载 " + target.name + " → " + target.parentFile
                },
                onError = { msg ->
                    statusOk = false
                    status = msg
                }
            )
        }
    }
}

// ---------------------------------------------------------------- 公告

@Composable
private fun NoticeTab(
    api: Api?,
    notices: List<Api.Notice>,
    onPublished: (Api.Notice) -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var author by remember { mutableStateOf("桌面端") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Api.Notice?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = author, onValueChange = { author = it },
                label = { Text("发布人") }, singleLine = true,
                modifier = Modifier.width(180.dp)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("标题") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = content, onValueChange = { content = it },
            label = { Text("正文") },
            modifier = Modifier.fillMaxWidth().height(110.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = api != null && !busy && title.isNotBlank(),
                onClick = {
                    val client = api ?: return@Button
                    busy = true
                    scope.launch {
                        try {
                            val created = io { client.createNotice(title.trim(), content.trim(), author.trim()) }
                            title = ""
                            content = ""
                            onPublished(created)
                        } catch (e: Exception) {
                            onError("发布失败：" + (e.message ?: e.toString()))
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "发布中…" else "发布公告") }
            Spacer(Modifier.width(12.dp))
            Text("共 ${notices.size} 条", fontSize = 12.sp)
        }

        Divider(modifier = Modifier.padding(vertical = 10.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(notices) { n ->
                val isSel = selected?.id == n.id
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = n }
                        .background(
                            if (isSel) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    Text(n.title(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "${n.author()} · ${n.createdAt()}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isSel) {
                        Spacer(Modifier.height(6.dp))
                        Text(n.content(), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 班级群

@Composable
private fun GroupTab(
    api: Api?,
    entries: List<ChatEntry>,
    localCopies: MutableMap<String, File>,
    onSent: (Api.Message) -> Unit,
    onUploaded: (Api.FileInfo, File) -> Unit,
    onDownloaded: (Api.FileInfo, File) -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var sender by remember { mutableStateOf("桌面端") }
    var input by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    /** 正在下载中的文件名：卡片显示「下载中…」并挡住重复点 */
    var busyFile by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.size - 1)
    }

    // 卡片点击：本机有副本直接打开；否则下载到「下载」目录，下载完动作变「点击打开」
    val onFileClick: (Api.FileInfo) -> Unit = { file ->
        val local = localCopies[file.name]
        when {
            local != null && local.isFile -> {
                try {
                    java.awt.Desktop.getDesktop().open(local)
                } catch (e: Exception) {
                    onError("打开失败：" + (e.message ?: e.toString()))
                }
            }
            api == null -> onError("先点「连接」再下载文件")
            busyFile != null -> Unit
            else -> {
                val client = api
                busyFile = file.name
                scope.launch {
                    try {
                        val target = File(downloadsDir(), File(file.name).name)
                        // 已经下过且大小对得上就直接认领，不重复下载
                        if (!target.isFile || target.length() != file.size) {
                            io { client.download(file.name, target.toPath()) }
                        }
                        onDownloaded(file, target)
                    } catch (e: Exception) {
                        onError("下载失败：" + (e.message ?: e.toString()))
                    } finally {
                        busyFile = null
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 时间线：文本消息 + 文件卡片（同一条时间线，和手机端一致）
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(entries) { entry ->
                    when (val item = entry) {
                        is ChatEntry.Text -> Text(
                            "${item.m.createdAt()}  ${item.m.sender()}：${item.m.content()}",
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        )
                        is ChatEntry.Card -> FileCard(
                            file = item.f,
                            self = item.f.uploader() == sender,
                            busy = busyFile == item.f.name,
                            local = localCopies[item.f.name]?.isFile == true,
                            onClick = { onFileClick(item.f) }
                        )
                    }
                }
            }
            if (entries.isEmpty()) {
                Text("还没有消息", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Divider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = sender, onValueChange = { sender = it },
                label = { Text("发送人") }, singleLine = true,
                modifier = Modifier.width(150.dp)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                label = { Text("消息") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = api != null && input.isNotBlank(),
                onClick = {
                    val client = api ?: return@Button
                    val text = input.trim()
                    scope.launch {
                        try {
                            val sent = io { client.createMessage(sender.ifBlank { "桌面端" }, text) }
                            input = ""
                            onSent(sent)
                        } catch (e: Exception) {
                            onError("发送失败：" + (e.message ?: e.toString()))
                        }
                    }
                }
            ) { Text("发送") }
        }

        Spacer(Modifier.height(8.dp))

        // 文件区
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val chooser = JFileChooser()
                    chooser.dialogTitle = "选择要上传的文件"
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        chosen = chooser.selectedFile
                    }
                }
            ) { Text("选择文件…") }
            Spacer(Modifier.width(8.dp))
            Text(
                chosen?.name ?: "未选择文件",
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Button(
                enabled = api != null && chosen != null && !busy,
                onClick = {
                    val client = api ?: return@Button
                    val file = chosen ?: return@Button
                    busy = true
                    scope.launch {
                        try {
                            val uploaded = io { client.upload(file.toPath(), sender.ifBlank { "桌面端" }) }
                            chosen = null
                            // 原件留在本机：这张卡片下次点就是「直接打开」而不是重新下载
                            onUploaded(uploaded, file)
                        } catch (e: Exception) {
                            onError("上传失败：" + (e.message ?: e.toString()))
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "上传中…" else "上传") }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            "消息与文件共用一条时间线；文件卡片本机点开，未下载过的先下载",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- 时间线与文件卡片

/** 时间线的一项：文本消息或文件卡片（与手机端 ChatTimeline 同口径） */
private sealed interface ChatEntry {
    val time: String

    data class Text(val m: Api.Message) : ChatEntry {
        override val time: String get() = m.createdAt
    }

    data class Card(val f: Api.FileInfo) : ChatEntry {
        override val time: String get() = f.uploadedAt
    }
}

/** 文件卡片：文件名 / 大小·上传人·时间 / 动作；自己传的靠右并用主题容器色 */
@Composable
private fun FileCard(
    file: Api.FileInfo,
    self: Boolean,
    busy: Boolean,
    local: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = if (self) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .background(
                    if (self) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
                .clickable(enabled = !busy, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                file.name(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                humanSize(file.size()) + " · " + file.uploader() + " · " + file.uploadedAt(),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    busy -> "下载中…"
                    local -> "点击打开"
                    else -> "点击下载"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ---------------------------------------------------------------- 工具

private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

/** 下载落点：用户「下载」目录；不可用时退到临时目录 */
private fun downloadsDir(): File {
    val home = File(System.getProperty("user.home"), "Downloads")
    return if (home.isDirectory) home
    else File(System.getProperty("java.io.tmpdir"), "keyou-downloads")
}

private fun humanSize(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", size / 1024.0)
    else -> String.format(java.util.Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
}
