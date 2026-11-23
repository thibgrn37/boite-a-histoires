package fr.boiteahistoires.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Secours si le code parent est oublié : depuis l'ordinateur,
 *   adb shell am broadcast -a fr.boiteahistoires.app.SECOURS -n fr.boiteahistoires.app/.RescueReceiver
 * remet le code à 1234 et quitte le mode kiosque. Protégé par la permission DUMP (adb seulement).
 */
public class RescueReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Store.init(context);
        Store.setParentCode("1234");
        Store.setKiosk(false);
        Kiosk.applyPolicies(context);
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra("secours", true);
        try { context.startActivity(i); } catch (Exception ignored) { }
    }
}
