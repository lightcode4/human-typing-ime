package com.example.humantypingime;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ClipboardManagerActivity extends AppCompatActivity {
    private ClipboardHistoryStore store;
    private List<ClipboardItem> items = new ArrayList<>();
    private ListView list;
    private TextView empty;
    private String mode = "all";
    private final DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_clipboard_manager);
        store = new ClipboardHistoryStore(this); list = findViewById(R.id.lv_clipboard); empty = findViewById(R.id.tv_clip_empty);
        ((EditText) findViewById(R.id.et_search)).addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void afterTextChanged(Editable e){}
            public void onTextChanged(CharSequence s,int a,int b,int c){ refresh(); }
        });
        findViewById(R.id.btn_filter_all).setOnClickListener(v->{mode="all";updateTabs();refresh();});
        findViewById(R.id.btn_filter_pinned).setOnClickListener(v->{mode="pinned";updateTabs();refresh();});
        findViewById(R.id.btn_filter_todo).setOnClickListener(v->{mode="todo";updateTabs();refresh();});
        findViewById(R.id.btn_clear_all).setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Clear unpinned history?").setMessage("Pinned items and todos are kept.").setPositiveButton("Clear",(d,w)->{store.clearUnpinned();refresh();}).setNegativeButton("Cancel",null).show());
        list.setOnItemClickListener((p,v,pos,id)->actions(items.get(pos)));
        list.setOnItemLongClickListener((p,v,pos,id)->{store.togglePin(items.get(pos));refresh();return true;});
        refresh(); updateTabs();
    }
    private void updateTabs() {
        styleTab(findViewById(R.id.btn_filter_all), "all".equals(mode));
        styleTab(findViewById(R.id.btn_filter_pinned), "pinned".equals(mode));
        styleTab(findViewById(R.id.btn_filter_todo), "todo".equals(mode));
    }
    private void styleTab(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_segment_selected : R.drawable.bg_segment_unselected);
        tab.setTextColor(ContextCompat.getColor(this, selected ? R.color.accent_on : R.color.text_secondary));
        tab.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }
    private void refresh() {
        String q=((EditText)findViewById(R.id.et_search)).getText().toString().trim().toLowerCase();
        items=new ArrayList<>();
        for(ClipboardItem item:store.getItems()) {
            boolean matches=q.isEmpty() || item.text.toLowerCase().contains(q) || item.tags.contains(q);
            if(matches && ("all".equals(mode) || ("pinned".equals(mode)&&item.pinned) || ("todo".equals(mode)&&item.todo))) items.add(item);
        }
        empty.setVisibility(items.isEmpty()?View.VISIBLE:View.GONE);
        list.setAdapter(new ArrayAdapter<ClipboardItem>(this, R.layout.item_clipboard, items) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView != null ? convertView : getLayoutInflater().inflate(R.layout.item_clipboard, parent, false);
                ClipboardItem clip = getItem(position);
                TextView itemText = row.findViewById(R.id.tv_text);
                TextView itemMeta = row.findViewById(R.id.tv_meta);
                ImageView iconKind = row.findViewById(R.id.icon_kind);
                ImageView iconTodo = row.findViewById(R.id.icon_todo);

                String display = clip.text.replace("\n", " ").trim();
                if (display.length() > 200) display = display.substring(0, 197) + "...";
                itemText.setText(display);

                StringBuilder meta = new StringBuilder(fmt.format(new Date(clip.timestamp)));
                if (clip.tags != null && !clip.tags.isEmpty()) {
                    meta.append("  •  ");
                    String[] parts = clip.tags.split(",");
                    for (int k = 0; k < parts.length; k++) {
                        if (k > 0) meta.append(' ');
                        if (!parts[k].isEmpty()) meta.append('#').append(parts[k]);
                    }
                }
                if (clip.todo && clip.done) meta.append("  •  done");
                itemMeta.setText(meta.toString());

                if (clip.pinned) {
                    iconKind.setImageResource(R.drawable.ic_pin);
                    iconKind.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ClipboardManagerActivity.this, R.color.accent)));
                } else if (clip.todo) {
                    iconKind.setImageResource(R.drawable.ic_check);
                    iconKind.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ClipboardManagerActivity.this, R.color.success)));
                } else {
                    iconKind.setImageResource(R.drawable.ic_history);
                    iconKind.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ClipboardManagerActivity.this, R.color.text_secondary)));
                }
                // Left icon already carries the primary state; surface the todo check only when the pin is showing instead.
                iconTodo.setVisibility(clip.pinned && clip.todo ? View.VISIBLE : View.GONE);

                itemText.setAlpha(clip.todo && clip.done ? 0.45f : 1f);
                return row;
            }
        });
    }
    private void actions(ClipboardItem item) {
        String[] options={"Copy","Pin / unpin","Add / remove todo","Mark done / not done","Edit tags","Delete"};
        new AlertDialog.Builder(this).setTitle(item.text.length()>40?item.text.substring(0,37)+"...":item.text).setItems(options,(d,w)->{
            if(w==0){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("clip",item.text));Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();}
            else if(w==1)store.togglePin(item); else if(w==2)store.toggleTodo(item); else if(w==3){if(item.todo)store.toggleDone(item);}
            else if(w==4)editTags(item); else store.delete(item); refresh();
        }).setNegativeButton("Cancel",null).show();
    }
    private void editTags(ClipboardItem item) {
        EditText input=new EditText(this);input.setText(item.tags);input.setHint("tag1, tag2");
        new AlertDialog.Builder(this).setTitle("Tags").setView(input).setPositiveButton("Save",(d,w)->{item.tags=ClipboardHistoryStore.normalizeTags(input.getText().toString());store.saveItem(item);refresh();}).setNegativeButton("Cancel",null).show();
    }
}
