package jp.cranberry.meishi;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQ_CAM = 21, REQ_PICK = 22, MAX_PX = 1600;
    private static final int INK = 0xFF152033, MUTED = 0xFF5D6A7E, BG = 0xFFF4F1F2, ACCENT = 0xFFB5123D,
            WARN_BG = 0xFFFFF3CD, WARN = 0xFF8A6100, BAD = 0xFFC9342B;
    private static final String[] SCENES = {"営業訪問", "担当者会議", "カンファレンス", "研修・勉強会", "来所", "その他"};
    private static final Map<String, String> FIELDS = new LinkedHashMap<>();

    static {
        FIELDS.put("name", "氏名");
        FIELDS.put("kana", "ふりがな");
        FIELDS.put("company", "会社・事業所名");
        FIELDS.put("department", "部署");
        FIELDS.put("title", "役職");
        FIELDS.put("tel", "電話");
        FIELDS.put("mobile", "携帯");
        FIELDS.put("fax", "FAX");
        FIELDS.put("email", "メール");
        FIELDS.put("zip", "郵便番号");
        FIELDS.put("address", "住所");
        FIELDS.put("url", "URL");
    }

    private SharedPreferences prefs;
    private LinearLayout box;
    private ScrollView sv;
    private TextView msg;
    private String imageB64, imageBackB64;
    private Bitmap preview, previewBack, lastBitmap;
    private boolean shootingBack;
    private boolean busy;
    private final Map<String, EditText> edits = new LinkedHashMap<>();
    private EditText memoEdit;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("meishi", Context.MODE_PRIVATE);
        sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        box.setPadding(p, p, p, p);
        sv.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
        if (prefs.getString("name", "").isEmpty() || prefs.getString("token", "").isEmpty()) showSetup();
        else showHome();
    }

    private boolean updateAsked;

    @Override
    protected void onResume() {
        super.onResume();
        if (!updateAsked) checkUpdateDialog();
    }

    /** 新しい版があれば、どの画面にいても更新を案内する（初回設定の画面でも更新できるように）。 */
    private void checkUpdateDialog() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String[] v = Updater.latest();
                if (v == null) return;
                long code;
                try {
                    code = Long.parseLong(v[0]);
                } catch (Exception e) {
                    return;
                }
                if (code <= Updater.currentCode(MainActivity.this)) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || updateAsked) return;
                        updateAsked = true;
                        new android.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("新しいバージョンがあります")
                                .setMessage("バージョン " + v[1] + " に更新できます。今すぐ更新しますか？")
                                .setPositiveButton("更新する", new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface d, int w) {
                                        updateAsked = false;
                                        Updater.start(MainActivity.this);
                                    }
                                })
                                .setNegativeButton("あとで", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    // ---------- 部品 ----------
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView tv(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.topMargin = dp(top);
        return l;
    }

    private Button btn(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : ACCENT);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(primary ? ACCENT : Color.WHITE);
        if (!primary) g.setStroke(dp(2), ACCENT);
        b.setBackground(g);
        b.setPadding(dp(12), dp(14), dp(12), dp(14));
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(Color.WHITE);
        c.setBackground(g);
        c.setPadding(dp(14), dp(12), dp(14), dp(14));
        box.addView(c, lp(12));
        return c;
    }

    private EditText edit(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(17);
        e.setTextColor(INK);
        e.setSingleLine(true);
        return e;
    }

    private void label(LinearLayout parent, String s) {
        parent.addView(tv(s, 13, false, MUTED), lp(8));
    }

    private void reset(String title) {
        box.removeAllViews();
        edits.clear();
        box.addView(tv(title, 22, true, INK));
        sv.scrollTo(0, 0);
    }

    private void addMsg() {
        msg = tv("", 15, false, BAD);
        msg.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(msg, lp(12));
    }

    private void say(final String s, final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (msg == null) return;
                msg.setText(s);
                msg.setTextColor(color);
            }
        });
    }

    private JSONObject base(String action) throws Exception {
        JSONObject o = new JSONObject();
        o.put("action", action);
        o.put("token", prefs.getString("token", ""));
        return o;
    }

    // ---------- 初回設定 ----------
    private void showSetup() {
        reset("名刺登録の設定");
        LinearLayout c = card();
        label(c, "あなたの名前（登録者として台帳に記録されます）");
        final EditText name = edit(prefs.getString("name", ""), "例：山野");
        c.addView(name);
        label(c, "合言葉（事務から案内されたもの）");
        final EditText token = edit(prefs.getString("token", ""), "合言葉");
        token.setInputType(InputType.TYPE_CLASS_TEXT); // 日本語の合言葉も入力できるようにする
        c.addView(token);
        final Button ok = btn("保存して始める", true);
        box.addView(ok, lp(16));
        addMsg();
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String n = name.getText().toString().trim();
                final String t = token.getText().toString().trim();
                if (n.isEmpty() || t.isEmpty()) {
                    say("名前と合言葉を入力してください", BAD);
                    return;
                }
                ok.setEnabled(false);
                say("確認しています…", MUTED);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject r;
                        try {
                            JSONObject o = new JSONObject();
                            o.put("action", "ping");
                            o.put("token", t);
                            r = Api.post(o);
                        } catch (Exception e) {
                            r = new JSONObject();
                        }
                        final boolean good = r.optBoolean("ok");
                        final String err = r.optString("error", "確認できませんでした");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (good) {
                                    prefs.edit().putString("name", n).putString("token", t).apply();
                                    showHome();
                                } else {
                                    ok.setEnabled(true);
                                    say(err, BAD);
                                }
                            }
                        });
                    }
                }).start();
            }
        });
    }

    // ---------- ホーム ----------
    private void showHome() {
        reset("名刺登録");
        imageB64 = null;
        preview = null;
        imageBackB64 = null;
        previewBack = null;
        shootingBack = false;
        LinearLayout c = card();
        c.addView(tv("登録者：" + prefs.getString("name", ""), 16, true, INK));
        TextView change = tv("名前・合言葉を変更する", 14, false, ACCENT);
        change.setPadding(0, dp(6), 0, 0);
        change.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSetup();
            }
        });
        c.addView(change);
        label(c, "取得場面");
        final Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SCENES);
        sp.setAdapter(ad);
        int last = prefs.getInt("scene", 0);
        if (last >= 0 && last < SCENES.length) sp.setSelection(last);
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                prefs.edit().putInt("scene", pos).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        c.addView(sp);

        Button cam = btn("名刺を撮影する", true);
        cam.setTextSize(20);
        box.addView(cam, lp(20));
        cam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
            }
        });
        Button pick = btn("撮影済みの画像から選ぶ", false);
        box.addView(pick, lp(12));
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_GET_CONTENT);
                it.setType("image/*");
                try {
                    startActivityForResult(it, REQ_PICK);
                } catch (Exception e) {
                    say("画像を選ぶアプリが見つかりません", BAD);
                }
            }
        });
        box.addView(tv("名刺は明るい場所で、机に置いて真上から撮るときれいに読み取れます。", 14, false, MUTED), lp(14));
        addMsg();
    }

    private void openCamera() {
        Uri u = PhotoProvider.uri();
        File f = PhotoProvider.file(this);
        if (f.exists()) f.delete();
        Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        it.putExtra(MediaStore.EXTRA_OUTPUT, u);
        it.setClipData(ClipData.newRawUri("", u));
        it.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(it, REQ_CAM);
        } catch (Exception e) {
            say("カメラを起動できませんでした", BAD);
        }
    }

    private void checkUpdate() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String[] v = Updater.latest();
                if (v == null) return;
                long code;
                try {
                    code = Long.parseLong(v[0]);
                } catch (Exception e) {
                    return;
                }
                if (code <= Updater.currentCode(MainActivity.this)) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (imageB64 != null) return;
                        LinearLayout c = card();
                        c.addView(tv("新しいバージョン " + v[1] + " があります", 15, true, INK));
                        Button u = btn("更新する", true);
                        c.addView(u, lp(8));
                        u.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Updater.start(MainActivity.this);
                            }
                        });
                    }
                });
            }
        }).start();
    }

    // ---------- 撮影結果 ----------
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) {
            if (shootingBack) shootingBack = false;
            return;
        }
        if (msg == null) showHome();
        try {
            byte[] raw;
            if (req == REQ_CAM) {
                File f = PhotoProvider.file(this);
                if (f.exists() && f.length() > 0) {
                    raw = readAll(new FileInputStream(f));
                } else if (data != null && data.getExtras() != null && data.getExtras().get("data") instanceof Bitmap) {
                    // 保存先を無視して縮小画像だけを返すカメラアプリ向け
                    ByteArrayOutputStream t = new ByteArrayOutputStream();
                    ((Bitmap) data.getExtras().get("data")).compress(Bitmap.CompressFormat.JPEG, 95, t);
                    raw = t.toByteArray();
                } else {
                    say("撮影画像を受け取れませんでした。「撮影済みの画像から選ぶ」をお試しください。", BAD);
                    return;
                }
            } else if (req == REQ_PICK && data != null && data.getData() != null) {
                raw = readAll(getContentResolver().openInputStream(data.getData()));
            } else {
                return;
            }
            prepare(raw);
        } catch (Exception e) {
            say("画像を読み込めませんでした。もう一度撮影してください。", BAD);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    /** 撮影・選択した画像を受け取る。表面なら裏面の撮影を案内し、裏面なら読み取りに送る。 */
    private void prepare(byte[] raw) throws Exception {
        String b64 = encode(raw);
        if (shootingBack) {
            shootingBack = false;
            imageBackB64 = b64;
            previewBack = lastBitmap;
            extract();
        } else {
            imageB64 = b64;
            preview = lastBitmap;
            imageBackB64 = null;
            previewBack = null;
            showSideChoice();
        }
    }

    /** 縮小・向き補正してJPEGのbase64にする。 */
    private String encode(byte[] raw) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= MAX_PX) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        Bitmap bm = BitmapFactory.decodeByteArray(raw, 0, raw.length, o2);
        if (bm == null) throw new Exception("decode");
        int rot = 0;
        try {
            ExifInterface ex = new ExifInterface(new ByteArrayInputStream(raw));
            int ori = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (ori == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
        } catch (Exception ignored) {
        }
        float sc = Math.min(1f, (float) MAX_PX / Math.max(bm.getWidth(), bm.getHeight()));
        Matrix m = new Matrix();
        m.postScale(sc, sc);
        if (rot != 0) m.postRotate(rot);
        Bitmap out = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
        ByteArrayOutputStream jo = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 85, jo);
        lastBitmap = out;
        return Base64.encodeToString(jo.toByteArray(), Base64.NO_WRAP);
    }

    // ---------- 裏面の確認 ----------
    private void showSideChoice() {
        reset("裏面はありますか？");
        box.addView(tv("裏面に情報（英語表記・携帯番号・別の住所など）がある名刺は、裏面も撮影してください。", 14, false, MUTED), lp(6));
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(preview);
        iv.setAdjustViewBounds(true);
        box.addView(iv, lp(10));
        Button back = btn("裏面も撮影する", true);
        box.addView(back, lp(16));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shootingBack = true;
                openCamera();
            }
        });
        Button pickBack = btn("裏面を撮影済みの画像から選ぶ", false);
        box.addView(pickBack, lp(10));
        pickBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shootingBack = true;
                Intent it = new Intent(Intent.ACTION_GET_CONTENT);
                it.setType("image/*");
                try {
                    startActivityForResult(it, REQ_PICK);
                } catch (Exception e) {
                    shootingBack = false;
                    say("画像を選ぶアプリが見つかりません", BAD);
                }
            }
        });
        Button frontOnly = btn("表面だけで読み取る", false);
        box.addView(frontOnly, lp(10));
        frontOnly.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                extract();
            }
        });
        addMsg();
    }

    private void extract() {
        if (busy) return;
        busy = true;
        reset("読み取り中");
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(preview);
        iv.setAdjustViewBounds(true);
        box.addView(iv, lp(12));
        addBackPreview();
        addMsg();
        say("名刺を読み取っています（10秒ほどかかります）", MUTED);
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject r;
                try {
                    JSONObject o = base("extract");
                    o.put("image", imageB64);
                    if (imageBackB64 != null) o.put("imageBack", imageBackB64);
                    o.put("mime", "image/jpeg");
                    r = Api.post(o);
                } catch (Exception e) {
                    r = new JSONObject();
                }
                final JSONObject res = r;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (res.optBoolean("ok")) {
                            showConfirm(res.optJSONObject("data"));
                        } else {
                            failed(res.optString("error", "読み取りに失敗しました"), true);
                        }
                    }
                });
            }
        }).start();
    }

    private void failed(String error, final boolean retryExtract) {
        say(error, BAD);
        Button again = btn("もう一度試す", true);
        box.addView(again, lp(12));
        again.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (retryExtract) extract();
                else save();
            }
        });
        Button back = btn("最初に戻る", false);
        box.addView(back, lp(10));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHome();
            }
        });
    }

    private void addBackPreview() {
        if (previewBack == null) return;
        box.addView(tv("裏面", 13, false, MUTED), lp(8));
        ImageView ib = new ImageView(this);
        ib.setImageBitmap(previewBack);
        ib.setAdjustViewBounds(true);
        box.addView(ib, lp(4));
    }

    // ---------- 確認・修正 ----------
    private void showConfirm(JSONObject d) {
        if (d == null) d = new JSONObject();
        reset("内容の確認");
        box.addView(tv("読み取り結果を確認し、違うところは直してから登録してください。", 14, false, MUTED), lp(6));
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(preview);
        iv.setAdjustViewBounds(true);
        box.addView(iv, lp(10));
        addBackPreview();

        JSONArray dup = d.optJSONArray("duplicates");
        if (dup != null && dup.length() > 0) {
            StringBuilder sb = new StringBuilder("登録済みの可能性があります");
            for (int i = 0; i < dup.length(); i++) {
                JSONObject h = dup.optJSONObject(i);
                if (h == null) continue;
                sb.append("\n").append(h.optString("row")).append("行目 ").append(h.optString("name"))
                        .append("（").append(h.optString("company")).append("）登録者：").append(h.optString("by"));
            }
            TextView w = tv(sb.toString(), 14, false, WARN);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(8));
            g.setColor(WARN_BG);
            w.setBackground(g);
            w.setPadding(dp(10), dp(8), dp(10), dp(8));
            box.addView(w, lp(10));
        }

        LinearLayout c = card();
        for (Map.Entry<String, String> f : FIELDS.entrySet()) {
            label(c, f.getValue());
            EditText e = edit(d.optString(f.getKey(), ""), "");
            if (f.getKey().equals("email")) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            if (f.getKey().equals("tel") || f.getKey().equals("mobile") || f.getKey().equals("fax")) e.setInputType(InputType.TYPE_CLASS_PHONE);
            c.addView(e);
            edits.put(f.getKey(), e);
        }
        label(c, "メモ（どこで会ったか、要件など）");
        memoEdit = edit("", "");
        memoEdit.setSingleLine(false);
        memoEdit.setMinLines(2);
        c.addView(memoEdit);

        Button ok = btn("この内容で登録", true);
        box.addView(ok, lp(16));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        Button retake = btn("撮り直す", false);
        box.addView(retake, lp(10));
        retake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHome();
                openCamera();
            }
        });
        addMsg();
    }

    private JSONObject pending;

    private void save() {
        if (busy) return;
        try {
            if (!edits.isEmpty()) {
                JSONObject d = new JSONObject();
                for (Map.Entry<String, EditText> e : edits.entrySet()) d.put(e.getKey(), e.getValue().getText().toString().trim());
                d.put("memo", memoEdit == null ? "" : memoEdit.getText().toString().trim());
                d.put("registrant", prefs.getString("name", ""));
                int sc = prefs.getInt("scene", 0);
                d.put("scene", SCENES[sc >= 0 && sc < SCENES.length ? sc : 0]);
                if (d.optString("name").isEmpty() && d.optString("company").isEmpty()) {
                    say("氏名か会社名のどちらかは入力してください", BAD);
                    return;
                }
                pending = d;
            }
            if (pending == null) return;
        } catch (Exception e) {
            return;
        }
        busy = true;
        reset("登録中");
        addMsg();
        say("台帳に登録しています…", MUTED);
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject r;
                try {
                    JSONObject o = base("save");
                    o.put("data", pending);
                    o.put("image", imageB64);
                    if (imageBackB64 != null) o.put("imageBack", imageBackB64);
                    o.put("mime", "image/jpeg");
                    r = Api.post(o);
                } catch (Exception e) {
                    r = new JSONObject();
                }
                final JSONObject res = r;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (res.optBoolean("ok")) showDone(pending);
                        else failed(res.optString("error", "登録に失敗しました"), false);
                    }
                });
            }
        }).start();
    }

    private void showDone(JSONObject d) {
        String who = d == null ? "" : (d.optString("company") + "　" + d.optString("name")).trim();
        pending = null;
        reset("登録しました");
        box.addView(tv(who, 17, false, INK), lp(10));
        Button next = btn("続けて撮影する", true);
        box.addView(next, lp(20));
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHome();
                openCamera();
            }
        });
        Button home = btn("終わる", false);
        box.addView(home, lp(10));
        home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHome();
            }
        });
        addMsg();
    }
}
package jp.cranberry.meishi;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQ_CAM = 21, REQ_PICK = 22, MAX_PX = 1600;
    private static final int INK = 0xFF152033, MUTED = 0xFF5D6A7E, BG = 0xFFF4F1F2, ACCENT = 0xFFB5123D,
            WARN_BG = 0xFFFFF3CD, WARN = 0xFF8A6100, BAD = 0xFFC9342B;
    private static final String[] SCENES = {"営業訪問", "担当者会議", "カンファレンス", "研修・勉強会", "来所", "その他"};
    private static final Map<String, String> FIELDS = new LinkedHashMap<>();

    static {
        FIELDS.put("name", "氏名");
        FIELDS.put("kana", "ふりがな");
        FIELDS.put("company", "会社・事業所名");
        FIELDS.put("department", "部署");
        FIELDS.put("title", "役職");
        FIELDS.put("tel", "電話");
        FIELDS.put("mobile", "携帯");
        FIELDS.put("fax", "FAX");
        FIELDS.put("email", "メール");
        FIELDS.put("zip", "郵便番号");
        FIELDS.put("address", "住所");
        FIELDS.put("url", "URL");
    }

    private SharedPreferences prefs;
    private LinearLayout box;
    private ScrollView sv;
    private TextView msg;
    private String imageB64;
    private Bitmap preview;
    private boolean busy;
    private final Map<String, EditText> edits = new LinkedHashMap<>();
    private EditText memoEdit;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("meishi", Context.MODE_PRIVATE);
        sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        box.setPadding(p, p, p, p);
        sv.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
        if (prefs.getString("name", "").isEmpty() || prefs.getString("token", "").isEmpty()) showSetup();
        else showHome();
    }

    private boolean updateAsked;

    @Override
    protected void onResume() {
        super.onResume();
        if (!updateAsked) checkUpdateDialog();
    }

    /** 新しい版があれば、どの画面にいても更新を案内する（初回設定の画面でも更新できるように）。 */
    private void checkUpdateDialog() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String[] v = Updater.latest();
                if (v == null) return;
                long code;
                try {
                    code = Long.parseLong(v[0]);
                } catch (Exception e) {
                    return;
                }
                if (code <= Updater.currentCode(MainActivity.this)) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || updateAsked) return;
                        updateAsked = true;
                        new android.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("新しいバージョンがあります")
                                .setMessage("バージョン " + v[1] + " に更新できます。今すぐ更新しますか？")
                                .setPositiveButton("更新する", new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface d, int w) {
                                        updateAsked = false;
                                        Updater.start(MainActivity.this);
                                    }
                                })
                                .setNegativeButton("あとで", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    // ---------- 部品 ----------
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView tv(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.topMargin = dp(top);
        return l;
    }

    private Button btn(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : ACCENT);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(primary ? ACCENT : Color.WHITE);
        if (!primary) g.setStroke(dp(2), ACCENT);
        b.setBackground(g);
        b.setPadding(dp(12), dp(14), dp(12), dp(14));
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(Color.WHITE);
        c.setBackground(g);
        c.setPadding(dp(14), dp(12), dp(14), dp(14));
        box.addView(c, lp(12));
        return c;
    }

    private EditText edit(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(17);
        e.setTextColor(INK);
        e.setSingleLine(true);
        return e;
    }

    private void label(LinearLayout parent, String s) {
        parent.addView(tv(s, 13, false, MUTED), lp(8));
    }

    private void reset(String title) {
        box.removeAllViews();
        edits.clear();
        box.addView(tv(title, 22, true, INK));
        sv.scrollTo(0, 0);
    }

    private void addMsg() {
        msg = tv("", 15, false, BAD);
        msg.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(msg, lp(12));
    }

    private void say(final String s, final int color) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (msg == null) return;
                msg.setText(s);
                msg.setTextColor(color);
            }
        });
    }

    private JSONObject base(String action) throws Exception {
        JSONObject o = new JSONObject();
        o.put("action", action);
        o.put("token", prefs.getString("token", ""));
        return o;
    }

    // ---------- 初回設定 ----------
    private void showSetup() {
        reset("名刺登録の設定");
        LinearLayout c = card();
        label(c, "あなたの名前（登録者として台帳に記録されます）");
        final EditText name = edit(prefs.getString("name", ""), "例：山野");
        c.addView(name);
        label(c, "合言葉（事務から案内されたもの）");
        final EditText token = edit(prefs.getString("token", ""), "合言葉");
        token.setInputType(InputType.TYPE_CLASS_TEXT); // 日本語の合言葉も入力できるようにする
        c.addView(token);
        final Button ok = btn("保存して始める", true);
        box.addView(ok, lp(16));
        addMsg();
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String n = name.getText().toString().trim();
                final String t = token.getText().toString().trim();
                if (n.isEmpty() || t.isEmpty()) {
                    say("名前と合言葉を入力してください", BAD);
                    return;
                }
                ok.setEnabled(false);
                say("確認しています…", MUTED);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject r;
                        try {
                            JSONObject o = new JSONObject();
                            o.put("action", "ping");
                            o.put("token", t);
                            r = Api.post(o);
                        } catch (Exception e) {
                            r = new JSONObject();
                        }
                        final boolean good = r.optBoolean("ok");
                        final String err = r.optString("error", "確認できませんでした");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (good) {
                                    prefs.edit().putString("name", n).putString("token", t).apply();
                                    showHome();
                                } else {
                                    ok.setEnabled(true);
                                    say(err, BAD);
                                }
                            }
                        });
                    }
                }).start();
            }
        });
    }

    // ---------- ホーム ----------
    private void showHome() {
        reset("名刺登録");
        imageB64 = null;
        preview = null;
        LinearLayout c = card();
        c.addView(tv("登録者：" + prefs.getString("name", ""), 16, true, INK));
        TextView change = tv("名前・合言葉を変更する", 14, false, ACCENT);
        change.setPadding(0, dp(6), 0, 0);
        change.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSetup();
            }
        });
        c.addView(change);
        label(c, "取得場面");
        final Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SCENES);
        sp.setAdapter(ad);
        int last = prefs.getInt("scene", 0);
        if (last >= 0 && last < SCENES.length) sp.setSelection(last);
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                prefs.edit().putInt("scene", pos).apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        c.addView(sp);

        Button cam = btn("名刺を撮影する", true);
        cam.setTextSize(20);
        box.addView(cam, lp(20));
        cam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
            }
        });
        Button pick = btn("撮影済みの画像から選ぶ", false);
        box.addView(pick, lp(12));
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_GET_CONTENT);
                it.setType("image/*");
                try {
                    startActivityForResult(it, REQ_PICK);
                } catch (Exception e) {
                    say("画像を選ぶアプリが見つかりません", BAD);
                }
            }
        });
        box.addView(tv("名刺は明るい場所で、机に置いて真上から撮るときれいに読み取れます。", 14, false, MUTED), lp(14));
        addMsg();
    }

    private void openCamera() {
        Uri u = PhotoProvider.uri();
        File f = PhotoProvider.file(this);
        if (f.exists()) f.delete();
        Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        it.putExtra(MediaStore.EXTRA_OUTPUT, u);
        it.setClipData(ClipData.newRawUri("", u));
        it.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(it, REQ_CAM);
        } catch (Exception e) {
            say("カメラを起動できませんでした", BAD);
        }
    }

    private void checkUpdate() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String[] v = Updater.latest();
                if (v == null) return;
                long code;
                try {
                    code = Long.parseLong(v[0]);
                } catch (Exception e) {
                    return;
                }
                if (code <= Updater.currentCode(MainActivity.this)) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (imageB64 != null) return;
                        LinearLayout c = card();
                        c.addView(tv("新しいバージョン " + v[1] + " があります", 15, true, INK));
                        Button u = btn("更新する", true);
                        c.addView(u, lp(8));
                        u.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Updater.start(MainActivity.this);
                            }
                        });
                    }
                });
            }
        }).start();
    }

    // ---------- 撮影結果 ----------
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) return;
        if (msg == null) showHome();
        try {
            byte[] raw;
            if (req == REQ_CAM) {
                File f = PhotoProvider.file(this);
                if (f.exists() && f.length() > 0) {
                    raw = readAll(new FileInputStream(f));
                } else if (data != null && data.getExtras() != null && data.getExtras().get("data") instanceof Bitmap) {
                    // 保存先を無視して縮小画像だけを返すカメラアプリ向け
                    ByteArrayOutputStream t = new ByteArrayOutputStream();
                    ((Bitmap) data.getExtras().get("data")).compress(Bitmap.CompressFormat.JPEG, 95, t);
                    raw = t.toByteArray();
                } else {
                    say("撮影画像を受け取れませんでした。「撮影済みの画像から選ぶ」をお試しください。", BAD);
                    return;
                }
            } else if (req == REQ_PICK && data != null && data.getData() != null) {
                raw = readAll(getContentResolver().openInputStream(data.getData()));
            } else {
                return;
            }
            prepare(raw);
        } catch (Exception e) {
            say("画像を読み込めませんでした。もう一度撮影してください。", BAD);
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    /** 縮小・向き補正してJPEGにし、読み取りに送る。 */
    private void prepare(byte[] raw) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, o);
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= MAX_PX) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        Bitmap bm = BitmapFactory.decodeByteArray(raw, 0, raw.length, o2);
        if (bm == null) throw new Exception("decode");
        int rot = 0;
        try {
            ExifInterface ex = new ExifInterface(new ByteArrayInputStream(raw));
            int ori = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (ori == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
        } catch (Exception ignored) {
        }
        float sc = Math.min(1f, (float) MAX_PX / Math.max(bm.getWidth(), bm.getHeight()));
        Matrix m = new Matrix();
        m.postScale(sc, sc);
        if (rot != 0) m.postRotate(rot);
        Bitmap out = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
        ByteArrayOutputStream jo = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 85, jo);
        imageB64 = Base64.encodeToString(jo.toByteArray(), Base64.NO_WRAP);
        preview = out;
        extract();
    }

    private void extract() {
        if (busy) return;
        busy = true;
        reset("読み取り中");
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(preview);
        iv.setAdjustViewBounds(true);
        box.addView(iv, lp(12));
        addMsg();
        say("名刺を読み取っています（10秒ほどかかります）", MUTED);
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject r;
                try {
                    JSONObject o = base("extract");
                    o.put("image", imageB64);
                    o.put("mime", "image/jpeg");
                    r = Api.post(o);
                } catch (Exception e) {
                    r = new JSONObject();
                }
                final JSONObject res = r;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (res.optBoolean("ok")) {
                            showConfirm(res.optJSONObject("data"));
                        } else {
                            failed(res.optString("error", "読み取りに失敗しました"), true);
                        }
                    }
                });
            }
        }).start();
    }

    private void failed(String error, final boolean retryExtract) {
        say(error, BAD);
        Button again = btn("もう一度試す", true);
        box.addView(again, lp(12));
        again.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (retryExtract) extract();
                else save();
            }
        });
        Button back = btn("最初に戻る", false);
        box.addView(back, lp(10));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHome();
            }
        });
    }

    // ---------- 確認・修正 ----------
    private void showConfirm(JSONObject d) {
        if (d == null) d = new JSONObject();
        reset("内容の確認");
        box.addView(tv("読み取り結果を確認し、違うところは直してから登録してください。", 14, false, MUTED), lp(6));
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(preview);
        iv.setAdjustViewBounds(true);
        box.addView(iv, lp(10));

        JSONArray dup = d.optJSONArray("duplicates");
        if (dup != null && dup.length() > 0) {
            StringBuilder sb = new StringBuilder("登録済みの可能性があります");
            for (int i = 0; i < dup.length(); i++) {
                JSONObject h = dup.optJSONObject(i);
                if (h == null) continue;
                sb.append("\n").append(h.optString("row")).append("行目 ").append(h.optString("name"))
                        .append("（").append(h.optString("company")).append("）登録者：").append(h.optString("by"));
            }
            TextView w = tv(sb.toString(), 14, false, WARN);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(8));
            g.setColor(WARN_BG);
            w.setBackground(g);
            w.setPadding(dp(10), dp(8), dp(10), dp(8));
            box.addView(w, lp(10));
        }

        LinearLayout c = card();
        for (Map.Entry<String, String> f : FIELDS.entrySet()) {
            label(c, f.getValue());
            EditText e = edit(d.optString(f.getKey(), ""), "");
            if (f.getKey().equals("email")) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            if (f.getKey().equals("tel") || f.getKey().equals("mobile") || f.getKey().equals("fax")) e.setInputType(InputType.TYPE_CLASS_PHONE);
            c.addView(e);
            edits.put(f.getKey(), e);
        }
        label(c, "メモ（どこで会ったか、要件など）");
        memoEdit = edit("", "");
        memoEdit.setSingleLine(false);
        memoEdit.setMinLines(2);
        c.addView(memoEdit);

        Button ok = btn("この内容で登録", true);
        box.addView(ok, lp(16));
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        Button retake = btn("撮り直す", false);
        box.addView(retake, lp(10));
        retake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHome();
                openCamera();
            }
        });
        addMsg();
    }

    private JSONObject pending;

    private void save() {
        if (busy) return;
        try {
            if (!edits.isEmpty()) {
                JSONObject d = new JSONObject();
                for (Map.Entry<String, EditText> e : edits.entrySet()) d.put(e.getKey(), e.getValue().getText().toString().trim());
                d.put("memo", memoEdit == null ? "" : memoEdit.getText().toString().trim());
                d.put("registrant", prefs.getString("name", ""));
                int sc = prefs.getInt("scene", 0);
                d.put("scene", SCENES[sc >= 0 && sc < SCENES.length ? sc : 0]);
                if (d.optString("name").isEmpty() && d.optString("company").isEmpty()) {
                    say("氏名か会社名のどちらかは入力してください", BAD);
                    return;
                }
                pending = d;
            }
            if (pending == null) return;
        } catch (Exception e) {
            return;
        }
        busy = true;
        reset("登録中");
        addMsg();
        say("台帳に登録しています…", MUTED);
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONObject r;
                try {
                    JSONObject o = base("save");
                    o.put("data", pending);
                    o.put("image", imageB64);
                    o.put("mime", "image/jpeg");
                    r = Api.post(o);
                } catch (Exception e) {
                    r = new JSONObject();
                }
                final JSONObject res = r;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (res.optBoolean("ok")) showDone(pending);
                        else failed(res.optString("error", "登録に失敗しました"), false);
                    }
                });
            }
        }).start();
    }

    private void showDone(JSONObject d) {
        String who = d == null ? "" : (d.optString("company") + "　" + d.optString("name")).trim();
        pending = null;
        reset("登録しました");
        box.addView(tv(who, 17, false, INK), lp(10));
        Button next = btn("続けて撮影する", true);
        box.addView(next, lp(20));
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHome();
                openCamera();
            }
        });
        Button home = btn("終わる", false);
        box.addView(home, lp(10));
        home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHome();
            }
        });
        addMsg();
    }
}
