package jp.cranberry.meishi;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 台帳（Google Apps Script）との通信。通信するので別スレッドで呼ぶ。 */
public class Api {
    /** 名刺台帳システム（GAS）のウェブアプリURL */
    static final String ENDPOINT = "https://script.google.com/macros/s/AKfycbwNKzGJCsD-wg4MIfaJ-EHCfjIEjz1Uf-cZsnlyITRMl0Z6stguFEqtKVn29VW0onSbJQ/exec";

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** JSONを送り、結果のJSONを返す。失敗時は {ok:false, error:"…"} を返す。 */
    public static JSONObject post(JSONObject body) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(20000);
            con.setReadTimeout(120000);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "text/plain;charset=utf-8");
            OutputStream o = con.getOutputStream();
            o.write(body.toString().getBytes(StandardCharsets.UTF_8));
            o.close();
            int code = con.getResponseCode();
            String txt;
            if (code == 301 || code == 302 || code == 303 || code == 307) {
                String loc = con.getHeaderField("Location");
                con.disconnect();
                HttpURLConnection g = (HttpURLConnection) new URL(loc).openConnection();
                g.setConnectTimeout(20000);
                g.setReadTimeout(120000);
                txt = read(g.getInputStream());
                g.disconnect();
            } else {
                txt = read(con.getInputStream());
                con.disconnect();
            }
            return new JSONObject(txt);
        } catch (Exception e) {
            JSONObject r = new JSONObject();
            try {
                r.put("ok", false);
                r.put("error", "通信できませんでした。電波の良い場所でもう一度お試しください。");
            } catch (Exception ignored) {
            }
            return r;
        }
    }
}
