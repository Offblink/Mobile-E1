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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
 * 课友 · 电脑端班级群客户端（Compose Multiplatform 桌面端）。
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
        Window(onCloseRequest = ::exitApplication, title = "课友 · 班级群（电脑端）") {
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

    val scope = rememberCoroutineScope()

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
                messages = messages,
                files = files,
                onSent = { m ->
                    messages = messages + m
                    lastMessageId = maxOf(lastMessageId, m.id)
                    statusOk = true
                    status = "已发送"
                },
                onUploaded = { f ->
                    files = listOf(f) + files
                    statusOk = true
                    status = "已上传 " + f.name
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
    var author by remember { mutableStateOf("电脑端") }
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
    messages: List<Api.Message>,
    files: List<Api.FileInfo>,
    onSent: (Api.Message) -> Unit,
    onUploaded: (Api.FileInfo) -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var sender by remember { mutableStateOf("电脑端") }
    var input by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 消息区
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(messages) { m ->
                    Text(
                        "${m.createdAt()}  ${m.sender()}：${m.content()}",
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    )
                }
            }
            if (messages.isEmpty()) {
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
                            val sent = io { client.createMessage(sender.ifBlank { "电脑端" }, text) }
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
                            val uploaded = io { client.upload(file.toPath(), sender.ifBlank { "电脑端" }) }
                            chosen = null
                            onUploaded(uploaded)
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

        Text("群文件 · ${files.size} 个", fontSize = 12.sp)
        LazyColumn(modifier = Modifier.height(140.dp).fillMaxWidth()) {
            items(files) { f ->
                Text(
                    "${f.name()}  ·  ${humanSize(f.size())}  ·  ${f.uploader()}  ·  ${f.uploadedAt()}",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 工具

private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

private fun humanSize(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
    else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
}
