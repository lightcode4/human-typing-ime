/*
 * VaultStore.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: HARDEN — replace silent empty catch blocks (encrypt/add, items
 *             JSON parse, items JSON build) with Log.w diagnostics; also log
 *             decrypt failures in reveal() while still returning null. The
 *             proguard-kept nested VaultItem class and its field names are
 *             unchanged. Behavior preserved.
 */

package com.example.humantypingime;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class VaultStore {

    private static final String TAG = "VaultStore";
    private static final String PREFS = "vault_prefs";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 50;

    public static class VaultItem {
        public long id;
        public String kind;
        public String preview;   // masked preview shown in list
        public byte[] blob;      // iv||ciphertext
        public long createdAt;
    }

    private final SharedPreferences prefs;
    private final VaultCrypto crypto = new VaultCrypto();

    public VaultStore(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void add(String plaintext, SensitivePatternDetector.Kind kind) {
        if (plaintext == null || plaintext.isEmpty()) return;

        List<VaultItem> items = getAll();

        // Dedupe: if the most recent item decrypts to the same plaintext, skip.
        if (!items.isEmpty()) {
            VaultItem last = items.get(0);
            String lastPlain = reveal(last);
            if (plaintext.equals(lastPlain)) return;
        }

        // Also skip if it matches ANY existing item, so re-copies of an older password don't add.
        for (VaultItem existing : items) {
            String p = reveal(existing);
            if (plaintext.equals(p)) return;
        }

        try {
            byte[] blob = crypto.encrypt(plaintext);
            VaultItem it = new VaultItem();
            it.id = System.currentTimeMillis();
            it.kind = kind.name();
            it.preview = mask(plaintext, kind);
            it.blob = blob;
            it.createdAt = System.currentTimeMillis();
            items.add(0, it);
            while (items.size() > MAX_ITEMS) items.remove(items.size() - 1);
            save(items);
        } catch (Exception e) {
            Log.w(TAG, "Failed to add vault item", e);
        }
    }

    public synchronized List<VaultItem> getAll() {
        List<VaultItem> out = new ArrayList<>();
        String raw = prefs.getString(KEY_ITEMS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                VaultItem it = new VaultItem();
                it.id = o.getLong("id");
                it.kind = o.getString("kind");
                it.preview = o.getString("preview");
                it.blob = Base64.decode(o.getString("blob"), Base64.NO_WRAP);
                it.createdAt = o.getLong("createdAt");
                out.add(it);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse vault items JSON", e);
        }
        return out;
    }

    public synchronized String reveal(VaultItem it) {
        try {
            return crypto.decrypt(it.blob);
        } catch (Exception e) {
            Log.w(TAG, "Failed to decrypt vault item", e);
            return null;
        }
    }

    public synchronized void delete(VaultItem it) {
        List<VaultItem> items = getAll();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == it.id) {
                items.remove(i);
                break;
            }
        }
        save(items);
    }

    public synchronized void clear() {
        prefs.edit().remove(KEY_ITEMS).apply();
    }

    private void save(List<VaultItem> items) {
        JSONArray arr = new JSONArray();
        try {
            for (VaultItem it : items) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("kind", it.kind);
                o.put("preview", it.preview);
                o.put("blob", Base64.encodeToString(it.blob, Base64.NO_WRAP));
                o.put("createdAt", it.createdAt);
                arr.put(o);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to build vault items JSON", e);
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply();
    }

    private static String mask(String plaintext, SensitivePatternDetector.Kind kind) {
        switch (kind) {
            case CARD: {
                String d = plaintext.replaceAll("[^0-9]", "");
                if (d.length() >= 4) return "•••• " + d.substring(d.length() - 4);
                return "••••";
            }
            case OTP: {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < plaintext.length(); i++) sb.append("•");
                return sb.toString();
            }
            case API_KEY:
                if (plaintext.length() > 8)
                    return plaintext.substring(0, 4) + "…" + plaintext.substring(plaintext.length() - 4);
                return "••••";
            case JWT:
                return "••••";
            case PASSWORD:
                return "••••••••";
            default:
                return "••••";
        }
    }
}
//（注：内容由AI生成）
