package com.example.humantypingime;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Template {
    private String id;
    private String name;
    private String trigger; // e.g. "followup" (typed as "::followup")
    private String body;    // e.g. "Hi {name}, following up on {topic}."

    public Template(String id, String name, String trigger, String body) {
        this.id = id;
        this.name = name;
        this.trigger = trigger != null ? trigger.trim().toLowerCase() : "";
        this.body = body;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getTrigger() { return trigger; }
    public String getBody() { return body; }

    public void setName(String name) { this.name = name; }
    public void setTrigger(String trigger) { this.trigger = trigger != null ? trigger.trim().toLowerCase() : ""; }
    public void setBody(String body) { this.body = body; }

    /**
     * Extracts variable placeholders matching {var_name}.
     */
    public List<String> getVariables() {
        List<String> vars = new ArrayList<>();
        if (body == null) return vars;
        Matcher m = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}").matcher(body);
        while (m.find()) {
            String v = m.group(1);
            if (!vars.contains(v)) {
                vars.add(v);
            }
        }
        return vars;
    }

    /**
     * Replaces variable occurrences with provided user values.
     */
    public String render(Map<String, String> values) {
        if (body == null) return "";
        String result = body;
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("trigger", trigger);
        obj.put("body", body);
        return obj;
    }

    public static Template fromJson(JSONObject obj) {
        return new Template(
                obj.optString("id", String.valueOf(System.currentTimeMillis())),
                obj.optString("name", "Untitled"),
                obj.optString("trigger", ""),
                obj.optString("body", "")
        );
    }
}
