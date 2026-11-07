package com.sugar.weather.data

import android.content.Context
import androidx.annotation.Keep
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.sugar.weather.data.model.City
import com.sugar.weather.data.model.WeatherReport
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** DataStore 单例:进程内同名文件只能有一个 DataStore,delegate 必须挂在顶层。 */
private val Context.weatherDataStore by preferencesDataStore(name = "weather_data")

/**
 * 城市列表与天气缓存的持久化(DESIGN.md §2.2,决策 D5:DataStore Preferences,JSON 字符串)。
 *
 * 键位(常量统一在本文件定义):
 * | Preferences Key            | 值               | 说明 |
 * |----------------------------|------------------|------|
 * | `city_list_v1`             | String(JSON 数组)| List<City> 经 Gson 序列化;读失败/为空 → 空列表(§2.2) |
 * | `weather_cache_{cityId}`   | String(JSON)    | [CachedWeather] 序列化;离线时读它渲染上次数据 |
 *
 * 读写纪律(§2.2):所有 DataStore 读写都封装在本类内,ViewModel 与 View 不得直接触碰 DataStore。
 * 城市数据的**业务规则**(定位城市置顶、去重、排序归一化)在 WeatherRepository,本类只管字节面。
 *
 * @param context 用于获取进程唯一的应用级 DataStore。
 */
class CityStore(context: Context) : CityStorage {

    private val appContext = context.applicationContext

    /**
     * Gson:WeatherReport 里含 java.time 类型(LocalDate/LocalDateTime/LocalTime),
     * Gson 默认按内部字段反射序列化 java.time 会产生脆弱结构,这里统一注册 ISO 字符串适配器。
     */
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, object : TypeAdapter<LocalDate>() {
            override fun write(out: JsonWriter, value: LocalDate) {
                out.value(value.toString())
            }
            override fun read(reader: JsonReader): LocalDate = LocalDate.parse(reader.nextString())
        }.nullSafe())
        .registerTypeAdapter(LocalDateTime::class.java, object : TypeAdapter<LocalDateTime>() {
            override fun write(out: JsonWriter, value: LocalDateTime) {
                out.value(value.toString())
            }
            override fun read(reader: JsonReader): LocalDateTime =
                LocalDateTime.parse(reader.nextString())
        }.nullSafe())
        .registerTypeAdapter(LocalTime::class.java, object : TypeAdapter<LocalTime>() {
            override fun write(out: JsonWriter, value: LocalTime) {
                out.value(value.toString())
            }
            override fun read(reader: JsonReader): LocalTime = LocalTime.parse(reader.nextString())
        }.nullSafe())
        .create()

    /** 城市列表流;读失败按 §2.2 回退空列表。 */
    override val cities: Flow<List<City>> = appContext.weatherDataStore.data
        .catch { cause ->
            if (cause is java.io.IOException) emit(emptyPreferences()) else throw cause
        }
        .map { prefs -> parseCities(prefs[CITY_LIST_KEY]) }

    /** 城市读改写与删除、坐标变化的缓存清理共用同一次事务，避免新地点沿用旧天气。 */
    override suspend fun updateCities(transform: (List<City>) -> List<City>): List<City> {
        var updated: List<City> = emptyList()
        appContext.weatherDataStore.edit { prefs ->
            val current = parseCities(prefs[CITY_LIST_KEY])
            updated = transform(current)
            if (updated != current) prefs[CITY_LIST_KEY] = gson.toJson(updated)
            val updatedById = updated.associateBy { it.id }
            current.filter { city ->
                updatedById[city.id]?.let { city.hasSameWeatherIdentity(other = it) } != true
            }.forEach { city ->
                prefs.remove(weatherCacheKey(city.id))
            }
        }
        return updated
    }

    /** 写入天气缓存;fetchedAt 外壳时间戳取自报告自身(§2.2 CachedWeather(report, fetchedAtMillis))。 */
    override suspend fun saveWeather(cityId: String, report: WeatherReport) {
        val json = gson.toJson(CachedWeather(report = report, fetchedAtMillis = report.fetchedAtMillis))
        appContext.weatherDataStore.edit { prefs ->
            prefs[weatherCacheKey(cityId)] = json
        }
    }

    /**
     * 读取全部天气缓存(cityId → report),供 WeatherRepository 启动时把内存层
     * `reports` 预热,保证进程重启后 15 天页/城市管理页离线仍有数据。
     */
    override suspend fun loadAllCachedWeather(): Map<String, WeatherReport> {
        val prefs = readPrefs() ?: return emptyMap()
        val result = LinkedHashMap<String, WeatherReport>()
        for ((key, value) in prefs.asMap()) {
            val cityId = key.name.removePrefix(WEATHER_CACHE_PREFIX)
            if (cityId == key.name || value !is String) continue
            // 每条独立校验；一份坏 JSON 或缺字段报告不能阻断其余城市的离线缓存。
            runCatching {
                gson.fromJson(value, CachedWeather::class.java)?.report?.normalizedFor(cityId = cityId)
            }
                .getOrNull()
                ?.let { result[cityId] = it }
        }
        return result
    }

    // ———————————————————————— 内部实现 ————————————————————————

    /** 读取缓存失败时允许联网恢复，但页面或进程取消必须继续向上传播。 */
    private suspend fun readPrefs(): Preferences? = try {
        appContext.weatherDataStore.data.first()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    /** 单个城市结构损坏时忽略该条目，避免影响其他合法城市。 */
    private fun parseCities(json: String?): List<City> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            // 只读取数组；使用 out 投影接收 Gson 的 City[]，同时过滤 JSON 中可能存在的 null。
            val parsed: Array<out City?>? = gson.fromJson(json, Array<City>::class.java)
            parsed?.toList()
                ?.filterNotNull()
                ?.filter {
                    !it.id.isNullOrBlank() && !it.name.isNullOrBlank() &&
                        it.latitude.isFinite() && it.latitude in -90.0..90.0 &&
                        it.longitude.isFinite() && it.longitude in -180.0..180.0
                }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    /** `weather_cache_{cityId}` 的持久化外壳(§2.2)。report 可空以抵御损坏 JSON 的反序列化。 */
    @Keep
    data class CachedWeather(val report: WeatherReport?, val fetchedAtMillis: Long)

    companion object {
        private const val CITY_LIST_KEY_NAME = "city_list_v1"
        private const val WEATHER_CACHE_PREFIX = "weather_cache_"
        private val CITY_LIST_KEY = stringPreferencesKey(CITY_LIST_KEY_NAME)
        private fun weatherCacheKey(cityId: String) =
            stringPreferencesKey(WEATHER_CACHE_PREFIX + cityId)
    }
}
