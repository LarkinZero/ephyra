package dev.jdtech.jellyfin.ce.presentation.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.presentation.enums.AppVariant
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceCategory
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceFileEdit
import dev.jdtech.jellyfin.settings.presentation.settings.SettingsViewModel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsTreeVariantTest {

    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val sp = context.getSharedPreferences("test_settings_prefs", Context.MODE_PRIVATE)
        appPreferences = AppPreferences(sp)
    }

    @Test
    fun `TV variant hides gestures, pip, and background playback in player settings`() = runTest {
        val tvViewModel = SettingsViewModel(context, appPreferences, AppVariant.TV)
        tvViewModel.loadPreferences(listOf(SettingsR.string.settings_category_player))

        val groups = tvViewModel.state.value.preferenceGroups
        val groupTitles = groups.mapNotNull { it.nameStringResource }

        assertFalse(
            groupTitles.contains(SettingsR.string.gestures),
            message = "Gestures group must be hidden on TV",
        )
        assertFalse(
            groupTitles.contains(SettingsR.string.picture_in_picture),
            message = "PiP group must be hidden on TV",
        )
        assertFalse(
            groupTitles.contains(SettingsR.string.pref_player_background_group),
            message = "Background audio group must be hidden on TV",
        )

        // Seeking group must be present on TV
        assertTrue(
            groupTitles.contains(SettingsR.string.seeking),
            message = "Seeking group must be present on TV",
        )
    }

    @Test
    fun `TV variant shows the trickplay switch but not the gesture one`() = runTest {
        val tvViewModel = SettingsViewModel(context, appPreferences, AppVariant.TV)
        tvViewModel.loadPreferences(listOf(SettingsR.string.settings_category_player))

        val trickplayGroup =
            tvViewModel.state.value.preferenceGroups.first {
                it.nameStringResource == SettingsR.string.trickplay
            }
        val names = trickplayGroup.preferences.map { it.nameStringResource }

        assertTrue(
            names.contains(SettingsR.string.pref_player_trickplay),
            message = "Trickplay switch must be present on TV",
        )
        assertFalse(
            names.contains(SettingsR.string.pref_player_gestures_seek_trickplay),
            message = "Gesture seek trickplay switch must be hidden on TV",
        )
    }

    @Test
    fun `Phone variant shows both trickplay switches`() = runTest {
        val phoneViewModel = SettingsViewModel(context, appPreferences, AppVariant.PHONE)
        phoneViewModel.loadPreferences(listOf(SettingsR.string.settings_category_player))

        val trickplayGroup =
            phoneViewModel.state.value.preferenceGroups.first {
                it.nameStringResource == SettingsR.string.trickplay
            }
        val names = trickplayGroup.preferences.map { it.nameStringResource }

        assertTrue(names.contains(SettingsR.string.pref_player_trickplay))
        assertTrue(names.contains(SettingsR.string.pref_player_gestures_seek_trickplay))
    }

    @Test
    fun `Phone variant shows gestures, trickplay, pip, and background playback in player settings`() =
        runTest {
            val phoneViewModel = SettingsViewModel(context, appPreferences, AppVariant.PHONE)
            phoneViewModel.loadPreferences(listOf(SettingsR.string.settings_category_player))

            val groups = phoneViewModel.state.value.preferenceGroups
            val groupTitles = groups.mapNotNull { it.nameStringResource }

            assertTrue(
                groupTitles.contains(SettingsR.string.gestures),
                message = "Gestures group must be present on Phone",
            )
            assertTrue(
                groupTitles.contains(SettingsR.string.trickplay),
                message = "Trickplay group must be present on Phone",
            )
            assertTrue(
                groupTitles.contains(SettingsR.string.picture_in_picture),
                message = "PiP group must be present on Phone",
            )
            assertTrue(
                groupTitles.contains(SettingsR.string.pref_player_background_group),
                message = "Background audio group must be present on Phone",
            )
        }

    @Test
    fun `TV variant hides downloads category and offline mode at top level`() = runTest {
        val tvViewModel = SettingsViewModel(context, appPreferences, AppVariant.TV)
        tvViewModel.loadPreferences(emptyList())

        val preferences = tvViewModel.state.value.preferenceGroups.flatMap { it.preferences }
        val names = preferences.map { it.nameStringResource }

        assertFalse(
            names.contains(SettingsR.string.offline_mode),
            message = "Offline mode switch must be hidden on TV",
        )
        assertFalse(
            names.contains(SettingsR.string.title_download),
            message = "Downloads category must be hidden on TV",
        )
    }

    @Test
    fun `Phone variant shows downloads category and offline mode at top level`() = runTest {
        val phoneViewModel = SettingsViewModel(context, appPreferences, AppVariant.PHONE)
        phoneViewModel.loadPreferences(emptyList())

        val preferences = phoneViewModel.state.value.preferenceGroups.flatMap { it.preferences }
        val names = preferences.map { it.nameStringResource }

        assertTrue(
            names.contains(SettingsR.string.offline_mode),
            message = "Offline mode switch must be present on Phone",
        )
        assertTrue(
            names.contains(SettingsR.string.title_download),
            message = "Downloads category must be present on Phone",
        )
    }

    /** CE (#271): the old global switches and type lists, migrated into the per-type actions. */
    private val removedSegmentSettings =
        listOf(
            SettingsR.string.pref_player_media_segments_skip_button,
            SettingsR.string.pref_player_media_segments_skip_button_type,
            SettingsR.string.pref_player_media_segments_auto_skip,
            SettingsR.string.pref_player_media_segments_auto_skip_type,
        )

    private val perTypeSegmentActions =
        listOf(
            SettingsR.string.pref_player_media_segments_action_intro,
            SettingsR.string.pref_player_media_segments_action_outro,
            SettingsR.string.pref_player_media_segments_action_recap,
            SettingsR.string.pref_player_media_segments_action_preview,
            SettingsR.string.pref_player_media_segments_action_commercial,
        )

    private suspend fun mediaSegmentSettings(variant: AppVariant): List<Int> {
        val viewModel = SettingsViewModel(context, appPreferences, variant)
        viewModel.loadPreferences(listOf(SettingsR.string.settings_category_player))
        return viewModel.state.value.preferenceGroups
            .first { it.nameStringResource == SettingsR.string.media_segments }
            .preferences
            .map { it.nameStringResource }
    }

    @Test
    fun `TV variant shows one action per segment type and the shared settings`() = runTest {
        val names = mediaSegmentSettings(AppVariant.TV)

        removedSegmentSettings.forEach { assertFalse(names.contains(it)) }
        perTypeSegmentActions.forEach { assertTrue(names.contains(it)) }
        // Shared with phone; TV shows the duration read-only.
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_skip_button_duration))
        assertTrue(
            names.contains(SettingsR.string.pref_player_media_segments_next_episode_threshold)
        )
        // Picture-in-picture is phone-only.
        assertFalse(names.contains(SettingsR.string.pref_player_media_segments_auto_skip_mode))
    }

    @Test
    fun `Phone variant shows one action per segment type and the shared settings`() = runTest {
        val names = mediaSegmentSettings(AppVariant.PHONE)

        removedSegmentSettings.forEach { assertFalse(names.contains(it)) }
        perTypeSegmentActions.forEach { assertTrue(names.contains(it)) }
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_skip_button_duration))
        assertTrue(names.contains(SettingsR.string.pref_player_media_segments_auto_skip_mode))
        assertTrue(
            names.contains(SettingsR.string.pref_player_media_segments_next_episode_threshold)
        )
    }

    @Test
    fun `TV variant shows match refresh rate switch and Phone variant hides it`() = runTest {
        val tvViewModel = SettingsViewModel(context, appPreferences, AppVariant.TV)
        tvViewModel.loadPreferences(listOf(SettingsR.string.settings_category_player))
        val tvPrefs =
            tvViewModel.state.value.preferenceGroups
                .flatMap { it.preferences }
                .map { it.nameStringResource }
        assertTrue(
            tvPrefs.contains(SettingsR.string.pref_player_tv_match_refresh_rate),
            message = "Match refresh rate switch must be present on TV",
        )

        val phoneViewModel = SettingsViewModel(context, appPreferences, AppVariant.PHONE)
        phoneViewModel.loadPreferences(listOf(SettingsR.string.settings_category_player))
        val phonePrefs =
            phoneViewModel.state.value.preferenceGroups
                .flatMap { it.preferences }
                .map { it.nameStringResource }
        assertFalse(
            phonePrefs.contains(SettingsR.string.pref_player_tv_match_refresh_rate),
            message = "Match refresh rate switch must be hidden on Phone",
        )
    }

    @Test
    fun `TV variant shows the Watch Next switch and Phone variant hides it`() = runTest {
        val path = listOf(SettingsR.string.settings_category_interface)
        val tvViewModel = SettingsViewModel(context, appPreferences, AppVariant.TV)
        tvViewModel.loadPreferences(path)
        val tvPrefs =
            tvViewModel.state.value.preferenceGroups
                .flatMap { it.preferences }
                .map { it.nameStringResource }
        assertTrue(
            tvPrefs.contains(SettingsR.string.pref_tv_watch_next),
            message = "Watch Next switch must be present on TV",
        )

        val phoneViewModel = SettingsViewModel(context, appPreferences, AppVariant.PHONE)
        phoneViewModel.loadPreferences(path)
        val phonePrefs =
            phoneViewModel.state.value.preferenceGroups
                .flatMap { it.preferences }
                .map { it.nameStringResource }
        assertFalse(
            phonePrefs.contains(SettingsR.string.pref_tv_watch_next),
            message = "Watch Next switch must be hidden on Phone",
        )
    }

    @Test
    fun `the extra info switch is gone from both variants`() = runTest {
        // #510: the media info sheet on the item page replaced the switch.
        val path =
            listOf(
                SettingsR.string.settings_category_interface,
                SettingsR.string.ce_settings_detail_pages,
            )
        for (variant in AppVariant.entries) {
            val viewModel = SettingsViewModel(context, appPreferences, variant)
            viewModel.loadPreferences(path)
            val names =
                viewModel.state.value.preferenceGroups
                    .flatMap { it.preferences }
                    .map { it.nameStringResource }
            // The page itself loads: its other switches are there.
            assertTrue(names.contains(SettingsR.string.cast_and_crew))
            assertFalse(
                names.contains(SettingsR.string.extra_info),
                message = "Extra info switch must be gone on $variant",
            )
        }
    }

    @Test
    fun `TV variant shows the theme music switch, off by default, and Phone variant hides it`() =
        runTest {
            val path = listOf(SettingsR.string.settings_category_interface)
            val tvViewModel = SettingsViewModel(context, appPreferences, AppVariant.TV)
            tvViewModel.loadPreferences(path)
            val tvPrefs =
                tvViewModel.state.value.preferenceGroups
                    .flatMap { it.preferences }
                    .map { it.nameStringResource }
            assertTrue(
                tvPrefs.contains(SettingsR.string.pref_tv_theme_music),
                message = "Theme music switch must be present on TV",
            )
            assertFalse(
                appPreferences.getValue(appPreferences.tvThemeMusic),
                message = "Theme music must be off by default",
            )

            val phoneViewModel = SettingsViewModel(context, appPreferences, AppVariant.PHONE)
            phoneViewModel.loadPreferences(path)
            val phonePrefs =
                phoneViewModel.state.value.preferenceGroups
                    .flatMap { it.preferences }
                    .map { it.nameStringResource }
            assertFalse(
                phonePrefs.contains(SettingsR.string.pref_tv_theme_music),
                message = "Theme music switch must be hidden on Phone",
            )
        }

    @Test
    fun `mpv options show the Advanced file editors on Phone and no Advanced group on TV`() =
        runTest {
            // #421: the TV has no file editor, so its group heading stood alone.
            val path =
                listOf(SettingsR.string.settings_category_player, SettingsR.string.mpv_options)
            val tvViewModel = SettingsViewModel(context, appPreferences, AppVariant.TV)
            tvViewModel.loadPreferences(path)
            val tvGroups = tvViewModel.state.value.preferenceGroups
            assertTrue(tvGroups.isNotEmpty(), message = "mpv options must have settings on TV")
            assertFalse(
                tvGroups.any { it.nameStringResource == SettingsR.string.advanced },
                message = "Advanced group must be hidden on TV",
            )
            assertTrue(
                tvGroups.flatMap { it.preferences }.none { it is PreferenceFileEdit },
                message = "No file editor on TV",
            )

            val phoneViewModel = SettingsViewModel(context, appPreferences, AppVariant.PHONE)
            phoneViewModel.loadPreferences(path)
            val advanced =
                phoneViewModel.state.value.preferenceGroups.single {
                    it.nameStringResource == SettingsR.string.advanced
                }
            assertEquals(
                listOf("mpv/mpv.conf", "mpv/input.conf"),
                advanced.preferences.filterIsInstance<PreferenceFileEdit>().map { it.filePath },
            )
        }

    @Test
    fun `dump phone and tv settings trees`() = runTest {
        fun dumpTree(variant: AppVariant): String {
            val vm = SettingsViewModel(context, appPreferences, variant)
            val sb = StringBuilder()

            fun printGroups(path: List<Int>, indent: String) {
                vm.loadPreferences(path)
                val groups = vm.state.value.preferenceGroups
                for (group in groups) {
                    val groupTitle =
                        group.nameStringResource?.let { context.resources.getResourceEntryName(it) }
                            ?: "(unnamed group)"
                    sb.appendLine("${indent}Group: $groupTitle")
                    for (pref in group.preferences) {
                        val prefName =
                            context.resources.getResourceEntryName(pref.nameStringResource)
                        sb.appendLine("${indent}  - $prefName (${pref::class.simpleName})")
                        if (
                            pref is PreferenceCategory && pref.nestedPreferenceGroups.isNotEmpty()
                        ) {
                            printGroups(path + pref.nameStringResource, "$indent    ")
                        }
                    }
                }
            }

            printGroups(emptyList(), "")
            return sb.toString()
        }

        val phoneTree = dumpTree(AppVariant.PHONE)
        val tvTree = dumpTree(AppVariant.TV)
        println("=== PHONE SETTINGS TREE ===\n$phoneTree")
        println("=== TV SETTINGS TREE ===\n$tvTree")
    }
}
