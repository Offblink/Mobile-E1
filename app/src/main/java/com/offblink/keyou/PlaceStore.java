package com.offblink.keyou;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 最近一次定位结果（纬度/经度/省份/来源/时间），存 SharedPreferences。
 *
 * 「我的」页离开再回来、App 重启后仍能显示上次推断的省份，不必每次重新定位。
 */
public final class PlaceStore {

    private static final String SP_NAME = "place";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final String KEY_PROVINCE = "province";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_TIME = "time";

    public static final class Place {
        public final double lat;
        public final double lon;
        public final String province;
        public final String provider;
        public final long time;

        Place(double lat, double lon, String province, String provider, long time) {
            this.lat = lat;
            this.lon = lon;
            this.province = province;
            this.provider = provider;
            this.time = time;
        }
    }

    private PlaceStore() {
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    public static void save(Context ctx, double lat, double lon,
                            String province, String provider, long time) {
        sp(ctx).edit()
                .putString(KEY_LAT, Double.toString(lat))
                .putString(KEY_LON, Double.toString(lon))
                .putString(KEY_PROVINCE, province)
                .putString(KEY_PROVIDER, provider)
                .putLong(KEY_TIME, time)
                .apply();
    }

    /** 没定位过返回 null */
    public static Place last(Context ctx) {
        SharedPreferences sp = sp(ctx);
        String lat = sp.getString(KEY_LAT, null);
        String lon = sp.getString(KEY_LON, null);
        String province = sp.getString(KEY_PROVINCE, null);
        if (lat == null || lon == null || province == null) {
            return null;
        }
        try {
            return new Place(Double.parseDouble(lat), Double.parseDouble(lon),
                    province, sp.getString(KEY_PROVIDER, ""), sp.getLong(KEY_TIME, 0));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
