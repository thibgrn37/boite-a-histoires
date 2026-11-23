package fr.boiteahistoires.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Store.init(context);
        Sync.schedulePeriodic(context);
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            // Après une mise à jour automatique : on rouvre l'appli (et donc le mode kiosque).
            Store.setUpdateStatus("Mise à jour installée ✔ (version " + Updater.currentVersionName(context) + ")");
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(i); } catch (Exception ignored) { }
        }
    }
}
