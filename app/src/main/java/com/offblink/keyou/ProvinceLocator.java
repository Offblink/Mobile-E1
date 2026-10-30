package com.offblink.keyou;

/**
 * 离线省份枚举：把 34 个省级行政区近似成一组经纬度矩形（狭长或包围别人的省拆成多块），
 * 坐标落进哪块就判成哪个省 —— 不联网、不接地图 SDK。
 *
 * 判定规则：在所有包含该点的矩形里取面积最小的那块；都不包含时退到「矩形中心最近」的省。
 * 省界不是矩形，边界附近、飞地与港澳这类嵌在广东里的小区域会有误差，展示时注明「离线推断」。
 */
public final class ProvinceLocator {

    private static final class Area {
        final String province;
        final double minLat;
        final double maxLat;
        final double minLon;
        final double maxLon;

        Area(String province, double minLat, double maxLat, double minLon, double maxLon) {
            this.province = province;
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.minLon = minLon;
            this.maxLon = maxLon;
        }

        double area() {
            return (maxLat - minLat) * (maxLon - minLon);
        }

        boolean contains(double lat, double lon) {
            return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
        }

        double centerLat() {
            return (minLat + maxLat) / 2;
        }

        double centerLon() {
            return (minLon + maxLon) / 2;
        }
    }

    /** 中国经纬度大致范围（不含南海诸岛以外的海域） */
    private static final double CHINA_MIN_LAT = 3.8;
    private static final double CHINA_MAX_LAT = 53.6;
    private static final double CHINA_MIN_LON = 73.4;
    private static final double CHINA_MAX_LON = 135.2;

    /** 每个矩形都是「省（或省的一块）」的外接框；顺序无关，命中多块时比面积。 */
    private static final Area[] AREAS = {
            new Area("北京", 39.55, 41.06, 115.42, 117.51),
            new Area("天津", 38.55, 40.25, 116.75, 118.06),
            new Area("河北", 36.05, 42.10, 113.45, 119.85),
            new Area("山西", 36.50, 40.74, 110.23, 113.60),
            new Area("山西", 35.00, 36.50, 110.23, 113.15),
            new Area("山西", 34.57, 35.00, 110.20, 111.00),
            new Area("内蒙古", 37.40, 53.34, 97.17, 126.04),
            new Area("辽宁", 38.70, 43.50, 119.80, 125.80),
            new Area("吉林", 40.90, 45.50, 123.50, 127.00),
            new Area("吉林", 42.00, 44.30, 127.00, 131.30),
            new Area("吉林", 45.50, 46.30, 121.60, 123.50),
            new Area("黑龙江", 43.40, 53.60, 122.40, 135.10),
            new Area("上海", 30.70, 31.88, 121.00, 122.12),
            new Area("江苏", 33.40, 35.00, 116.35, 120.50),
            new Area("江苏", 31.50, 33.45, 118.60, 121.95),
            new Area("江苏", 31.00, 31.50, 119.30, 121.95),
            new Area("江苏", 31.20, 31.60, 118.60, 119.00),
            new Area("浙江", 27.03, 31.18, 118.02, 123.15),
            new Area("安徽", 30.00, 33.50, 115.00, 119.65),
            new Area("安徽", 33.50, 34.60, 115.70, 117.00),
            new Area("安徽", 29.30, 30.00, 117.80, 119.00),
            new Area("福建", 23.53, 28.32, 116.45, 120.50),
            new Area("江西", 24.90, 30.08, 113.57, 118.48),
            new Area("山东", 34.60, 38.20, 116.20, 122.70),
            new Area("山东", 34.60, 36.50, 115.20, 116.20),
            new Area("河南", 31.38, 36.37, 110.35, 116.65),
            new Area("湖北", 29.80, 31.80, 108.60, 113.50),
            new Area("湖北", 29.00, 31.80, 113.50, 115.99),
            new Area("湖北", 31.70, 33.27, 108.60, 112.50),
            new Area("湖南", 25.40, 30.13, 108.78, 114.26),
            new Area("广东", 20.20, 22.50, 109.70, 112.20),
            new Area("广东", 22.15, 25.52, 111.60, 116.90),
            new Area("广西", 20.54, 26.39, 104.45, 112.06),
            new Area("海南", 18.10, 20.17, 108.61, 111.05),
            new Area("海南", 15.50, 17.50, 111.00, 113.00),
            new Area("重庆", 28.16, 30.40, 105.50, 110.20),
            new Area("重庆", 30.40, 31.00, 106.30, 110.20),
            new Area("重庆", 31.00, 32.20, 108.20, 110.20),
            new Area("四川", 26.05, 34.32, 97.35, 108.55),
            new Area("贵州", 26.50, 27.80, 104.70, 109.53),
            new Area("贵州", 27.80, 29.22, 105.60, 109.53),
            new Area("贵州", 24.90, 26.50, 104.25, 109.53),
            new Area("云南", 21.14, 26.00, 97.53, 106.20),
            new Area("云南", 26.00, 28.50, 98.00, 101.00),
            new Area("云南", 26.00, 28.50, 103.00, 104.60),
            new Area("西藏", 26.85, 36.50, 78.39, 99.11),
            new Area("陕西", 31.71, 39.59, 106.00, 111.24),
            new Area("甘肃", 37.00, 40.50, 94.00, 104.50),
            new Area("甘肃", 35.00, 37.00, 102.50, 108.70),
            new Area("甘肃", 32.10, 35.00, 97.50, 106.50),
            new Area("青海", 31.60, 39.20, 89.35, 103.10),
            new Area("宁夏", 35.20, 39.40, 104.20, 107.70),
            new Area("新疆", 34.30, 49.20, 73.40, 96.40),
            new Area("台湾", 21.90, 25.32, 119.30, 122.05),
            new Area("香港", 22.15, 22.48, 113.83, 114.30),
            new Area("澳门", 22.10, 22.22, 113.52, 113.60),
    };

    private ProvinceLocator() {
    }

    /** 坐标是否落在中国经纬度范围内（境外返回 false） */
    public static boolean inChina(double lat, double lon) {
        return lat >= CHINA_MIN_LAT && lat <= CHINA_MAX_LAT
                && lon >= CHINA_MIN_LON && lon <= CHINA_MAX_LON;
    }

    /**
     * 省份短名（如「四川」「内蒙古」「香港」）；坐标不在中国范围内返回 null。
     * 纯枚举：矩形命中取最小面积，全不命中取最近矩形中心。
     */
    public static String provinceOf(double lat, double lon) {
        if (!inChina(lat, lon)) {
            return null;
        }
        Area best = null;
        for (Area area : AREAS) {
            if (area.contains(lat, lon) && (best == null || area.area() < best.area())) {
                best = area;
            }
        }
        if (best != null) {
            return best.province;
        }
        // 省界凹口或近海：最近矩形中心（经度差按当前纬度收缩，避免高纬被放大）
        Area nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        double lonScale = Math.cos(Math.toRadians(lat));
        for (Area area : AREAS) {
            double dLat = lat - area.centerLat();
            double dLon = (lon - area.centerLon()) * lonScale;
            double distance = dLat * dLat + dLon * dLon;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = area;
            }
        }
        return nearest == null ? null : nearest.province;
    }
}
