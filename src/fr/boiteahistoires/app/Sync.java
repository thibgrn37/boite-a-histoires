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
                    sleep(2000);
                }
            }
        } while (again && !stop.get());
        if (stop.get()) return false;
        Store.setLastSync(System.currentTimeMillis());
        if (ok) Store.setStatus("Tout est à jour ✔");
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
            if (e.imageUrl != null && !e.imageUrl.equals(p.imageUrl) && !Store.image(e).exists()) {
                try { Net.download(e.imageUrl, Store.image(e), stop); } catch (Exception ex) { Log.w(Store.TAG, "image", ex); }
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

    static String lastSyncText() {
        long t = Store.lastSync();
        if (t == 0) return "Jamais";
        return new SimpleDateFormat("EEEE d MMMM 'à' HH:mm", Locale.FRANCE).format(new Date(t));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
