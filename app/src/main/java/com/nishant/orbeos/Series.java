package com.nishant.orbeos;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Series for you": new episodes released on streaming services in the last few days
 * (from TVmaze's public schedule — free, no account). Ranked so shows on apps you
 * have, and on the apps you actually watch most, come first.
 */
final class Series {
    static final class Show {
        int id;
        String name, channel, genres, episode, image, pkg;
        float rating;
        double score;
    }

    private Series() {}

    static List<Show> fetch(List<AppRepo.AppEntry> apps, Map<String, Long> watchWeek) throws Exception {
        LinkedHashMap<Integer, Show> map = new LinkedHashMap<>();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        long now = System.currentTimeMillis();
        for (int day = 0; day < 3; day++) {
            String date = f.format(new Date(now - day * 86400_000L));
            JSONArray arr;
            try {
                arr = new JSONArray(Net.get("https://api.tvmaze.com/schedule/web?date=" + date));
            } catch (Exception e) {
                if (day == 0) throw e;
                continue;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject ep = arr.optJSONObject(i);
                if (ep == null) continue;
                JSONObject emb = ep.optJSONObject("_embedded");
                JSONObject show = emb == null ? null : emb.optJSONObject("show");
                if (show == null) continue;
                int id = show.optInt("id");
                if (map.containsKey(id)) continue;
                String type = show.optString("type");
                if (!type.equals("Scripted") && !type.equals("Animation") && !type.equals("Documentary")) continue;
                JSONObject img = show.optJSONObject("image");
                if (img == null || img.optString("medium").isEmpty()) continue;

                Show s = new Show();
                s.id = id;
                s.name = show.optString("name");
                s.image = img.optString("medium").replace("http://", "https://");
                JSONObject wc = show.optJSONObject("webChannel");
                JSONObject nw = show.optJSONObject("network");
                s.channel = wc != null ? wc.optString("name") : nw != null ? nw.optString("name") : "";
                JSONArray g = show.optJSONArray("genres");
                StringBuilder gs = new StringBuilder();
                if (g != null) for (int k = 0; k < g.length() && k < 2; k++) {
                    if (gs.length() > 0) gs.append(", ");
                    gs.append(g.optString(k));
                }
                s.genres = gs.toString();
                int season = ep.optInt("season", 0), number = ep.optInt("number", 0);
                s.episode = season > 0 && number > 0 ? "S" + season + " · E" + number : "New episode";
                JSONObject r = show.optJSONObject("rating");
                s.rating = r == null ? 0f : (float) r.optDouble("average", 0);
                if (Float.isNaN(s.rating)) s.rating = 0f;
                s.pkg = match(s.channel, apps);

                String lang = show.optString("language");
                double minutes = 0;
                if (s.pkg != null && watchWeek != null) {
                    Long ms = watchWeek.get(s.pkg);
                    if (ms != null) minutes = ms / 60000.0;
                }
                s.score = (s.pkg != null ? 100 : 0)
                        + Math.min(minutes, 600) * 0.15
                        + show.optInt("weight", 0)
                        + s.rating * 4
                        + ("English".equals(lang) || "Hindi".equals(lang) ? 15 : 0)
                        - day * 5;
                map.put(id, s);
            }
        }
        ArrayList<Show> list = new ArrayList<>(map.values());
        Collections.sort(list, new Comparator<Show>() {
            @Override
            public int compare(Show a, Show b) {
                return Double.compare(b.score, a.score);
            }
        });
        return list.size() > 15 ? new ArrayList<>(list.subList(0, 15)) : list;
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
    }

    /** Matches a streaming service name to an installed app, if any. */
    static String match(String channel, List<AppRepo.AppEntry> apps) {
        String nc = norm(channel);
        if (nc.isEmpty()) return null;
        String want = null;
        if (nc.contains("disney") || nc.contains("hotstar") || nc.contains("jiocinema")) want = "hotstar";
        else if (nc.contains("prime") || nc.contains("amazon")) want = "prime";
        else if (nc.contains("appletv")) want = "appletv";
        for (AppRepo.AppEntry a : apps) {
            String nl = norm(a.label);
            if (nl.length() < 3) continue;
            if (want != null ? nl.contains(want) : (nc.contains(nl) || nl.contains(nc))) return a.pkg;
        }
        return null;
    }
}
