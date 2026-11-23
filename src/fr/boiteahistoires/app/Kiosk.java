package fr.boiteahistoires.app;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

/** Mode kiosque : l'appli devient l'écran d'accueil, verrouillée, sans barre d'état. */
public final class Kiosk {
    private Kiosk() {}

    static ComponentName admin(Context c) { return new ComponentName(c, AdminReceiver.class); }
    static DevicePolicyManager dpm(Context c) { return c.getSystemService(DevicePolicyManager.class); }

    public static boolean isOwner(Context c) {
        try { return dpm(c).isDeviceOwnerApp(c.getPackageName()); } catch (Exception e) { return false; }
    }

    public static boolean isLocked(Context c) {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        return am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
    }

    /** Applique (ou retire) les règles d'administration selon le réglage « kiosque ». */
    public static void applyPolicies(Context c) {
        if (!isOwner(c)) return;
        DevicePolicyManager d = dpm(c);
        ComponentName a = admin(c);
        String pkg = c.getPackageName();
        try {
            d.setLockTaskPackages(a, new String[]{pkg});
            if (Build.VERSION.SDK_INT >= 28) {
                // Seul le menu d'extinction (bouton marche/arrêt) reste accessible.
                d.setLockTaskFeatures(a, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS);
            }
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    d.setPermissionGrantState(a, pkg, Manifest.permission.POST_NOTIFICATIONS,
                            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
                } catch (Exception ignored) { }
            }
            if (Store.kiosk()) {
                IntentFilter f = new IntentFilter(Intent.ACTION_MAIN);
                f.addCategory(Intent.CATEGORY_HOME);
                f.addCategory(Intent.CATEGORY_DEFAULT);
                d.addPersistentPreferredActivity(a, f, new ComponentName(c, MainActivity.class));
                d.setKeyguardDisabled(a, true);
                d.setStatusBarDisabled(a, true);
            } else {
                d.clearPackagePersistentPreferredActivities(a, pkg);
                d.setStatusBarDisabled(a, false);
                d.setKeyguardDisabled(a, false);
            }
        } catch (Exception e) {
            Log.e(Store.TAG, "règles kiosque", e);
        }
    }

    /** À appeler depuis l'activité principale (onResume). */
    public static void sync(Activity act) {
        applyPolicies(act);
        boolean locked = isLocked(act);
        try {
            // Sans droits administrateur, startLockTask() active l'« épinglage d'application ».
            if (Store.kiosk() && !locked) act.startLockTask();
            else if (!Store.kiosk() && locked) act.stopLockTask();
        } catch (Exception e) {
            Log.e(Store.TAG, "lock task", e);
        }
    }

    /** Retire complètement les droits (avant de désinstaller l'appli). */
    public static void releaseOwner(Activity act) {
        Store.setKiosk(false);
        applyPolicies(act);
        try { if (isLocked(act)) act.stopLockTask(); } catch (Exception ignored) { }
        try { dpm(act).clearDeviceOwnerApp(act.getPackageName()); } catch (Exception e) { Log.e(Store.TAG, "clear owner", e); }
    }

    public static boolean ignoresBattery(Context c) {
        PowerManager pm = c.getSystemService(PowerManager.class);
        return pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    public static Intent batteryIntent(Context c) {
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        i.setData(Uri.parse("package:" + c.getPackageName()));
        return i;
    }
}
