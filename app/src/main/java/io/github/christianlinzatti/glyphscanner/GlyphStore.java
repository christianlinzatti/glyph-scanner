package io.github.christianlinzatti.glyphscanner;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.christianlinzatti.glyphscanner.core.GlyphDictionary;
import io.github.christianlinzatti.glyphscanner.core.GlyphGeometry;

/** Persists the names the user taught the app ("code\tname" entries). */
final class GlyphStore {
    private static final String KEY = "custom_glyphs";

    private GlyphStore() {
    }

    static void load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(CaptureService.PREFS, Context.MODE_PRIVATE);
        for (String e : p.getStringSet(KEY, new HashSet<>())) {
            int tab = e.indexOf('\t');
            if (tab <= 0) continue;
            try {
                GlyphDictionary.setCustom(GlyphGeometry.parseCode(e.substring(0, tab)), e.substring(tab + 1));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    static void save(Context ctx, long mask, String name) {
        GlyphDictionary.setCustom(mask, name);
        Set<String> out = new HashSet<>();
        for (Map.Entry<Long, String> e : GlyphDictionary.customNames().entrySet()) {
            out.add(GlyphGeometry.toCode(e.getKey()) + "\t" + e.getValue());
        }
        ctx.getSharedPreferences(CaptureService.PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY, out).apply();
    }
}
