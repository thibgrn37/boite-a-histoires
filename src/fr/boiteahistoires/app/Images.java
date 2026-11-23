package fr.boiteahistoires.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Chargement d'images locales en arrière-plan, avec cache mémoire. */
public final class Images {
    private static final LruCache<String, Bitmap> cache =
            new LruCache<String, Bitmap>((int) Math.min(Runtime.getRuntime().maxMemory() / 6, 64L * 1024 * 1024)) {
                @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
            };
    private static final ExecutorService pool = Executors.newFixedThreadPool(2);

    private Images() {}

    public static void load(final ImageView v, final File f, final int px) {
        if (f == null || !f.exists()) {
            v.setTag(null);
            v.setImageDrawable(null);
            return;
        }
        final String key = f.getPath() + "@" + px + "#" + f.lastModified();
        v.setTag(key);
        Bitmap b = cache.get(key);
        if (b != null) {
            v.setImageBitmap(b);
            return;
        }
        v.setImageDrawable(null);
        pool.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bm = decode(f, px);
                if (bm == null) return;
                cache.put(key, bm);
                v.post(new Runnable() {
                    @Override public void run() {
                        if (key.equals(v.getTag())) v.setImageBitmap(bm);
                    }
                });
            }
        });
    }

    public static Bitmap decode(File f, int px) {
        if (f == null || !f.exists()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int s = 1;
            while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            return BitmapFactory.decodeFile(f.getPath(), o2);
        } catch (Throwable t) {
            return null;
        }
    }
}
