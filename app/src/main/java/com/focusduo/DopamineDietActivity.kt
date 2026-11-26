package com.focusduo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.focusduo.ui.DopamineDietHabitsDestination
import com.focusduo.ui.DopamineDietNavigationShell
import com.focusduo.ui.DopamineDietScreenTimeDestination
import com.focusduo.ui.DopamineDietScrollDestination
import com.focusduo.ui.DopamineDietSettingsDestination
import com.mindful.android.nativeapp.MainActivity as MindfulHostActivity

/**
 * The single product activity.
 *
 * Mindful's startup coordinator, database migration, permission flow, usage
 * refresh, focus-session recovery and restriction synchronization remain in
 * the inherited host. Only the post-onboarding content is replaced with the
 * unified Dopamine Diet experience.
 */
class DopamineDietActivity : MindfulHostActivity() {

    @Composable
    override fun MainContent() {
        val background = Color(0xFF08090E)
        val surface = Color(0xFF171923)
        val violet = Color(0xFFAA83FF)

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = violet,
                secondary = Color(0xFF4D9DFF),
                background = background,
                surface = surface,
                onPrimary = Color(0xFF17121F),
                onBackground = Color(0xFFF7F5FC),
                onSurface = Color(0xFFF7F5FC)
            )
        ) {
            DopamineDietNavigationShell(
                habits = { DopamineDietHabitsDestination() },
                scroll = { DopamineDietScrollDestination() },
                screenTime = { DopamineDietScreenTimeDestination() },
                settings = {
                    DopamineDietSettingsDestination(
                        database = database,
                        onStartFocus = ::startFocusSession,
                        onFinishFocus = ::finishFocusSession,
                        onSaveFocusProfile = ::saveFocusProfile,
                        onSaveFocusReflection = ::saveFocusReflection,
                        onRequestOverlayAccess = ::requestOverlayAccess,
                        onApplyRestrictions = ::applySavedRestrictions,
                        onSaveAppRestriction = ::saveAppRestriction,
                        onDeleteAppRestriction = ::deleteAppRestriction,
                        onSaveRestrictionGroup = ::saveRestrictionGroup,
                        onDeleteRestrictionGroup = ::deleteRestrictionGroup,
                        onSaveWellbeing = ::saveWellbeing
                    )
                }
            )
        }
    }
}
