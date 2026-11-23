package fr.boiteahistoires.app;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * Mise à jour automatique de l'appli : lit un petit fichier version.json à une adresse fixe
 * (par défaut la dernière « release » GitHub), télécharge le nouvel APK et l'installe.
 * Comme l'appli est « propriétaire de l'appareil », l'installation se fait sans rien demander.
 */
public final class Updater {
    static final String DEFAULT_URL = "https://raw.githubusercontent.com/thibgrn37/boite-a-histoires/main/release/version.json";
    static final long CHECK_EVERY = 12L * 3600 * 1000;

    private Updater() {}

    public static long currentVersion(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    public static String currentVersionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Appelé après chaque mise à jour des podcasts : vérifie au plus toutes les 12 h. */
    static void maybeCheck(Context c) {
        if (System.currentTimeMillis() - Store.lastUpdateCheck() < CHECK_EVERY) return;
        check(c, false);
    }

    /** Vérifie et installe si besoin. À appeler hors du fil principal. */
    public static void check(Context c, boolean interactive) {
        Store.setLastUpdateCheck(System.currentTimeMillis());
        String url = Store.updateUrl();
        if (url.isEmpty()) return;
        try {
            Store.setUpdateStatus("Recherche d'une nouvelle version…");
            JSONObject o = new JSONObject(Net.text(url));
            long remote = o.getLong("versionCode");
            String name = o.optString("versionName", String.valueOf(remote));
            long local = currentVersion(c);
            if (remote <= local) {
                Store.setUpdateStatus("L'appli est à jour (version " + currentVersionName(c) + ").");
                return;
            }
            String apkUrl = new URL(new URL(url), o.optString("apk", "BoiteAHistoires.apk")).toString();
            File apk = new File(c.getCacheDir(), "maj.apk");
            Store.setUpdateStatus("Téléchargement de la version " + name + "…");
            Net.download(apkUrl, apk, null);
            PackageInfo pi = c.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
            if (pi == null || !c.getPackageName().equals(pi.packageName) || pi.getLongVersionCode() <= local) {
                apk.delete();
                Store.setUpdateStatus("Le fichier téléchargé n'est pas une mise à jour valide.");
                return;
            }
            if (!interactive && !Kiosk.isOwner(c)) {
                // Sans droits administrateur, Android demande une confirmation : on attend le parent.
                Store.setUpdateStatus("Nouvelle version " + name + " disponible : touche « Vérifier les mises à jour » pour l'installer.");
                return;
            }
            Store.setUpdateStatus("Installation de la version " + name + "…");
            install(c, apk);
        } catch (Exception e) {
            Log.w(Store.TAG, "maj", e);
            Store.setUpdateStatus("Vérification impossible : " + e.getMessage());
        }
    }

    private static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setAppPackageName(c.getPackageName());
        int id = pi.createSession(sp);
        PackageInstaller.Session s = pi.openSession(id);
        try {
            OutputStream out = s.openWrite("app.apk", 0, apk.length());
            InputStream in = new FileInputStream(apk);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            s.fsync(out);
            out.close();
            Intent i = new Intent(c, UpdateReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent p = PendingIntent.getBroadcast(c, id, i, flags);
            s.commit(p.getIntentSender());
        } finally {
            s.close();
        }
    }
}
