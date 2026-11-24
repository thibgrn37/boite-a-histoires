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
        for (Episode e : episodes) if (e.id.equals(eid)) { e.pageImage = url; break; }
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

    private static final java.util.LinkedList<String> journal = new java.util.LinkedList<>();

    /** Petit journal visible dans l'espace parents (40 dernières lignes). */
    public static void log(String msg) {
        Log.i(TAG, msg);
        String line = new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.FRANCE).format(new java.util.Date()) + "  " + msg;
        synchronized (journal) {
            if (journal.isEmpty()) {
                String old = prefs.getString("journal", "");
                if (!old.isEmpty()) for (String l : old.split("\n")) journal.add(l);
            }
            journal.addFirst(line);
            while (journal.size() > 40) journal.removeLast();
            StringBuilder sb = new StringBuilder();
            for (String l : journal) { if (sb.length() > 0) sb.append('\n'); sb.append(l); }
            prefs.edit().putString("journal", sb.toString()).apply();
        }
        changed();
    }

    public static String journal() { return prefs.getString("journal", ""); }

    public static boolean flag(String k) { return prefs.getBoolean(k, false); }
    public static void setFlag(String k) { prefs.edit().putBoolean(k, true).apply(); }

    // Limite d'épisodes par jour (0 = illimité) et luminosité imposée (0 = réglage du téléphone)
    // Limites : journée et soir (soir = heures du mode nuit). Type 0 = épisodes, 1 = minutes. Valeur 0 = pas de limite.
    public static int episodeLimit() { return prefs.getInt("limite", 0); }
    public static void setEpisodeLimit(int n) { prefs.edit().putInt("limite", clampLimit(limitType(), n)).apply(); changed(); }
    public static int limitType() { return prefs.getInt("limite_type", 0); }
    public static void setLimitType(int t) { prefs.edit().putInt("limite_type", t).putInt("limite", 0).apply(); changed(); }
    public static int eveningLimit() { return prefs.getInt("soir_limite", 0); }
    public static void setEveningLimit(int n) { prefs.edit().putInt("soir_limite", clampLimit(eveningType(), n)).apply(); changed(); }
    public static int eveningType() { return prefs.getInt("soir_type", 0); }
    public static void setEveningType(int t) { prefs.edit().putInt("soir_type", t).putInt("soir_limite", 0).apply(); changed(); }
    private static int clampLimit(int type, int n) { return Math.max(0, Math.min(type == 1 ? 600 : 50, n)); }

    private static String today() { return new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date()); }

    /** Vrai pendant les heures du soir / de la nuit (mêmes heures que le mode nuit automatique). */
    public static boolean inEvening() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        int a = nightStart(), b = nightEnd();
        if (a == b) return false;
        return a < b ? (h >= a && h < b) : (h >= a || h < b);
    }

    /** Identifiant de la soirée en cours (date du début de soirée). */
    private static String eveningKey() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        if (nightStart() > nightEnd() && c.get(java.util.Calendar.HOUR_OF_DAY) < nightEnd()) c.add(java.util.Calendar.DAY_OF_MONTH, -1);
        return "s" + new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(c.getTime());
    }

    public static int playedToday() {
        if (!today().equals(prefs.getString("ecoutes_jour", ""))) return 0;
        return prefs.getInt("ecoutes", 0);
    }
    public static long listenedTodayMs() {
        if (!today().equals(prefs.getString("ecoutes_jour", ""))) return 0;
        return prefs.getLong("ecoute_ms", 0);
    }
    public static int playedEvening() {
        if (!inEvening() || !eveningKey().equals(prefs.getString("soir_cle", ""))) return 0;
        return prefs.getInt("soir_ep", 0);
    }
    public static long listenedEveningMs() {
        if (!inEvening() || !eveningKey().equals(prefs.getString("soir_cle", ""))) return 0;
        return prefs.getLong("soir_ms", 0);
    }

    private static void add(int episodes, long ms) {
        SharedPreferences.Editor ed = prefs.edit();
        boolean ev = inEvening();
        // Avec une limite du soir, le soir a son propre compteur : la limite de la journée ne compte que la journée.
        if (!(ev && eveningLimit() > 0))
            ed.putString("ecoutes_jour", today()).putInt("ecoutes", playedToday() + episodes).putLong("ecoute_ms", listenedTodayMs() + ms);
        if (ev) ed.putString("soir_cle", eveningKey()).putInt("soir_ep", playedEvening() + episodes).putLong("soir_ms", listenedEveningMs() + ms);
        ed.apply();
    }
    public static void addPlayed() { add(1, 0); changed(); }
    public static void addListened(long ms) { add(0, ms); }

    public static void resetPlayed() {
        prefs.edit().putString("ecoutes_jour", today()).putInt("ecoutes", 0).putLong("ecoute_ms", 0)
                .putString("soir_cle", eveningKey()).putInt("soir_ep", 0).putLong("soir_ms", 0).apply();
        changed();
    }

    public static boolean dayLimitReached() {
        int l = episodeLimit();
        if (l <= 0) return false;
        if (eveningLimit() > 0 && inEvening()) return false;
        return limitType() == 1 ? listenedTodayMs() >= l * 60000L : playedToday() >= l;
    }
    public static boolean eveningLimitReached() {
        int l = eveningLimit();
        if (l <= 0 || !inEvening()) return false;
        return eveningType() == 1 ? listenedEveningMs() >= l * 60000L : playedEvening() >= l;
    }
    public static boolean limitReached() { return dayLimitReached() || eveningLimitReached(); }

    public static int brightness() { return prefs.getInt("luminosite", 0); }
    public static void setBrightness(int b) { prefs.edit().putInt("luminosite", b).apply(); }

    // Mode nuit : 0 = automatique selon l'heure, 1 = toujours jour, 2 = toujours nuit
    public static int nightMode() { return prefs.getInt("nuit_mode", 0); }
    public static void setNightMode(int m) { prefs.edit().putInt("nuit_mode", m).apply(); }
    public static int nightStart() { return prefs.getInt("nuit_debut", 19); }
    public static int nightEnd() { return prefs.getInt("nuit_fin", 7); }
    public static void setNightStart(int h) { prefs.edit().putInt("nuit_debut", (h + 24) % 24).apply(); }
    public static void setNightEnd(int h) { prefs.edit().putInt("nuit_fin", (h + 24) % 24).apply(); }
    public static boolean isNightNow() {
        int m = nightMode();
        if (m == 1) return false;
        if (m == 2) return true;
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        int a = nightStart(), b = nightEnd();
        if (a == b) return false;
        return a < b ? (h >= a && h < b) : (h >= a || h < b);
    }

    // Écran qui s'assombrit sans toucher (secondes, 0 = jamais)
    public static int dimDelay() { return prefs.getInt("veille_s", 30); }
    public static void setDimDelay(int s) { prefs.edit().putInt("veille_s", Math.max(0, Math.min(600, s))).apply(); }

    // ---------------------------------------------------------------- masqués, favoris, reprise

    private static java.util.Set<String> set(String key) {
        return new java.util.HashSet<>(prefs.getStringSet(key, new java.util.HashSet<String>()));
    }
    private static void putSet(String key, java.util.Set<String> v) { prefs.edit().putStringSet(key, v).apply(); }

    public static boolean isHidden(String id) { return prefs.getStringSet("caches", java.util.Collections.<String>emptySet()).contains(id); }
    public static void setHidden(String id, boolean h) {
        java.util.Set<String> s = set("caches");
        if (h) s.add(id); else s.remove(id);
        putSet("caches", s);
        changed();
    }

    public static boolean isFav(String eid) { return prefs.getStringSet("favoris", java.util.Collections.<String>emptySet()).contains(eid); }
    public static void setFav(String eid, boolean f) {
        java.util.Set<String> s = set("favoris");
        if (f) s.add(eid); else s.remove(eid);
        putSet("favoris", s);
        changed();
    }

    public static synchronized Episode episode(String id) {
        if (id == null) return null;
        for (Episode e : episodes) if (e.id.equals(id)) return e;
        return null;
    }

    /** Podcasts visibles par l'enfant. */
    public static List<Podcast> visiblePodcasts() {
        ArrayList<Podcast> l = new ArrayList<>();
        for (Podcast p : podcasts()) if (!isHidden(p.id)) l.add(p);
        return l;
    }

    /** Épisodes téléchargés et visibles par l'enfant. */
    public static List<Episode> visibleEpisodes(String pid) {
        ArrayList<Episode> l = new ArrayList<>();
        for (Episode e : downloaded(pid)) if (!isHidden(e.id)) l.add(e);
        return l;
    }

    /** Favoris disponibles hors ligne (podcasts et épisodes masqués exclus). */
    public static List<Episode> favorites() {
        ArrayList<Episode> l = new ArrayList<>();
        for (String id : set("favoris")) {
            Episode e = episode(id);
            if (e != null && audio(e).exists() && !isHidden(e.id) && !isHidden(e.podcastId)) l.add(e);
        }
        Collections.sort(l, NEWEST_FIRST);
        return l;
    }

    /** Dernier épisode écouté et la liste d'où il vient (id de podcast ou FAVORIS). */
    public static final String FAVORIS = "__favoris__";
    public static void setLast(String eid, String listId) { prefs.edit().putString("dernier_ep", eid).putString("dernier_liste", listId).apply(); }
    public static String lastEpisodeId() { return prefs.getString("dernier_ep", null); }
    public static String lastListId() { return prefs.getString("dernier_liste", null); }

    // ---------------------------------------------------------------- réglages du soir

    public static int eveningBrightness() { return prefs.getInt("soir_luminosite", 0); }
    public static void setEveningBrightness(int b) { prefs.edit().putInt("soir_luminosite", b).apply(); }
    public static int eveningMaxVolume() { return prefs.getInt("soir_volmax", 0); }
    public static void setEveningMaxVolume(int v) { prefs.edit().putInt("soir_volmax", v).apply(); }
    /** Luminosité à appliquer maintenant (le soir, celle du soir si elle est réglée). */
    public static int effectiveBrightness() {
        int e = eveningBrightness();
        return e > 0 && inEvening() ? e : brightness();
    }
    /** Volume maximum à appliquer maintenant (le soir, celui du soir s'il est réglé). */
    public static int effectiveMaxVolume() {
        int e = eveningMaxVolume();
        return e > 0 && inEvening() ? Math.min(e, maxVolume()) : maxVolume();
    }

    // ---------------------------------------------------------------- sauvegarde / restauration

    private static final java.util.Set<String> NOT_SAVED = new java.util.HashSet<>(java.util.Arrays.asList(
            "journal", "maj_statut", "maj_verif", "derniere_maj", "ecoutes_jour", "ecoutes", "ecoute_ms",
            "soir_cle", "soir_ep", "soir_ms", "dernier_ep", "dernier_liste", "essais_echec"));

    private static JSONObject dumpPrefs(SharedPreferences sp, boolean filter) throws Exception {
        JSONObject o = new JSONObject();
        for (java.util.Map.Entry<String, ?> en : sp.getAll().entrySet()) {
            if (filter && NOT_SAVED.contains(en.getKey())) continue;
            Object v = en.getValue();
            JSONObject t = new JSONObject();
            if (v instanceof Boolean) t.put("b", v);
            else if (v instanceof Integer) t.put("i", v);
            else if (v instanceof Long) t.put("l", v);
            else if (v instanceof Float) t.put("f", ((Float) v).doubleValue());
            else if (v instanceof String) t.put("s", v);
            else if (v instanceof java.util.Set) { JSONArray a = new JSONArray(); for (Object x : (java.util.Set<?>) v) a.put(String.valueOf(x)); t.put("set", a); }
            else continue;
            o.put(en.getKey(), t);
        }
        return o;
    }

    private static void loadPrefs(SharedPreferences sp, JSONObject o) throws Exception {
        SharedPreferences.Editor ed = sp.edit();
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject t = o.getJSONObject(k);
            if (t.has("b")) ed.putBoolean(k, t.getBoolean("b"));
            else if (t.has("i")) ed.putInt(k, t.getInt("i"));
            else if (t.has("l")) ed.putLong(k, t.getLong("l"));
            else if (t.has("f")) ed.putFloat(k, (float) t.getDouble("f"));
            else if (t.has("s")) ed.putString(k, t.getString("s"));
            else if (t.has("set")) {
                JSONArray a = t.getJSONArray("set");
                java.util.Set<String> set = new java.util.HashSet<>();
                for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                ed.putStringSet(k, set);
            }
        }
        ed.apply();
    }

    /** Podcasts, réglages, favoris, épisodes masqués et positions de lecture, en JSON. */
    public static String exportBackup() throws Exception {
        JSONObject root = new JSONObject();
        root.put("appli", "Boîte à histoires");
        root.put("version", 1);
        root.put("date", System.currentTimeMillis());
        JSONArray pa = new JSONArray();
        for (Podcast p : podcasts()) {
            JSONObject o = new JSONObject();
            o.put("name", p.name);
            o.put("feed", p.feedUrl);
            o.put("skip", p.skipSeconds);
            o.put("keep", p.keep);
            pa.put(o);
        }
        root.put("podcasts", pa);
        root.put("reglages", dumpPrefs(prefs, true));
        root.put("positions", dumpPrefs(positions, false));
        return root.toString(2);
    }

    /** @return le nombre de podcasts ajoutés. */
    public static int importBackup(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!root.has("podcasts") || !root.has("reglages")) throw new Exception("ce fichier n'est pas une sauvegarde de Boîte à histoires.");
        loadPrefs(prefs, root.getJSONObject("reglages"));
        if (root.has("positions")) loadPrefs(positions, root.getJSONObject("positions"));
        int added = 0;
        JSONArray pa = root.getJSONArray("podcasts");
        for (int i = 0; i < pa.length(); i++) {
            JSONObject o = pa.getJSONObject(i);
            String feed = o.optString("feed", "");
            if (feed.isEmpty()) continue;
            Podcast p = addPodcast(o.optString("name", "Podcast"), feed, null, o.optInt("skip", 0));
            if (p != null) { added++; setKeep(p.id, o.optInt("keep", defaultKeep())); }
            else {
                Podcast ex = podcast(hash(feed.trim()));
                if (ex != null) { setSkip(ex.id, o.optInt("skip", ex.skipSeconds)); setKeep(ex.id, o.optInt("keep", ex.keep)); }
            }
        }
        changed();
        return added;
    }

    public static int retries() { return prefs.getInt("essais_echec", 0); }
    public static void setRetries(int n) { prefs.edit().putInt("essais_echec", n).apply(); }

    public static boolean firstRunDone() { return prefs.getBoolean("init", false); }
    public static void setFirstRunDone() { prefs.edit().putBoolean("init", true).apply(); }
}
