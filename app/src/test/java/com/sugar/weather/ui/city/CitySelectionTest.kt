package com.sugar.weather.ui.city

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 多选跟随城市身份，不能因定位卡或列表更新误删。 */
class CitySelectionTest {
    /** 首个手动城市不依赖列表位置即可进入选择。 */
    @Test fun `manual city enters selection mode`() {
        val selection = CitySelection().toggle(cityId = "first", eligibleIds = setOf("first"))
        assertTrue(selection.editing)
        assertEquals(setOf("first"), selection.cityIds)
    }

    /** 定位城市不在资格集合内，不能进入多选或成为删除目标。 */
    @Test fun `located and unknown cities cannot be selected`() {
        val selection = CitySelection().toggle(cityId = "located", eligibleIds = setOf("manual"))
        assertFalse(selection.editing)
        assertTrue(selection.cityIds.isEmpty())
    }

    /** 取消最后一项后保持编辑模式，用户可以选择其他城市。 */
    @Test fun `deselecting last city keeps editing mode`() {
        val selection = CitySelection(editing = true, cityIds = setOf("a"))
            .toggle(cityId = "a", eligibleIds = setOf("a", "b"))
        assertTrue(selection.editing)
        assertTrue(selection.cityIds.isEmpty())
    }

    /** 多项选择不依赖卡片顺序。 */
    @Test fun `selection accumulates by identity`() {
        val eligible = setOf("a", "b", "c")
        val selection = CitySelection()
            .toggle(cityId = "c", eligibleIds = eligible)
            .toggle(cityId = "a", eligibleIds = eligible)
        assertEquals(setOf("a", "c"), selection.cityIds)
    }

    /** 删除或转为定位城市后移除失效选择。 */
    @Test fun `list changes discard selections that are no longer removable`() {
        val selection = CitySelection(editing = true, cityIds = setOf("a", "b", "c"))
            .reconcile(eligibleIds = setOf("b"))
        assertEquals(setOf("b"), selection.cityIds)
    }

    /** 天气内容更新不影响选择集合。 */
    @Test fun `reconciling unchanged identities preserves selection`() {
        val selection = CitySelection(editing = true, cityIds = setOf("b"))
        assertEquals(selection, selection.reconcile(eligibleIds = setOf("a", "b")))
    }
}
