package fr.boiteahistoires.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Lecteur audio unique de l'appli (MediaPlayer + session média pour Bluetooth / voiture). */
public final class Player implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    public interface Listener { void onPlayerChanged(); }

    private static Player instance;

    public static synchronized Player get(Context c) {
        if (instance == null) instance = new Player(c.getApplicationContext());
        return instance;
    }

    /** Identifiant de l'épisode en cours (ou null), utilisable sans contexte. */
    public static String currentId() {
        Player p = instance;
        if (p == null) return null;
        Store.Episode e = p.current();
        return e == null ? null : e.id;
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AudioManager am;
    private final MediaSession session;
    private AudioFocusRequest focusRequest;
    private MediaPlayer mp;
    private boolean prepared = false;
    private boolean wantPlay = false;
    private boolean resumeOnFocus = false;
    private boolean noisyRegistered = false;
    private ArrayList<Store.Episode> queue = new ArrayList<>();
    private int index = -1;

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { pause(); }
    };

    private long lastTick = 0;
    /** Histoire en cours quand la limite a été atteinte : on peut l'écouter jusqu'au bout. */
    private String graceEpisodeId = null;

    /** Vrai si l'enfant peut lancer / reprendre l'épisode courant. */
    public boolean canPlayCurrent() {
        if (!Store.limitReached()) return true;
        Store.Episode e = current();
        return e != null && e.id.equals(graceEpisodeId);
    }

    /** Vrai si l'enfant peut lancer cet épisode. */
    public boolean canPlay(Store.Episode e) {
        return !Store.limitReached() || (e != null && e.id.equals(graceEpisodeId));
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            long now = android.os.SystemClock.elapsedRealtime();
            if (isPlaying()) {
                if (lastTick > 0) Store.addListened(Math.min(now - lastTick, 10000));
                savePosition();
                updateSession();
                // Temps écoulé en pleine histoire : on la laisse finir, mais on n'en relancera pas d'autre.
                Store.Episode cur = current();
                if (Store.limitReached() && graceEpisodeId == null && cur != null) graceEpisodeId = cur.id;
            }
            if (!Store.limitReached()) graceEpisodeId = null;
            lastTick = now;
            main.postDelayed(this, 3000);
        }
    };

    private Player(Context c) {
        ctx = c;
        am = c.getSystemService(AudioManager.class);
        session = new MediaSession(c, "BoiteAHistoires");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onStop() { pause(); }
            @Override public void onSkipToNext() { next(); }
            @Override public void onSkipToPrevious() { previous(); }
        });
        main.postDelayed(ticker, 3000);
    }

    public MediaSession.Token sessionToken() { return session.getSessionToken(); }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    private void fire() {
        main.post(new Runnable() {
            @Override public void run() {
                for (Listener l : listeners) l.onPlayerChanged();
            }
        });
    }

    // ------------------------------------------------------------------ état

    public Store.Episode current() {
        if (index < 0 || index >= queue.size()) return null;
        return queue.get(index);
    }

    public boolean isPlaying() {
        try { return mp != null && prepared && mp.isPlaying(); } catch (Exception e) { return false; }
    }

    /** Vrai si la lecture est lancée ou en cours de chargement. */
    public boolean isActive() { return isPlaying() || (wantPlay && !prepared); }

    public int position() {
        try { return mp != null && prepared ? mp.getCurrentPosition() : 0; } catch (Exception e) { return 0; }
    }

    public int duration() {
        try { return mp != null && prepared ? mp.getDuration() : 0; } catch (Exception e) { return 0; }
    }

    public boolean hasNext() { return index >= 0 && index + 1 < queue.size(); }
    public boolean hasPrevious() { return index > 0; }

    // ------------------------------------------------------------------ commandes

    /** Lance un épisode d'une liste (les suivants s'enchaînent). */
    public void play(List<Store.Episode> list, int idx) {
        Store.Episode target = list.get(idx);
        Store.Episode cur = current();
        queue = new ArrayList<>(list);
        index = idx;
        if (cur != null && cur.id.equals(target.id) && mp != null) {
            resume();
            return;
        }
        load(true);
    }

    public void toggle() {
        if (isActive()) pause(); else resume();
    }

    public void resume() {
        if (current() == null) return;
        if (mp == null) { load(true); return; }
        if (!prepared) { wantPlay = true; fire(); return; }
        startInternal();
    }

    public void pause() {
        resumeOnFocus = false;
        pauseInternal(false);
    }

    private void pauseInternal(boolean keepFocus) {
        wantPlay = false;
        try { if (mp != null && prepared && mp.isPlaying()) mp.pause(); } catch (Exception ignored) { }
        savePosition();
        stopForeground(keepFocus);
        updateSession();
        fire();
    }

    public void next() {
        if (!hasNext()) return;
        savePosition();
        index++;
        load(true);
    }

    public void previous() {
        if (hasPrevious()) {
            savePosition();
            index--;
            load(true);
        } else if (mp != null && prepared) {
            Store.Podcast p = Store.podcast(current().podcastId);
            mp.seekTo(p == null ? 0 : p.skipSeconds * 1000);
            fire();
        }
    }

    // ------------------------------------------------------------------ interne

    private void load(boolean autoplay) {
        releasePlayer();
        Store.Episode e = current();
        if (e == null) { fire(); return; }
        File f = Store.audio(e);
        if (!f.exists()) {
            Log.w(Store.TAG, "fichier absent " + f);
            if (hasNext()) { index++; load(autoplay); } else { stopForeground(); fire(); }
            return;
        }
        MediaPlayer m = new MediaPlayer();
        m.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        m.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK);
        m.setOnPreparedListener(this);
        m.setOnCompletionListener(this);
        m.setOnErrorListener(this);
        mp = m;
        prepared = false;
        wantPlay = autoplay;
        try {
            m.setDataSource(f.getAbsolutePath());
            m.prepareAsync();
        } catch (Exception ex) {
            Log.e(Store.TAG, "lecture impossible", ex);
            releasePlayer();
        }
        updateMetadata();
        fire();
    }

    @Override
    public void onPrepared(MediaPlayer m) {
        if (m != mp) return;
        prepared = true;
        Store.Episode e = current();
        if (e != null) {
            Store.Podcast p = Store.podcast(e.podcastId);
            int skip = p == null ? 0 : p.skipSeconds * 1000;
            long pos = Store.isDone(e.id) ? 0 : Store.position(e.id);
            int dur = m.getDuration();
            if (pos < skip) pos = skip;
            if (dur > 0 && pos > dur - 5000) pos = Math.min(skip, Math.max(0, dur - 1000));
            if (pos > 0) m.seekTo((int) pos);
        }
        if (wantPlay) startInternal(); else fire();
    }

    private void startInternal() {
        if (!canPlayCurrent()) { wantPlay = false; fire(); return; }
        if (!requestFocus()) { fire(); return; }
        VolumeCap.enforce(ctx);
        try { mp.start(); } catch (Exception e) { Log.e(Store.TAG, "start", e); }
        wantPlay = true;
        Store.Episode e = current();
        if (e != null && Store.isDone(e.id)) Store.setDone(e.id, false);
        if (!noisyRegistered) {
            IntentFilter nf = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            if (android.os.Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(noisy, nf, Context.RECEIVER_NOT_EXPORTED);
            else ctx.registerReceiver(noisy, nf);
            noisyRegistered = true;
        }
        session.setActive(true);
        updateSession();
        try {
            ctx.startForegroundService(new Intent(ctx, PlayerService.class));
        } catch (Exception ex) {
            Log.w(Store.TAG, "service premier plan", ex);
        }
        fire();
    }

    @Override
    public void onCompletion(MediaPlayer m) {
        if (m != mp) return;
        Store.Episode e = current();
        if (e != null) {
            Store.setPosition(e.id, 0);
            Store.setDone(e.id, true);
            Store.addPlayed();
            graceEpisodeId = null;
        }
        if (hasNext() && !Store.limitReached()) {
            index++;
            load(true);
        } else {
            wantPlay = false;
            stopForeground();
            updateSession();
            fire();
        }
    }

    @Override
    public boolean onError(MediaPlayer m, int what, int extra) {
        Log.e(Store.TAG, "erreur lecteur " + what + "/" + extra);
        if (m == mp) {
            releasePlayer();
            stopForeground();
            fire();
        }
        return true;
    }

    private boolean requestFocus() {
        if (focusRequest == null) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setOnAudioFocusChangeListener(this, main)
                    .setWillPauseWhenDucked(true)
                    .build();
        }
        return am.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @Override
    public void onAudioFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS) {
            pause();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            boolean was = isPlaying();
            pauseInternal(was);
            resumeOnFocus = was;
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            if (resumeOnFocus) {
                resumeOnFocus = false;
                resume();
            }
        }
    }

    private void savePosition() {
        Store.Episode e = current();
        if (e != null && mp != null && prepared) Store.setPosition(e.id, position());
    }

    private void releasePlayer() {
        if (mp != null) {
            try { mp.release(); } catch (Exception ignored) { }
        }
        mp = null;
        prepared = false;
    }

    private void stopForeground() { stopForeground(false); }

    private void stopForeground(boolean keepFocus) {
        if (noisyRegistered) {
            try { ctx.unregisterReceiver(noisy); } catch (Exception ignored) { }
            noisyRegistered = false;
        }
        if (focusRequest != null && !keepFocus) am.abandonAudioFocusRequest(focusRequest);
        ctx.stopService(new Intent(ctx, PlayerService.class));
    }

    private void updateSession() {
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP;
        int state = isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, position(), 1f)
                .build());
    }

    private void updateMetadata() {
        Store.Episode e = current();
        if (e == null) return;
        Store.Podcast p = Store.podcast(e.podcastId);
        MediaMetadata.Builder b = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, e.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, p == null ? "" : p.name)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, p == null ? "" : p.name);
        Bitmap art = Images.decode(Store.displayImage(e), 512);
        if (art != null) b.putBitmap(MediaMetadata.METADATA_KEY_ART, art);
        session.setMetadata(b.build());
    }

    public String currentTitle() {
        Store.Episode e = current();
        return e == null ? "" : e.title;
    }
}
