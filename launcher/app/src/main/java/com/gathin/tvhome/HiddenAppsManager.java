package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages user-hidden apps on the Home screen and provides the popup window
 * to view and launch hidden apps.
 */
public class HiddenAppsManager {

    private static final String PREF_NAME = "home_custom_prefs";
    private static final String KEY_HIDDEN = "user_hidden_apps";

    public static Set<String> getHiddenApps(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getStringSet(KEY_HIDDEN, Collections.emptySet());
    }

    public static void setHiddenApps(Context ctx, Set<String> set) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putStringSet(KEY_HIDDEN, new HashSet<>(set)).apply();
    }

    public static void showManageDialog(Activity activity, Runnable onUpdated) {
        PackageManager pm = activity.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> all = new ArrayList<>(pm.queryIntentActivities(query, 0));

        // Add Leanback apps
        Intent tvQuery = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> tvApps = pm.queryIntentActivities(tvQuery, 0);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : all) seen.add(ri.activityInfo.packageName);
        for (ResolveInfo ri : tvApps) {
            if (seen.add(ri.activityInfo.packageName)) all.add(ri);
        }

        // Filter out this launcher
        List<ResolveInfo> apps = new ArrayList<>();
        String myPkg = activity.getPackageName();
        for (ResolveInfo ri : all) {
            if (ri.activityInfo != null && !myPkg.equals(ri.activityInfo.packageName)) {
                apps.add(ri);
            }
        }

        Collections.sort(apps, (a, b) -> String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));

        Set<String> currentlyHidden = new HashSet<>(getHiddenApps(activity));
        boolean[] checked = new boolean[apps.size()];
        String[] labels = new String[apps.size()];

        for (int i = 0; i < apps.size(); i++) {
            ResolveInfo ri = apps.get(i);
            labels[i] = String.valueOf(ri.loadLabel(pm));
            checked[i] = currentlyHidden.contains(ri.activityInfo.packageName);
        }

        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Select Apps to Hide from Dock")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                })
                .setPositiveButton("Save", (dialog, which) -> {
                    Set<String> newHidden = new HashSet<>();
                    for (int i = 0; i < apps.size(); i++) {
                        if (checked[i]) {
                            newHidden.add(apps.get(i).activityInfo.packageName);
                        }
                    }
                    setHiddenApps(activity, newHidden);
                    Toast.makeText(activity, "Hidden apps updated (" + newHidden.size() + ")", Toast.LENGTH_SHORT).show();
                    if (onUpdated != null) onUpdated.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void showHiddenAppsWindow(Activity activity, View anchor, Runnable onUnhide) {
        Set<String> hiddenSet = getHiddenApps(activity);
        if (hiddenSet.isEmpty()) {
            Toast.makeText(activity, "No apps are hidden", Toast.LENGTH_SHORT).show();
            return;
        }

        PackageManager pm = activity.getPackageManager();
        List<ResolveInfo> hiddenApps = new ArrayList<>();
        for (String pkg : hiddenSet) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) {
                ResolveInfo ri = pm.resolveActivity(i, 0);
                if (ri != null) hiddenApps.add(ri);
            }
        }

        if (hiddenApps.isEmpty()) {
            Toast.makeText(activity, "No hidden apps found", Toast.LENGTH_SHORT).show();
            return;
        }

        float density = activity.getResources().getDisplayMetrics().density;
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_hidden_apps, null);
        GridView grid = dialogView.findViewById(R.id.hiddenAppsGrid);
        TextView title = dialogView.findViewById(R.id.hiddenAppsTitle);
        title.setText("HIDDEN APPS (" + hiddenApps.size() + ")");

        PopupWindow popup = new PopupWindow(dialogView,
                (int) (520 * density), (int) (340 * density), true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);

        dialogView.findViewById(R.id.btnHiddenClose).setOnClickListener(v -> popup.dismiss());

        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return hiddenApps.size(); }
            @Override public Object getItem(int pos) { return hiddenApps.get(pos); }
            @Override public long getItemId(int pos) { return pos; }

            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                View v = convert;
                if (v == null) {
                    v = LayoutInflater.from(activity).inflate(R.layout.item_hidden_app, parent, false);
                }
                ResolveInfo ri = hiddenApps.get(pos);
                ImageView icon = v.findViewById(R.id.hiddenAppIcon);
                TextView name = v.findViewById(R.id.hiddenAppName);

                icon.setImageDrawable(ri.loadIcon(pm));
                name.setText(ri.loadLabel(pm));

                v.setOnClickListener(view -> {
                    popup.dismiss();
                    Intent launch = pm.getLeanbackLaunchIntentForPackage(ri.activityInfo.packageName);
                    if (launch == null) launch = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
                    if (launch != null) {
                        activity.startActivity(launch);
                    }
                });

                v.setOnLongClickListener(view -> {
                    new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle(ri.loadLabel(pm))
                            .setMessage("Unhide this app and restore it to the Home dock?")
                            .setPositiveButton("Unhide", (d, w) -> {
                                Set<String> updated = new HashSet<>(getHiddenApps(activity));
                                updated.remove(ri.activityInfo.packageName);
                                setHiddenApps(activity, updated);
                                popup.dismiss();
                                Toast.makeText(activity, "Unhidden: " + ri.loadLabel(pm), Toast.LENGTH_SHORT).show();
                                if (onUnhide != null) onUnhide.run();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                });

                return v;
            }
        });

        popup.showAtLocation(anchor != null ? anchor : activity.getWindow().getDecorView(),
                Gravity.CENTER, 0, 0);
    }
}
