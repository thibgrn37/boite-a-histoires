package fr.boiteahistoires.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.content.res.ColorStateList;

import java.util.ArrayList;
import java.util.List;

/** Écran unique de l'enfant : podcasts → épisodes → lecteur. Espace parents caché. */
public class MainActivity extends Activity implements Store.Listener, Player.Listener {
    static final int HOME = 0, EPISODES = 1, PLAYER = 2, PARENT = 3;

    private int screen = HOME;
    private String podcastId;
    private FrameLayout root;
    private View codeOverlay;
    private Player player;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // grilles
    private final List<Store.Podcast> podList = new ArrayList<>();
    private final List<Store.Episode> epList = new ArrayList<>();
    private BaseAdapter gridAdapter;
    private View emptyText;
    private GridView grid;

    // mini lecteur
    private View miniBar;
    private ImageView miniImg, miniPlay;
    private TextView miniTitle;

    // lecteur
    private ImageView plImg, plPlay, plPrev, plNext;
    private TextView plTitle;
    private ProgressBar plProgress;
    private String shownEpisodeId;

    private ParentScreen parent;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (screen == PLAYER) updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    // ------------------------------------------------------------------ cycle de vie

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Store.init(this);
        // Afficher l'appli directement au réveil, par-dessus l'écran « glisser pour déverrouiller ».
        setShowWhenLocked(true);
        dismissKeyguard();
        player = Player.get(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.CREAM);
        setContentView(root);
        showHome();
        if (getIntent() != null && getIntent().getBooleanExtra("secours", false)) showParent();
        if (Build.VERSION.SDK_INT >= 33 && !Kiosk.isOwner(this)
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Store.addListener(this);
        player.addListener(this);
        dismissKeyguard();
        if (screen != PARENT) Kiosk.sync(this);
        applyImmersive();
        VolumeCap.enforce(this);
        onLibraryChanged();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Store.removeListener(this);
        player.removeListener(this);
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("secours", false)) { showParent(); return; }
        if (screen == PARENT) return;
        closeCode();
        showHome();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override
    public void onBackPressed() {
        if (codeOverlay != null) { closeCode(); return; }
        switch (screen) {
            case PLAYER:
                if (podcastId != null) showEpisodes(podcastId); else showHome();
                break;
            case EPISODES:
            case PARENT:
                showHome();
                break;
            default:
                // Écran d'accueil : on ne quitte pas.
                if (!Store.kiosk()) super.onBackPressed();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (VolumeCap.atCap(this)) return true;
            handler.postDelayed(new Runnable() {
                @Override public void run() { VolumeCap.enforce(MainActivity.this); }
            }, 100);
        }
        return super.dispatchKeyEvent(event);
    }

    private void dismissKeyguard() {
        try {
            android.app.KeyguardManager km = getSystemService(android.app.KeyguardManager.class);
            if (km != null && km.isKeyguardLocked() && !km.isKeyguardSecure()) km.requestDismissKeyguard(this, null);
        } catch (Exception ignored) { }
    }

    void applyImmersive() {
        boolean hide = Store.kiosk();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c == null) return;
            if (hide) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            int flags = hide ? (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE) : 0;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    // ------------------------------------------------------------------ écouteurs

    @Override
    public void onLibraryChanged() {
        if (screen == HOME) {
            podList.clear();
            podList.addAll(Store.podcasts());
            if (gridAdapter != null) gridAdapter.notifyDataSetChanged();
            if (emptyText != null) emptyText.setVisibility(podList.isEmpty() ? View.VISIBLE : View.GONE);
        } else if (screen == EPISODES) {
            if (Store.podcast(podcastId) == null) { showHome(); return; }
            epList.clear();
            epList.addAll(Store.downloaded(podcastId));
            if (gridAdapter != null) gridAdapter.notifyDataSetChanged();
            if (emptyText != null) emptyText.setVisibility(epList.isEmpty() ? View.VISIBLE : View.GONE);
        } else if (screen == PARENT && parent != null) {
            parent.refresh();
        }
        updateMini();
    }

    @Override
    public void onPlayerChanged() {
        updateMini();
        if (screen == PLAYER) updatePlayer();
        if (screen == EPISODES && gridAdapter != null) gridAdapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------------ écran d'accueil

    private void showHome() {
        screen = HOME;
        podcastId = null;
        parent = null;
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        FrameLayout header = new FrameLayout(this);
        LinearLayout title = new LinearLayout(this);
        title.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_notif);
        logo.setImageTintList(ColorStateList.valueOf(Ui.ORANGE));
        title.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        TextView t = Ui.text(this, getString(R.string.app_name), 22, Ui.DARK, true);
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        title.addView(t);
        title.setPadding(Ui.dp(this, 20), 0, 0, 0);
        header.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START | Gravity.CENTER_VERTICAL));

        // Coin secret : appui long de 3 secondes en haut à droite.
        View secret = new View(this);
        secret.setOnTouchListener(new View.OnTouchListener() {
            final Runnable open = new Runnable() {
                @Override public void run() { showCode(); }
            };
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) handler.postDelayed(open, 3000);
                else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) handler.removeCallbacks(open);
                return true;
            }
        });
        header.addView(secret, new FrameLayout.LayoutParams(Ui.dp(this, 90), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        col.addView(header, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 80)));

        FrameLayout body = new FrameLayout(this);
        grid = makeGrid(170);
        podList.clear();
        podList.addAll(Store.podcasts());
        gridAdapter = new BaseAdapter() {
            @Override public int getCount() { return podList.size(); }
            @Override public Object getItem(int i) { return podList.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View convert, ViewGroup parentView) {
                Ui.Card card = convert instanceof Ui.Card ? (Ui.Card) convert : new Ui.Card(MainActivity.this);
                Store.Podcast p = podList.get(i);
                card.setPlaceholder(p.name);
                Images.load(card.image, Store.cover(p.id), Ui.dp(MainActivity.this, 200));
                boolean none = Store.downloaded(p.id).isEmpty();
                card.setBadge(none ? R.drawable.ic_hourglass : 0, Ui.GREY);
                Store.Episode cur = player.current();
                card.setHighlighted(cur != null && cur.podcastId.equals(p.id));
                return card;
            }
        };
        grid.setAdapter(gridAdapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                showEpisodes(podList.get(pos).id);
            }
        });
        body.addView(grid);
        TextView emptyTv = Ui.text(this, "Aucun podcast pour l'instant.\nLes parents peuvent en ajouter dans l'espace parents.", 18, Ui.GREY, false);
        emptyTv.setGravity(Gravity.CENTER);
        emptyText = emptyTv;
        emptyText.setVisibility(podList.isEmpty() ? View.VISIBLE : View.GONE);
        body.addView(emptyText, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        col.addView(makeMiniBar());
        root.addView(col);
        updateMini();
    }

    private GridView makeGrid(int columnDp) {
        GridView g = new GridView(this);
        g.setNumColumns(GridView.AUTO_FIT);
        g.setColumnWidth(Ui.dp(this, columnDp));
        g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        int sp = Ui.dp(this, 18);
        g.setHorizontalSpacing(sp);
        g.setVerticalSpacing(sp);
        g.setPadding(sp, Ui.dp(this, 4), sp, sp);
        g.setClipToPadding(false);
        g.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        g.setDrawSelectorOnTop(false);
        return g;
    }

    // ------------------------------------------------------------------ épisodes

    private void showEpisodes(String pid) {
        final Store.Podcast p = Store.podcast(pid);
        if (p == null) { showHome(); return; }
        screen = EPISODES;
        podcastId = pid;
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        ImageView back = Ui.roundButton(this, R.drawable.ic_back, Ui.ORANGE, 72);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHome(); }
        });
        header.addView(back);
        Ui.Card mini = new Ui.Card(this);
        mini.setPlaceholder(p.name);
        mini.letter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        Images.load(mini.image, Store.cover(p.id), Ui.dp(this, 64));
        header.addView(mini, Ui.lpMargins(this, Ui.dp(this, 64), Ui.dp(this, 64), 16, 0, 12, 0));
        TextView name = Ui.text(this, p.name, 20, Ui.DARK, true);
        name.setMaxLines(2);
        header.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        col.addView(header);

        FrameLayout body = new FrameLayout(this);
        grid = makeGrid(150);
        epList.clear();
        epList.addAll(Store.downloaded(pid));
        gridAdapter = new BaseAdapter() {
            @Override public int getCount() { return epList.size(); }
            @Override public Object getItem(int i) { return epList.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View convert, ViewGroup parentView) {
                LinearLayout cell;
                Ui.Card card;
                TextView title;
                if (convert instanceof LinearLayout) {
                    cell = (LinearLayout) convert;
                    card = (Ui.Card) cell.getChildAt(0);
                    title = (TextView) cell.getChildAt(1);
                } else {
                    cell = new LinearLayout(MainActivity.this);
                    cell.setOrientation(LinearLayout.VERTICAL);
                    card = new Ui.Card(MainActivity.this);
                    cell.addView(card, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    title = Ui.text(MainActivity.this, "", 13, Ui.GREY, false);
                    title.setMaxLines(2);
                    title.setPadding(Ui.dp(MainActivity.this, 4), Ui.dp(MainActivity.this, 6), Ui.dp(MainActivity.this, 4), 0);
                    cell.addView(title);
                }
                Store.Episode e = epList.get(i);
                card.setPlaceholder(p.name);
                Images.load(card.image, Store.displayImage(e), Ui.dp(MainActivity.this, 180));
                Store.Episode cur = player.current();
                boolean playing = cur != null && cur.id.equals(e.id);
                card.setHighlighted(playing);
                if (playing) card.setBadge(player.isActive() ? R.drawable.ic_play : R.drawable.ic_pause, Ui.ORANGE);
                else card.setBadge(Store.isDone(e.id) ? R.drawable.ic_check : 0, Ui.GREEN);
                title.setText(e.title);
                return cell;
            }
        };
        grid.setAdapter(gridAdapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                player.play(new ArrayList<>(epList), pos);
                showPlayer();
            }
        });
        body.addView(grid);
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        ImageView hg = new ImageView(this);
        hg.setImageResource(R.drawable.ic_hourglass);
        hg.setImageTintList(ColorStateList.valueOf(Ui.GREY));
        empty.addView(hg, new LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96)));
        TextView et = Ui.text(this, "Les histoires arrivent dès que le téléphone est en Wi-Fi.", 16, Ui.GREY, false);
        et.setGravity(Gravity.CENTER);
        et.setPadding(Ui.dp(this, 32), Ui.dp(this, 16), Ui.dp(this, 32), 0);
        empty.addView(et);
        body.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        empty.setVisibility(epList.isEmpty() ? View.VISIBLE : View.GONE);
        emptyText = empty;
        col.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        col.addView(makeMiniBar());
        root.addView(col);
        updateMini();
    }

    // ------------------------------------------------------------------ mini lecteur

    private View makeMiniBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(Ui.rounded(Ui.WHITE, Ui.dp(this, 24)));
        bar.setElevation(Ui.dp(this, 8));
        int p = Ui.dp(this, 10);
        bar.setPadding(p, p, Ui.dp(this, 16), p);
        miniImg = new ImageView(this);
        miniImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniImg.setBackground(Ui.rounded(Ui.colorFor("x"), Ui.dp(this, 16)));
        miniImg.setClipToOutline(true);
        bar.addView(miniImg, new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72)));
        miniTitle = Ui.text(this, "", 15, Ui.DARK, false);
        miniTitle.setMaxLines(2);
        miniTitle.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 10), 0);
        bar.addView(miniTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        miniPlay = Ui.roundButton(this, R.drawable.ic_play, Ui.GREEN, 76);
        miniPlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { player.toggle(); }
        });
        bar.addView(miniPlay);
        bar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPlayer(); }
        });
        LinearLayout.LayoutParams lp = Ui.lpMargins(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 12, 4, 12, 12);
        bar.setLayoutParams(lp);
        miniBar = bar;
        return bar;
    }

    private void updateMini() {
        if (miniBar == null || (screen != HOME && screen != EPISODES)) return;
        Store.Episode e = player.current();
        if (e == null) { miniBar.setVisibility(View.GONE); return; }
        miniBar.setVisibility(View.VISIBLE);
        miniTitle.setText(e.title);
        Images.load(miniImg, Store.displayImage(e), Ui.dp(this, 80));
        miniPlay.setImageResource(player.isActive() ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    // ------------------------------------------------------------------ lecteur plein écran

    private void showPlayer() {
        Store.Episode e = player.current();
        if (e == null) return;
        screen = PLAYER;
        podcastId = e.podcastId;
        miniBar = null;
        root.removeAllViews();
        DisplayMetrics dm = getResources().getDisplayMetrics();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout header = new LinearLayout(this);
        header.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), 0);
        ImageView back = Ui.roundButton(this, R.drawable.ic_back, Ui.ORANGE, 72);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onBackPressed(); }
        });
        header.addView(back);
        col.addView(header, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

        int size = Math.min(dm.widthPixels - Ui.dp(this, 64), (int) (dm.heightPixels * 0.42f));
        plImg = new ImageView(this);
        plImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        plImg.setBackground(Ui.rounded(Ui.colorFor(e.podcastId), Ui.dp(this, 32)));
        plImg.setClipToOutline(true);
        plImg.setElevation(Ui.dp(this, 8));
        plImg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { player.toggle(); }
        });
        col.addView(plImg, new LinearLayout.LayoutParams(size, size));

        plTitle = Ui.text(this, "", 16, Ui.GREY, false);
        plTitle.setGravity(Gravity.CENTER);
        plTitle.setMaxLines(2);
        col.addView(plTitle, Ui.lpMargins(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 32, 16, 32, 0));

        plProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        plProgress.setMax(1000);
        plProgress.setProgressTintList(ColorStateList.valueOf(Ui.ORANGE));
        plProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFFFCCBC));
        plProgress.setScaleY(3f);
        col.addView(plProgress, Ui.lpMargins(this, ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 16), 40, 20, 40, 0));

        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        plPrev = Ui.roundButton(this, R.drawable.ic_prev, Ui.BLUE, 96);
        plPlay = Ui.roundButton(this, R.drawable.ic_play, Ui.GREEN, 140);
        plNext = Ui.roundButton(this, R.drawable.ic_next, Ui.BLUE, 96);
        plPrev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { player.previous(); }
        });
        plPlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { player.toggle(); }
        });
        plNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { player.next(); }
        });
        controls.addView(plPrev);
        View s1 = new View(this);
        controls.addView(s1, new LinearLayout.LayoutParams(Ui.dp(this, 20), 1));
        controls.addView(plPlay);
        View s2 = new View(this);
        controls.addView(s2, new LinearLayout.LayoutParams(Ui.dp(this, 20), 1));
        controls.addView(plNext);
        col.addView(controls, Ui.lpMargins(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 48));

        root.addView(col);
        shownEpisodeId = null;
        updatePlayer();
    }

    private void updatePlayer() {
        Store.Episode e = player.current();
        if (e == null) { if (podcastId != null) showEpisodes(podcastId); else showHome(); return; }
        if (!e.id.equals(shownEpisodeId)) {
            shownEpisodeId = e.id;
            plTitle.setText(e.title);
            Images.load(plImg, Store.displayImage(e), Ui.dp(this, 400));
        }
        plPlay.setImageResource(player.isActive() ? R.drawable.ic_pause : R.drawable.ic_play);
        plPrev.setAlpha(player.hasPrevious() ? 1f : 0.35f);
        plNext.setAlpha(player.hasNext() ? 1f : 0.35f);
        updateProgress();
    }

    private void updateProgress() {
        if (plProgress == null) return;
        int d = player.duration();
        plProgress.setProgress(d > 0 ? (int) (player.position() * 1000L / d) : 0);
    }

    // ------------------------------------------------------------------ espace parents

    private void showCode() {
        if (codeOverlay != null) return;
        final StringBuilder typed = new StringBuilder();
        final FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(0xAA000000);
        ov.setClickable(true);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Ui.rounded(Ui.WHITE, Ui.dp(this, 24)));
        int p = Ui.dp(this, 20);
        card.setPadding(p, p, p, p);
        TextView title = Ui.text(this, "Code parent", 20, Ui.DARK, true);
        card.addView(title);
        final TextView dots = Ui.text(this, " ", 32, Ui.DARK, true);
        dots.setGravity(Gravity.CENTER);
        card.addView(dots, Ui.lpMargins(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 8));
        final TextView err = Ui.text(this, "", 14, 0xFFD32F2F, false);
        card.addView(err);
        GridLayout pad = new GridLayout(this);
        pad.setColumnCount(3);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "✕", "0", "OK"};
        for (final String k : keys) {
            Button b = Ui.button(this, k, "OK".equals(k) ? Ui.GREEN : ("✕".equals(k) ? Ui.GREY : Ui.BLUE));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Ui.dp(this, 76);
            lp.height = Ui.dp(this, 64);
            lp.setMargins(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
            b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if ("✕".equals(k)) {
                        if (typed.length() == 0) closeCode(); else typed.setLength(0);
                    } else if ("OK".equals(k)) {
                        if (typed.toString().equals(Store.parentCode())) {
                            closeCode();
                            showParent();
                            return;
                        }
                        typed.setLength(0);
                        err.setText("Code incorrect");
                    } else if (typed.length() < 8) {
                        typed.append(k);
                        err.setText("");
                    }
                    StringBuilder d = new StringBuilder();
                    for (int i = 0; i < typed.length(); i++) d.append("● ");
                    dots.setText(d.length() == 0 ? " " : d.toString());
                }
            });
            pad.addView(b);
        }
        card.addView(pad);
        Button cancel = Ui.button(this, "Annuler", Ui.GREY);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeCode(); }
        });
        card.addView(cancel, Ui.lpMargins(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 12, 0, 0));
        ov.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(ov, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        codeOverlay = ov;
        // Fermeture automatique si l'enfant l'a ouvert par hasard.
        handler.postDelayed(new Runnable() {
            @Override public void run() { if (codeOverlay == ov) closeCode(); }
        }, 60000);
    }

    private void closeCode() {
        if (codeOverlay != null) {
            root.removeView(codeOverlay);
            codeOverlay = null;
        }
    }

    void showParent() {
        closeCode();
        screen = PARENT;
        miniBar = null;
        root.removeAllViews();
        parent = new ParentScreen(this);
        root.addView(parent.view());
    }

    void closeParent() {
        parent = null;
        Kiosk.sync(this);
        applyImmersive();
        showHome();
    }
}
