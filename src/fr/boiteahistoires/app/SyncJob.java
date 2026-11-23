package fr.boiteahistoires.app;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

public class SyncJob extends JobService {
    private volatile AtomicBoolean stop;

    @Override
    public boolean onStartJob(final JobParameters params) {
        Store.init(this);
        if (!Sync.running.compareAndSet(false, true)) {
            Sync.again = true;
            return false;
        }
        final AtomicBoolean s = new AtomicBoolean(false);
        stop = s;
        new Thread(new Runnable() {
            @Override public void run() {
                boolean complete = false;
                try {
                    complete = Sync.runAll(s);
                    if (!s.get()) Updater.maybeCheck(SyncJob.this);
                } catch (Throwable t) {
                    Log.e(Store.TAG, "sync", t);
                } finally {
                    Sync.running.set(false);
                    if (!s.get()) {
                        try { jobFinished(params, !complete); } catch (Exception ignored) { }
                    }
                }
            }
        }, "sync").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        AtomicBoolean s = stop;
        if (s != null) s.set(true);
        Store.setStatus("Mise à jour interrompue, elle reprendra plus tard.");
        return true;
    }
}
