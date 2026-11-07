package dev.jdtech.jellyfin

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Rational
import android.util.TypedValue
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.databinding.ActivityPlayerBinding
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import dev.jdtech.jellyfin.film.presentation.season.SeasonViewModel
import dev.jdtech.jellyfin.player.local.PlaybackSessionStore
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.player.ChapterListDialogFragment
import dev.jdtech.jellyfin.presentation.player.PlayerEpisodeSheet
import dev.jdtech.jellyfin.presentation.player.QualitySelectionDialogFragment
import dev.jdtech.jellyfin.presentation.player.SpeedSelectionDialogFragment
import dev.jdtech.jellyfin.presentation.player.StillWatchingDialogFragment
import dev.jdtech.jellyfin.presentation.player.TrackSelectionDialogFragment
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.PlayerGestureHelper
import dev.jdtech.jellyfin.utils.PreviewScrubListener
import dev.jdtech.jellyfin.utils.applySubtitleStyle
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

var isControlsLocked: Boolean = false

/** Below this many dp a window is compact width (Material window size classes). */
private const val COMPACT_WIDTH_DP = 600

@AndroidEntryPoint
class PlayerActivity : BasePlayerActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    lateinit var binding: ActivityPlayerBinding
    private var playerGestureHelper: PlayerGestureHelper? = null
    @Inject override lateinit var sessionStore: PlaybackSessionStore
    override val viewModel: PlayerViewModel by lazy {
        ViewModelProvider(
            sessionStore.viewModelStore,
            defaultViewModelProviderFactory,
            defaultViewModelCreationExtras,
        )[PlayerViewModel::class.java]
    }
    private var previewScrubListener: PreviewScrubListener? = null
    private var wasZoom: Boolean = false

    private lateinit var skipSegmentButton: Button

    private var stillWatchingDialog: StillWatchingDialogFragment? = null

    /** CE (R8): locks or unlocks the controls as their buttons do. Set in [onCreate]. */
    private var toggleLock: () -> Unit = {}

    /** CE (R8): wheel movement not yet turned into a seek; touchpads scroll in fractions. */
    private var pendingScroll = 0f

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    /** CE (R8): the TV remote's seek steps (10 s back, 30 s forward unless changed on a TV). */
    private val keySeekBackMs by lazy {
        appPreferences.getValue(appPreferences.playerTvSeekBackInc).toLongOrNull() ?: 10_000L
    }
    private val keySeekForwardMs by lazy {
        appPreferences.getValue(appPreferences.playerTvSeekForwardInc).toLongOrNull() ?: 30_000L
    }

    private val hideVolumeIndicator = Runnable {
        binding.gestureVolumeLayout.visibility = View.GONE
    }

    /**
     * CE (R6, T7): the orientation the player asks for while the controls are not locked. Phones
     * keep the forced landscape; tablets and unfolded foldables play in whatever orientation they
     * are held. Set by [updatePlayerOrientation].
     */
    private var playerOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

    /**
     * Picks [playerOrientation] from the window the player gets when it fills the screen: forced
     * landscape when that window is compact width (< 600 dp) held in portrait, i.e. its shorter
     * side is under 600 dp; otherwise unspecified.
     *
     * The shorter side and not the current width, because a phone held in landscape is wider than
     * 600 dp and must still lock. The maximum window and not the current one, because a requested
     * orientation only applies to a full-screen window: a tablet that starts the player in
     * split-screen, or returns from picture-in-picture, must not lock itself to landscape. Folding
     * or unfolding changes the maximum window, hence the call from [onConfigurationChanged].
     */
    private fun updatePlayerOrientation() {
        val bounds = WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(this).bounds
        val shorterSideDp = minOf(bounds.width(), bounds.height()) / resources.displayMetrics.density
        playerOrientation =
            if (shorterSideDp < COMPACT_WIDTH_DP) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (!isControlsLocked && requestedOrientation != playerOrientation) {
            requestedOrientation = playerOrientation
        }
    }

    /**
     * CE (R9, #51): the hinge, in window coordinates, while the window is in tabletop posture
     * (half-opened, hinge horizontal); null otherwise. Set from [WindowInfoTracker].
     */
    private var tabletopFold: Rect? = null

    /**
     * CE (R9, #51): in tabletop posture the video (with its subtitles and the title bar) sits above
     * the hinge, and the transport row, the time bar, the volume and brightness panels, the
     * unlock button and the episode sheet (R10) sit below it. Done with margins on views that
     * normally fill the player, so leaving the posture puts every margin back to 0 and the layout
     * is the usual one. Only views move: the player and its surface stay attached, so playback
     * carries on.
     *
     * Ignored in picture-in-picture, and when the hinge doesn't cross the player (a window on one
     * half of the screen).
     */
    private fun applyTabletopLayout() {
        if (!::binding.isInitialized) return
        val playerView = binding.playerView
        val fold = tabletopFold
        var videoBottom = 0
        var controlsTop = 0
        if (fold != null && !isInPictureInPictureMode && playerView.isLaidOut) {
            val location = IntArray(2)
            playerView.getLocationInWindow(location)
            val foldTop = fold.top - location[1]
            val foldBottom = fold.bottom - location[1]
            if (foldTop > 0 && foldBottom < playerView.height) {
                videoBottom = playerView.height - foldTop
                controlsTop = foldBottom
            }
        }
        val above = listOf<View?>(playerView.findViewById(R.id.video_area), playerView.subtitleView)
        for (view in above) view?.setVerticalMargins(top = 0, bottom = videoBottom)
        val below =
            listOf<View?>(
                playerView.findViewById(R.id.lower_controls),
                playerView.findViewById(R.id.locked_player_view),
                binding.gesturePanels,
                binding.episodeSheet,
            )
        for (view in below) view?.setVerticalMargins(top = controlsTop, bottom = 0)
    }

    private fun View.setVerticalMargins(top: Int, bottom: Int) {
        val params = layoutParams as ViewGroup.MarginLayoutParams
        if (params.topMargin == top && params.bottomMargin == bottom) return
        params.topMargin = top
        params.bottomMargin = bottom
        layoutParams = params
    }

    /** CE (R9): whether the window is in R7's `w840dp-h480dp` bucket, the expanded player. */
    private fun isExpandedWindow(): Boolean {
        val config = resources.configuration
        return config.screenWidthDp >= 840 && config.screenHeightDp >= 480
    }

    /** CE (R9): whether the window was in R7's `w840dp-h480dp` bucket at the last resize. */
    private var expandedPlayerDimens: Boolean? = null

    /**
     * CE (R9): applies the `player_*` dimens (R7) to the views that use them. The activity handles
     * size changes itself, so the layout is inflated once; without this a tablet rotated from
     * portrait to landscape during playback would keep the phone sizes. The chapter thumbnails are
     * left out: the chapter list inflates them each time it opens.
     */
    private fun applyPlayerDimens() {
        val expanded = isExpandedWindow()
        if (expanded == expandedPlayerDimens) return
        expandedPlayerDimens = expanded

        fun px(id: Int) = resources.getDimensionPixelSize(id)
        fun View.size(width: Int = layoutParams.width, height: Int = layoutParams.height) {
            if (layoutParams.width == width && layoutParams.height == height) return
            layoutParams =
                layoutParams.also {
                    it.width = width
                    it.height = height
                }
        }

        val playerView = binding.playerView
        val spacing = px(R.dimen.player_transport_spacing)
        val transport = playerView.findViewById<ViewGroup>(R.id.transport_controls)
        for (i in 0 until transport.childCount) {
            (transport.getChildAt(i) as? Space)?.size(width = spacing)
        }
        playerView
            .findViewById<View>(R.id.image_preview)
            .size(px(R.dimen.player_image_preview_width), px(R.dimen.player_image_preview_height))
        binding.progressScrubberTrickplay.size(
            px(R.dimen.player_scrubber_trickplay_width),
            px(R.dimen.player_scrubber_trickplay_height),
        )
        val panelWidth = px(R.dimen.player_gesture_panel_width)
        binding.gestureVolumeLayout.size(width = panelWidth)
        binding.gestureBrightnessLayout.size(width = panelWidth)
        val barHeight = px(R.dimen.player_gesture_bar_height)
        binding.gestureVolumeProgressBar.size(height = barHeight)
        binding.gestureBrightnessProgressBar.size(height = barHeight)
        val timeSize = resources.getDimension(R.dimen.player_time_text_size)
        for (id in listOf(R.id.exo_position, R.id.exo_duration)) {
            playerView.findViewById<TextView>(id).setTextSize(TypedValue.COMPLEX_UNIT_PX, timeSize)
        }
        val ripple = px(R.dimen.player_seek_ripple_size)
        binding.imageFfwdAnimationRipple.size(ripple, ripple)
        binding.imageRewindAnimationRipple.size(ripple, ripple)
        binding.imagePlaybackAnimationRipple.size(ripple, ripple)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updatePlayerOrientation()
        if (::binding.isInitialized) {
            applyPlayerDimens()
            updateEpisodesButton()
        }
    }

    /**
     * CE (R10, design §9): the episode sheet. Its season comes from [SeasonViewModel], created the
     * first time the sheet opens; the sheet's content is set up then too.
     */
    private val seasonViewModel: SeasonViewModel by viewModels()
    private val episodeSheetVisible = MutableTransitionState(false)
    private var episodeSheetComposed = false
    private lateinit var episodesButton: ImageButton
    private lateinit var episodesSpace: Space

    /** The controls' hide timeout, paused (0) while the sheet is open. */
    private var controlsHideTimeoutMs = 0

    /** Back, and Escape (which presses Back), closes the sheet before it leaves the player. */
    private val episodeSheetBackCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = closeEpisodeSheet()
        }

    /**
     * The episodes button is offered at expanded width (R7's bucket, read from the window, never
     * the device type) while an episode plays, outside picture-in-picture. Leaving that closes
     * the sheet at once.
     */
    private fun updateEpisodesButton() {
        if (!::episodesButton.isInitialized) return
        val show =
            isExpandedWindow() &&
                !isInPictureInPictureMode &&
                viewModel.uiState.value.currentItem?.seasonId != null
        episodesButton.isVisible = show
        episodesSpace.isVisible = show
        if (!show) closeEpisodeSheet(animate = false)
    }

    private fun openEpisodeSheet() {
        val item = viewModel.uiState.value.currentItem ?: return
        val seasonId = item.seasonId ?: return
        if (isControlsLocked || isInPictureInPictureMode) return
        if (seasonViewModel.shouldReload(seasonId)) {
            seasonViewModel.loadSeason(seasonId = seasonId, seriesId = item.seriesId)
        }
        if (!episodeSheetComposed) {
            episodeSheetComposed = true
            binding.episodeSheet.setContent {
                FindroidTheme(darkTheme = true) {
                    val seasonState by seasonViewModel.state.collectAsStateWithLifecycle()
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val currentItem = uiState.currentItem
                    PlayerEpisodeSheet(
                        visibleState = episodeSheetVisible,
                        // Until the playing season has loaded, the sheet shows it loading rather
                        // than the season it showed last.
                        state =
                            if (seasonState.season?.id == currentItem?.seasonId) seasonState
                            else SeasonState(error = seasonState.error),
                        seasonNumber = currentItem?.parentIndexNumber,
                        currentEpisodeId = uiState.currentItemId,
                        onEpisodeClick = { episode ->
                            if (episode.id != uiState.currentItemId) {
                                viewModel.playEpisode(episode.id)
                            }
                            closeEpisodeSheet()
                        },
                        onDismiss = { closeEpisodeSheet() },
                        onClosed = { binding.episodeSheet.isVisible = false },
                    )
                }
            }
        }
        binding.episodeSheet.isVisible = true
        episodeSheetVisible.targetState = true
        episodeSheetBackCallback.isEnabled = true
        // The controls stay up while the sheet is open.
        binding.playerView.controllerShowTimeoutMs = 0
        binding.playerView.showController()
    }

    /** Closes the sheet with its fade, or at once when [animate] is false. */
    private fun closeEpisodeSheet(animate: Boolean = true) {
        if (!episodeSheetBackCallback.isEnabled) return
        episodeSheetBackCallback.isEnabled = false
        episodeSheetVisible.targetState = false
        if (!animate) binding.episodeSheet.isVisible = false
        // Restarts the hide timer when the controls are showing.
        binding.playerView.controllerShowTimeoutMs = controlsHideTimeoutMs
    }

    private val isPipSupported by lazy {
        // Check if device has PiP feature
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return@lazy false
        }

        // Check if PiP is enabled for the app
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager?
        appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    /**
     * CE (#271): the skip button shows in its first seconds in a segment (the ViewModel's
     * [PlayerViewModel.UiState.skipButtonInWindow]) and, for the rest of the segment, whenever the
     * controls are visible. Never in picture-in-picture.
     */
    private fun updateSkipSegmentButton(uiState: PlayerViewModel.UiState) {
        val segment = uiState.currentSegment
        skipSegmentButton.isVisible =
            segment != null &&
                !isInPictureInPictureMode &&
                (uiState.skipButtonInWindow || binding.playerView.isControllerFullyVisible)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without an item the media notification brought us back to a session that is still
        // playing in the background. If it is gone by now there is nothing to show.
        val itemIdExtra = intent.extras?.getString("itemId")
        if (itemIdExtra == null && !sessionStore.hasSession) {
            finish()
            return
        }
        val itemId = itemIdExtra?.let { UUID.fromString(it) }
        val itemKind = intent.extras?.getString("itemKind")
        val startFromBeginning = intent.extras?.getBoolean("startFromBeginning") ?: false
        val mediaSourceId = intent.extras?.getString("mediaSourceId")

        isControlsLocked = false
        updatePlayerOrientation()

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyPlayerDimens()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.attachWindow(window)

        binding.playerView.player = viewModel.player
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (visibility == View.GONE) {
                    hideSystemUI()
                }
                // CE (R8): a mouse pointer hides with the controls and comes back with them.
                binding.playerView.pointerIcon =
                    if (visibility == View.GONE) {
                        PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL)
                    } else {
                        null
                    }
                updateSkipSegmentButton(viewModel.uiState.value)
            }
        )

        val playerControls = binding.playerView.findViewById<View>(R.id.player_controls)
        val lockedControls = binding.playerView.findViewById<View>(R.id.locked_player_view)

        configureInsets(playerControls)
        configureInsets(lockedControls)

        // CE (R9): the tabletop margins depend on the player's height, which changes without a
        // new posture when the window is resized or rotated.
        binding.playerView.addOnLayoutChangeListener { view, _, top, _, bottom, _, oldT, _, oldB ->
            if (bottom - top != oldB - oldT) view.post { applyTabletopLayout() }
        }

        if (appPreferences.getValue(appPreferences.playerGestures)) {
            playerGestureHelper =
                PlayerGestureHelper(
                    appPreferences,
                    this,
                    binding.playerView,
                    getSystemService(AUDIO_SERVICE) as AudioManager,
                )
        }

        binding.playerView.findViewById<View>(R.id.back_button).setOnClickListener {
            finishPlayback()
        }

        // Apply user-configured subtitle appearance to the embedded SubtitleView. The view is
        // created by the inflated exo_player_view.xml and exposed via the @id/exo_subtitles id.
        binding.playerView.subtitleView?.applySubtitleStyle(appPreferences)

        val videoNameTextView = binding.playerView.findViewById<TextView>(R.id.video_name)

        val audioButton = binding.playerView.findViewById<ImageButton>(R.id.btn_audio_track)
        val subtitleButton = binding.playerView.findViewById<ImageButton>(R.id.btn_subtitle)
        val speedButton = binding.playerView.findViewById<ImageButton>(R.id.btn_speed)
        val qualityButton = binding.playerView.findViewById<ImageButton>(R.id.btn_quality)
        val qualitySpace = binding.playerView.findViewById<Space>(R.id.space_quality)
        skipSegmentButton = binding.playerView.findViewById(R.id.btn_skip_segment)
        val pipButton = binding.playerView.findViewById<ImageButton>(R.id.btn_pip)
        val lockButton = binding.playerView.findViewById<ImageButton>(R.id.btn_lockview)
        val unlockButton = binding.playerView.findViewById<ImageButton>(R.id.btn_unlock)
        val chaptersButton = binding.playerView.findViewById<ImageButton>(R.id.btn_chapters)
        val chaptersSpace = binding.playerView.findViewById<Space>(R.id.space_chapters)
        episodesButton = binding.playerView.findViewById(R.id.btn_episodes)
        episodesSpace = binding.playerView.findViewById(R.id.space_episodes)
        controlsHideTimeoutMs = binding.playerView.controllerShowTimeoutMs
        onBackPressedDispatcher.addCallback(this, episodeSheetBackCallback)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // CE (R9, #51): tabletop posture on a foldable.
                launch {
                    WindowInfoTracker.getOrCreate(this@PlayerActivity)
                        .windowLayoutInfo(this@PlayerActivity)
                        .collect { layoutInfo ->
                            tabletopFold =
                                layoutInfo.displayFeatures
                                    .filterIsInstance<FoldingFeature>()
                                    .firstOrNull {
                                        it.state == FoldingFeature.State.HALF_OPENED &&
                                            it.orientation == FoldingFeature.Orientation.HORIZONTAL
                                    }
                                    ?.bounds
                            applyTabletopLayout()
                        }
                }

                launch {
                    viewModel.uiState.collect { uiState ->
                        Timber.d("$uiState")
                        uiState.apply {
                            // Title
                            videoNameTextView.text = currentItemTitle

                            // Media segment (#271)
                            currentSegment?.let { segment ->
                                skipSegmentButton.text = getString(currentSkipButtonStringRes)
                                skipSegmentButton.setOnClickListener {
                                    viewModel.skipSegment(segment)
                                    skipSegmentButton.isVisible = false
                                }
                            }
                            updateSkipSegmentButton(this)

                            // Trickplay
                            previewScrubListener?.let { it.currentTrickplay = currentTrickplay }

                            playerGestureHelper?.let { it.currentTrickplay = currentTrickplay }

                            // Chapters
                            val playerControlView =
                                findViewById<PlayerControlView>(R.id.exo_controller)
                            val hasChapters = currentChapters.isNotEmpty()
                            val showChapterMarkers =
                                hasChapters &&
                                    appPreferences.getValue(appPreferences.playerChapterMarkers)
                            if (showChapterMarkers) {
                                val numOfChapters = currentChapters.size
                                playerControlView.setExtraAdGroupMarkers(
                                    LongArray(numOfChapters) { index ->
                                        currentChapters[index].startPosition
                                    },
                                    BooleanArray(numOfChapters) { false },
                                )
                            } else {
                                playerControlView.setExtraAdGroupMarkers(null, null)
                            }
                            // Hide the spacer too so the controls bar does not end in dead space.
                            chaptersButton.isVisible = hasChapters
                            chaptersSpace.isVisible = hasChapters

                            // Episodes (R10)
                            updateEpisodesButton()

                            // File Loaded
                            if (fileLoaded) {
                                audioButton.isEnabled = true
                                audioButton.imageAlpha = 255
                                lockButton.isEnabled = true
                                lockButton.imageAlpha = 255
                                subtitleButton.isEnabled = true
                                subtitleButton.imageAlpha = 255
                                speedButton.isEnabled = true
                                speedButton.imageAlpha = 255
                                qualityButton.isEnabled = true
                                qualityButton.imageAlpha = 255
                                pipButton.isEnabled = true
                                pipButton.imageAlpha = 255
                            }

                            // Still watching prompt — show / hide a dialog driven by the
                            // ViewModel. The ViewModel is the source of truth for the timeout;
                            // the dialog is purely a render of the state.
                            if (showStillWatching && stillWatchingDialog == null) {
                                val dialog = StillWatchingDialogFragment()
                                stillWatchingDialog = dialog
                                dialog.show(supportFragmentManager, "stillwatchingdialog")
                            } else if (!showStillWatching && stillWatchingDialog != null) {
                                stillWatchingDialog?.dismissAllowingStateLoss()
                                stillWatchingDialog = null
                            }
                        }
                    }
                }

                launch {
                    viewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is PlayerEvents.NavigateBack -> finishPlayback()
                            is PlayerEvents.IsPlayingChanged -> {
                                if (event.isPlaying) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                } else {
                                    window.clearFlags(
                                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                    )
                                }

                                if (appPreferences.getValue(appPreferences.playerPipGesture)) {
                                    try {
                                        setPictureInPictureParams(pipParams(event.isPlaying))
                                    } catch (_: IllegalArgumentException) {}
                                }
                            }
                            is PlayerEvents.PlayerError -> {
                                // Error is already logged in PlayerViewModel
                            }
                        }
                    }
                }

                launch {
                    while (true) {
                        viewModel.updatePlaybackProgress()
                        delay(5000L)
                    }
                }

                // Keeps the position used for the stop report on next/previous within a second.
                launch {
                    while (true) {
                        viewModel.refreshPlayingSnapshot()
                        delay(1000L)
                    }
                }

                if (viewModel.shouldPollSegments()) {
                    launch {
                        while (true) {
                            viewModel.updateCurrentSegment()
                            delay(1000L)
                        }
                    }
                }
            }
        }

        audioButton.isEnabled = false
        audioButton.imageAlpha = 75

        lockButton.isEnabled = false
        lockButton.imageAlpha = 75

        subtitleButton.isEnabled = false
        subtitleButton.imageAlpha = 75

        speedButton.isEnabled = false
        speedButton.imageAlpha = 75

        // A bitrate cap only makes sense against the server; offline playback
        // always plays the local file as-is.
        if (appPreferences.getValue(appPreferences.offlineMode)) {
            qualityButton.isVisible = false
            qualitySpace.isVisible = false
        } else {
            qualityButton.isEnabled = false
            qualityButton.imageAlpha = 75
        }

        if (isPipSupported) {
            pipButton.isEnabled = false
            pipButton.imageAlpha = 75
        } else {
            val pipSpace = binding.playerView.findViewById<Space>(R.id.space_pip)
            pipButton.isVisible = false
            pipSpace.isVisible = false
        }

        audioButton.setOnClickListener {
            TrackSelectionDialogFragment.newInstance(C.TRACK_TYPE_AUDIO)
                .show(supportFragmentManager, "trackselectiondialog")
        }

        val exoPlayerControlView = findViewById<FrameLayout>(R.id.player_controls)
        val lockedLayout = findViewById<FrameLayout>(R.id.locked_player_view)

        lockButton.setOnClickListener {
            closeEpisodeSheet(animate = false)
            exoPlayerControlView.visibility = View.GONE
            lockedLayout.visibility = View.VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            isControlsLocked = true
        }

        unlockButton.setOnClickListener {
            exoPlayerControlView.visibility = View.VISIBLE
            lockedLayout.visibility = View.GONE
            requestedOrientation = playerOrientation
            isControlsLocked = false
        }

        // CE (R8): the L key presses whichever of the two is showing.
        toggleLock = {
            if (isControlsLocked) unlockButton.performClick()
            else if (lockButton.isEnabled) lockButton.performClick()
        }

        subtitleButton.setOnClickListener {
            TrackSelectionDialogFragment.newInstance(C.TRACK_TYPE_TEXT)
                .show(supportFragmentManager, "trackselectiondialog")
        }

        speedButton.setOnClickListener {
            SpeedSelectionDialogFragment()
                .show(supportFragmentManager, "speedselectiondialog")
        }

        qualityButton.setOnClickListener {
            QualitySelectionDialogFragment()
                .show(supportFragmentManager, "qualityselectiondialog")
        }

        chaptersButton.setOnClickListener {
            ChapterListDialogFragment()
                .show(supportFragmentManager, "chapterlistdialog")
        }

        pipButton.setOnClickListener { pictureInPicture() }

        episodesButton.setOnClickListener { openEpisodeSheet() }

        // Set marker color
        val timeBar = binding.playerView.findViewById<DefaultTimeBar>(R.id.exo_progress)
        timeBar.setAdMarkerColor(Color.WHITE)

        if (appPreferences.getValue(appPreferences.playerTrickplay)) {
            val imagePreview = binding.playerView.findViewById<ImageView>(R.id.image_preview)
            previewScrubListener = PreviewScrubListener(imagePreview, timeBar, viewModel.player)

            timeBar.addListener(previewScrubListener!!)
        }

        if (itemId != null) {
            viewModel.initializePlayer(
                itemId = itemId,
                itemKind = itemKind ?: "",
                startFromBeginning = startFromBeginning,
                mediaSourceId = mediaSourceId,
            )
        }
        hideSystemUI()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::binding.isInitialized && onPlayerKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /**
     * CE (R8, T15): the player's keyboard map. It runs before the view hierarchy, so media3's
     * `PlayerView` only gets the keys this leaves alone. Returns true when the key was handled.
     *
     * | Key | Action |
     * |---|---|
     * | Space, media play/pause | play or pause |
     * | Left / Right | seek back / forward by the TV steps |
     * | Up / Down | volume one step up / down, in the swipe's volume indicator |
     * | M | mute or unmute |
     * | L | lock or unlock the controls |
     * | Escape | close the episode sheet (R10), else leave the player, as Back does |
     * | Media next / previous | next / previous item in the queue |
     *
     * Any key but the media keys and Escape also shows the controls, or keeps them up. While the
     * controls are showing and one of them has focus (after Tab), the arrows and Space go to that
     * control. With the controls locked only L, Escape and the media keys do anything.
     */
    private fun onPlayerKey(event: KeyEvent): Boolean {
        // A picture-in-picture window takes no keys; leave everything to the system.
        if (isInPictureInPictureMode) return false
        val keyCode = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN

        mediaKeyAction(keyCode)?.let { action ->
            if (down && event.repeatCount == 0) {
                viewModel.markUserInteraction()
                action()
            }
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_ESCAPE && event.hasNoModifiers()) {
            // On the up event, as Back is. Back closes the episode sheet first, when it is open.
            if (!down && !event.isCanceled) onBackPressedDispatcher.onBackPressed()
            return true
        }

        if (!down || KeyEvent.isModifierKey(keyCode) || isSystemKey(keyCode)) return false

        val playerView = binding.playerView
        // Judged before the controls are shown: showing them may move focus into them.
        val controlFocused =
            playerView.isControllerFullyVisible &&
                currentFocus.let { it != null && it !== playerView && it.isInside(playerView) }
        viewModel.markUserInteraction()
        // Shows the controls, or restarts their hide timer when they are up.
        if (playerView.useController) playerView.showController()

        if (!event.hasNoModifiers()) return false
        if (keyCode == KeyEvent.KEYCODE_L) {
            if (event.repeatCount == 0) toggleLock()
            return true
        }
        if (isControlsLocked) return false

        val handled =
            when (keyCode) {
                KeyEvent.KEYCODE_SPACE -> {
                    if (!controlFocused && event.repeatCount == 0) togglePlayPause()
                    !controlFocused
                }
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!controlFocused) {
                        seekByStep(forward = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    }
                    !controlFocused
                }
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!controlFocused) {
                        changeVolume(
                            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) AudioManager.ADJUST_RAISE
                            else AudioManager.ADJUST_LOWER
                        )
                    }
                    !controlFocused
                }
                KeyEvent.KEYCODE_M -> {
                    if (event.repeatCount == 0) changeVolume(AudioManager.ADJUST_TOGGLE_MUTE)
                    true
                }
                else -> false
            }
        // Showing the controls puts focus on play/pause, which would turn the next arrow into
        // focus navigation. A shortcut keeps focus where it was; Tab still moves into the controls.
        if (handled && !controlFocused && currentFocus?.isInside(playerView) == true) {
            // The player view itself, not its first focusable child.
            val focusability = playerView.descendantFocusability
            playerView.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            playerView.requestFocus()
            playerView.descendantFocusability = focusability
        }
        return handled
    }

    /** The media keys, which act without showing the controls. */
    private fun mediaKeyAction(keyCode: Int): (() -> Unit)? {
        val player = viewModel.player
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { -> togglePlayPause() }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { -> player.play() }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { -> player.pause() }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { -> seekByStep(forward = true) }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { -> seekByStep(forward = false) }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { ->
                if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) player.seekToNext()
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { ->
                if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) {
                    player.seekToPrevious()
                }
            }
            else -> null
        }
    }

    /** Keys the system acts on (volume, Back, Home, power...) are left to it. */
    private fun isSystemKey(keyCode: Int): Boolean =
        KeyEvent.isMediaSessionKey(keyCode) ||
            keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE ||
            keyCode == KeyEvent.KEYCODE_HOME ||
            keyCode == KeyEvent.KEYCODE_POWER ||
            keyCode == KeyEvent.KEYCODE_APP_SWITCH

    private fun View.isInside(ancestor: View): Boolean {
        var current = parent
        while (current is View) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }

    private fun togglePlayPause() {
        viewModel.player.playWhenReady = !viewModel.player.playWhenReady
    }

    private fun seekByStep(forward: Boolean) {
        val player = viewModel.player
        val duration = player.duration
        val target = player.currentPosition + if (forward) keySeekForwardMs else -keySeekBackMs
        player.seekTo(
            if (duration != C.TIME_UNSET && duration > 0) target.coerceIn(0L, duration)
            else target.coerceAtLeast(0L)
        )
    }

    /** One step of [AudioManager.adjustStreamVolume], shown in the swipe's volume indicator. */
    @SuppressLint("SetTextI18n")
    private fun changeVolume(direction: Int) {
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        } catch (e: SecurityException) {
            // Do Not Disturb can refuse a mute change.
            Timber.w(e)
            return
        }
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volume =
            if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) 0
            else audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val percent = if (max > 0) volume * 100 / max else 0
        binding.gestureVolumeProgressBar.max = max * 100
        binding.gestureVolumeProgressBar.progress = volume * 100
        binding.gestureVolumeText.text = "$percent%"
        binding.gestureVolumeImage.setImageLevel(percent)
        binding.gestureVolumeLayout.visibility = View.VISIBLE
        binding.gestureVolumeLayout.removeCallbacks(hideVolumeIndicator)
        binding.gestureVolumeLayout.postDelayed(hideVolumeIndicator, 1000)
    }

    /**
     * CE (R8): the mouse in the player. Moving the pointer shows the controls and restarts their
     * hide timer; the wheel seeks one Left/Right step per notch (up or right is forward). Taken at
     * the activity, before the controls' buttons consume the hover events.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (
            ::binding.isInitialized &&
                !isInPictureInPictureMode &&
                event.isFromSource(InputDevice.SOURCE_MOUSE)
        ) {
            val playerView = binding.playerView
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_MOVE ->
                    if (playerView.useController) playerView.showController()
                MotionEvent.ACTION_SCROLL ->
                    if (!isControlsLocked) {
                        pendingScroll +=
                            event.getAxisValue(MotionEvent.AXIS_VSCROLL) +
                                event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                        while (pendingScroll >= 1f) {
                            pendingScroll -= 1f
                            seekByStep(forward = true)
                        }
                        while (pendingScroll <= -1f) {
                            pendingScroll += 1f
                            seekByStep(forward = false)
                        }
                        viewModel.markUserInteraction()
                        if (playerView.useController) playerView.showController()
                        return true
                    }
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind")
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning")
        val mediaSourceId = intent.extras!!.getString("mediaSourceId")

        viewModel.initializePlayer(
            itemId = itemId,
            itemKind = itemKind ?: "",
            startFromBeginning = startFromBeginning,
            mediaSourceId = mediaSourceId,
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                appPreferences.getValue(appPreferences.playerPipGesture) &&
                viewModel.player.isPlaying &&
                !isControlsLocked
        ) {
            pictureInPicture()
        }
    }

    private fun finishPlayback() {
        try {
            viewModel.player.clearVideoSurfaceView(
                binding.playerView.videoSurfaceView as SurfaceView
            )
        } catch (e: Exception) {
            Timber.e(e)
        }
        finish()
    }

    private fun pipParams(
        enableAutoEnter: Boolean = viewModel.player.isPlaying
    ): PictureInPictureParams {
        val displayAspectRatio = Rational(binding.playerView.width, binding.playerView.height)

        // videoSize is VideoSize.UNKNOWN (0x0) until the file is loaded, which would yield a NaN
        // Rational and crash setAspectRatio. Bail with IAE so the existing call-site catches
        // (setPictureInPictureParams / enterPictureInPictureMode both wrap in try { } catch (_: IAE)).
        val videoSize = binding.playerView.player?.videoSize
        val aspectRatio =
            if (videoSize != null && videoSize.width > 0 && videoSize.height > 0) {
                Rational(
                    videoSize.width.coerceAtMost((videoSize.height * 2.39f).toInt()),
                    videoSize.height.coerceAtMost((videoSize.width * 2.39f).toInt()),
                )
            } else {
                throw IllegalArgumentException("Video size unknown, cannot build PiP params")
            }

        val sourceRectHint =
            if (displayAspectRatio < aspectRatio) {
                val space =
                    ((binding.playerView.height -
                            (binding.playerView.width.toFloat() / aspectRatio.toFloat())) / 2)
                        .toInt()
                Rect(
                    0,
                    space,
                    binding.playerView.width,
                    (binding.playerView.width.toFloat() / aspectRatio.toFloat()).toInt() + space,
                )
            } else {
                val space =
                    ((binding.playerView.width -
                            (binding.playerView.height.toFloat() * aspectRatio.toFloat())) / 2)
                        .toInt()
                Rect(
                    space,
                    0,
                    (binding.playerView.height.toFloat() * aspectRatio.toFloat()).toInt() + space,
                    binding.playerView.height,
                )
            }

        val builder =
            PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .setSourceRectHint(sourceRectHint)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(enableAutoEnter)
        }

        return builder.build()
    }

    private fun pictureInPicture() {
        if (!isPipSupported) {
            return
        }

        try {
            enterPictureInPictureMode(pipParams())
        } catch (_: IllegalArgumentException) {}
    }

    override fun onStart() {
        super.onStart()
        viewModel.setVideoEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        // Background audio: keep playing without a picture. The player stays attached to
        // PlayerView: detaching it drops mpv's surface callbacks and closes the shutter.
        if (viewModel.isBackgroundAudioEnabled && viewModel.player.isPlaying) {
            viewModel.setVideoEnabled(false)
        }
    }

    override fun onDestroy() {
        viewModel.detachWindow()
        // Cancel both trickplay MainScopes so any in-flight tile fetches don't outlive the
        // activity. onDestroy is the right hook here: previewScrubListener and
        // playerGestureHelper are bound to this activity's view hierarchy (binding,
        // playerView) and the listener is attached to a view-owned TimeBar, so they must
        // survive across onStop/onStart (e.g. PiP, screen off) and are only safe to tear
        // down when the activity itself is being destroyed.
        previewScrubListener?.dispose()
        playerGestureHelper?.dispose()
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.isInPictureInPictureMode = isInPictureInPictureMode
        applyTabletopLayout()
        updateEpisodesButton()
        when (isInPictureInPictureMode) {
            true -> {
                binding.playerView.useController = false
                skipSegmentButton.isVisible = false

                wasZoom = playerGestureHelper?.isZoomEnabled == true
                playerGestureHelper?.updateZoomMode(false)

                // Brightness mode Auto
                window.attributes =
                    window.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
            }

            false -> {
                binding.playerView.useController = true
                playerGestureHelper?.updateZoomMode(wasZoom)

                // Override auto brightness
                if (
                    appPreferences.getValue(appPreferences.playerGesturesVB) &&
                        appPreferences.getValue(appPreferences.playerGesturesBrightnessRemember)
                ) {
                    window.attributes =
                        window.attributes.apply {
                            screenBrightness =
                                appPreferences.getValue(appPreferences.playerBrightness)
                        }
                }
            }
        }
    }
}
