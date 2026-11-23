package fr.boiteahistoires.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public class App extends Application {
    static final String BESTIOLES = "https://radiofrance-podcast.net/podcast09/podcast_a80ecbd5-df3d-4c9d-bee7-4e3d9efc1974.xml";

    @Override
    public void onCreate() {
        super.onCreate();
        Store.init(this);
        NotificationChannel ch = new NotificationChannel(PlayerService.CHANNEL, "Lecture", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        VolumeCap.install(this);
        if (!Store.firstRunDone()) {
            Store.addPodcast("Les Bestioles", BESTIOLES, null, 6);
            Store.setFirstRunDone();
            Sync.runNow(this);
        }
        Sync.schedulePeriodic(this);
    }
}
