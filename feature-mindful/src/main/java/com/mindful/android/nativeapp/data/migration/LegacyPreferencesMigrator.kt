package com.mindful.android.nativeapp.data.migration

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val UNIQUE_PREFS_FILE = "UniquePrefs"
private const val CRASH_LOG_PREFS_FILE = "CrashLogPrefs"
private const val MIGRATION_MARKER = "__mindful_legacy_shared_prefs_migrated_v1"

internal val Context.nativeUniqueDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mindful_unique_preferences"
)

internal val Context.nativeCrashLogDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mindful_crash_log_preferences"
)

/**
 * Copies the two Android-owned SharedPreferences stores into separate DataStore files.
 *
 * This is intentionally a copy, not a destructive migration: legacy SharedPreferences are
 * never cleared or edited. Run only when the native app is ready to become the sole writer,
 * because the still-running Flutter/native legacy code may continue changing these stores.
 */
class LegacyPreferencesMigrator(private val context: Context) {

    data class Result(
        val uniquePreferencesCopied: Int,
        val crashLogPreferencesCopied: Int,
        val alreadyMigrated: Boolean
    )

    suspend fun migrateIfNeeded(): Result = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val unique = copyStore(
            appContext,
            UNIQUE_PREFS_FILE,
            appContext.nativeUniqueDataStore
        )
        val crash = copyStore(
            appContext,
            CRASH_LOG_PREFS_FILE,
            appContext.nativeCrashLogDataStore
        )

        Result(
            uniquePreferencesCopied = unique.first,
            crashLogPreferencesCopied = crash.first,
            alreadyMigrated = unique.second && crash.second
        )
    }

    private suspend fun copyStore(
        appContext: Context,
        legacyName: String,
        dataStore: DataStore<Preferences>
    ): Pair<Int, Boolean> {
        val legacy = appContext.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
        val snapshot = legacy.all.toMap()
        val supported = snapshot.map { (key, value) -> key to normalizeValue(key, value) }

        var copiedCount = 0
        var wasAlreadyMigrated = false

        dataStore.edit { target ->
            val marker = booleanPreferencesKey(MIGRATION_MARKER)
            if (target[marker] == true) {
                wasAlreadyMigrated = true
                return@edit
            }

            for ((key, value) in supported) {
                if (key == MIGRATION_MARKER) {
                    throw IllegalStateException("Legacy preferences contain a reserved migration key.")
                }
                if (writeTypedValue(target, key, value)) copiedCount++
            }

            // The marker and copied values are committed atomically in this DataStore file.
            target[marker] = true
        }

        return copiedCount to wasAlreadyMigrated
    }

    private fun normalizeValue(key: String, value: Any?): Any {
        return when (value) {
            is String, is Boolean, is Int, is Long, is Float -> value
            is Set<*> -> {
                check(value.all { it is String }) {
                    "Unsupported non-string set in SharedPreferences key '$key'."
                }
                value.filterIsInstance<String>().toSet()
            }
            null -> throw IllegalStateException("Null SharedPreferences value for key '$key'.")
            else -> throw IllegalStateException(
                "Unsupported SharedPreferences value type for key '$key': ${value.javaClass.name}"
            )
        }
    }

    private fun writeTypedValue(
        target: MutablePreferences,
        key: String,
        value: Any
    ): Boolean {
        when (value) {
            is String -> target[stringPreferencesKey(key)] = value
            is Boolean -> target[booleanPreferencesKey(key)] = value
            is Int -> target[intPreferencesKey(key)] = value
            is Long -> target[longPreferencesKey(key)] = value
            is Float -> target[floatPreferencesKey(key)] = value
            is Set<*> -> {
                @Suppress("UNCHECKED_CAST")
                target[stringSetPreferencesKey(key)] = value as Set<String>
            }
            else -> error("Value was not normalized before DataStore write.")
        }
        return true
    }
}
