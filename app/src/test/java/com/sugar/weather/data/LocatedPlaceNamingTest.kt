package com.sugar.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 区县命名规则独立于 Android 服务，覆盖不同提供方的行政字段布局与缺测回退。 */
class LocatedPlaceNamingTest {
    /** 直辖市和普通城市均不能用 locality 遮住 subLocality 的区名。 */
    @Test fun `district in sub locality takes priority over city`() {
        assertEquals(
            "海淀区",
            locatedPlaceName(
                subLocality = "海淀区", locality = "北京市",
                subAdminArea = "北京市", adminArea = "北京市"
            )
        )
        assertEquals(
            "西湖区",
            locatedPlaceName(
                subLocality = "西湖区", locality = "杭州市",
                subAdminArea = "杭州市", adminArea = "浙江省"
            )
        )
    }

    /** 区县也可能落在 subAdminArea；含自治县和旗的结果均先于市名。 */
    @Test fun `county level sub admin area takes priority over city`() {
        for (county in listOf("临安区", "淳安县", "景宁畲族自治县", "土默特左旗")) {
            assertEquals(
                county,
                locatedPlaceName(
                    subLocality = null, locality = "上级城市",
                    subAdminArea = county, adminArea = "省级区域"
                )
            )
        }
    }

    /** subLocality 中的街道或社区不能遮住可用区县，也不能取代城市回退。 */
    @Test fun `street level sub locality does not become district name`() {
        for (street in listOf("望京街道", "望京街道办事处", "双桥镇", "东升乡", "幸福村", "幸福社区", "幸福小区")) {
            assertEquals(
                "朝阳区",
                locatedPlaceName(
                    subLocality = street, locality = "北京市",
                    subAdminArea = "朝阳区", adminArea = "北京市"
                )
            )
            assertEquals(
                "北京市",
                locatedPlaceName(
                    subLocality = street, locality = "北京市",
                    subAdminArea = null, adminArea = "北京市"
                )
            )
        }
    }

    /** 县级市沿用 locality，不被上级地级市覆盖。 */
    @Test fun `county level city remains more specific than prefecture`() {
        assertEquals(
            "昆山市",
            locatedPlaceName(
                subLocality = null, locality = "昆山市",
                subAdminArea = "苏州市", adminArea = "江苏省"
            )
        )
    }

    /** 明确的 locality 区县优先于社区别名，不能回退到上级城市。 */
    @Test fun `district in locality remains available`() {
        assertEquals(
            "浦东新区",
            locatedPlaceName(
                subLocality = "陆家嘴", locality = "浦东新区",
                subAdminArea = "上海市", adminArea = "上海市"
            )
        )
    }

    /** 地区、自治区和特别行政区不能因“区”后缀被当成更细的县级区。 */
    @Test fun `higher administrative areas do not replace locality`() {
        for ((locality, subAdminArea) in listOf(
            "普兰县" to "阿里地区",
            "呼和浩特市" to "内蒙古自治区",
            "香港" to "香港特别行政区"
        )) {
            assertEquals(
                locality,
                locatedPlaceName(
                    subLocality = null, locality = locality,
                    subAdminArea = subAdminArea, adminArea = null
                )
            )
        }
    }

    /** 空白及分类占位名应被忽略；合法区名保留并去掉首尾空格。 */
    @Test fun `blank fields and generic administrative labels are skipped`() {
        assertEquals(
            "淳安县",
            locatedPlaceName(
                subLocality = "市辖区", locality = "杭州市",
                subAdminArea = " 淳安县 ", adminArea = "浙江省"
            )
        )
        assertEquals(
            "郊区",
            locatedPlaceName(
                subLocality = " 郊区 ", locality = "铜陵市",
                subAdminArea = "铜陵市", adminArea = "安徽省"
            )
        )
        assertEquals(
            "浙江省",
            locatedPlaceName(
                subLocality = " \t ", locality = "区",
                subAdminArea = "县", adminArea = " 浙江省 "
            )
        )
    }

    /** 海外分区无需中文后缀；海外 county 不应覆盖已存在的城市名。 */
    @Test fun `overseas locality fields keep their hierarchy`() {
        assertEquals(
            "Manhattan",
            locatedPlaceName(
                subLocality = "Manhattan", locality = "New York",
                subAdminArea = "New York County", adminArea = "New York"
            )
        )
        assertEquals(
            "Mountain View",
            locatedPlaceName(
                subLocality = null, locality = "Mountain View",
                subAdminArea = "Santa Clara County", adminArea = "California"
            )
        )
        assertEquals(
            "Santa Clara County",
            locatedPlaceName(
                subLocality = null, locality = null,
                subAdminArea = "Santa Clara County", adminArea = "California"
            )
        )
    }

    /** 全部行政字段缺失时交给仓库保留旧名或使用“我的位置”。 */
    @Test fun `missing administrative fields return no name`() {
        assertNull(
            locatedPlaceName(
                subLocality = null, locality = null, subAdminArea = null, adminArea = null
            )
        )
    }
}
