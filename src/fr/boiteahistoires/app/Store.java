package fr.boiteahistoires.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Bibliothèque locale : podcasts, épisodes (archivés), réglages et positions de lecture. */
public final class Store {
    static final String TAG = "BoiteAHistoires";

    public static final class Podcast {
        public String id, name, feedUrl, imageUrl;
        public int skipSeconds, keep;
        public long added;
    }

    public static final class Episode {
        public String id, podcastId, guid, title, audioUrl, imageUrl, pageUrl, pageImage;
        public long pubDate;
        public int order;
    }

    public interface Listener { void onLibraryChanged(); }

    private static Context app;
    private static SharedPreferences prefs, positions;
    private static final ArrayList<Podcast> podcasts = new ArrayList<>();
    private static final ArrayList<Episode> episodes = new ArrayList<>();
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile String status = "";
    private static boolean notifyPending = false;

    private Store() {}

    // ---------------------------------------------------------------- init / persistance

    public static synchronized void init(Context c) {
        if (app != null) return;
        app = c.getApplicationContext();
        prefs = app.getSharedPreferences("reglages", Context.MODE_PRIVATE);
        positions = app.getSharedPreferences("positions", Context.MODE_PRIVATE);
        load();
    }

    public static Context context() { return app; }

    private static File libFile() { return new File(app.getFilesDir(), "bibliotheque.json"); }

    private static void load() {
        podcasts.clear();
        episodes.clear();
        File f = libFile();
        if (!f.exists()) return;
        try {
            FileInputStream in = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            JSONObject root = new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            JSONArray pa = root.optJSONArray("podcasts");
            for (int i = 0; pa != null && i < pa.length(); i++) {
                JSONObject o = pa.getJSONObject(i);
                Podcast p = new Podcast();
                p.id = o.getString("id");
                p.name = o.optString("name", "");
                p.feedUrl = o.optString("feed", "");
                p.imageUrl = o.has("img") ? o.optString("img", null) : null;
                p.skipSeconds = o.optInt("skip", 0);
                p.keep = o.optInt("keep", 30);
                p.added = o.optLong("added", 0);
                podcasts.add(p);
            }
            JSONArray ea = root.optJSONArray("episodes");
            for (int i = 0; ea != null && i < ea.length(); i++) {
                JSONObject o = ea.getJSONObject(i);
                Episode e = new Episode();
                e.id = o.getString("id");
                e.podcastId = o.getString("pid");
                e.guid = o.optString("guid", "");
                e.title = o.optString("title", "");
                e.audioUrl = o.optString("audio", "");
                e.imageUrl = o.has("img") ? o.optString("img", null) : null;
                e.pubDate = o.optLong("date", 0);
                e.order = o.optInt("ord", 0);
                e.pageUrl = o.has("page") ? o.optString("page", null) : null;
                e.pageImage = o.has("pimg") ? o.optString("pimg", null) : null;
                episodes.add(e);
            }
        } catch (Exception ex) {
            Log.e(TAG, "Lecture bibliothèque impossible", ex);
        }
    }

    private static void save() {
        try {
            JSONObject root = new JSONObject();
            JSONArray pa = new JSONArray();
            for (Podcast p : podcasts) {
                JSONObject o = new JSONObject();
                o.put("id", p.id);
                o.put("name", p.name);
                o.put("feed", p.feedUrl);
                if (p.imageUrl != null) o.put("img", p.imageUrl);
                o.put("skip", p.skipSeconds);
                o.put("keep", p.keep);
                o.put("added", p.added);
                pa.put(o);
            }
            JSONArray ea = new JSONArray();
            for (Episode e : episodes) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("pid", e.podcastId);
                o.put("guid", e.guid);
                o.put("title", e.title);
                o.put("audio", e.audioUrl);
                if (e.imageUrl != null) o.put("img", e.imageUrl);
                o.put("date", e.pubDate);
                o.put("ord", e.order);
                if (e.pageUrl != null) o.put("page", e.pageUrl);
                if (e.pageImage != null) o.put("pimg", e.pageImage);
                ea.put(o);
            }
            root.put("podcasts", pa);
            root.put("episodes", ea);
            File tmp = new File(app.getFilesDir(), "bibliotheque.json.tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
            out.close();
            if (!tmp.renameTo(libFile())) Log.e(TAG, "renommage bibliothèque impossible");
        } catch (Exception ex) {
            Log.e(TAG, "Sauvegarde bibliothèque impossible", ex);
        }
    }

    // ---------------------------------------------------------------- notifications d'UI

    public static void addListener(Listener l) { listeners.add(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }

    /** Prévient l'interface (regroupe les appels rapprochés). */
    public static void changed() {
        synchronized (Store.class) {
            if (notifyPending) return;
            notifyPending = true;
        }
        main.postDelayed(new Runnable() {
            @Override public void run() {
                synchronized (Store.class) { notifyPending = false; }
                for (Listener l : listeners) {
                    try { l.onLibraryChanged(); } catch (Exception ex) { Log.e(TAG, "listener", ex); }
                }
            }
        }, 150);
    }

    public static String status() { return status; }
    public static void setStatus(String s) { status = s == null ? "" : s; changed(); }

    // ---------------------------------------------------------------- fichiers

    public static File podcastDir(String pid) {
        File d = new File(app.getFilesDir(), "podcasts/" + pid);
        if (!d.exists()) d.mkdirs();
        return d;
    }
    public static File cover(String pid) { return new File(podcastDir(pid), "couverture.img"); }
    public static File audio(Episode e) { return new File(podcastDir(e.podcastId), e.id + ".audio"); }
    public static File image(Episode e) { return new File(podcastDir(e.podcastId), e.id + ".img"); }

    /** Image à afficher pour un épisode : la sienne, sinon celle du podcast. */
    public static File displayImage(Episode e) {
        File f = image(e);
        if (f.exists()) return f;
        return cover(e.podcastId);
    }

    public static long usedBytes() {
        return dirSize(new File(app.getFilesDir(), "podcasts"));
    }
    private static long dirSize(File d) {
        long s = 0;
        File[] l = d.listFiles();
        if (l == null) return 0;
        for (File f : l) s += f.isDirectory() ? dirSize(f) : f.length();
        return s;
    }
    private static void deleteRec(File d) {
        File[] l = d.listFiles();
        if (l != null) for (File f : l) deleteRec(f);
        d.delete();
    }

    // ---------------------------------------------------------------- podcasts

    public static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", h[i] & 0xff));
            return sb.toString();
        } catch (Exception ex) {
            return Integer.toHexString(s.hashCode());
        }
    }

    public static synchronized List<Podcast> podcasts() {
        ArrayList<Podcast> l = new ArrayList<>(podcasts);
        Collections.sort(l, new Comparator<Podcast>() {
            @Override public int compare(Podcast a, Podcast b) { return Long.compare(a.added, b.added); }
        });
        return l;
    }

    public static synchronized Podcast podcast(String id) {
        for (Podcast p : podcasts) if (p.id.equals(id)) return p;
        return null;
    }

    /** Ajoute un podcast ; renvoie null s'il existe déjà. */
    public static synchronized Podcast addPodcast(String name, String feedUrl, String imageUrl, int skip) {
        String id = hash(feedUrl.trim());
        if (podcast(id) != null) return null;
        Podcast p = new Podcast();
        p.id = id;
        p.name = name;
        p.feedUrl = feedUrl.trim();
        p.imageUrl = imageUrl;
        p.skipSeconds = skip;
        p.keep = defaultKeep();
        p.added = System.currentTimeMillis();
        podcasts.add(p);
        save();
        changed();
        return p;
    }

    public static void removePodcast(String id) {
        synchronized (Store.class) {
            for (int i = podcasts.size() - 1; i >= 0; i--) if (podcasts.get(i).id.equals(id)) podcasts.remove(i);
            for (int i = episodes.size() - 1; i >= 0; i--) if (episodes.get(i).podcastId.equals(id)) episodes.remove(i);
            save();
        }
        deleteRec(new File(app.getFilesDir(), "podcasts/" + id));
        changed();
    }

    public static synchronized void setSkip(String id, int s) {
        Podcast p = podcast(id);
        if (p == null) return;
        p.skipSeconds = Math.max(0, Math.min(600, s));
        save();
        changed();
    }

    public static synchronized void setKeep(String id, int k) {
        Podcast p = podcast(id);
        if (p == null) return;
        p.keep = Math.max(1, Math.min(1000, k));
        save();
        changed();
    }

    public static synchronized void setImageUrl(String id, String url) {
        Podcast p = podcast(id);
        if (p == null || url == null || url.equals(p.imageUrl)) return;
        p.imageUrl = url;
        save();
    }

    // ---------------------------------------------------------------- épisodes

    private static final Comparator<Episode> NEWEST_FIRST = new Comparator<Episode>() {
        @Override public int compare(Episode a, Episode b) {
            if (a.pubDate != b.pubDate) return Long.compare(b.pubDate, a.pubDate);
            return Integer.compare(a.order, b.order);
        }
    };

    /** Tous les épisodes connus (archivés compris), du plus récent au plus ancien. */
    public static synchronized List<Episode> episodes(String pid) {
        ArrayList<Episode> l = new ArrayList<>();
        for (Episode e : episodes) if (e.podcastId.equals(pid)) l.add(e);
        Collections.sort(l, NEWEST_FIRST);
        return l;
    }

    /** Épisodes disponibles hors ligne (fichier audio complet présent). */
    public static List<Episode> downloaded(String pid) {
        List<Episode> all = episodes(pid);
        ArrayList<Episode> l = new ArrayList<>();
        for (Episode e : all) if (audio(e).exists()) l.add(e);
        return l;
    }

    /** Fusionne les épisodes du flux. On ne supprime jamais un épisode sorti du flux (archive). */
    public static int mergeEpisodes(String pid, List<FeedParser.Item> items) {
        int added = 0;
        synchronized (Store.class) {
            for (int i = 0; i < items.size(); i++) {
                FeedParser.Item it = items.get(i);
                if (it.audioUrl == null || it.audioUrl.isEmpty()) continue;
                String key = it.guid != null && !it.guid.isEmpty() ? it.guid : it.audioUrl;
                String id = hash(pid + "|" + key);
                Episode found = null;
                for (Episode e : episodes) if (e.id.equals(id)) { found = e; break; }
                if (found == null) {
                    found = new Episode();
                    found.id = id;
                    found.podcastId = pid;
                    found.guid = key;
                    episodes.add(found);
                    added++;
                }
                found.title = it.title == null ? "" : it.title;
                found.audioUrl = it.audioUrl;
                if (it.imageUrl != null) found.imageUrl = it.imageUrl;
                if (it.pubDate > 0) found.pubDate = it.pubDate;
                found.order = i;
                if (it.pageUrl != null) found.pageUrl = it.pageUrl;
            }
            save();
        }
        changed();
        return added;
    }

    /** Mémorise l'image trouvée sur la page web de l'épisode ("" = rien trouvé). */
    public static synchronized void setPageImage(String eid, String url) {
        for (Episode e : episodes) if (e.id.equals(eid)) { e.pageImage = url == null ? "" : url; break; }
        save();
    }

    // ---------------------------------------------------------------- positions de lecture

    public static long position(String eid) { return positions.getLong("p_" + eid, 0); }
    public static void setPosition(String eid, long ms) { positions.edit().putLong("p_" + eid, ms).apply(); }
    public static boolean isDone(String eid) { return positions.getBoolean("d_" + eid, false); }
    public static void setDone(String eid, boolean d) {
        positions.edit().putBoolean("d_" + eid, d).apply();
        changed();
    }

    // ---------------------------------------------------------------- réglages

    public static String parentCode() { return prefs.getString("code", "1234"); }
    public static void setParentCode(String c) { prefs.edit().putString("code", c).apply(); }

    public static int maxVolume() { return prefs.getInt("volmax", 70); }
    public static void setMaxVolume(int v) { prefs.edit().putInt("volmax", v).apply(); }

    public static boolean kiosk() { return prefs.getBoolean("kiosque", true); }
    public static void setKiosk(boolean k) { prefs.edit().putBoolean("kiosque", k).apply(); }

    public static int defaultKeep() { return 30; }

    public static long lastSync() { return prefs.getLong("derniere_maj", 0); }
    public static void setLastSync(long t) { prefs.edit().putLong("derniere_maj", t).apply(); }

    public static String updateUrl() {
        String u = prefs.getString("maj_url", Updater.DEFAULT_URL);
        // Ancienne adresse (releases GitHub) remplacée par le dossier release/ du dépôt.
        if (u.contains("/releases/latest/download/")) u = Updater.DEFAULT_URL;
        return u;
    }
    public static void setUpdateUrl(String u) { prefs.edit().putString("maj_url", u == null ? "" : u.trim()).apply(); }
    public static long lastUpdateCheck() { return prefs.getLong("maj_verif", 0); }
    public static void setLastUpdateCheck(long t) { prefs.edit().putLong("maj_verif", t).apply(); }
    public static String updateStatus() { return prefs.getString("maj_statut", ""); }
    public static void setUpdateStatus(String s) { prefs.edit().putString("maj_statut", s).apply(); changed(); }

    public static boolean flag(String k) { return prefs.getBoolean(k, false); }
    public static void setFlag(String k) { prefs.edit().putBoolean(k, true).apply(); }

    public static boolean firstRunDone() { return prefs.getBoolean("init", false); }
    public static void setFirstRunDone() { prefs.edit().putBoolean("init", true).apply(); }
}
