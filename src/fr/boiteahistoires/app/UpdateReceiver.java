package fr.boiteahistoires.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Résultat de l'installation d'une mise à jour. */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Store.init(context);
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Appli pas encore « propriétaire de l'appareil » : Android demande une confirmation.
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(confirm); } catch (Exception ignored) { }
            }
            Store.setUpdateStatus("Confirme l'installation à l'écran.");
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Store.setUpdateStatus("Mise à jour installée ✔");
        } else {
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Store.setUpdateStatus("Échec de l'installation : " + (msg == null ? status : msg));
        }
    }
}
