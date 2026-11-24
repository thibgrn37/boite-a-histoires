package fr.boiteahistoires.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Espace parents (protégé par code). */
final class ParentScreen {
    private final MainActivity a;
    private final ScrollView scroll;
    private TextView playedTv, logTv, updTv, statusTv, lastTv, spaceTv, addResult, kioskInfo, batteryTv;
    private LinearLayout podcastsBox;
    private Button kioskBtn, settingsBtn, batteryBtn, releaseBtn, addBtn;
    private boolean adding = false;

    ParentScreen(MainActivity act) {
        a = act;
        scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.CREAM);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(a, 16);
        col.setPadding(p, p, p, Ui.dp(a, 48));
        scroll.addView(col);

        // En-tête
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(a, "Espace parents", 24, Ui.DARK, true);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button close = Ui.button(a, "Retour à l'appli", Ui.GREEN);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.closeParent(); }
        });
        head.addView(close);
        col.addView(head);

        // Ajouter un podcast
        LinearLayout add = section(col, "Ajouter un podcast");
        final EditText name = edit("Nom du podcast", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        add.addView(name);
        final EditText link = edit("Lien RSS ou Apple Podcasts", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        add.addView(link);
        add.addView(small("Sans lien, le nom est cherché sur Apple Podcasts. Le téléchargement démarre tout de suite."));
        addBtn = Ui.button(a, "Ajouter", Ui.ORANGE);
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { addPodcast(name, link); }
        });
        add.addView(addBtn, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));
        addResult = Ui.text(a, "", 15, Ui.DARK, false);
        add.addView(addResult);

        // Téléchargements
        LinearLayout dl = section(col, "Téléchargements");
        statusTv = Ui.text(a, "", 15, Ui.DARK, false);
        dl.addView(statusTv);
        lastTv = small("");
        dl.addView(lastTv);
        spaceTv = small("");
        dl.addView(spaceTv);
        dl.addView(small("Automatique toutes les 6 heures quand le téléphone est en Wi-Fi."));
        Button now = Ui.button(a, "Mettre à jour maintenant", Ui.BLUE);
        now.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Sync.runNow(a); }
        });
        dl.addView(now, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));

        // Podcasts
        podcastsBox = section(col, "Mes podcasts");

        // Limite d'épisodes
        LinearLayout lim = section(col, "Limites d'écoute");
        lim.addView(Ui.text(a, "La journée", 16, Ui.DARK, true));
        limitBlock(lim, false);
        View sep = new View(a);
        sep.setBackgroundColor(0x22000000);
        lim.addView(sep, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, 1, 0, 14, 0, 10));
        lim.addView(Ui.text(a, "Le soir (heures du mode nuit)", 16, Ui.DARK, true));
        limitBlock(lim, true);
        playedTv = small("");
        lim.addView(playedTv);
        lim.addView(small("Quand une limite est atteinte, l'histoire en cours va jusqu'au bout, sans pouvoir changer d'histoire (précédent, suivant et autres épisodes bloqués), puis la lecture s'arrête et une lune s'affiche. On compte les épisodes écoutés jusqu'au bout et les minutes de vraie lecture (pas la pause). Si une limite du soir est réglée, le soir repart avec son propre compteur à zéro ; la limite de la journée ne compte alors que la journée. Les heures du soir se règlent dans « Mode nuit »."));
        Button resetBtn = Ui.button(a, "Remettre le compteur à zéro", Ui.BLUE);
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Store.resetPlayed(); refresh(); }
        });
        lim.addView(resetBtn, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));

        // Luminosité
        LinearLayout lum = section(col, "Luminosité de l'écran");
        final TextView lumTv = Ui.text(a, "", 16, Ui.DARK, true);
        lum.addView(lumTv);
        SeekBar lsb = new SeekBar(a);
        lsb.setMax(100);
        lsb.setProgress(Store.brightness());
        lumTv.setText(Store.brightness() == 0 ? "Réglage du téléphone" : Store.brightness() + " %");
        lsb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                int val = v == 0 ? 0 : Math.max(3, v);
                lumTv.setText(val == 0 ? "Réglage du téléphone" : val + " %");
                Store.setBrightness(val);
                a.applyBrightness();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        lum.addView(lsb, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 48), 0, 4, 0, 0));
        lum.addView(small("Tout à gauche = luminosité normale du téléphone. Sinon, l'appli garde toujours cette luminosité."));

        // Jour / nuit
        LinearLayout nuit = section(col, "Mode nuit (fond noir)");
        final TextView modeTv = Ui.text(a, "", 16, Ui.DARK, true);
        nuit.addView(modeTv);
        LinearLayout modes = new LinearLayout(a);
        final String[] modeNames = {"Selon l'heure", "Toujours jour", "Toujours nuit"};
        for (int i = 0; i < 3; i++) {
            final int m = i;
            Button b = Ui.button(a, modeNames[i], Ui.BLUE);
            b.setTextSize(14);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { Store.setNightMode(m); modeTv.setText(nightText()); a.applyTheme(); }
            });
            modes.addView(b, Ui.lpMargins(a, 0, ViewGroup.LayoutParams.WRAP_CONTENT, i == 0 ? 0 : 4, 6, 0, 6));
            ((LinearLayout.LayoutParams) b.getLayoutParams()).weight = 1;
        }
        nuit.addView(modes);
        modeTv.setText(nightText());
        final TextView startTv = Ui.text(a, "", 15, Ui.DARK, false);
        final TextView endTv = Ui.text(a, "", 15, Ui.DARK, false);
        startTv.setText("Nuit à partir de " + Store.nightStart() + " h");
        endTv.setText("Jour à partir de " + Store.nightEnd() + " h");
        nuit.addView(stepperTv(startTv, new Runnable() {
            @Override public void run() { Store.setNightStart(Store.nightStart() - 1); startTv.setText("Nuit à partir de " + Store.nightStart() + " h"); modeTv.setText(nightText()); }
        }, new Runnable() {
            @Override public void run() { Store.setNightStart(Store.nightStart() + 1); startTv.setText("Nuit à partir de " + Store.nightStart() + " h"); modeTv.setText(nightText()); }
        }));
        nuit.addView(stepperTv(endTv, new Runnable() {
            @Override public void run() { Store.setNightEnd(Store.nightEnd() - 1); endTv.setText("Jour à partir de " + Store.nightEnd() + " h"); modeTv.setText(nightText()); }
        }, new Runnable() {
            @Override public void run() { Store.setNightEnd(Store.nightEnd() + 1); endTv.setText("Jour à partir de " + Store.nightEnd() + " h"); modeTv.setText(nightText()); }
        }));
        nuit.addView(small("Le soir, les écrans de l'enfant passent sur fond noir pour ne pas éblouir."));

        // Assombrissement
        LinearLayout veille = section(col, "Écran qui s'assombrit");
        final TextView dimTv = Ui.text(a, "", 15, Ui.DARK, false);
        dimTv.setText(dimText());
        veille.addView(stepperTv(dimTv, new Runnable() {
            @Override public void run() { Store.setDimDelay(prevDim(Store.dimDelay())); dimTv.setText(dimText()); }
        }, new Runnable() {
            @Override public void run() { Store.setDimDelay(nextDim(Store.dimDelay())); dimTv.setText(dimText()); }
        }));
        veille.addView(small("Sans toucher l'écran, il devient très sombre ; le son continue. Un toucher le rallume (ce toucher n'appuie sur aucun bouton). Pendant la lecture, l'écran ne s'éteint plus complètement."));

        // Volume
        LinearLayout vol = section(col, "Volume maximum");
        final TextView volTv = Ui.text(a, Store.maxVolume() + " %", 16, Ui.DARK, true);
        vol.addView(volTv);
        SeekBar sb = new SeekBar(a);
        sb.setMax(90);
        sb.setProgress(Store.maxVolume() - 10);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                int val = v + 10;
                volTv.setText(val + " %");
                Store.setMaxVolume(val);
                VolumeCap.enforce(a);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        vol.addView(sb, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 48), 0, 4, 0, 0));
        vol.addView(small("Les boutons de volume du téléphone ne pourront pas aller au-delà."));

        // Le soir
        LinearLayout soir = section(col, "Le soir (heures du mode nuit)");
        soir.addView(percentSlider("Luminosité le soir", Store.eveningBrightness(), 3, new IntSetter() {
            @Override public void set(int v) { Store.setEveningBrightness(v); a.applyBrightness(); }
        }));
        soir.addView(percentSlider("Volume maximum le soir", Store.eveningMaxVolume(), 5, new IntSetter() {
            @Override public void set(int v) { Store.setEveningMaxVolume(v); VolumeCap.enforce(a); }
        }));
        soir.addView(small("Tout à gauche = comme la journée. Sinon, le soir l'écran et le son ne dépassent pas ces réglages (le volume ne dépasse jamais le maximum de la journée)."));

        // Sauvegarde
        LinearLayout sav = section(col, "Sauvegarde et restauration");
        sav.addView(small("Enregistre dans un fichier la liste des podcasts, tous les réglages, les favoris, les épisodes masqués et où il en est dans chaque histoire. Garde-le par exemple dans Téléchargements ou envoie-le-toi."));
        Button saveBtn = Ui.button(a, "Sauvegarder dans un fichier…", Ui.BLUE);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                unpin();
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "boite-a-histoires-sauvegarde-"
                        + new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(new java.util.Date()) + ".json");
                try { a.startActivityForResult(i, REQ_SAVE); } catch (Exception e) { savTv.setText("Impossible d'ouvrir le choix du fichier : " + e.getMessage()); }
            }
        });
        sav.addView(saveBtn, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));
        Button restBtn = Ui.button(a, "Restaurer depuis un fichier…", Ui.GREY);
        restBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                unpin();
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { a.startActivityForResult(i, REQ_RESTORE); } catch (Exception e) { savTv.setText("Impossible d'ouvrir le choix du fichier : " + e.getMessage()); }
            }
        });
        sav.addView(restBtn, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));
        savTv = small("");
        sav.addView(savTv);

        // Code
        LinearLayout code = section(col, "Code parent");
        final EditText codeEt = edit("Nouveau code (4 à 8 chiffres)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        code.addView(codeEt);
        final TextView codeRes = small("Pour ouvrir cet espace : appui long (3 s) tout en haut à droite de l'écran d'accueil.");
        Button codeBtn = Ui.button(a, "Changer le code", Ui.BLUE);
        codeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String c = codeEt.getText().toString().trim();
                if (c.length() < 4 || c.length() > 8 || !c.matches("\\d+")) {
                    codeRes.setText("Le code doit faire 4 à 8 chiffres.");
                    return;
                }
                Store.setParentCode(c);
                codeEt.setText("");
                codeRes.setText("Nouveau code enregistré ✔");
            }
        });
        code.addView(codeBtn, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));
        code.addView(codeRes);

        // Kiosque
        LinearLayout k = section(col, "Mode kiosque (téléphone bloqué sur l'appli)");
        kioskInfo = Ui.text(a, "", 15, Ui.DARK, false);
        k.addView(kioskInfo);
        kioskBtn = Ui.button(a, "", Ui.ORANGE);
        kioskBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Store.setKiosk(!Store.kiosk());
                Kiosk.sync(a);
                a.applyImmersive();
                refresh();
            }
        });
        k.addView(kioskBtn, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));
        settingsBtn = Ui.button(a, "Ouvrir les réglages Android", Ui.GREY);
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { a.startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) { }
            }
        });
        k.addView(settingsBtn, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));
        batteryTv = small("");
        k.addView(batteryTv);
        batteryBtn = Ui.button(a, "Batterie : ne pas restreindre l'appli", Ui.GREY);
        batteryBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { a.startActivity(Kiosk.batteryIntent(a)); } catch (Exception ignored) { }
            }
        });
        k.addView(batteryBtn, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));
        releaseBtn = Ui.button(a, "Retirer les droits d'administration (avant désinstallation)", 0xFFD32F2F);
        releaseBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(a)
                        .setTitle("Retirer les droits ?")
                        .setMessage("Le mode kiosque ne sera plus possible. Pour le remettre, il faudra refaire la commande adb depuis l'ordinateur.")
                        .setPositiveButton("Retirer", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                Kiosk.releaseOwner(a);
                                a.applyImmersive();
                                refresh();
                            }
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
            }
        });
        k.addView(releaseBtn, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 16, 0, 0));

        // Mises à jour de l'appli
        LinearLayout up = section(col, "Mises à jour de l'appli");
        up.addView(Ui.text(a, "Version installée : " + Updater.currentVersionName(a), 15, Ui.DARK, false));
        updTv = small("");
        up.addView(updTv);
        up.addView(small("Vérifiées automatiquement deux fois par jour en Wi-Fi, installées sans rien demander."));
        final EditText urlEt = edit("Adresse de version.json", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlEt.setText(Store.updateUrl());
        urlEt.setTextSize(12);
        up.addView(urlEt);
        Button check = Ui.button(a, "Vérifier les mises à jour", Ui.BLUE);
        check.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Store.setUpdateUrl(urlEt.getText().toString());
                // L'écran de confirmation d'Android ne peut pas s'ouvrir pendant l'épinglage.
                if (!Kiosk.isOwner(a) && Kiosk.isLocked(a)) { try { a.stopLockTask(); } catch (Exception ignored) { } }
                Store.setUpdateStatus("Recherche d'une nouvelle version…");
                new Thread(new Runnable() {
                    @Override public void run() { Updater.check(a.getApplicationContext(), true); }
                }).start();
            }
        });
        up.addView(check, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));

        LinearLayout lg = section(col, "Journal (pour comprendre un problème)");
        logTv = small("");
        logTv.setTextIsSelectable(true);
        lg.addView(logTv);

        TextView ver = small("Boîte à histoires " + Updater.currentVersionName(a));
        ver.setGravity(Gravity.CENTER);
        col.addView(ver, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 16, 0, 0));

        refresh();
    }

    View view() { return scroll; }

    private LinearLayout section(LinearLayout parent, String title) {
        LinearLayout s = new LinearLayout(a);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setBackground(Ui.rounded(Ui.WHITE, Ui.dp(a, 18)));
        int p = Ui.dp(a, 16);
        s.setPadding(p, p, p, p);
        TextView t = Ui.text(a, title, 18, Ui.ORANGE, true);
        t.setPadding(0, 0, 0, Ui.dp(a, 8));
        s.addView(t);
        parent.addView(s, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 16, 0, 0));
        return s;
    }

    private EditText edit(String hint, int type) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(16);
        return e;
    }

    private TextView small(String s) {
        TextView t = Ui.text(a, s, 13, Ui.GREY, false);
        t.setPadding(0, Ui.dp(a, 4), 0, 0);
        return t;
    }

    void refresh() {
        String lt = "Aujourd'hui : " + Store.playedToday() + " épisode(s) fini(s), " + Store.listenedTodayMs() / 60000 + " min d'écoute";
        if (Store.inEvening()) lt += "\nCe soir : " + Store.playedEvening() + " épisode(s), " + Store.listenedEveningMs() / 60000 + " min";
        playedTv.setText(lt);
        String j = Store.journal();
        logTv.setText(j.isEmpty() ? "Rien pour l'instant." : j);
        String us = Store.updateStatus();
        updTv.setText(us.isEmpty() ? "Pas encore vérifié." : us);
        String st = Store.status();
        if (Sync.running.get() && st.isEmpty()) st = "Mise à jour en cours…";
        statusTv.setText(st.isEmpty() ? "Aucune mise à jour en cours." : st);
        lastTv.setText("Dernière mise à jour complète : " + Sync.lastSyncText());
        long free = Store.context().getFilesDir().getUsableSpace();
        spaceTv.setText(String.format(Locale.FRANCE, "Espace utilisé : %.1f Go — libre : %.1f Go",
                Store.usedBytes() / 1e9, free / 1e9));
        rebuildPodcasts();

        boolean owner = Kiosk.isOwner(a);
        boolean on = Store.kiosk();
        if (owner) {
            kioskInfo.setText(on ? "Activé : l'appli remplace l'écran d'accueil, pas de barre d'état ni de sortie possible."
                    : "Désactivé : le téléphone fonctionne normalement.");
            kioskBtn.setText(on ? "Quitter le mode kiosque" : "Activer le mode kiosque");
            kioskBtn.setEnabled(true);
            kioskBtn.setAlpha(1f);
            releaseBtn.setVisibility(View.VISIBLE);
        } else {
            kioskInfo.setText(on ? "Activé (épinglage) : l'appli s'épingle à l'écran et cache les barres. Pour sortir sans code : appui long simultané sur Retour + Aperçu (ou glisser-maintenir selon les gestes)."
                    : "Désactivé : le téléphone fonctionne normalement.");
            kioskBtn.setText(on ? "Quitter le mode kiosque" : "Activer le mode kiosque (épinglage)");
            kioskBtn.setEnabled(true);
            kioskBtn.setAlpha(1f);
            releaseBtn.setVisibility(View.GONE);
        }
        settingsBtn.setVisibility(on ? View.GONE : View.VISIBLE);
        boolean bat = Kiosk.ignoresBattery(a);
        boolean locked = on;
        batteryTv.setText(bat ? "Optimisation de batterie : désactivée pour l'appli ✔"
                : (locked ? "Conseillé : désactiver l'optimisation de batterie (quitte d'abord le mode kiosque pour y accéder)."
                          : "Conseillé : désactiver l'optimisation de batterie pour que les téléchargements se fassent."));
        batteryBtn.setVisibility(bat || locked ? View.GONE : View.VISIBLE);
    }

    private void rebuildPodcasts() {
        while (podcastsBox.getChildCount() > 1) podcastsBox.removeViewAt(1);
        List<Store.Podcast> list = Store.podcasts();
        if (list.isEmpty()) podcastsBox.addView(small("Aucun podcast."));
        for (final Store.Podcast p : list) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 10));

            LinearLayout top = new LinearLayout(a);
            top.setGravity(Gravity.CENTER_VERTICAL);
            Ui.Card card = new Ui.Card(a);
            card.setPlaceholder(p.name);
            card.letter.setTextSize(20);
            Images.load(card.image, Store.cover(p.id), Ui.dp(a, 56));
            top.addView(card, new LinearLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 56)));
            LinearLayout info = new LinearLayout(a);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(Ui.dp(a, 12), 0, 0, 0);
            info.addView(Ui.text(a, p.name + (Store.isHidden(p.id) ? " (masqué)" : ""), 16, Store.isHidden(p.id) ? Ui.GREY : Ui.DARK, true));
            int dl = Store.downloaded(p.id).size();
            int hid = dl - Store.visibleEpisodes(p.id).size();
            int known = Store.episodes(p.id).size();
            info.addView(small(dl + " épisode(s) sur le téléphone, " + known + " connu(s)" + (hid > 0 ? ", " + hid + " masqué(s)" : "")));
            top.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(top);

            row.addView(stepper("Passer le début : " + p.skipSeconds + " s", new Runnable() {
                @Override public void run() { Store.setSkip(p.id, p.skipSeconds - 1); }
            }, new Runnable() {
                @Override public void run() { Store.setSkip(p.id, p.skipSeconds + 1); }
            }));
            row.addView(stepper("Épisodes gardés : " + p.keep, new Runnable() {
                @Override public void run() { Store.setKeep(p.id, p.keep <= 5 ? p.keep - 1 : p.keep - 5); }
            }, new Runnable() {
                @Override public void run() { Store.setKeep(p.id, p.keep < 5 ? p.keep + 1 : p.keep + 5); }
            }));

            LinearLayout btns = new LinearLayout(a);
            final boolean hidden = Store.isHidden(p.id);
            Button hide = Ui.button(a, hidden ? "Afficher" : "Masquer", hidden ? Ui.GREEN : Ui.GREY);
            hide.setTextSize(14);
            hide.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { Store.setHidden(p.id, !hidden); }
            });
            btns.addView(hide, Ui.lpMargins(a, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 6, 4, 0));
            ((LinearLayout.LayoutParams) hide.getLayoutParams()).weight = 1;
            Button epBtn = Ui.button(a, "Épisodes visibles…", Ui.BLUE);
            epBtn.setTextSize(14);
            epBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { chooseEpisodes(p); }
            });
            btns.addView(epBtn, Ui.lpMargins(a, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 4, 6, 0, 0));
            ((LinearLayout.LayoutParams) epBtn.getLayoutParams()).weight = 1;
            row.addView(btns);

            Button del = Ui.button(a, "Supprimer ce podcast", 0xFFE57373);
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new AlertDialog.Builder(a)
                            .setTitle("Supprimer « " + p.name + " » ?")
                            .setMessage("Tous ses épisodes téléchargés seront effacés du téléphone.")
                            .setPositiveButton("Supprimer", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) {
                                    Store.Episode cur = Player.get(a).current();
                                    if (cur != null && cur.podcastId.equals(p.id)) Player.get(a).pause();
                                    Store.removePodcast(p.id);
                                }
                            })
                            .setNegativeButton("Annuler", null)
                            .show();
                }
            });
            row.addView(del, Ui.lpMargins(a, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 6, 0, 0));

            View sep = new View(a);
            sep.setBackgroundColor(0x22000000);
            row.addView(sep, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, 1, 0, 12, 0, 0));
            podcastsBox.addView(row);
        }
    }

    private View stepper2(final TextView label) {
        label.setText(limitText());
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button m = Ui.button(a, "−", Ui.BLUE);
        m.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Store.setEpisodeLimit(Store.episodeLimit() - 1); label.setText(limitText()); }
        });
        Button p = Ui.button(a, "+", Ui.BLUE);
        p.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Store.setEpisodeLimit(Store.episodeLimit() + 1); label.setText(limitText()); }
        });
        r.addView(m, Ui.lpMargins(a, Ui.dp(a, 56), Ui.dp(a, 44), 4, 0, 4, 0));
        r.addView(p, Ui.lpMargins(a, Ui.dp(a, 56), Ui.dp(a, 44), 4, 0, 0, 0));
        return r;
    }

    private static String limitText() {
        int l = Store.episodeLimit();
        return l == 0 ? "Pas de limite" : l + " épisode" + (l > 1 ? "s" : "") + " par jour";
    }

    private static final int[] DIMS = {0, 15, 30, 60, 120, 300};
    private static int nextDim(int v) { for (int d : DIMS) if (d > v) return d; return DIMS[DIMS.length - 1]; }
    private static int prevDim(int v) { int r = 0; for (int d : DIMS) if (d < v) r = d; return r; }
    private static String dimText() {
        int d = Store.dimDelay();
        if (d == 0) return "Jamais";
        return "Après " + (d < 60 ? d + " secondes" : (d / 60) + " minute" + (d >= 120 ? "s" : ""));
    }
    private static String nightText() {
        switch (Store.nightMode()) {
            case 1: return "Toujours en mode jour";
            case 2: return "Toujours en mode nuit";
            default: return "Selon l'heure : nuit de " + Store.nightStart() + " h à " + Store.nightEnd() + " h"
                    + (Store.isNightNow() ? " (c'est la nuit)" : " (c'est le jour)");
        }
    }

    /** Réglage d'une limite : choix épisodes / minutes + valeur. */
    private void limitBlock(LinearLayout box, final boolean evening) {
        final TextView val = Ui.text(a, "", 15, Ui.DARK, false);
        LinearLayout types = new LinearLayout(a);
        final Button[] tb = new Button[2];
        String[] names = {"En épisodes", "En minutes"};
        for (int i = 0; i < 2; i++) {
            final int t = i;
            tb[i] = Ui.button(a, names[i], Ui.BLUE);
            tb[i].setTextSize(14);
            tb[i].setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (evening) Store.setEveningType(t); else Store.setLimitType(t);
                    paintLimit(val, tb, evening);
                }
            });
            LinearLayout.LayoutParams lp = Ui.lpMargins(a, 0, ViewGroup.LayoutParams.WRAP_CONTENT, i == 0 ? 0 : 6, 6, 0, 0);
            lp.weight = 1;
            types.addView(tb[i], lp);
        }
        box.addView(types);
        box.addView(stepperTv(val, new Runnable() {
            @Override public void run() { step(evening, -1); paintLimit(val, tb, evening); }
        }, new Runnable() {
            @Override public void run() { step(evening, +1); paintLimit(val, tb, evening); }
        }));
        paintLimit(val, tb, evening);
    }

    private static void step(boolean evening, int dir) {
        int type = evening ? Store.eveningType() : Store.limitType();
        int v = evening ? Store.eveningLimit() : Store.episodeLimit();
        if (type == 1) {
            int[] mins = {0, 5, 10, 15, 20, 30, 45, 60, 90, 120, 150, 180, 240};
            int r = dir > 0 ? mins[mins.length - 1] : 0;
            if (dir > 0) { for (int m : mins) if (m > v) { r = m; break; } }
            else { for (int m : mins) if (m < v) r = m; }
            v = r;
        } else v = v + dir;
        if (evening) Store.setEveningLimit(v); else Store.setEpisodeLimit(v);
    }

    private void paintLimit(TextView val, Button[] tb, boolean evening) {
        int type = evening ? Store.eveningType() : Store.limitType();
        int v = evening ? Store.eveningLimit() : Store.episodeLimit();
        for (int i = 0; i < 2; i++) tb[i].setBackground(Ui.ripple(Ui.rounded(i == type ? Ui.ORANGE : Ui.GREY, Ui.dp(a, 12))));
        if (v == 0) val.setText("Pas de limite");
        else if (type == 1) val.setText(v >= 60 ? (v / 60) + " h " + (v % 60 == 0 ? "" : (v % 60) + " min") : v + " minutes");
        else val.setText(v + " épisode" + (v > 1 ? "s" : ""));
        if (playedTv != null) refresh();
    }

    // ------------------------------------------------------------------ masquer des épisodes

    private void chooseEpisodes(final Store.Podcast p) {
        final List<Store.Episode> eps = Store.downloaded(p.id);
        if (eps.isEmpty()) {
            new AlertDialog.Builder(a).setMessage("Aucun épisode téléchargé pour l'instant.").setPositiveButton("OK", null).show();
            return;
        }
        String[] titles = new String[eps.size()];
        boolean[] checked = new boolean[eps.size()];
        for (int i = 0; i < eps.size(); i++) {
            titles[i] = eps.get(i).title;
            checked[i] = !Store.isHidden(eps.get(i).id);
        }
        new AlertDialog.Builder(a)
                .setTitle("Épisodes visibles : « " + p.name + " »")
                .setMultiChoiceItems(titles, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override public void onClick(DialogInterface d, int which, boolean isChecked) {
                        Store.setHidden(eps.get(which).id, !isChecked);
                    }
                })
                .setPositiveButton("OK", null)
                .show();
    }

    // ------------------------------------------------------------------ réglages en % (0 = comme la journée)

    interface IntSetter { void set(int v); }

    private View percentSlider(final String label, int value, final int min, final IntSetter setter) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(a, 6), 0, 0);
        final TextView tv = Ui.text(a, "", 15, Ui.DARK, false);
        box.addView(tv);
        SeekBar sb = new SeekBar(a);
        sb.setMax(100);
        sb.setProgress(value);
        tv.setText(label + " : " + (value == 0 ? "comme la journée" : value + " %"));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                int val = v == 0 ? 0 : Math.max(min, v);
                tv.setText(label + " : " + (val == 0 ? "comme la journée" : val + " %"));
                setter.set(val);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        box.addView(sb, Ui.lpMargins(a, ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 44), 0, 0, 0, 0));
        return box;
    }

    // ------------------------------------------------------------------ sauvegarde / restauration

    static final int REQ_SAVE = 71, REQ_RESTORE = 72;
    private TextView savTv;

    /** Le choix du fichier est une autre appli : on désépingle le temps du choix (on reste dans l'espace parents). */
    private void unpin() {
        if (!Kiosk.isOwner(a) && Kiosk.isLocked(a)) { try { a.stopLockTask(); } catch (Exception ignored) { } }
    }

    void onResult(int req, int res, Intent data) {
        if (res != android.app.Activity.RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        if (req == REQ_SAVE) {
            try {
                java.io.OutputStream out = a.getContentResolver().openOutputStream(uri, "wt");
                out.write(Store.exportBackup().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.close();
                savTv.setText("Sauvegarde enregistrée ✔");
                Store.log("Sauvegarde enregistrée");
            } catch (Exception e) {
                savTv.setText("Sauvegarde impossible : " + e.getMessage());
            }
        } else if (req == REQ_RESTORE) {
            new AlertDialog.Builder(a)
                    .setTitle("Restaurer cette sauvegarde ?")
                    .setMessage("Les podcasts du fichier sont ajoutés (ceux déjà là sont gardés) et les réglages sont remplacés, code parent compris.")
                    .setPositiveButton("Restaurer", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) {
                            try {
                                java.io.InputStream in = a.getContentResolver().openInputStream(uri);
                                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                                byte[] buf = new byte[16384];
                                int n;
                                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                                in.close();
                                int added = Store.importBackup(new String(bo.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                                Store.log("Sauvegarde restaurée (" + added + " podcast(s) ajouté(s))");
                                Sync.runNow(a);
                                a.showParent();
                                a.applyBrightness();
                                VolumeCap.enforce(a);
                            } catch (Exception e) {
                                savTv.setText("Restauration impossible : " + e.getMessage());
                            }
                        }
                    })
                    .setNegativeButton("Annuler", null)
                    .show();
        }
    }

    private View stepperTv(TextView label, final Runnable minus, final Runnable plus) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(a, 6), 0, 0);
        r.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button m = Ui.button(a, "−", Ui.BLUE);
        m.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { minus.run(); }
        });
        Button p = Ui.button(a, "+", Ui.BLUE);
        p.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { plus.run(); }
        });
        r.addView(m, Ui.lpMargins(a, Ui.dp(a, 56), Ui.dp(a, 44), 4, 0, 4, 0));
        r.addView(p, Ui.lpMargins(a, Ui.dp(a, 56), Ui.dp(a, 44), 4, 0, 0, 0));
        return r;
    }

    private View stepper(String label, final Runnable minus, final Runnable plus) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(a, 6), 0, 0);
        r.addView(Ui.text(a, label, 15, Ui.DARK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button m = Ui.button(a, "−", Ui.BLUE);
        m.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { minus.run(); }
        });
        Button p = Ui.button(a, "+", Ui.BLUE);
        p.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { plus.run(); }
        });
        r.addView(m, Ui.lpMargins(a, Ui.dp(a, 56), Ui.dp(a, 44), 4, 0, 4, 0));
        r.addView(p, Ui.lpMargins(a, Ui.dp(a, 56), Ui.dp(a, 44), 4, 0, 0, 0));
        return r;
    }

    private void addPodcast(final EditText nameEt, final EditText linkEt) {
        if (adding) return;
        final String name = nameEt.getText().toString().trim();
        final String link = linkEt.getText().toString().trim();
        if (name.isEmpty() && link.isEmpty()) {
            addResult.setText("Indique au moins un nom ou un lien.");
            return;
        }
        adding = true;
        addBtn.setEnabled(false);
        addResult.setText("Recherche du podcast…");
        new Thread(new Runnable() {
            @Override public void run() {
                String msg;
                boolean ok = false;
                try {
                    String feedUrl = Net.resolveFeed(link, name);
                    FeedParser.Feed f = FeedParser.parse(Net.bytes(feedUrl));
                    if (f.items.isEmpty()) throw new Exception("Ce lien ne contient aucun épisode audio.");
                    String n = !f.title.isEmpty() ? f.title : (name.isEmpty() ? "Podcast" : name);
                    Store.Podcast p = Store.addPodcast(n, feedUrl, f.imageUrl, 0);
                    if (p == null) throw new Exception("Ce podcast est déjà dans la liste.");
                    Store.mergeEpisodes(p.id, f.items);
                    Sync.runNow(a);
                    msg = "« " + n + " » ajouté (" + f.items.size() + " épisodes dans le flux). Téléchargement lancé ✔";
                    ok = true;
                    Store.log("Ajouté : « " + n + " » " + feedUrl);
                } catch (Throwable e) {
                    msg = "Impossible d'ajouter : " + e.getMessage();
                    Store.log("Ajout de « " + name + " » " + link + " impossible : " + e);
                }
                final String m = msg;
                final boolean success = ok;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        adding = false;
                        addBtn.setEnabled(true);
                        addResult.setText(m);
                        if (success) { nameEt.setText(""); linkEt.setText(""); }
                    }
                });
            }
        }).start();
    }
}
