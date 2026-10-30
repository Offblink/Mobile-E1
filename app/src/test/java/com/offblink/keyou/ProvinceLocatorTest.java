package com.offblink.keyou;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 省份枚举表回归：34 个省会/首府各取一个点，外加直辖市、特别行政区与几个易串界的城市。
 * 这里钉住的是「表与判定规则」，不涉及 Android 运行时。
 */
public class ProvinceLocatorTest {

    private static void assertProvince(String province, String city, double lat, double lon) {
        assertEquals(city + " 应归属 " + province, province, ProvinceLocator.provinceOf(lat, lon));
    }

    @Test
    public void capitalsBelongToTheirProvince() {
        assertProvince("北京", "北京", 39.9042, 116.4074);
        assertProvince("天津", "天津", 39.0842, 117.2009);
        assertProvince("河北", "石家庄", 38.0428, 114.5149);
        assertProvince("山西", "太原", 37.8706, 112.5489);
        assertProvince("内蒙古", "呼和浩特", 40.8424, 111.7490);
        assertProvince("辽宁", "沈阳", 41.8057, 123.4315);
        assertProvince("吉林", "长春", 43.8171, 125.3235);
        assertProvince("黑龙江", "哈尔滨", 45.8038, 126.5349);
        assertProvince("上海", "上海", 31.2304, 121.4737);
        assertProvince("江苏", "南京", 32.0603, 118.7969);
        assertProvince("浙江", "杭州", 30.2741, 120.1551);
        assertProvince("安徽", "合肥", 31.8206, 117.2272);
        assertProvince("福建", "福州", 26.0745, 119.2965);
        assertProvince("江西", "南昌", 28.6820, 115.8579);
        assertProvince("山东", "济南", 36.6512, 117.1201);
        assertProvince("河南", "郑州", 34.7466, 113.6254);
        assertProvince("湖北", "武汉", 30.5928, 114.3055);
        assertProvince("湖南", "长沙", 28.2282, 112.9388);
        assertProvince("广东", "广州", 23.1291, 113.2644);
        assertProvince("广西", "南宁", 22.8170, 108.3665);
        assertProvince("海南", "海口", 20.0442, 110.1999);
        assertProvince("重庆", "重庆", 29.5630, 106.5516);
        assertProvince("四川", "成都", 30.5728, 104.0668);
        assertProvince("贵州", "贵阳", 26.6470, 106.6302);
        assertProvince("云南", "昆明", 25.0389, 102.7183);
        assertProvince("西藏", "拉萨", 29.6520, 91.1721);
        assertProvince("陕西", "西安", 34.3416, 108.9398);
        assertProvince("甘肃", "兰州", 36.0611, 103.8343);
        assertProvince("青海", "西宁", 36.6171, 101.7782);
        assertProvince("宁夏", "银川", 38.4872, 106.2309);
        assertProvince("新疆", "乌鲁木齐", 43.8256, 87.6168);
        assertProvince("台湾", "台北", 25.0330, 121.5654);
        assertProvince("香港", "香港", 22.3193, 114.1694);
        assertProvince("澳门", "澳门", 22.1987, 113.5439);
    }

    @Test
    public void enclavesAndBorderCitiesDoNotLeak() {
        // 港澳嵌在广东的包围盒里：面积最小优先
        assertProvince("广东", "深圳", 22.5431, 114.0579);
        assertProvince("广东", "珠海", 22.2707, 113.5767);
        // 直辖市嵌在邻省包围盒里
        assertProvince("上海", "上海", 31.2304, 121.4737);
        assertProvince("江苏", "苏州", 31.2989, 120.5853);
        assertProvince("江苏", "徐州", 34.2044, 117.2858);
        assertProvince("河北", "廊坊", 39.5379, 116.6836);
        assertProvince("河北", "沧州", 38.3037, 116.8388);
        assertProvince("河南", "安阳", 36.1034, 114.3931);
        assertProvince("河南", "洛阳", 34.6197, 112.4540);
        assertProvince("安徽", "合肥", 31.8206, 117.2272);
        assertProvince("安徽", "黄山", 29.7147, 118.3376);
        assertProvince("湖北", "武汉", 30.5928, 114.3055);
        assertProvince("湖南", "岳阳", 29.3571, 113.1289);
        assertProvince("江西", "九江", 29.7051, 116.0019);
        assertProvince("浙江", "温州", 27.9949, 120.6994);
        assertProvince("福建", "龙岩", 25.0751, 117.0173);
        assertProvince("广东", "韶关", 24.8103, 113.5972);
        assertProvince("广西", "桂林", 25.2736, 110.2900);
        assertProvince("广西", "梧州", 23.4769, 111.2791);
        assertProvince("贵州", "贵阳", 26.6470, 106.6302);
        assertProvince("四川", "南充", 30.8373, 106.1106);
        assertProvince("四川", "宜宾", 28.7513, 104.6417);
        assertProvince("重庆", "万州", 30.8079, 108.4086);
        assertProvince("重庆", "奉节", 31.0184, 109.4658);
        assertProvince("陕西", "汉中", 33.0675, 107.0238);
        assertProvince("甘肃", "天水", 34.5809, 105.7249);
        assertProvince("宁夏", "银川", 38.4872, 106.2309);
        assertProvince("内蒙古", "赤峰", 42.2586, 118.8881);
        assertProvince("吉林", "通化", 41.7284, 125.9398);
        assertProvince("黑龙江", "牡丹江", 44.5517, 129.6334);
        assertProvince("山东", "临沂", 35.1047, 118.3564);
        assertProvince("海南", "三亚", 18.2528, 109.5119);
    }

    @Test
    public void allProvinceNamesAreDistinct() {
        Object[][] capitals = {
                {"北京", 39.9042, 116.4074}, {"天津", 39.0842, 117.2009},
                {"河北", 38.0428, 114.5149}, {"山西", 37.8706, 112.5489},
                {"内蒙古", 40.8424, 111.7490}, {"辽宁", 41.8057, 123.4315},
                {"吉林", 43.8171, 125.3235}, {"黑龙江", 45.8038, 126.5349},
                {"上海", 31.2304, 121.4737}, {"江苏", 32.0603, 118.7969},
                {"浙江", 30.2741, 120.1551}, {"安徽", 31.8206, 117.2272},
                {"福建", 26.0745, 119.2965}, {"江西", 28.6820, 115.8579},
                {"山东", 36.6512, 117.1201}, {"河南", 34.7466, 113.6254},
                {"湖北", 30.5928, 114.3055}, {"湖南", 28.2282, 112.9388},
                {"广东", 23.1291, 113.2644}, {"广西", 22.8170, 108.3665},
                {"海南", 20.0442, 110.1999}, {"重庆", 29.5630, 106.5516},
                {"四川", 30.5728, 104.0668}, {"贵州", 26.6470, 106.6302},
                {"云南", 25.0389, 102.7183}, {"西藏", 29.6520, 91.1721},
                {"陕西", 34.3416, 108.9398}, {"甘肃", 36.0611, 103.8343},
                {"青海", 36.6171, 101.7782}, {"宁夏", 38.4872, 106.2309},
                {"新疆", 43.8256, 87.6168}, {"台湾", 25.0330, 121.5654},
                {"香港", 22.3193, 114.1694}, {"澳门", 22.1987, 113.5439},
        };
        Set<String> names = new HashSet<>();
        for (Object[] c : capitals) {
            String province = ProvinceLocator.provinceOf(
                    (Double) c[1], (Double) c[2]);
            assertEquals("首府点应落在本省", c[0], province);
            assertTrue("省份名重复：" + province, names.add(province));
        }
        assertEquals(34, names.size());
    }

    @Test
    public void outsideChinaReturnsNull() {
        assertNull(ProvinceLocator.provinceOf(0, 0));
        assertNull(ProvinceLocator.provinceOf(35.0, 140.0));
        assertNull(ProvinceLocator.provinceOf(60.0, 100.0));
        assertFalse(ProvinceLocator.inChina(48.8566, 2.3522));
        assertTrue(ProvinceLocator.inChina(39.9042, 116.4074));
    }

    @Test
    public void coordinatesAreRoundedForDisplayWithoutLosingProvince() {
        // 与 UI 显示的 4 位小数口径一致，避免边界点在展示/判定之间跳动
        double[][] points = {{31.2304, 121.4737}, {22.5431, 114.0579}, {43.8256, 87.6168}};
        String[] expected = {"上海", "广东", "新疆"};
        for (int i = 0; i < points.length; i++) {
            String rounded = String.format(Locale.US, "%.4f,%.4f",
                    points[i][0], points[i][1]);
            String[] parts = rounded.split(",");
            assertEquals(expected[i], ProvinceLocator.provinceOf(
                    Double.parseDouble(parts[0]), Double.parseDouble(parts[1])));
        }
    }
}
