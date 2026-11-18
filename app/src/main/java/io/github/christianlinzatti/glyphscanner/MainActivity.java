package io.github.christianlinzatti.glyphscanner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import io.github.christianlinzatti.glyphscanner.core.GlyphDictionary;

public class MainActivity extends Activity {

    private static final int REQ_NOTIFICATIONS = 2;

    private TextView state;
    private Button overlayButton;
    private CheckBox launchIngress;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(CaptureService.PREFS, MODE_PRIVATE);

        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xFF0A0D12);

        TextView title = new TextView(this);
        title.setText("Glyph Scanner");
        title.setTextColor(0xFF6FE3C6);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView info = text(
                "Erkennt die Glyphen beim Ingress-Glyph-Hack automatisch und zeigt sie oben als Overlay an.\n\n"
                        + "1. Overlay-Berechtigung erteilen.\n"
                        + "2. „Starten“ tippen und die Bildschirmaufnahme erlauben "
                        + "(bei Nachfrage „Gesamter Bildschirm“ wählen).\n"
                        + "3. In Ingress einen Glyph-Hack starten – die Sequenz erscheint oben.\n\n"
                        + "Schnellstart: Kachel „Glyph Scanner“ in den Schnelleinstellungen hinzufügen "
                        + "oder App-Symbol lange drücken → „Schnellstart“.\n\n"
                        + "Tippe im Overlay auf eine Glyphe, um ihr einen eigenen Namen zu geben.\n\n"
                        + "Overlay: ✕ beendet, ↻ leert die Anzeige (passiert 8 s nach dem Hack auch automatisch), ↻ lange drücken = Diagnose. "
                        + "Die Leiste lässt sich vertikal verschieben – nicht über die Hexagon-Reihe von Ingress legen.");
        root.addView(info);

        state = text("");
        state.setTextColor(0xFFFFC65C);
        state.setPadding(0, dp(12), 0, dp(4));
        root.addView(state);

        overlayButton = button("Overlay-Berechtigung erteilen", v -> openOverlaySettings());
        root.addView(overlayButton);

        launchIngress = new CheckBox(this);
        launchIngress.setText("Danach Ingress öffnen");
        launchIngress.setTextColor(Color.WHITE);
        launchIngress.setChecked(prefs.getBoolean("launch_ingress", true));
        launchIngress.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("launch_ingress", c).apply());
        root.addView(launchIngress);

        TextView settings = text("Einstellungen");
        settings.setTextColor(0xFF6FE3C6);
        settings.setTypeface(Typeface.DEFAULT_BOLD);
        settings.setPadding(0, dp(20), 0, 0);
        root.addView(settings);

        CheckBox names = new CheckBox(this);
        names.setText("Namen unter den Glyphen anzeigen");
        names.setTextColor(Color.WHITE);
        names.setChecked(prefs.getBoolean(OverlayController.KEY_SHOW_NAMES, true));
        names.setOnCheckedChangeListener((b, c) ->
                prefs.edit().putBoolean(OverlayController.KEY_SHOW_NAMES, c).apply());
        root.addView(names);

        final TextView sizeLabel = text("");
        root.addView(sizeLabel);
        SeekBar size = new SeekBar(this);
        // 100 % .. 250 % in steps of 10
        size.setMax(15);
        int current = prefs.getInt(CaptureService.KEY_ICON_SCALE, CaptureService.DEFAULT_ICON_SCALE);
        size.setProgress(Math.max(0, Math.min(15, (current - 100) / 10)));
        sizeLabel.setText(sizeText(current));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int pct = 100 + progress * 10;
                sizeLabel.setText(sizeText(pct));
                prefs.edit().putInt(CaptureService.KEY_ICON_SCALE, pct).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        root.addView(size);
        TextView sizeHint = text("Während der Anzeige bleibt die Leiste schmal (sie darf die Hexagon-Reihe "
                + "nicht verdecken). Sobald du zeichnest, wird sie auf diese Größe vergrößert. "
                + "100 % = nie vergrößern.");
        sizeHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sizeHint.setTextColor(0xFF8FA3AD);
        root.addView(sizeHint);

        root.addView(button("Starten", v -> start()));
        root.addView(button("Beenden", v -> {
            startService(new Intent(this, CaptureService.class).setAction(CaptureService.ACTION_STOP));
            state.postDelayed(this::refresh, 300);
        }));
        root.addView(button("Gelernte Glyphen", v -> showLearned()));
        root.addView(button("Kalibrierung & Position zurücksetzen", v -> {
            CaptureService.clearCalibration(prefs);
            OverlayController.resetPosition(prefs);
            Toast.makeText(this, "Zurückgesetzt – wirkt beim nächsten Start", Toast.LENGTH_SHORT).show();
        }));

        TextView credits = text("Glyph-Wörterbuch: gm9/ingress-glyph-tools (MIT). "
                + "Inoffizielles Hilfsprogramm, nicht mit Niantic verbunden.");
        credits.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        credits.setTextColor(0xFF7A8790);
        credits.setPadding(0, dp(24), 0, 0);
        root.addView(credits);

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(0xFF0A0D12);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean overlayOk = Settings.canDrawOverlays(this);
        overlayButton.setEnabled(!overlayOk);
        overlayButton.setText(overlayOk ? "Overlay-Berechtigung ✓" : "Overlay-Berechtigung erteilen");
        state.setText(CaptureService.running ? "Status: läuft" : "Status: gestoppt");
    }

    private void openOverlaySettings() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void start() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Bitte zuerst die Overlay-Berechtigung erteilen", Toast.LENGTH_LONG).show();
            openOverlaySettings();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !prefs.getBoolean("asked_notifications", false)) {
            prefs.edit().putBoolean("asked_notifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }
        requestCapture();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) requestCapture();
    }

    private void requestCapture() {
        startActivity(new Intent(this, StartActivity.class));
    }

    private void showLearned() {
        GlyphStore.load(this);
        final java.util.List<java.util.Map.Entry<Long, String>> list =
                new java.util.ArrayList<>(GlyphDictionary.customNames().entrySet());
        if (list.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Gelernte Glyphen")
                    .setMessage("Noch keine. Tippe im Overlay auf eine Glyphe (z. B. ein „?“), um ihr einen Namen zu geben.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        list.sort((x, y) -> x.getValue().compareToIgnoreCase(y.getValue()));
        String[] items = new String[list.size()];
        for (int i = 0; i < items.length; i++) {
            String builtIn = GlyphDictionary.builtIn(list.get(i).getKey());
            items[i] = list.get(i).getValue() + (builtIn != null ? "  (statt " + builtIn + ")" : "");
        }
        new AlertDialog.Builder(this)
                .setTitle("Gelernte Glyphen – antippen zum Ändern")
                .setItems(items, (d, which) -> startActivity(LabelActivity.intent(this, list.get(which).getKey())))
                .setNegativeButton("Schließen", null)
                .show();
    }

    private static String sizeText(int pct) {
        return "Glyph-Größe beim Zeichnen: " + pct + " %";
    }

    // ------------------------------------------------------------------ tiny UI helpers

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFFDDE6EA);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setPadding(0, dp(12), 0, 0);
        return t;
    }

    private Button button(String label, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        b.setGravity(Gravity.CENTER);
        return b;
    }
}
