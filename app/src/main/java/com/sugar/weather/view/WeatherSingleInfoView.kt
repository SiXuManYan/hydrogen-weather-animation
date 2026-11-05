package com.sugar.weather.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.sugar.weather.databinding.ViewWeatherSingleInfoBinding
import com.sugar.weather.ui.weather.DetailCardUi

/**
 * 详情卡(DESIGN.md §6.2 第 6 条 / §10.2):标签次要色 13sp + 数值白 22sp + 单位小号。
 * 纯绑定 View:[bind] 只做字符串赋值,数据格式化在 WeatherViewModel 完成。
 * card 为 null(数据未就绪)时整卡隐藏。
 */
class WeatherSingleInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewWeatherSingleInfoBinding.inflate(
        LayoutInflater.from(context), this
    )

    fun bind(card: DetailCardUi?) {
        if (card == null) {
            isVisible = false
            return
        }
        isVisible = true
        binding.textLabel.text = card.label
        binding.textValue.text = card.value
        binding.textUnit.text = card.unit
        binding.textUnit.isVisible = card.unit.isNotEmpty()
    }
}
