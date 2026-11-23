package fr.boiteahistoires.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Réseau : HTTP avec redirections, téléchargements, conversion lien Apple → RSS. */
public final class Net {
    static final String UA = "Mozilla/5.0 (Linux; Android 14; 23124RA7EO) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    private Net() {}

    public static HttpURLConnection open(String url) throws IOException {
        String u = url;
        for (int i = 0; i < 10; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "*/*");
            c.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Redirection sans adresse");
                u = new URL(new URL(u), loc).toString();
                continue;
            }
            if (code < 200 || code >= 300) {
                c.disconnect();
                throw new IOException("Le serveur a répondu " + code);
            }
            return c;
        }
        throw new IOException("Trop de redirections");
    }

    public static byte[] bytes(String url) throws IOException {
        HttpURLConnection c = open(url);
        try {
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return bo.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    public static String text(String url) throws IOException {
        return new String(bytes(url), StandardCharsets.UTF_8);
    }

    /** Télécharge dans un fichier .part puis renomme : un fichier présent est toujours complet. */
    public static void download(String url, File dest, AtomicBoolean stop) throws IOException {
        File tmp = new File(dest.getPath() + ".part");
        HttpURLConnection c = open(url);
        try {
            InputStream in = c.getInputStream();
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (stop != null && stop.get()) throw new IOException("interrompu");
                    out.write(buf, 0, n);
                }
                out.getFD().sync();
            } finally {
                out.close();
                in.close();
            }
            long expected = c.getContentLengthLong();
            if (expected > 0 && tmp.length() < expected) throw new IOException("Téléchargement incomplet");
            if (!tmp.renameTo(dest)) throw new IOException("Impossible d'enregistrer le fichier");
        } finally {
            c.disconnect();
            if (tmp.exists()) tmp.delete();
        }
    }

    private static final Pattern APPLE_ID = Pattern.compile("id(\\d{5,})");
    private static final Pattern DIGITS = Pattern.compile("/(\\d{5,})(?:[/?#]|$)");

    /** Cherche un podcast sur Apple Podcasts ; garde le résultat dont le nom contient {@code mustContain}. */
    public static String searchFeed(String term, String mustContain) throws Exception {
        String q = URLEncoder.encode(term, "UTF-8");
        for (String country : new String[]{"FR", "BE", "CH", "CA"}) {
            JSONObject o = new JSONObject(text("https://itunes.apple.com/search?media=podcast&entity=podcast&limit=25&country=" + country + "&term=" + q));
            JSONArray r = o.optJSONArray("results");
            for (int i = 0; r != null && i < r.length(); i++) {
                JSONObject it = r.getJSONObject(i);
                String name = it.optString("collectionName", "").toLowerCase();
                String f = it.optString("feedUrl", "");
                if (!f.isEmpty() && name.contains(mustContain.toLowerCase())) return f;
            }
        }
        throw new IOException("introuvable sur Apple Podcasts");
    }

    /**
     * Transforme ce que le parent a saisi en adresse de flux RSS :
     * lien RSS direct, lien Apple Podcasts, ou (sans lien) recherche du nom sur Apple Podcasts.
     */
    public static String resolveFeed(String link, String name) throws Exception {
        String s = link == null ? "" : link.trim();
        if (s.isEmpty()) {
            if (name == null || name.trim().isEmpty()) throw new IOException("Indique un nom ou un lien.");
            String q = URLEncoder.encode(name.trim(), "UTF-8");
            JSONObject o = new JSONObject(text("https://itunes.apple.com/search?media=podcast&entity=podcast&country=FR&limit=5&term=" + q));
            JSONArray r = o.optJSONArray("results");
            if (r == null || r.length() == 0) throw new IOException("Aucun podcast trouvé pour « " + name.trim() + " ».");
            for (int i = 0; i < r.length(); i++) {
                String f = r.getJSONObject(i).optString("feedUrl", "");
                if (!f.isEmpty()) return f;
            }
            throw new IOException("Podcast trouvé mais sans flux public.");
        }
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://" + s;
        if (s.contains("apple.com")) {
            Matcher m = APPLE_ID.matcher(s);
            String id = null;
            if (m.find()) id = m.group(1);
            else {
                Matcher m2 = DIGITS.matcher(s);
                if (m2.find()) id = m2.group(1);
            }
            if (id == null) throw new IOException("Lien Apple Podcasts non reconnu.");
            JSONObject o = new JSONObject(text("https://itunes.apple.com/lookup?entity=podcast&id=" + id));
            JSONArray r = o.optJSONArray("results");
            if (r != null) {
                for (int i = 0; i < r.length(); i++) {
                    String f = r.getJSONObject(i).optString("feedUrl", "");
                    if (!f.isEmpty()) return f;
                }
            }
            throw new IOException("Ce podcast Apple n'a pas de flux public.");
        }
        return s;
    }
}
