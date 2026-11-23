package fr.boiteahistoires.app;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Lecture d'un flux RSS de podcast (RSS 2.0 + balises iTunes / media). */
public final class FeedParser {

    public static final class Item {
        public String guid, title, audioUrl, imageUrl;
        public long pubDate;
    }

    public static final class Feed {
        public String title = "";
        public String imageUrl;
        public final List<Item> items = new ArrayList<>();
    }

    private FeedParser() {}

    public static Feed parse(byte[] data) throws Exception {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(false);
        XmlPullParser p = f.newPullParser();
        p.setInput(new ByteArrayInputStream(data), null);

        Feed feed = new Feed();
        String rssImage = null;
        Item cur = null;
        boolean inChannelImage = false;
        String enclosureType = null;
        StringBuilder text = new StringBuilder();

        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String n = p.getName();
                text.setLength(0);
                if ("item".equals(n) || "entry".equals(n)) {
                    cur = new Item();
                    enclosureType = null;
                } else if ("image".equals(n) && cur == null) {
                    inChannelImage = true;
                } else if ("itunes:image".equals(n)) {
                    String h = attr(p, "href");
                    if (h == null) h = attr(p, "url");
                    if (h != null) {
                        if (cur != null) { if (cur.imageUrl == null) cur.imageUrl = h; }
                        else if (feed.imageUrl == null) feed.imageUrl = h;
                    }
                } else if (cur != null && "enclosure".equals(n)) {
                    String url = attr(p, "url");
                    String type = attr(p, "type");
                    boolean audio = type == null || type.startsWith("audio") || type.startsWith("video");
                    if (url != null && audio && (cur.audioUrl == null || enclosureType == null || !enclosureType.startsWith("audio"))) {
                        cur.audioUrl = url;
                        enclosureType = type;
                    }
                } else if (cur != null && ("media:thumbnail".equals(n))) {
                    String url = attr(p, "url");
                    if (url != null && cur.imageUrl == null) cur.imageUrl = url;
                } else if (cur != null && "media:content".equals(n)) {
                    String url = attr(p, "url");
                    String type = attr(p, "type");
                    String medium = attr(p, "medium");
                    if (url != null) {
                        boolean img = "image".equals(medium) || (type != null && type.startsWith("image"));
                        boolean aud = "audio".equals(medium) || (type != null && type.startsWith("audio"));
                        if (img && cur.imageUrl == null) cur.imageUrl = url;
                        else if (aud && cur.audioUrl == null) cur.audioUrl = url;
                    }
                } else if (cur != null && "link".equals(n)) {
                    // Atom : <link rel="enclosure" href="..."/>
                    String rel = attr(p, "rel");
                    String href = attr(p, "href");
                    if ("enclosure".equals(rel) && href != null && cur.audioUrl == null) cur.audioUrl = href;
                }
            } else if (ev == XmlPullParser.TEXT) {
                text.append(p.getText());
            } else if (ev == XmlPullParser.END_TAG) {
                String n = p.getName();
                String t = text.toString().trim();
                if (cur != null) {
                    if ("item".equals(n) || "entry".equals(n)) {
                        if (cur.audioUrl != null) feed.items.add(cur);
                        cur = null;
                    } else if ("title".equals(n)) {
                        if (cur.title == null || cur.title.isEmpty()) cur.title = t;
                    } else if ("guid".equals(n) || "id".equals(n)) {
                        if (!t.isEmpty()) cur.guid = t;
                    } else if ("pubDate".equals(n) || "published".equals(n) || "dc:date".equals(n)) {
                        long d = parseDate(t);
                        if (d > 0) cur.pubDate = d;
                    }
                } else {
                    if (inChannelImage && "url".equals(n)) {
                        if (!t.isEmpty()) rssImage = t;
                    } else if ("image".equals(n)) {
                        inChannelImage = false;
                    } else if ("title".equals(n) && !inChannelImage && feed.title.isEmpty()) {
                        feed.title = t;
                    }
                }
                text.setLength(0);
            }
            ev = p.next();
        }
        if (feed.imageUrl == null) feed.imageUrl = rssImage;
        return feed;
    }

    private static String attr(XmlPullParser p, String name) {
        String v = p.getAttributeValue(null, name);
        if (v == null) return null;
        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    private static final String[] DATE_FORMATS = {
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, d MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm Z",
            "dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd",
    };

    static long parseDate(String s) {
        if (s == null || s.isEmpty()) return 0;
        for (String fmt : DATE_FORMATS) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(fmt, Locale.US);
                f.setLenient(true);
                Date d = f.parse(s);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }
}
