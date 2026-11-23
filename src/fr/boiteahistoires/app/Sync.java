package fr.boiteahistoires.app;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Mise à jour des flux et téléchargement des épisodes. */
public final class Sync {
    static final int JOB_PERIODIC = 1;
    static final int JOB_NOW = 2;
    static final long MIN_FREE = 300L * 1024 * 1024;

    static final AtomicBoolean running = new AtomicBoolean(false);
    static volatile boolean again = false;

    private Sync() {}

    /** Toutes les 6 h, uniquement en Wi-Fi (réseau non facturé). */
    public static void schedulePeriodic(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js.getPendingJob(JOB_PERIODIC) != null) return;
        JobInfo ji = new JobInfo.Builder(JOB_PERIODIC, new ComponentName(c, SyncJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setPeriodic(6L * 3600 * 1000)
                .setPersisted(true)
                .build();
        js.schedule(ji);
    }

    /** Dès que possible (dès qu'il y a du réseau). */
    public static void runNow(Context c) {
        if (running.get()) { again = true; return; }
        JobScheduler js = c.getSystemService(JobScheduler.class);
        JobInfo ji = new JobInfo.Builder(JOB_NOW, new ComponentName(c, SyncJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(60000, JobInfo.BACKOFF_POLICY_LINEAR)
                .build();
        js.schedule(ji);
        Store.setStatus("Mise à jour prévue (attente du réseau)…");
    }

    /** @return true si tout a été traité. */
    static boolean runAll(AtomicBoolean stop) {
        boolean ok = true;
        Store.log("Mise à jour lancée");
        migrateImages();
        seedDefaults();
        do {
            again = false;
            List<Store.Podcast> list = Store.podcasts();
            for (Store.Podcast p : list) {
                if (stop.get()) return false;
                try {
                    if (!syncPodcast(p, stop)) ok = false;
                } catch (Exception e) {
                    ok = false;
                    Log.e(Store.TAG, "sync " + p.name, e);
                    Store.setStatus("Problème avec « " + p.name + " » : " + e.getMessage());
                    Store.log("Problème avec « " + p.name + " » : " + e);
                    sleep(2000);
                }
            }
        } while (again && !stop.get());
        if (stop.get()) return false;
        Store.setLastSync(System.currentTimeMillis());
        if (ok) Store.setStatus("Tout est à jour ✔");
        Store.log("Mise à jour terminée" + (ok ? "" : " avec des erreurs"));
        return true;
    }

    /** @return false si on a manqué de place. */
    static boolean syncPodcast(Store.Podcast p, AtomicBoolean stop) throws Exception {
        Store.setStatus("Lecture du flux « " + p.name + " »…");
        FeedParser.Feed feed = FeedParser.parse(Net.bytes(p.feedUrl));
        if (feed.imageUrl != null) Store.setImageUrl(p.id, feed.imageUrl);
        Store.mergeEpisodes(p.id, feed.items);
        Store.Podcast fresh = Store.podcast(p.id);
        if (fresh == null) return true; // supprimé entre-temps
        p = fresh;

        File cover = Store.cover(p.id);
        if (!cover.exists() && p.imageUrl != null) {
            try { Net.download(p.imageUrl, cover, stop); Store.changed(); } catch (Exception e) { Log.w(Store.TAG, "couverture", e); }
        }

        List<Store.Episode> eps = Store.episodes(p.id);
        int keep = Math.min(p.keep, eps.size());
        int todo = 0;
        for (int i = 0; i < keep; i++) if (!Store.audio(eps.get(i)).exists()) todo++;
        int done = 0;
        for (int i = 0; i < keep; i++) {
            if (stop.get()) return true;
            Store.Episode e = eps.get(i);
            if (Store.podcast(p.id) == null) return true;
            if (!Store.image(e).exists()) {
                String img = episodeImage(e, p);
                if (img != null) {
                    try { Net.download(img, Store.image(e), stop); } catch (Exception ex) { Log.w(Store.TAG, "image", ex); }
                }
            }
            File a = Store.audio(e);
            if (a.exists()) continue;
            if (a.getParentFile().getUsableSpace() < MIN_FREE) {
                Store.setStatus("Plus assez de place sur le téléphone : réduis le nombre d'épisodes gardés.");
                return false;
            }
            done++;
            Store.setStatus("Téléchargement « " + p.name + " » " + done + "/" + todo + " : " + e.title);
            Net.download(e.audioUrl, a, stop);
            Store.changed();
        }
        // On ne garde que les N plus récents (réglable par podcast).
        for (int i = keep; i < eps.size(); i++) {
            Store.Episode e = eps.get(i);
            File a = Store.audio(e);
            if (a.exists() && !e.id.equals(Player.currentId())) {
                a.delete();
                Store.image(e).delete();
            }
        }
        Store.changed();
        return true;
    }

    private static final java.util.regex.Pattern OG_IMAGE = java.util.regex.Pattern.compile(
            "<meta[^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"'][^>]*content=[\"']([^\"']+)[\"']"
            + "|<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"'](?:og:image|twitter:image)[\"']",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Page web propre à l'épisode (pas juste l'accueil du site). */
    static boolean isEpisodePage(String url) {
        if (url == null) return false;
        try {
            String path = new java.net.URL(url).getPath();
            return path != null && path.replaceAll("/+$", "").split("/").length > 2;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Image propre à l'épisode. Priorité à l'illustration de la page web de l'épisode (og:image),
     * car beaucoup de flux (Radio France) reprennent l'image de l'émission pour chaque épisode.
     */
    static String episodeImage(Store.Episode e, Store.Podcast p) {
        if (isEpisodePage(e.pageUrl)) {
            if (e.pageImage == null) {
                String found = "";
                try {
                    String html = Net.text(e.pageUrl);
                    java.util.regex.Matcher m = OG_IMAGE.matcher(html);
                    if (m.find()) found = (m.group(1) != null ? m.group(1) : m.group(2)).replace("&amp;", "&");
                    Store.log(found.isEmpty() ? "Pas d'image trouvée sur la page de « " + e.title + " »"
                            : "Image trouvée pour « " + e.title + " »");
                } catch (Exception ex) {
                    Store.log("Page de « " + e.title + " » illisible : " + ex.getMessage());
                    return null; // on réessaiera plus tard
                }
                Store.setPageImage(e.id, found);
                e.pageImage = found;
            }
            if (!e.pageImage.isEmpty() && !e.pageImage.equals(p.imageUrl)) return e.pageImage;
        }
        if (e.imageUrl != null && !e.imageUrl.equals(p.imageUrl)) return e.imageUrl;
        return null;
    }

    /** Ajoute une fois les podcasts proposés par défaut : flux direct, sinon lien Apple, sinon recherche du nom. */
    static void seedDefaults() {
        String[][] wanted = {{"Les aventures du professeur Caillou", "caillou",
                "https://radiofrance-podcast.net/podcast09/podcast_9cfc0cf4-6d0d-4ffa-a327-0c099c366d3c.xml",
                "https://podcasts.apple.com/fr/podcast/les-aventures-du-professeur-caillou/id1811582936"}};
        for (String[] w : wanted) {
            String key = "seed3_" + Store.hash(w[0]);
            if (Store.flag(key)) continue;
            boolean present = false;
            for (Store.Podcast p : Store.podcasts()) {
                if (p.feedUrl.contains("9cfc0cf4-6d0d-4ffa-a327-0c099c366d3c")) present = true;
                else if (p.name.toLowerCase().contains("cailloux")) {
                    // 1.5 à 1.7 avaient trouvé un autre podcast (« professeur cailloux ») : on le retire.
                    Store.removePodcast(p.id);
                    Store.log("Mauvais podcast « " + p.name + " » retiré");
                }
            }
            if (present) { Store.setFlag(key); continue; }
            FeedParser.Feed f = null;
            String feed = null;
            for (int attempt = 0; attempt < 2 && f == null; attempt++) {
                try {
                    feed = attempt == 0 ? w[2] : Net.resolveFeed(w[3], "");
                    f = FeedParser.parse(Net.bytes(feed));
                    Store.log("« " + w[0] + " » : flux lu " + feed);
                } catch (Throwable ex) {
                    f = null;
                    Store.log("« " + w[0] + " » essai " + (attempt + 1) + " raté : " + ex);
                }
            }
            if (f == null) continue;
            try {
                Store.Podcast p = Store.addPodcast(f.title.isEmpty() ? w[0] : f.title, feed, f.imageUrl, 0);
                if (p != null) Store.mergeEpisodes(p.id, f.items);
                Store.log("« " + w[0] + " » ajouté (" + f.items.size() + " épisodes)");
                Store.setFlag(key);
            } catch (Throwable ex) {
                Store.log("Ajout de « " + w[0] + " » impossible : " + ex);
            }
        }
    }

    /** Une seule fois en 1.6 : on reprend les images d'épisodes (les anciennes étaient celles de l'émission). */
    static void migrateImages() {
        if (Store.flag("images_v2")) return;
        for (Store.Podcast p : Store.podcasts()) {
            for (Store.Episode e : Store.episodes(p.id)) {
                Store.image(e).delete();
                if (e.pageImage != null) Store.setPageImage(e.id, null);
                e.pageImage = null;
            }
        }
        Store.setFlag("images_v2");
        Store.log("Images d'épisodes remises à zéro, elles vont être retéléchargées.");
    }

    static String lastSyncText() {
        long t = Store.lastSync();
        if (t == 0) return "Jamais";
        return new SimpleDateFormat("EEEE d MMMM 'à' HH:mm", Locale.FRANCE).format(new Date(t));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
