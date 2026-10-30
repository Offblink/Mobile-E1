package com.offblink.keyou;

import android.os.Bundle;
import android.util.Log;
import android.util.Xml;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;

/**
 * 任务 7：XmlPullParser 解析 RSS（server /api/rss 提供 XML 数据）。
 *
 * 要点：pull 解析是"拉模式"——nextToken()/next() 逐事件推进，
 * 只在需要时读取，内存占用与文档大小无关（对比 SAX 推模式的事件回调）。
 */
public class RssParseActivity extends KeyouActivity {

    private static final String TAG = "Keyou/RssParse";

    private TextView tvRssResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rss_parse);
        Log.d(TAG, "onCreate");

        tvRssResult = findViewById(R.id.tv_rss_result);

        Button btnParse = findViewById(R.id.btn_fetch_rss);
        btnParse.setOnClickListener(v -> fetchAndParse());
    }

    private void fetchAndParse() {
        tvRssResult.setText(R.string.json_parsing);
        HttpUtil.get(this, "/api/rss", new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                tvRssResult.setText(parseRss(body));
            }

            @Override
            public void onError(String message, Exception e) {
                tvRssResult.setText(getString(R.string.json_fetch_failed, message));
            }
        });
    }

    /** XmlPullParser 逐事件解析 <item> 的 title/link */
    private String parseRss(String xml) {
        StringBuilder sb = new StringBuilder();
        String currentTag = null;
        String title = null;
        String link = null;
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xml));
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                switch (event) {
                    case XmlPullParser.START_TAG:
                        currentTag = parser.getName();
                        if ("item".equals(currentTag)) {
                            title = null;
                            link = null;
                        }
                        break;
                    case XmlPullParser.TEXT:
                        if ("title".equals(currentTag) && title == null) {
                            title = parser.getText().trim();
                        } else if ("link".equals(currentTag) && link == null) {
                            link = parser.getText().trim();
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        if ("item".equals(parser.getName()) && title != null) {
                            sb.append("• ").append(title).append('\n')
                                    .append("  ").append(link).append('\n');
                        }
                        currentTag = null;
                        break;
                    default:
                        break;
                }
                event = parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "RSS 解析失败", e);
            return "解析失败: " + e.getMessage();
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "(没有 item)" : result;
    }
}
