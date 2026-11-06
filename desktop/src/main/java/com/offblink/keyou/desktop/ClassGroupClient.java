package com.offblink.keyou.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

/**
 * 课友班级群 —— 电脑端客户端。
 *
 * 两件事：发布公告、发消息 / 发文件。零第三方依赖（JDK 17 + Swing + java.net.http）。
 *
 * 用法：
 *   java -cp out com.offblink.keyou.desktop.ClassGroupClient
 *   java -cp out com.offblink.keyou.desktop.ClassGroupClient --check http://127.0.0.1:8081
 */
public final class ClassGroupClient extends JFrame {

    private static final String DEFAULT_BASE = "http://127.0.0.1:8081";
    private static final int POLL_INTERVAL_MS = 3000;
    private static final int NOTICE_SIZE = 50;
    private static final int MESSAGE_SIZE = 100;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Color OK_COLOR = new Color(0x1B7F3B);
    private static final Color WARN_COLOR = new Color(0xA16A00);
    private static final Color ERR_COLOR = new Color(0xC0392B);
    private static final Color IDLE_COLOR = new Color(0x666666);

    // ---- 网络与状态
    private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "classgroup-net");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private final Timer timer = new Timer(POLL_INTERVAL_MS, e -> refreshNow());

    private volatile Api api;
    private volatile boolean connected;
    private volatile String dbName = "";
    private volatile long lastMessageId = 0L;
    private volatile boolean netHealthy = true;
    private String lastError = "";
    private volatile List<Api.Notice> notices = new ArrayList<>();

    // ---- 顶部
    private final JTextField serverField = new JTextField(DEFAULT_BASE, 30);
    private final JButton connectBtn = new JButton("连接");
    private final JLabel connLabel = new JLabel("未连接");
    private final JLabel statusLabel = new JLabel("就绪");

    // ---- 发布公告
    private final JTextField authorField = new JTextField("电脑端", 10);
    private final JTextField titleField = new JTextField(36);
    private final JTextArea contentArea = new JTextArea(6, 60);
    private final JButton publishBtn = new JButton("发布");
    private final DefaultTableModel noticeModel =
            readonlyModel(new String[]{"标题", "作者", "时间"});
    private final JTable noticeTable = new JTable(noticeModel);
    private final JTextArea noticeDetail = new JTextArea();

    // ---- 班级群
    private final JTextField senderField = new JTextField("电脑端", 10);
    private final JTextField msgField = new JTextField(40);
    private final JButton sendBtn = new JButton("发送");
    private final JTextArea chatArea = new JTextArea();
    private final JButton pickBtn = new JButton("选择文件…");
    private final JLabel fileLabel = new JLabel("（未选择）");
    private final JButton uploadBtn = new JButton("上传");
    private final DefaultTableModel fileModel =
            readonlyModel(new String[]{"文件名", "大小", "上传人", "时间"});
    private final JTable fileTable = new JTable(fileModel);
    private File selectedFile;

    // ================================================================ 入口

    public static void main(String[] args) {
        if (args.length >= 1 && ("--check".equals(args[0]) || "-c".equals(args[0]))) {
            String base = args.length >= 2 ? args[1] : DEFAULT_BASE;
            runCheck(base);
            return;
        }
        SwingUtilities.invokeLater(() -> new ClassGroupClient().start());
    }

    /** 无界面自检：只打一行结果，绝不建窗口。 */
    private static void runCheck(String base) {
        try {
            Api api = new Api(base);
            String db = api.ping().db();
            System.out.println("OK " + db);
            System.exit(0);
        } catch (Exception e) {
            System.out.println("FAIL " + describe(e));
            System.exit(1);
        }
    }

    // ================================================================ 构造

    private ClassGroupClient() {
        super("课友班级群 · 电脑端");
        initLook();
        buildUi();
    }

    private void start() {
        setVisible(true);
        connect();
    }

    private static void initLook() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 回退默认外观即可
        }
        Font ui = new Font("Microsoft YaHei UI", Font.PLAIN, 13);
        String[] keys = {
            "Label.font", "Button.font", "TextField.font", "TextArea.font", "TextPane.font",
            "Table.font", "TableHeader.font", "TabbedPane.font", "List.font", "Panel.font",
            "TitledBorder.font", "CheckBox.font", "RadioButton.font", "ComboBox.font",
            "OptionPane.font", "FileChooser.font", "ToolTip.font", "Menu.font", "MenuItem.font"
        };
        for (String key : keys) {
            UIManager.put(key, ui);
        }
        UIManager.put("TabbedPane.tabInsets", new javax.swing.plaf.InsetsUIResource(8, 18, 8, 18));
    }

    private void buildUi() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(BorderFactory.createEmptyBorder(6, 8, 0, 8));
        tabs.addTab("发布公告", buildNoticeTab());
        tabs.addTab("班级群", buildChatTab());
        add(tabs, BorderLayout.CENTER);

        add(buildStatusBar(), BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                timer.stop();
                pool.shutdownNow();
                dispose();
                System.exit(0);
            }
        });

        setSize(1010, 780);
        setMinimumSize(new Dimension(880, 640));
        setLocationRelativeTo(null);

        // 未连接前禁止提交
        publishBtn.setEnabled(false);
        sendBtn.setEnabled(false);
        pickBtn.setEnabled(false);
        uploadBtn.setEnabled(false);
    }

    // ================================================================ 界面

    private JPanel buildHeader() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xD5D5D5)),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        p.add(new JLabel("服务器："));
        serverField.setToolTipText("例如 http://127.0.0.1:8081");
        serverField.addActionListener(e -> connect());
        p.add(serverField);

        connectBtn.addActionListener(e -> connect());
        p.add(connectBtn);

        connLabel.setForeground(IDLE_COLOR);
        p.add(connLabel);
        return p;
    }

    private JPanel buildNoticeTab() {
        JPanel form = new JPanel(new BorderLayout(6, 8));
        form.setBorder(BorderFactory.createTitledBorder("新建公告"));

        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        line.add(new JLabel("作者："));
        line.add(authorField);
        line.add(new JLabel("标题："));
        titleField.setPreferredSize(new Dimension(400, 28));
        line.add(titleField);
        form.add(line, BorderLayout.NORTH);

        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        JScrollPane contentScroll = new JScrollPane(contentArea);
        contentScroll.setBorder(BorderFactory.createTitledBorder("正文"));
        form.add(contentScroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        publishBtn.addActionListener(e -> publish());
        actions.add(publishBtn);
        form.add(actions, BorderLayout.SOUTH);

        noticeTable.setRowHeight(24);
        noticeTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        noticeTable.getColumnModel().getColumn(0).setPreferredWidth(460);
        noticeTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        noticeTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        noticeTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedNotice();
            }
        });

        noticeDetail.setEditable(false);
        noticeDetail.setLineWrap(true);
        noticeDetail.setWrapStyleWord(true);
        noticeDetail.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JSplitPane historySplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(noticeTable), new JScrollPane(noticeDetail));
        historySplit.setResizeWeight(0.55);
        historySplit.setDividerSize(8);

        JPanel history = new JPanel(new BorderLayout());
        history.setBorder(BorderFactory.createTitledBorder("历史公告"));
        history.add(historySplit, BorderLayout.CENTER);

        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, form, history);
        main.setResizeWeight(0.44);
        main.setDividerSize(8);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        root.add(main, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildChatTab() {
        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));

        JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        who.add(new JLabel("发送人："));
        who.add(senderField);
        head.add(who, BorderLayout.WEST);

        JPanel text = new JPanel(new BorderLayout(6, 0));
        text.add(new JLabel("消息："), BorderLayout.WEST);
        msgField.addActionListener(e -> send());
        text.add(msgField, BorderLayout.CENTER);
        head.add(text, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        sendBtn.addActionListener(e -> send());
        actions.add(sendBtn);
        head.add(actions, BorderLayout.EAST);

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder("群消息"));

        JPanel fileBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        pickBtn.addActionListener(e -> pickFile());
        uploadBtn.addActionListener(e -> upload());
        fileBar.add(pickBtn);
        fileLabel.setForeground(IDLE_COLOR);
        fileBar.add(fileLabel);
        fileBar.add(uploadBtn);

        fileTable.setRowHeight(24);
        fileTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(420);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(150);

        JPanel filePanel = new JPanel(new BorderLayout(4, 4));
        filePanel.setBorder(BorderFactory.createTitledBorder("群文件"));
        filePanel.add(fileBar, BorderLayout.NORTH);
        filePanel.add(new JScrollPane(fileTable), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chatScroll, filePanel);
        split.setResizeWeight(0.62);
        split.setDividerSize(8);

        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        root.add(head, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildStatusBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xD5D5D5)),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        statusLabel.setForeground(new Color(0x444444));
        p.add(statusLabel, BorderLayout.WEST);
        return p;
    }

    // ================================================================ 连接

    private void connect() {
        String base = serverField.getText().trim();
        if (base.isEmpty()) {
            status("请填写服务器地址", ERR_COLOR);
            serverField.requestFocusInWindow();
            return;
        }
        connectBtn.setEnabled(false);
        connLabel.setText("连接中…");
        connLabel.setForeground(WARN_COLOR);
        status("正在连接 " + base + " …", IDLE_COLOR);

        final Api client;
        try {
            client = new Api(base);
        } catch (Exception e) {
            connectBtn.setEnabled(true);
            connLabel.setText("未连接");
            connLabel.setForeground(IDLE_COLOR);
            status("连接失败：" + describe(e), ERR_COLOR);
            return;
        }

        pool.submit(() -> {
            try {
                String db = client.ping().db();
                SwingUtilities.invokeLater(() -> onConnected(client, db));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> onConnectFailed(e));
            }
        });
    }

    private void onConnected(Api client, String db) {
        api = client;
        connected = true;
        dbName = db;
        netHealthy = true;
        lastError = "";
        lastMessageId = 0L;

        serverField.setText(client.base());
        connLabel.setText("已连接 · " + db);
        connLabel.setForeground(OK_COLOR);
        connectBtn.setText("重新连接");
        connectBtn.setEnabled(true);
        publishBtn.setEnabled(true);
        sendBtn.setEnabled(true);
        pickBtn.setEnabled(true);
        uploadBtn.setEnabled(true);

        notices = new ArrayList<>();
        noticeModel.setRowCount(0);
        noticeDetail.setText("");
        fileModel.setRowCount(0);
        chatArea.setText("");

        status("已连接 " + client.base() + "（" + db + "）", OK_COLOR);
        if (!timer.isRunning()) {
            timer.start();
        }
        refreshNow();
    }

    private void onConnectFailed(Exception e) {
        api = null;
        connected = false;
        timer.stop();
        connLabel.setText("连接失败");
        connLabel.setForeground(ERR_COLOR);
        connectBtn.setEnabled(true);
        publishBtn.setEnabled(false);
        sendBtn.setEnabled(false);
        pickBtn.setEnabled(false);
        uploadBtn.setEnabled(false);
        status("连接失败：" + describe(e), ERR_COLOR);
    }

    // ================================================================ 刷新

    private void refreshNow() {
        if (!connected || api == null) {
            return;
        }
        if (!refreshing.compareAndSet(false, true)) {
            return; // 上一轮还没回来，跳过这次
        }
        final Api client = api;
        pool.submit(() -> {
            try {
                // 三个接口各自刷新：某一个挂了（如表未建好）不拖累另外两个
                List<Api.Notice> ns = null;
                List<Api.FileInfo> fs = null;
                List<Api.Message> ms = null;
                Exception first = null;
                try {
                    ns = client.notices(NOTICE_SIZE);
                } catch (Exception e) {
                    first = e;
                }
                try {
                    fs = client.files();
                } catch (Exception e) {
                    if (first == null) {
                        first = e;
                    }
                }
                try {
                    ms = client.messages(lastMessageId, MESSAGE_SIZE);
                } catch (Exception e) {
                    if (first == null) {
                        first = e;
                    }
                }
                final List<Api.Notice> fns = ns;
                final List<Api.FileInfo> ffs = fs;
                final List<Api.Message> fms = ms;
                final Exception failure = first;
                SwingUtilities.invokeLater(() -> {
                    if (fns != null) {
                        applyNotices(fns);
                    }
                    if (ffs != null) {
                        applyFiles(ffs);
                    }
                    if (fms != null) {
                        appendMessages(fms);
                    }
                    if (failure == null) {
                        onNetOk();
                    } else {
                        onNetError(failure);
                    }
                });
            } finally {
                refreshing.set(false);
            }
        });
    }

    private void applyNotices(List<Api.Notice> list) {
        List<Object[]> rows = new ArrayList<>(list.size());
        for (Api.Notice n : list) {
            rows.add(new Object[]{n.title(), n.author(), Api.displayTime(n.createdAt())});
        }
        notices = list;
        if (sameRows(noticeModel, rows)) {
            return; // 数据没变就别动表格，避免选中行与滚动位置每 3 秒跳一次
        }
        int keep = noticeTable.getSelectedRow();
        fill(noticeModel, rows);
        if (keep >= 0 && keep < rows.size()) {
            noticeTable.setRowSelectionInterval(keep, keep);
        } else {
            noticeDetail.setText("");
        }
    }

    private void applyFiles(List<Api.FileInfo> list) {
        List<Object[]> rows = new ArrayList<>(list.size());
        for (Api.FileInfo f : list) {
            rows.add(new Object[]{
                f.name(), sizeText(f.size()), f.uploader(), Api.displayTime(f.uploadedAt())
            });
        }
        if (sameRows(fileModel, rows)) {
            return;
        }
        fill(fileModel, rows);
    }

    private static boolean sameRows(DefaultTableModel model, List<Object[]> rows) {
        if (model.getRowCount() != rows.size()) {
            return false;
        }
        for (int i = 0; i < rows.size(); i++) {
            Object[] want = rows.get(i);
            for (int c = 0; c < want.length; c++) {
                if (!Objects.equals(model.getValueAt(i, c), want[c])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void fill(DefaultTableModel model, List<Object[]> rows) {
        model.setRowCount(0);
        for (Object[] row : rows) {
            model.addRow(row);
        }
    }

    private void appendMessages(List<Api.Message> list) {
        if (list.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        boolean grew = false;
        for (Api.Message m : list) {
            sb.append(Api.displayTime(m.createdAt())).append(' ')
              .append(m.sender()).append("：").append(m.content()).append('\n');
            if (m.id() > lastMessageId) {
                lastMessageId = m.id();
                grew = true;
            }
        }
        if (!grew) {
            return;
        }
        chatArea.append(sb.toString());
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void onNetOk() {
        lastError = "";
        if (!netHealthy) {
            netHealthy = true;
            connLabel.setText(dbName.isEmpty() ? "已连接" : "已连接 · " + dbName);
            connLabel.setForeground(OK_COLOR);
            status("连接已恢复", OK_COLOR);
        }
    }

    private void onNetError(Exception e) {
        String text = describe(e);
        // 同一错误反复出现时只写一次状态栏，避免每 3 秒刷屏
        if (!text.equals(lastError)) {
            status("刷新失败：" + text, ERR_COLOR);
            lastError = text;
        }
        boolean transport = e instanceof Api.ApiException ae && ae.transport();
        if (transport && netHealthy) {
            netHealthy = false;
            connLabel.setText("连接中断，重试中…");
            connLabel.setForeground(ERR_COLOR);
        }
    }

    private void showSelectedNotice() {
        int row = noticeTable.getSelectedRow();
        List<Api.Notice> list = notices;
        if (row < 0 || row >= list.size()) {
            noticeDetail.setText("");
            return;
        }
        Api.Notice n = list.get(row);
        noticeDetail.setText("标题：" + n.title()
                + "\n作者：" + n.author()
                + "      时间：" + Api.displayTime(n.createdAt())
                + "\n\n" + n.content());
        noticeDetail.setCaretPosition(0);
    }

    // ================================================================ 动作

    private void publish() {
        if (!requireConnected()) {
            return;
        }
        String title = titleField.getText().trim();
        String content = contentArea.getText().trim();
        String author = authorField.getText().trim();
        if (title.isEmpty()) {
            status("标题不能为空", ERR_COLOR);
            titleField.requestFocusInWindow();
            return;
        }
        if (content.isEmpty()) {
            status("正文不能为空", ERR_COLOR);
            contentArea.requestFocusInWindow();
            return;
        }
        if (author.isEmpty()) {
            author = "电脑端";
            authorField.setText(author);
        }

        final Api client = api;
        final String a = author;
        publishBtn.setEnabled(false);
        status("正在发布公告…", IDLE_COLOR);
        pool.submit(() -> {
            try {
                Api.Notice n = client.createNotice(title, content, a);
                SwingUtilities.invokeLater(() -> {
                    publishBtn.setEnabled(true);
                    titleField.setText("");
                    contentArea.setText("");
                    status("公告已发布：" + n.title(), OK_COLOR);
                    refreshNow();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    publishBtn.setEnabled(true);
                    status("发布失败：" + describe(e), ERR_COLOR);
                });
            }
        });
    }

    private void send() {
        if (!requireConnected()) {
            return;
        }
        String content = msgField.getText().trim();
        String sender = senderField.getText().trim();
        if (content.isEmpty()) {
            status("消息内容不能为空", ERR_COLOR);
            msgField.requestFocusInWindow();
            return;
        }
        if (sender.isEmpty()) {
            sender = "电脑端";
            senderField.setText(sender);
        }

        final Api client = api;
        final String s = sender;
        sendBtn.setEnabled(false);
        status("正在发送…", IDLE_COLOR);
        pool.submit(() -> {
            try {
                client.createMessage(s, content);
                SwingUtilities.invokeLater(() -> {
                    sendBtn.setEnabled(true);
                    msgField.setText("");
                    msgField.requestFocusInWindow();
                    status("已发送", OK_COLOR);
                    refreshNow();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    sendBtn.setEnabled(true);
                    status("发送失败：" + describe(e), ERR_COLOR);
                });
            }
        });
    }

    private void pickFile() {
        if (!requireConnected()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择要上传的文件");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = chooser.getSelectedFile();
            if (selectedFile != null) {
                String name = selectedFile.getName();
                String shown = name.length() > 32 ? name.substring(0, 30) + "…" : name;
                fileLabel.setText(shown + "（" + sizeText(selectedFile.length()) + "）");
                fileLabel.setToolTipText(selectedFile.getAbsolutePath());
                fileLabel.setForeground(new Color(0x222222));
                status("已选择 " + selectedFile.getName(), IDLE_COLOR);
            }
        }
    }

    private void upload() {
        if (!requireConnected()) {
            return;
        }
        final File f = selectedFile;
        if (f == null || !f.isFile()) {
            status("请先选择文件", ERR_COLOR);
            return;
        }
        String uploader = senderField.getText().trim();
        if (uploader.isEmpty()) {
            uploader = "电脑端";
        }
        if (f.length() > 50L * 1024 * 1024) {
            status("文件过大：" + sizeText(f.length()) + "，服务端上限 50MB", ERR_COLOR);
            return;
        }

        final Api client = api;
        final String up = uploader;
        final Path path = f.toPath();
        uploadBtn.setEnabled(false);
        status("正在上传 " + f.getName() + " …", IDLE_COLOR);
        pool.submit(() -> {
            try {
                Api.FileInfo info = client.upload(path, up);
                SwingUtilities.invokeLater(() -> {
                    uploadBtn.setEnabled(true);
                    selectedFile = null;
                    fileLabel.setText("（未选择）");
                    fileLabel.setForeground(IDLE_COLOR);
                    fileLabel.setToolTipText(null);
                    status("上传成功：" + info.name() + "（" + sizeText(info.size()) + "）", OK_COLOR);
                    refreshNow();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    uploadBtn.setEnabled(true);
                    status("上传失败：" + describe(e), ERR_COLOR);
                });
            }
        });
    }

    // ================================================================ 工具

    private boolean requireConnected() {
        if (!connected || api == null) {
            status("请先连接服务器", ERR_COLOR);
            return false;
        }
        return true;
    }

    private void status(String text, Color color) {
        statusLabel.setText("[" + LocalTime.now().format(CLOCK) + "] " + text);
        statusLabel.setForeground(color);
    }

    private static String describe(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) {
            m = e.getClass().getSimpleName();
        }
        return m;
    }

    private static String sizeText(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static DefaultTableModel readonlyModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
}
