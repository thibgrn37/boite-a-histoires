package fr.boiteahistoires.app;

import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/** Empêche le volume « média » de dépasser le maximum choisi par le parent. */
public final class VolumeCap {
    private static boolean installed = false;

    private VolumeCap() {}

    public static synchronized void install(final Context c) {
        if (installed) return;
        installed = true;
        c.getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override public void onChange(boolean selfChange) { enforce(c); }
                });
        enforce(c);
    }

    public static int cap(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return Math.max(1, Math.round(max * Store.effectiveMaxVolume() / 100f));
    }

    public static boolean atCap(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) >= cap(c);
    }

    public static void enforce(Context c) {
        try {
            AudioManager am = c.getSystemService(AudioManager.class);
            int cap = cap(c);
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) > cap) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0);
            }
        } catch (Exception ignored) { }
    }
}
