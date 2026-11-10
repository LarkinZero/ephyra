package dev.jdtech.jellyfin

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #381: removing the current user from Settings, Users switches [NavigationRoot] from the main to
 * the setup [NavDisplay] while the main back stack still holds [UsersRoute], and setup restarts on
 * [UsersRoute] too. The two displays must not register that key in the same saved state holder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ModeEntryDecoratorsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun switchingToSetupWhileMainHoldsTheSameRouteDoesNotCrash() {
        var setupComplete by mutableStateOf(true)

        rule.setContent {
            val entryDecorators = rememberModeEntryDecorators()
            val mainBackStack = rememberNavBackStack(HomeRoute, UsersRoute)
            val setupBackStack = rememberNavBackStack(UsersRoute)
            val provider = entryProvider {
                entry<HomeRoute> { Text("home") }
                entry<UsersRoute> { Text("users") }
            }

            if (setupComplete) {
                NavDisplay(
                    backStack = mainBackStack,
                    entryDecorators = entryDecorators.main,
                    entryProvider = provider,
                )
            } else {
                NavDisplay(
                    backStack = setupBackStack,
                    entryDecorators = entryDecorators.setup,
                    entryProvider = provider,
                )
            }
        }
        rule.onNodeWithText("users").assertIsDisplayed()

        // The current user was removed: setup is no longer complete.
        setupComplete = false
        rule.waitForIdle()
        rule.onNodeWithText("users").assertIsDisplayed()

        // Signed in again, and back to setup once more.
        setupComplete = true
        rule.waitForIdle()
        setupComplete = false
        rule.waitForIdle()
        rule.onNodeWithText("users").assertIsDisplayed()
    }
}
