/*
 * ClipboardItem.java — Human Typing IME (com.example.humantypingime)
 *
 * Change log:
 * 2026-09-30: unchanged
 */

package com.example.humantypingime;

/** A locally persisted clipboard entry. Sensitive content is never added here. */
public class ClipboardItem {
    public String id;
    public String text;
    public long timestamp;
    public boolean pinned;
    public String tags;
    public boolean todo;
    public boolean done;
}
//（注：内容由AI生成）
