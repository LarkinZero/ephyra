package dev.jdtech.jellyfin.presentation.film

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSection
import dev.jdtech.jellyfin.core.presentation.dummy.dummyServer
import dev.jdtech.jellyfin.film.presentation.home.HomeState
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h914dp")
class HomeScreenLayoutTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun setLayout(state: HomeState) {
        rule.setContent {
            FindroidTheme {
                CompositionLocalProvider(LocalOfflineMode provides false) {
                    HomeScreenLayout(state = state, onAction = {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    /** #391: a reload showed the pull indicator and a second spinner in the header. */
    @Test
    fun aReloadShowsOneLoadingIndicator() {
        setLayout(
            HomeState(server = dummyServer, resumeSection = dummyHomeSection, isLoading = true)
        )

        rule
            .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertCountEquals(1)
    }

    /** #391: the first load after a cold start, with nothing on screen yet, still shows one. */
    @Test
    fun theFirstLoadShowsOneLoadingIndicator() {
        setLayout(HomeState(server = dummyServer, isLoading = true))

        rule
            .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertCountEquals(1)
    }
}
