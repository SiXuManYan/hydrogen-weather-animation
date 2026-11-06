package com.template.framework.ui.recyclerview

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 在本机 JVM 提供 Observable 的 Android 实现，检查数据与通知，不创建 View 或运行页面。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [29])
class MutableDataAdapterTest {
    private class Adapter : MutableDataAdapter<Int, RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = error("Not used")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
        fun submitInternalList() = setNewDataList(dataList)
    }

    @Test
    fun appendAndClearDispatchExactRanges() {
        val adapter = Adapter()
        val events = mutableListOf<String>()
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { events.add("insert:$positionStart:$itemCount") }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { events.add("remove:$positionStart:$itemCount") }
        })
        adapter.addDataList(listOf(1, 2, 3))
        adapter.clearAllData()
        assertEquals(listOf("insert:0:3", "remove:0:3"), events)
    }

    @Test
    fun moveKeepsDataOrderAndRejectsInvalidPositions() {
        val adapter = Adapter()
        adapter.addDataList(listOf(1, 2, 3))
        adapter.moveItem(0, 2)
        assertEquals(listOf(2, 3, 1), adapter.getAllData())
        assertFalse(adapter.moveItem(RecyclerView.NO_POSITION, 1))
    }

    @Test
    fun replacingWithSameMutableListDoesNotEraseIt() {
        val adapter = Adapter()
        adapter.addDataList(listOf(1, 2))
        adapter.submitInternalList()
        assertEquals(listOf(1, 2), adapter.getAllData())
    }

    /** 可空占位与普通条目混合时，差分仍可插入、移动和替换。 */
    @Test
    fun nullableItemsCanBeDiffedWithoutPassingNullToCallback() {
        val callback = object : DiffUtil.ItemCallback<String?>() {
            /** 非空字符串代表条目身份，空占位由适配器处理。 */
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem

            /** 测试条目没有独立显示字段。 */
            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        }
        val adapter = object : MutableDataAdapter<String?, RecyclerView.ViewHolder>(diffCallback = callback) {
            /** 单元测试只执行差分与通知，不创建 Android View。 */
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = error("Not used")

            /** 无真实 View 参与绑定。 */
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
        }
        adapter.setNewDataList(listOf(null, "first", "second"))
        adapter.setNewDataList(listOf("first", null, "third"))
        assertEquals(listOf("first", null, "third"), adapter.getAllData())
        adapter.setNewDataList(listOf(null, null))
        assertEquals(listOf(null, null), adapter.getAllData())
    }
}
