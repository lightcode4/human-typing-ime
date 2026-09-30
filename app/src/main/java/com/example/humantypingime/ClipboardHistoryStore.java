/*
 * ClipboardHistoryStore.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: HARDEN — replace silent empty catch blocks (decrypt/JSON parse/encrypt
 *             failures) with Log.w diagnostics; behavior unchanged.
 */

package com.example.humantypingime;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Local clipboard history with pins, tags, and todo metadata. Encrypted at rest. */
public class ClipboardHistoryStore {
    private static final String TAG = "ClipboardHistoryStore";
    private static final String PREFS = "clipboard_history_prefs";
    private static final String KEY_ITEMS_ENC = "items_enc_v3";
    private static final String KEY_ITEMS = "items_v2";
    private static final String LEGACY_KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 200;
    private final SharedPreferences prefs;
    private final VaultCrypto crypto = new VaultCrypto("clipboard_history_key_v1");

    public ClipboardHistoryStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateLegacyIfNeeded();
    }

    public synchronized void add(String text) {
        if (text == null || (text = text.trim()).isEmpty()) return;
        List<ClipboardItem> items = getItems();
        for (ClipboardItem item : items) {
            if (text.equals(item.text)) { item.timestamp = System.currentTimeMillis(); save(items); return; }
        }
        ClipboardItem item = new ClipboardItem();
        item.id = UUID.randomUUID().toString(); item.text = text; item.timestamp = System.currentTimeMillis();
        item.tags = ""; items.add(item); trim(items); save(items);
    }

    public synchronized List<String> getAll() {
        List<String> result = new ArrayList<>();
        for (ClipboardItem item : getItems()) result.add(item.text);
        return result;
    }
    public synchronized List<ClipboardItem> getItems() { return sort(read()); }
    public synchronized void saveItem(ClipboardItem item) { List<ClipboardItem> all = read(); replace(all, item); save(all); }
    public synchronized void delete(ClipboardItem item) { List<ClipboardItem> all = read(); remove(all, item.id); save(all); }
    public synchronized void togglePin(ClipboardItem item) { item.pinned = !item.pinned; saveItem(item); }
    public synchronized void toggleTodo(ClipboardItem item) { item.todo = !item.todo; if (!item.todo) item.done = false; saveItem(item); }
    public synchronized void toggleDone(ClipboardItem item) { item.done = !item.done; saveItem(item); }
    public synchronized void clear() { clearUnpinned(); }
    public synchronized void clearUnpinned() { List<ClipboardItem> all = read(); for (int i=all.size()-1;i>=0;i--) if (!all.get(i).pinned && !all.get(i).todo) all.remove(i); save(all); }

    public static String normalizeTags(String raw) {
        if (raw == null) return "";
        StringBuilder result = new StringBuilder();
        for (String tag : raw.toLowerCase().split("[,\\s]+")) if (!tag.trim().isEmpty()) {
            if (result.length() > 0) result.append(','); result.append(tag.trim());
        }
        return result.toString();
    }
    private List<ClipboardItem> read() {
        List<ClipboardItem> out = new ArrayList<>();
        String json = prefs.getString(KEY_ITEMS_ENC, null);
        if (json != null) {
            try {
                json = crypto.decrypt(Base64.decode(json, Base64.NO_WRAP));
            } catch (Exception e) {
                Log.w(TAG, "Failed to decrypt clipboard history; falling back", e);
                json = null;
            }
        } else {
            json = prefs.getString(KEY_ITEMS, "[]");
        }
        if (json == null) json = "[]";
        try { JSONArray a = new JSONArray(json); for (int i=0;i<a.length();i++) {
            JSONObject o=a.getJSONObject(i); ClipboardItem x=new ClipboardItem();
            x.id=o.optString("id", UUID.randomUUID().toString()); x.text=o.optString("text", "");
            x.timestamp=o.optLong("timestamp"); x.pinned=o.optBoolean("pinned"); x.tags=o.optString("tags", "");
            x.todo=o.optBoolean("todo"); x.done=o.optBoolean("done"); if (!x.text.isEmpty()) out.add(x);
        }} catch (Exception e) { Log.w(TAG, "Failed to parse clipboard history JSON", e); } return out;
    }
    private void save(List<ClipboardItem> items) {
        JSONArray a=new JSONArray(); try { for (ClipboardItem x:items) { JSONObject o=new JSONObject(); o.put("id",x.id);o.put("text",x.text);o.put("timestamp",x.timestamp);o.put("pinned",x.pinned);o.put("tags",x.tags);o.put("todo",x.todo);o.put("done",x.done);a.put(o); }} catch(Exception e) { Log.w(TAG, "Failed to build clipboard history JSON", e); }
        try {
            String enc = Base64.encodeToString(crypto.encrypt(a.toString()), Base64.NO_WRAP);
            prefs.edit().putString(KEY_ITEMS_ENC, enc)
                    .remove(KEY_ITEMS).remove(LEGACY_KEY_ITEMS).apply();
        } catch (Exception e) { Log.w(TAG, "Failed to encrypt/save clipboard history", e); }
    }
    private List<ClipboardItem> sort(List<ClipboardItem> all) { Collections.sort(all,(a,b)-> a.pinned != b.pinned ? (a.pinned ? -1:1) : Long.compare(b.timestamp,a.timestamp)); return all; }
    private void replace(List<ClipboardItem> all, ClipboardItem item) { for(int i=0;i<all.size();i++) if(all.get(i).id.equals(item.id)){all.set(i,item);return;} all.add(item); }
    private void remove(List<ClipboardItem> all,String id) { for(int i=all.size()-1;i>=0;i--)if(all.get(i).id.equals(id))all.remove(i); }
    private void trim(List<ClipboardItem> all) { sort(all); while(all.size()>MAX_ITEMS){ClipboardItem last=all.get(all.size()-1);if(last.pinned||last.todo)break;all.remove(all.size()-1);} }
    private void migrateLegacyIfNeeded() { if (prefs.contains(KEY_ITEMS) || !prefs.contains(LEGACY_KEY_ITEMS)) return; try { JSONArray legacy=new JSONArray(prefs.getString(LEGACY_KEY_ITEMS,"[]")); for(int i=0;i<legacy.length();i++) add(legacy.getString(i)); }catch(Exception e){ Log.w(TAG, "Failed to migrate legacy clipboard history", e); } }
}
//（注：内容由AI生成）
