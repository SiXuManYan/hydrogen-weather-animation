package com.template.framework.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Mirrors preference values between DataStore and a SharedPreferences backup.
 *
 * Writes and removals target both stores. Reads prefer DataStore and restore a missing value from
 * SharedPreferences in the background. Blank strings are treated as missing values.
 *
 * - 中文：写入时双写，读取时优先 DataStore；缺失值会从 SharedPreferences 恢复。
 *
 * @param context context used to open the backup SharedPreferences file
 * @param dataStore primary preferences store
 * @param backupPrefsName backup SharedPreferences file name
 */
class DataStoreBackupHelper(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
    backupPrefsName: String = "framework_preferences_backup"
) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(backupPrefsName, Context.MODE_PRIVATE)

    private fun isBlankValue(value: String?): Boolean = value.isNullOrBlank()

    /** 只读同步备份，不等待 DataStore；用于首帧前应用语言等轻量设置。 */
    fun getStringBackup(key: Preferences.Key<String>, defaultValue: String? = null): String? =
        sharedPreferences.getString(key.name, defaultValue)

    /**
     * Stores a string in both stores.
     *
     * @param key typed DataStore key; its name is reused for SharedPreferences
     * @param value value to persist
     * @param useCommit when `true`, blocks until the backup write reaches disk; otherwise uses
     * asynchronous `apply()`
     */
    suspend fun putString(key: Preferences.Key<String>, value: String, useCommit: Boolean = true) {
        try {
            dataStore.edit { it[key] = value }
            val editor = sharedPreferences.edit().putString(key.name, value)
            if (useCommit) editor.commit() else editor.apply()
            Timber.tag(TAG).i("Saved ${key.name}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save ${key.name}")
            throw e
        }
    }

    /** Stores an integer in both stores; see [putString] for [useCommit] semantics. */
    suspend fun putInt(key: Preferences.Key<Int>, value: Int, useCommit: Boolean = true) {
        try {
            dataStore.edit { it[key] = value }
            val editor = sharedPreferences.edit().putInt(key.name, value)
            if (useCommit) editor.commit() else editor.apply()
            Timber.tag(TAG).i("Saved ${key.name}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save ${key.name}")
            throw e
        }
    }

    /** Stores a Boolean in both stores; see [putString] for [useCommit] semantics. */
    suspend fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean, useCommit: Boolean = true) {
        try {
            dataStore.edit { it[key] = value }
            val editor = sharedPreferences.edit().putBoolean(key.name, value)
            if (useCommit) editor.commit() else editor.apply()
            Timber.tag(TAG).i("Saved ${key.name}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save ${key.name}")
            throw e
        }
    }

    /**
     * Reads a string from DataStore, falling back to the backup.
     *
     * A non-blank backup value is returned immediately and restored to DataStore asynchronously.
     *
     * @param key typed preference key
     * @param defaultValue value returned when neither store contains a non-blank value
     */
    suspend fun getString(key: Preferences.Key<String>, defaultValue: String? = null): String? {
        return try {
            val dsValue = dataStore.data.first()[key]
            if (!isBlankValue(dsValue)) return dsValue
            val backup = sharedPreferences.getString(key.name, defaultValue)
            if (!isBlankValue(backup)) {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { putString(key, backup!!, useCommit = true) }
                }
                backup
            } else defaultValue
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get ${key.name}")
            sharedPreferences.getString(key.name, defaultValue)
        }
    }

    /** Reads an integer from DataStore, then the backup, then [defaultValue]. */
    suspend fun getInt(key: Preferences.Key<Int>, defaultValue: Int? = null): Int? {
        return try {
            val dsValue = dataStore.data.first()[key]
            if (dsValue != null) return dsValue
            val backup = if (sharedPreferences.contains(key.name)) {
                sharedPreferences.getInt(key.name, defaultValue ?: 0)
            } else defaultValue
            if (backup != null && (defaultValue == null || backup != defaultValue)) {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { putInt(key, backup, useCommit = true) }
                }
            }
            backup
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get ${key.name}")
            if (sharedPreferences.contains(key.name)) sharedPreferences.getInt(key.name, defaultValue ?: 0)
            else defaultValue
        }
    }

    /** Reads a Boolean from DataStore, then the backup, then [defaultValue]. */
    suspend fun getBoolean(key: Preferences.Key<Boolean>, defaultValue: Boolean? = null): Boolean? {
        return try {
            val dsValue = dataStore.data.first()[key]
            if (dsValue != null) return dsValue
            val backup = if (sharedPreferences.contains(key.name)) {
                sharedPreferences.getBoolean(key.name, defaultValue ?: false)
            } else defaultValue
            if (backup != null && (defaultValue == null || backup != defaultValue)) {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { putBoolean(key, backup, useCommit = true) }
                }
            }
            backup
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get ${key.name}")
            if (sharedPreferences.contains(key.name)) sharedPreferences.getBoolean(key.name, defaultValue ?: false)
            else defaultValue
        }
    }

    /** Removes [key] from both stores. */
    suspend fun remove(key: Preferences.Key<*>) {
        try {
            dataStore.edit { it.remove(key) }
            sharedPreferences.edit().remove(key.name).commit()
            Timber.tag(TAG).d("Removed ${key.name}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to remove ${key.name}")
            throw e
        }
    }

    /**
     * Observes a string preference with backup fallback and duplicate suppression.
     *
     * Backup recovery is scheduled when DataStore emits a missing or blank value.
     */
    fun getStringFlow(key: Preferences.Key<String>, defaultValue: String? = null): Flow<String?> {
        return dataStore.data.map { prefs ->
            val v = prefs[key]
            if (!isBlankValue(v)) v
            else {
                val backup = sharedPreferences.getString(key.name, defaultValue)
                if (!isBlankValue(backup)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching { putString(key, backup!!, useCommit = true) }
                    }
                    backup
                } else defaultValue
            }
        }.distinctUntilChanged()
    }

    /** Observes an integer preference with backup fallback and duplicate suppression. */
    fun getIntFlow(key: Preferences.Key<Int>, defaultValue: Int? = null): Flow<Int?> {
        return dataStore.data.map { prefs ->
            val v = prefs[key]
            if (v != null) v
            else {
                val backup = if (sharedPreferences.contains(key.name)) {
                    sharedPreferences.getInt(key.name, defaultValue ?: 0)
                } else defaultValue
                if (backup != null && (defaultValue == null || backup != defaultValue)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching { putInt(key, backup, useCommit = true) }
                    }
                }
                backup
            }
        }.distinctUntilChanged()
    }

    /** Observes a Boolean preference with backup fallback and duplicate suppression. */
    fun getBooleanFlow(key: Preferences.Key<Boolean>, defaultValue: Boolean? = null): Flow<Boolean?> {
        return dataStore.data.map { prefs ->
            val v = prefs[key]
            if (v != null) v
            else {
                val backup = if (sharedPreferences.contains(key.name)) {
                    sharedPreferences.getBoolean(key.name, defaultValue ?: false)
                } else defaultValue
                if (backup != null && (defaultValue == null || backup != defaultValue)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching { putBoolean(key, backup, useCommit = true) }
                    }
                }
                backup
            }
        }.distinctUntilChanged()
    }

    private companion object {
        const val TAG = "DataStoreBackup"
    }
}
