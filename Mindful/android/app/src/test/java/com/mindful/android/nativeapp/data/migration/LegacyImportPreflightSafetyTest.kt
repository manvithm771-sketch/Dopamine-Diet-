package com.mindful.android.nativeapp.data.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyImportPreflightSafetyTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun unsupportedLegacySchemaDoesNotCreateNativeDatabaseOrModifySource() = runBlocking {
        val target = context.getDatabasePath("mindful_native.db")
        target.delete()
        File(target.absolutePath + "-wal").delete()
        File(target.absolutePath + "-shm").delete()

        val directory = File(context.filesDir, "app_flutter").apply { mkdirs() }
        val source = File(directory, "Mindful.sqlite")
        source.delete()

        val legacy = SQLiteDatabase.openOrCreateDatabase(source, null)
        try {
            legacy.version = 13
        } finally {
            legacy.close()
        }

        val originalBytes = source.readBytes()
        var rejected = false
        try {
            LegacyDriftImporter(context).importIfNeeded()
        } catch (_: IllegalStateException) {
            rejected = true
        }

        assertTrue("A schema other than Drift v14 must be rejected.", rejected)
        assertFalse("Preflight rejection must happen before Room creates the target.", target.exists())
        assertTrue("Preflight must not modify the legacy database.", originalBytes.contentEquals(source.readBytes()))
    }
}
