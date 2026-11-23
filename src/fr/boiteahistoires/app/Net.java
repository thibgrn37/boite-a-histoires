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
import java.util.List;
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
        File dir = dest.getParentFile();
        if (dir != null && !dir.isDirectory()) dir.mkdirs();
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

    static String norm(String s) {
        String n = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
        return n.replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Ressemblance entre le nom cherché et le nom d'un podcast (0 à 100). */
    static int score(String wanted, String found) {
        String w = norm(wanted), f = norm(found);
        if (w.isEmpty() || f.isEmpty()) return 0;
        if (w.equals(f)) return 100;
        if ((" " + f + " ").contains(" " + w + " ")) return 85;
        if ((" " + w + " ").contains(" " + f + " ")) return 70;
        String[] ws = w.split(" ");
        java.util.Set<String> fs = new java.util.HashSet<>(java.util.Arrays.asList(f.split(" ")));
        int hit = 0, tot = 0;
        for (String x : ws) { if (x.length() < 3) continue; tot++; if (fs.contains(x)) hit++; }
        return tot == 0 ? 0 : hit * 60 / tot;
    }

    /** Cherche un podcast par son nom sur Apple Podcasts et garde le plus ressemblant. */
    public static String searchFeed(String term, String unused) throws Exception {
        String q = URLEncoder.encode(term.trim(), "UTF-8");
        String best = null, bestName = null;
        int bestScore = -1;
        List<String> seen = new java.util.ArrayList<>();
        for (String country : new String[]{"FR", "BE", "CH", "CA"}) {
            JSONObject o = new JSONObject(text("https://itunes.apple.com/search?media=podcast&entity=podcast&limit=25&country=" + country + "&term=" + q));
            JSONArray r = o.optJSONArray("results");
            for (int i = 0; r != null && i < r.length(); i++) {
                JSONObject it = r.getJSONObject(i);
                String name = it.optString("collectionName", "");
                String f = it.optString("feedUrl", "");
                if (f.isEmpty()) continue;
                if (seen.size() < 3 && !seen.contains(name)) seen.add(name);
                int sc = score(term, name);
                if (sc > bestScore) { bestScore = sc; best = f; bestName = name; }
            }
            if (bestScore >= 85) break;
        }
        if (best == null) throw new IOException("aucun podcast trouvé pour « " + term.trim() + " » sur Apple Podcasts.");
        if (bestScore < 50) throw new IOException("pas de podcast qui s'appelle vraiment « " + term.trim() + " ». Trouvés : " + seen + ". Colle plutôt le lien du podcast.");
        Store.log("Recherche « " + term.trim() + " » → « " + bestName + " »");
        return best;
    }

    /**
     * Transforme ce que le parent a saisi en adresse de flux RSS :
     * lien RSS direct, lien Apple Podcasts, ou (sans lien) recherche du nom sur Apple Podcasts.
     */
    public static String resolveFeed(String link, String name) throws Exception {
        String s = link == null ? "" : link.trim();
        if (s.isEmpty()) {
            if (name == null || name.trim().isEmpty()) throw new IOException("Indique un nom ou un lien.");
            return searchFeed(name, null);
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
