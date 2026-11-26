package com.mindful.android.nativeapp

import androidx.compose.ui.platform.ComposeView
import com.mindful.android.nativeapp.data.local.NativeDatabaseProvider
import org.junit.After
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @After
    fun tearDown() {
        NativeDatabaseProvider.closeForTests()
        RuntimeEnvironment.getApplication().deleteDatabase(NativeDatabaseProvider.DATABASE_NAME)
    }

    @Test
    fun launchesAndAttachesComposeContent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        assertNotNull(activity)
        val contentRoot = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        assertEquals(1, contentRoot.childCount)
        assertNotNull(contentRoot.getChildAt(0) as? ComposeView)

        controller.pause().stop().destroy()
    }
}
