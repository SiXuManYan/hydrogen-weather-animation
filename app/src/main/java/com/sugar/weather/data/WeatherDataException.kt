package com.sugar.weather.data

import java.io.IOException
import retrofit2.HttpException

/**
 * 数据层统一错误类型(ask 约束:网络失败等异常统一包装成明确错误类型,不抛裸异常给 ViewModel)。
 *
 * ViewModel 只需处理本类型(或经 [Result.failure] 携带),不会收到 IOException /
 * HttpException / Gson 异常等裸异常;CANCEL 协程取消会原样重抛,不做包装。
 */
sealed class WeatherDataException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** 连接失败:网络不可达、DNS、超时等 IO 层错误。 */
    class Network(cause: Throwable) : WeatherDataException(
        "网络请求失败:${cause.message ?: cause.javaClass.simpleName}", cause
    )

    /** 服务端返回非 2xx。 */
    class Http(val code: Int) : WeatherDataException("服务端返回错误(HTTP $code)")

    /** 响应结构与预期不符,无法解析。 */
    class Parse(cause: Throwable? = null) : WeatherDataException("天气数据解析失败", cause)

    /** 本地 DataStore 读写失败。 */
    class Store(cause: Throwable) : WeatherDataException("本地存储读写失败", cause)

    /** 城市不存在:cityId 未在列表中(未添加或已被删除)。 */
    class CityNotFound(val cityId: String) : WeatherDataException("城市不存在:$cityId")

    companion object {
        /**
         * 把任意网络调用抛出的异常归一化为 [WeatherDataException];
         * CancellationException 原样重抛(协程取消不属于业务错误)。
         */
        fun wrap(cause: Throwable): WeatherDataException = when (cause) {
            is WeatherDataException -> cause
            is kotlinx.coroutines.CancellationException -> throw cause
            is HttpException -> Http(cause.code())
            is com.google.gson.JsonSyntaxException -> Parse(cause)
            is IOException -> Network(cause)
            else -> Parse(cause)
        }
    }
}
