package dev.jdtech.jellyfin.ce.presentation.film

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.ce.ui.TvSafeArea
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.film.presentation.home.HomeState
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #688: a Home row that changes under the focused card. Focus follows the card and the row keeps it
 * on screen; a focused card that leaves hands focus to the one that takes its place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp")
class HomeRowUpdateTest {
    @get:Rule val rule = createComposeRule()

    private val movies: List<FindroidItem> =
        (0 until 10).map { dummyMovie.copy(id = UUID(0, it.toLong()), name = "Movie $it") }

    private var items by mutableStateOf(movies)

    private fun setUpHome() {
        rule.setContent {
            FindroidTheme {
                HomeScreenLayout(
                    state =
                        HomeState(
                            suggestionsSection =
                                HomeItem.Suggestions(id = UUID(1, 0), items = items),
                            isLoading = false,
                        ),
                    refreshed = true,
                    onAction = {},
                )
            }
        }
        rule.waitForIdle()
    }

    private fun card(item: FindroidItem): SemanticsMatcher =
        hasAnyAncestor(hasTestTag(homeCardTag(item.id))) and isFocusable()

    private fun assertOnScreen(item: FindroidItem) {
        val bounds = rule.onNode(card(item)).getBoundsInRoot()
        assertTrue("${item.name} starts at ${bounds.left}", bounds.left >= TvSafeArea.horizontal)
        assertTrue("${item.name} ends at ${bounds.right}", bounds.right <= 960.dp)
    }

    @Test
    fun focusFollowsACardThatMovesToTheFront() {
        setUpHome()
        val target = movies[4]
        rule.onNode(card(target)).requestFocus()
        rule.waitForIdle()

        items = listOf(target) + (movies - target)
        rule.waitForIdle()

        rule.onNode(card(target)).assertIsFocused()
        assertOnScreen(target)
    }

    @Test
    fun focusFollowsACardThatMovesToTheEnd() {
        setUpHome()
        val target = movies[1]
        rule.onNode(card(target)).requestFocus()
        rule.waitForIdle()

        items = (movies - target) + target
        rule.waitForIdle()

        rule.onNode(card(target)).assertIsFocused()
        assertOnScreen(target)
    }

    @Test
    fun aFocusedCardThatLeavesHandsFocusToTheNext() {
        setUpHome()
        val target = movies[2]
        rule.onNode(card(target)).requestFocus()
        rule.waitForIdle()

        items = movies - target
        rule.waitForIdle()

        rule.onNode(card(movies[3])).assertIsFocused()
        assertOnScreen(movies[3])
    }

    @Test
    fun aCardLandingInFrontOfTheFocusedFirstCardShows() {
        setUpHome()
        rule.onNode(card(movies[0])).assertIsFocused()
        val added = dummyMovie.copy(id = UUID(0, 99), name = "Movie 99")

        items = listOf(added) + movies
        rule.waitForIdle()

        rule.onNode(card(movies[0])).assertIsFocused()
        assertOnScreen(added)
        assertOnScreen(movies[0])
    }
}
