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
        val background = Color(0xFF09090D)
        val surface = Color(0xFF17171F)
        val violet = Color(0xFFB69AFF)

        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = violet,
                secondary = Color(0xFFD3C2FF),
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
