package dev.jdtech.jellyfin.ce.presentation.main

import android.app.Application
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.core.presentation.dummy.dummyCollections
import dev.jdtech.jellyfin.core.presentation.dummy.dummyServer
import dev.jdtech.jellyfin.core.presentation.dummy.dummyUser
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #539: the drawer's icons, avatar and labels start inside the TV safe area (48 dp from the left
 * edge) with the drawer collapsed and expanded, and the pages beside it still start at 80 dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp")
class DrawerSafeAreaTest {
    @get:Rule val rule = createComposeRule()

    private val iconTags =
        listOf(
            "drawer_icon_profile",
            "drawer_icon_search",
            "drawer_icon_home",
            "drawer_icon_favorites",
            "drawer_icon_settings",
        ) + dummyCollections.map { "drawer_icon_library_${it.id}" }

    private fun setUp(
        homeContent: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize().testTag("home_screen_content").focusable())
        }
    ) {
        rule.setContent {
            FindroidTheme {
                MainScreenLayout(
                    user = dummyUser,
                    server = dummyServer,
                    libraries = dummyCollections,
                    navigateToSettings = {},
                    navigateToUsers = {},
                    homeContent = homeContent,
                    searchContent = {},
                    favoritesContent = {},
                    libraryContent = {},
                )
            }
        }
        rule.waitForIdle()
    }

    private fun assertIconsInSafeArea(state: String) {
        for (tag in iconTags) {
            val left = rule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().left
            assertTrue(
                "$state: $tag starts at $left, in the margin (${TvSafeArea.horizontal})",
                left >= TvSafeArea.horizontal,
            )
        }
    }

    @Test
    fun collapsedIconsAreInsideTheSafeArea() {
        setUp()
        rule.onNodeWithTag("home_screen_content").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("home_screen_content").assertIsFocused()

        assertIconsInSafeArea("collapsed")
        // The avatar is the widest leading content: it sits on the edge.
        assertEquals(
            TvSafeArea.horizontal,
            rule.onNodeWithTag("drawer_icon_profile", useUnmergedTree = true)
                .getBoundsInRoot()
                .left,
        )
    }

    @Test
    fun expandedIconsAndLabelsAreInsideTheSafeArea() {
        setUp()
        rule.onNodeWithTag("drawer_item_home").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_item_home").assertIsFocused()

        assertIconsInSafeArea("expanded")
        for (label in listOf(dummyUser.name, "Search", "Home", "Settings")) {
            val left = rule.onNodeWithText(label, useUnmergedTree = true).getBoundsInRoot().left
            assertTrue("The label $label starts at $left", left >= TvSafeArea.horizontal)
        }
    }

    /**
     * The expanded drawer's items now end at 292 dp, right of a poster that starts at the page's
     * 128 dp and is 150 dp wide (Favorites). A plain Right finds nothing right of the item there and
     * left focus in the drawer.
     */
    @Test
    fun rightFromTheDrawerReachesAPosterThatEndsBeforeTheDrawerItem() {
        setUp(
            homeContent = {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.padding(start = TvSafeArea.horizontal, top = 120.dp)
                            .size(150.dp, 225.dp)
                            .focusable()
                            .testTag("poster")
                    )
                }
            }
        )
        rule.onNodeWithTag("poster").requestFocus()
        rule.waitForIdle()

        rule.onNodeWithTag("poster").performKeyInput { pressKey(Key.DirectionLeft) }
        rule.waitForIdle()
        rule.onNodeWithTag("drawer_item_home").assertIsFocused()

        rule.onNodeWithTag("drawer_item_home").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithTag("poster").assertIsFocused()
    }

    @Test
    fun pagesBesideTheDrawerStillStartAt80dp() {
        setUp()
        assertEquals(
            80.dp,
            rule.onNodeWithTag("home_screen_content").getBoundsInRoot().left,
        )
    }
}
