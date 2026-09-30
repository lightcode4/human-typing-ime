/*
 * TemplateRepository.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: HARDEN — replace silent empty catch blocks (template JSON parse
 *             and build failures) with Log.w diagnostics; behavior unchanged.
 */

package com.example.humantypingime;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class TemplateRepository {

    private static final String TAG = "TemplateRepository";
    private static final String PREFS = "template_prefs";
    private static final String KEY_TEMPLATES = "templates_json";

    private final SharedPreferences prefs;

    public TemplateRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seedDefaultTemplatesIfEmpty();
    }

    private void seedDefaultTemplatesIfEmpty() {
        if (!prefs.contains(KEY_TEMPLATES)) {
            List<Template> defaults = new ArrayList<>();
            defaults.add(new Template("1", "Followup", "followup",
                    "Hi {name}, following up on {topic}. Let me know if you need anything else!"));
            defaults.add(new Template("2", "Meeting Notes", "meeting",
                    "Meeting summary with {person} regarding {project}: Next steps agreed."));
            defaults.add(new Template("3", "Quick Greeting", "greet",
                    "Hello {name}, hope you are having a productive day!"));
            save(defaults);
        }
    }

    public synchronized List<Template> getAll() {
        String raw = prefs.getString(KEY_TEMPLATES, "[]");
        List<Template> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Template.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse templates JSON", e);
        }
        return list;
    }

    public synchronized void save(List<Template> templates) {
        JSONArray arr = new JSONArray();
        for (Template t : templates) {
            try {
                arr.put(t.toJson());
            } catch (JSONException e) {
                Log.w(TAG, "Failed to build template JSON", e);
            }
        }
        prefs.edit().putString(KEY_TEMPLATES, arr.toString()).apply();
    }

    public synchronized Template findByTrigger(String trigger) {
        if (trigger == null) return null;
        String normalized = trigger.trim().toLowerCase();
        for (Template t : getAll()) {
            if (t.getTrigger() != null && t.getTrigger().equalsIgnoreCase(normalized)) {
                return t;
            }
        }
        return null;
    }

    public synchronized void addOrUpdate(Template template) {
        List<Template> list = getAll();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(template.getId())) {
                list.set(i, template);
                save(list);
                return;
            }
        }
        list.add(template);
        save(list);
    }

    public synchronized void delete(String templateId) {
        List<Template> list = getAll();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(templateId)) {
                list.remove(i);
                save(list);
                return;
            }
        }
    }
}
//（注：内容由AI生成）
