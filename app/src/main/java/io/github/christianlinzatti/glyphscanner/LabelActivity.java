package io.github.christianlinzatti.glyphscanner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.github.christianlinzatti.glyphscanner.core.GlyphDictionary;
import io.github.christianlinzatti.glyphscanner.core.GlyphGeometry;

/** Small dialog to give a recognised glyph a (new) name. */
public class LabelActivity extends Activity {

    static final String EXTRA_CODE = "code";

    static Intent intent(Context ctx, long glyph) {
        return new Intent(ctx, LabelActivity.class)
                .putExtra(EXTRA_CODE, GlyphGeometry.toCode(glyph))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GlyphStore.load(this);
        String code = getIntent().getStringExtra(EXTRA_CODE);
        final long glyph;
        try {
            glyph = code == null ? 0 : GlyphGeometry.parseCode(code);
        } catch (IllegalArgumentException e) {
            finish();
            return;
        }
        if (glyph == 0) {
            finish();
            return;
        }
        setTitle("Glyphe benennen");
        float dp = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * dp);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad / 2, pad, pad);

        GlyphPreviewView preview = new GlyphPreviewView(this, glyph);
        root.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (180 * dp)));

        String builtIn = GlyphDictionary.builtIn(glyph);
        TextView info = new TextView(this);
        info.setText((builtIn != null ? "Im Wörterbuch: " + builtIn : "Nicht im Wörterbuch")
                + "\nCode: " + GlyphGeometry.toCode(glyph));
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        root.addView(info);

        final EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setHint("Name, z. B. Escape");
        String current = GlyphDictionary.custom(glyph);
        if (current != null) name.setText(current);
        root.addView(name);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button remove = new Button(this);
        remove.setText(current != null ? "Eigenen Namen löschen" : "Abbrechen");
        remove.setAllCaps(false);
        remove.setOnClickListener(v -> {
            if (GlyphDictionary.custom(glyph) != null) {
                GlyphStore.save(this, glyph, null);
                refreshOverlay();
            }
            finish();
        });
        Button save = new Button(this);
        save.setText("Speichern");
        save.setAllCaps(false);
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                Toast.makeText(this, "Bitte einen Namen eingeben", Toast.LENGTH_SHORT).show();
                return;
            }
            GlyphStore.save(this, glyph, n);
            refreshOverlay();
            Toast.makeText(this, "Gespeichert: " + n, Toast.LENGTH_SHORT).show();
            finish();
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        buttons.addView(remove, half);
        buttons.addView(save, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(buttons);

        setContentView(root);
    }

    private void refreshOverlay() {
        if (CaptureService.running) {
            startService(new Intent(this, CaptureService.class).setAction(CaptureService.ACTION_REFRESH));
        }
    }
}
