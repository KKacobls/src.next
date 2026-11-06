package com.kk.jsbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Local-only, explicitly enabled user scripts. Runs in the current main frame at document-end. */
final class AutoScriptManager {
    interface ConsoleLogger { void log(String message); }
    private static final String STORAGE = "user_scripts_v1";
    private final Activity activity;
    private final WebView browser;
    private final SharedPreferences prefs;
    private final ConsoleLogger logger;
    private final Runnable changed;
    private final List<Script> scripts = new ArrayList<>();
    private final HashSet<String> executed = new HashSet<>();
    private int documentCounter = 0;
    private boolean loading = false;

    static final class Script {
        String id = UUID.randomUUID().toString();
        String name = "";
        String matches = "";
        String code = "";
        boolean enabled = true;
    }

    AutoScriptManager(Activity activity, WebView browser, SharedPreferences prefs,
                      ConsoleLogger logger, Runnable changed) {
        this.activity = activity;
        this.browser = browser;
        this.prefs = prefs;
        this.logger = logger;
        this.changed = changed;
        load();
    }

    int enabledCount() {
        int n = 0;
        for (Script s : scripts) if (s.enabled) n++;
        return n;
    }

    void onPageStarted() {
        documentCounter++;
        executed.clear();
        loading = true;
    }

    void onPageFinished(String url) {
        loading = false;
        runMatches(url);
    }

    /** Single-page apps can change URLs with pushState without a document navigation. */
    void onHistoryChanged(String url) {
        final int document = documentCounter;
        browser.postDelayed(() -> {
            if (!loading && document == documentCounter && url != null && url.equals(browser.getUrl())) {
                runMatches(url);
            }
        }, 200);
    }

    private void runMatches(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
        for (Script s : new ArrayList<>(scripts)) {
            String key = documentCounter + "|" + url + "|" + s.id;
            if (!s.enabled || executed.contains(key) || !matchesAny(s.matches, url)) continue;
            executed.add(key);
            execute(s, true);
        }
    }

    private void execute(Script script, boolean automatic) {
        if (browser.getUrl() == null || !(browser.getUrl().startsWith("http://") || browser.getUrl().startsWith("https://"))) {
            Toast.makeText(activity, R.string.no_webpage, Toast.LENGTH_SHORT).show();
            return;
        }
        // Scripts are user-provided. An IIFE isolates declarations, while exceptions appear in the Console.
        String source = "(function(){try{\n" + script.code + "\n}catch(e){console.error('[Auto JS] '+ "
                + JSONObject.quote(script.name) + ",e);}})();";
        browser.evaluateJavascript(source, result -> {});
        logger.log((automatic ? "[AUTO JS] " : "[MANUAL JS] ") + script.name + " @ " + browser.getUrl());
    }

    private void load() {
        scripts.clear();
        try {
            JSONArray json = new JSONArray(prefs.getString(STORAGE, "[]"));
            for (int i = 0; i < json.length(); i++) {
                JSONObject data = json.optJSONObject(i);
                if (data == null) continue;
                Script script = new Script();
                script.id = data.optString("id", script.id);
                script.name = data.optString("name", "");
                script.matches = data.optString("matches", "");
                script.code = data.optString("code", "");
                script.enabled = data.optBoolean("enabled", true);
                scripts.add(script);
            }
        } catch (Exception error) {
            logger.log("[AUTO JS] Could not read saved scripts: " + error.getMessage());
        }
    }

    private void save() {
        JSONArray json = new JSONArray();
        try {
            for (Script s : scripts) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("name", s.name);
                o.put("matches", s.matches);
                o.put("code", s.code);
                o.put("enabled", s.enabled);
                json.put(o);
            }
            prefs.edit().putString(STORAGE, json.toString()).apply();
            changed.run();
        } catch (Exception e) {
            logger.log("[AUTO JS] Save failed: " + e.getMessage());
        }
    }

    void showManager() {
        String[] choices = new String[scripts.size() + 1];
        choices[0] = "＋ " + activity.getString(R.string.add_script);
        for (int i = 0; i < scripts.size(); i++) {
            Script s = scripts.get(i);
            choices[i + 1] = (s.enabled ? "● " : "○ ") + s.name + "\n" + s.matches.replace('\n', ' ');
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.auto_scripts)
                .setItems(choices, (dialog, index) -> {
                    if (index == 0) edit(null);
                    else edit(scripts.get(index - 1));
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private EditText field(String hint, String value, int lines) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setText(value);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setSingleLine(lines == 1);
        if (lines != 1) {
            input.setMinLines(lines);
            input.setMaxLines(Math.max(lines, 12));
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setTypeface(Typeface.MONOSPACE);
        }
        return input;
    }

    private TextView label(int resource) {
        TextView label = new TextView(activity);
        label.setText(resource);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(0, dp(12), 0, dp(4));
        return label;
    }

    private int dp(int value) { return (int) (value * activity.getResources().getDisplayMetrics().density + .5f); }

    private String suggestMatch() {
        String url = browser.getUrl();
        if (url == null) return "https://example.com/*";
        Uri uri = Uri.parse(url);
        if (uri.getHost() == null || uri.getScheme() == null ||
                !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) return "https://example.com/*";
        String port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
        return uri.getScheme() + "://" + uri.getHost() + port + "/*";
    }

    private void edit(Script existing) {
        Script target = existing == null ? new Script() : existing;
        boolean adding = existing == null;
        ScrollView scroll = new ScrollView(activity);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(22), 0, dp(22), dp(8));
        scroll.addView(form);

        form.addView(label(R.string.script_name));
        EditText name = field(activity.getString(R.string.script_name), adding ? "" : target.name, 1);
        form.addView(name);

        form.addView(label(R.string.script_matches));
        EditText matches = field("https://example.com/*\n*://*.example.com/*", adding ? suggestMatch() : target.matches, 3);
        form.addView(matches);
        TextView help = new TextView(activity);
        help.setText(R.string.script_match_help);
        help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        help.setPadding(0, dp(4), 0, 0);
        form.addView(help);

        form.addView(label(R.string.script_code));
        EditText code = field("console.log('Hello from auto JS');", target.code, 7);
        form.addView(code);

        CheckBox enabled = new CheckBox(activity);
        enabled.setText(R.string.script_enabled);
        enabled.setChecked(target.enabled);
        form.addView(enabled);

        Button test = new Button(activity);
        test.setText(R.string.test_now);
        form.addView(test);
        test.setOnClickListener(v -> {
            if (code.getText().toString().trim().isEmpty()) return;
            Script preview = new Script();
            preview.name = name.getText().toString().trim();
            preview.code = code.getText().toString();
            execute(preview, false);
            Toast.makeText(activity, R.string.test_sent, Toast.LENGTH_SHORT).show();
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(adding ? R.string.add_script : R.string.edit_script)
                .setView(scroll)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null);
        if (!adding) builder.setNeutralButton(R.string.delete_script, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String n = name.getText().toString().trim();
                String m = matches.getText().toString().trim();
                String c = code.getText().toString();
                if (n.isEmpty() || m.isEmpty() || c.trim().isEmpty() || !validMatches(m)) {
                    Toast.makeText(activity, R.string.script_invalid, Toast.LENGTH_LONG).show();
                    return;
                }
                if (adding && scripts.size() >= 100) {
                    Toast.makeText(activity, R.string.script_limit, Toast.LENGTH_SHORT).show();
                    return;
                }
                target.name = n;
                target.matches = m;
                target.code = c;
                target.enabled = enabled.isChecked();
                if (adding) scripts.add(target);
                // Editing an already-run script allows it to run again after the next navigation.
                save();
                dialog.dismiss();
                Toast.makeText(activity, R.string.script_saved, Toast.LENGTH_SHORT).show();
            });
            if (!adding) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    new AlertDialog.Builder(activity).setMessage(R.string.confirm_delete_script)
                            .setPositiveButton(R.string.delete_script, (d2, w2) -> {
                                scripts.remove(target);
                                save();
                                dialog.dismiss();
                            }).setNegativeButton(R.string.cancel, null).show());
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static boolean validMatches(String rules) {
        int count = 0;
        for (String rule : rules.split("\\r?\\n")) {
            rule = normalizeRule(rule);
            if (rule.isEmpty()) continue;
            if (!isValidPattern(rule)) return false;
            count++;
        }
        return count > 0;
    }

    private static String normalizeRule(String rule) {
        String s = rule.trim();
        if (s.startsWith("//")) s = s.substring(2).trim();
        if (s.startsWith("@match")) s = s.substring(6).trim();
        if (s.startsWith("#")) return "";
        return s;
    }

    private static boolean isValidPattern(String pattern) {
        if (pattern.length() > 512 || pattern.contains(" ") || pattern.contains("\\")) return false;
        int sep = pattern.indexOf("://");
        if (sep < 0) return false;
        String scheme = pattern.substring(0, sep);
        if (!(scheme.equals("http") || scheme.equals("https") || scheme.equals("*"))) return false;
        String tail = pattern.substring(sep + 3);
        int slash = tail.indexOf('/');
        String host = slash < 0 ? tail : tail.substring(0, slash);
        if (host.isEmpty() || host.contains("?") || host.contains("#")) return false;
        // Constrain host wildcards to exact host, leading *. wildcard or all-hosts *.
        if (!host.equals("*") && host.indexOf('*') >= 0 && !host.startsWith("*.")) return false;
        return host.equals("*") || host.replaceFirst("^\\*\\.", "").matches("[a-zA-Z0-9.:_-]+");
    }

    static boolean matchesAny(String rules, String url) {
        for (String raw : rules.split("\\r?\\n")) {
            String pattern = normalizeRule(raw);
            if (pattern.isEmpty() || !isValidPattern(pattern)) continue;
            if (matchesPattern(pattern, url)) return true;
        }
        return false;
    }

    static boolean matchesPattern(String pattern, String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return false;
        int sep = pattern.indexOf("://");
        String scheme = pattern.substring(0, sep);
        String tail = pattern.substring(sep + 3);
        int slash = tail.indexOf('/');
        String host = slash < 0 ? tail : tail.substring(0, slash);
        String path = slash < 0 ? "/*" : tail.substring(slash);
        String schemeRegex = scheme.equals("*") ? "https?" : scheme;
        String hostRegex;
        if (host.equals("*")) hostRegex = "[^/]+";
        else if (host.startsWith("*.")) hostRegex = "(?:[^./:]+\\.)*" + Pattern.quote(host.substring(2));
        else hostRegex = Pattern.quote(host);
        StringBuilder pathRegex = new StringBuilder();
        for (String part : path.split("\\*", -1)) {
            if (pathRegex.length() > 0) pathRegex.append(".*");
            pathRegex.append(Pattern.quote(part));
        }
        // '?' in the actual URL is included in the wildcard path, as in WebExtension match rules.
        String regex = "^" + schemeRegex + "://" + hostRegex + pathRegex + "$";
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(url).matches();
    }
}
