package com.nishant.orbeos;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/** Live weather from Open-Meteo (free, no account, no personal data sent). */
final class Weather {
    static final class Now {
        float temp, hi, lo;
        int code;
        boolean day;
        String city;
        long at;
    }

    private Weather() {}

    static Now fetch(double lat, double lon, String city) throws Exception {
        String u = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto";
        JSONObject j = new JSONObject(Net.get(u));
        JSONObject cur = j.getJSONObject("current");
        JSONObject d = j.getJSONObject("daily");
        Now n = new Now();
        n.temp = (float) cur.getDouble("temperature_2m");
        n.code = cur.getInt("weather_code");
        n.day = cur.optInt("is_day", 1) == 1;
        n.hi = (float) d.getJSONArray("temperature_2m_max").getDouble(0);
        n.lo = (float) d.getJSONArray("temperature_2m_min").getDouble(0);
        n.city = city;
        n.at = System.currentTimeMillis();
        return n;
    }

    /** City name to {name, lat, lon}; null if not found. */
    static String[] geocode(String name) throws Exception {
        String u = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name="
                + URLEncoder.encode(name, "UTF-8");
        JSONObject j = new JSONObject(Net.get(u));
        JSONArray r = j.optJSONArray("results");
        if (r == null || r.length() == 0) return null;
        JSONObject o = r.getJSONObject(0);
        return new String[]{o.getString("name"), String.valueOf(o.getDouble("latitude")),
                String.valueOf(o.getDouble("longitude"))};
    }

    static String describe(int c) {
        if (c == 0) return "Clear";
        if (c == 1) return "Mostly clear";
        if (c == 2) return "Partly cloudy";
        if (c == 3) return "Cloudy";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 80 && c <= 82) return "Showers";
        if (c == 85 || c == 86) return "Snow showers";
        if (c >= 95) return "Thunderstorm";
        return "—";
    }

    static int icon(int c, boolean day) {
        if (c <= 1) return day ? Icons.SUN : Icons.NIGHT;
        if (c == 2) return day ? Icons.PARTLY : Icons.CLOUD;
        if (c == 3) return Icons.CLOUD;
        if (c == 45 || c == 48) return Icons.FOG;
        if ((c >= 51 && c <= 67) || (c >= 80 && c <= 82)) return Icons.RAIN;
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return Icons.SNOW;
        if (c >= 95) return Icons.STORM;
        return Icons.CLOUD;
    }
}
