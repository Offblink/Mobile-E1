package com.offblink.keyou;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.List;

/**
 * JSON 解析对比 —— 同一接口数据分别用 org.json 与 Gson 解析。
 *
 * 对比结论（写报告素材）：
 * - org.json：系统自带零依赖，手写 getString/getLong，字段多时代码冗长、
 *   类型错了运行时才发现（JSONException）；
 * - Gson：三方库，反射映射实体类一行代码，类型安全在编译期/解析期集中报错；
 *   实体类字段与 JSON 对不上时静默为 null（要靠日志发现），两者适用场景见思考题。
 *
 * 数据来自 server /api/mates（统一 {code,message,data:{list,total}}）。
 */
public class JsonCompareActivity extends KeyouActivity {

    private static final String TAG = "Keyou/JsonCompare";

    /** 服务端地址由 ServerConfig 统一管理（10.0.2.2 优先，失败回退 127.0.0.1） */

    private TextView tvOrgJson;
    private TextView tvGson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_json_compare);
        AppLog.d(TAG, "onCreate");

        tvOrgJson = findViewById(R.id.tv_orgjson_result);
        tvGson = findViewById(R.id.tv_gson_result);

        Button btnFetch = findViewById(R.id.btn_fetch_parse);
        btnFetch.setOnClickListener(v -> fetchAndParse());
    }

    private void fetchAndParse() {
        tvOrgJson.setText(R.string.json_parsing);
        tvGson.setText(R.string.json_parsing);
        HttpUtil.get(this, "/api/mates", new HttpUtil.Callback() {
            @Override
            public void onSuccess(String body) {
                long t0 = System.nanoTime();
                String orgResult = parseByOrgJson(body);
                long t1 = System.nanoTime();
                String gsonResult = parseByGson(body);
                long t2 = System.nanoTime();

                tvOrgJson.setText(orgResult + "\n\n耗时 " + (t1 - t0) / 1000 + " μs");
                tvGson.setText(gsonResult + "\n\n耗时 " + (t2 - t1) / 1000 + " μs");
                AppLog.i(TAG, "org.json " + (t1 - t0) / 1000 + "μs, Gson " + (t2 - t1) / 1000 + "μs");
            }

            @Override
            public void onError(String message, Exception e) {
                tvOrgJson.setText(getString(R.string.json_fetch_failed, message));
                tvGson.setText(getString(R.string.json_fetch_failed, message));
            }
        });
    }

    /** org.json 解析：手写逐字段取值 */
    private String parseByOrgJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject data = root.getJSONObject("data");
            JSONArray array = data.getJSONArray("list");
            StringBuilder sb = new StringBuilder("共 ").append(array.length()).append(" 条\n");
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                // 字段名/类型写错在这里抛 JSONException —— 运行时才发现
                sb.append(item.getLong("id")).append(' ')
                        .append(item.getString("name")).append(' ')
                        .append(item.optString("phone", "-")).append('\n');
            }
            return sb.toString().trim();
        } catch (Exception e) {
            AppLog.e(TAG, "org.json 解析失败", e);
            return "解析失败: " + e.getMessage();
        }
    }

    /** Gson 解析：反射映射实体类 */
    private String parseByGson(String json) {
        try {
            JSONObject data = new JSONObject(json).getJSONObject("data");
            Type type = new TypeToken<List<Mate>>() {
            }.getType();
            List<Mate> mates = new Gson().fromJson(data.getJSONArray("list").toString(), type);
            StringBuilder sb = new StringBuilder("共 ").append(mates.size()).append(" 条\n");
            for (Mate m : mates) {
                sb.append(m.getId()).append(' ').append(m.brief()).append('\n');
            }
            return sb.toString().trim();
        } catch (Exception e) {
            AppLog.e(TAG, "Gson 解析失败", e);
            return "解析失败: " + e.getMessage();
        }
    }
}
