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
        LinearLayout lim = section(col, "Épisodes par jour");
        final TextView limTv = Ui.text(a, "", 16, Ui.DARK, true);
        lim.addView(stepper2(limTv));
        playedTv = small("");
        lim.addView(playedTv);
        lim.addView(small("Quand la limite est atteinte, la lecture s'arrête et une lune s'affiche ; impossible de relancer. Le compteur repart à zéro chaque jour à minuit."));
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
        playedTv.setText("Écoutés aujourd'hui : " + Store.playedToday());
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
            info.addView(Ui.text(a, p.name, 16, Ui.DARK, true));
            int dl = Store.downloaded(p.id).size();
            int known = Store.episodes(p.id).size();
            info.addView(small(dl + " épisode(s) sur le téléphone, " + known + " connu(s)"));
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
                    String n = !name.isEmpty() ? name : (f.title.isEmpty() ? "Podcast" : f.title);
                    Store.Podcast p = Store.addPodcast(n, feedUrl, f.imageUrl, 0);
                    if (p == null) throw new Exception("Ce podcast est déjà dans la liste.");
                    Store.mergeEpisodes(p.id, f.items);
                    Sync.runNow(a);
                    msg = "« " + n + " » ajouté (" + f.items.size() + " épisodes dans le flux). Téléchargement lancé ✔";
                    ok = true;
                } catch (Exception e) {
                    msg = "Impossible d'ajouter : " + e.getMessage();
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
