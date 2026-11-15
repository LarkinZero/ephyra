package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.library.LibraryAction
import dev.jdtech.jellyfin.film.presentation.library.LibraryState
import dev.jdtech.jellyfin.film.presentation.library.LibraryViewModel
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.components.ErrorDialog
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.EmptyState
import dev.jdtech.jellyfin.presentation.film.components.ItemCard
import dev.jdtech.jellyfin.presentation.film.components.LetterFastScroller
import dev.jdtech.jellyfin.presentation.film.components.ServerOrErrorCard
import dev.jdtech.jellyfin.presentation.film.components.SortByDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.GridCellsAdaptiveWithMinColumns
import dev.jdtech.jellyfin.presentation.utils.Layout
import dev.jdtech.jellyfin.presentation.utils.currentWidthClass
import dev.jdtech.jellyfin.presentation.utils.plus
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun LibraryScreen(
    libraryId: UUID,
    libraryName: String,
    libraryType: CollectionType,
    onItemClick: (item: FindroidItem) -> Unit,
    navigateBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) {
        // CE (#672): placeholders, so the fast scroller can jump anywhere, and the letter index.
        viewModel.setup(parentId = libraryId, libraryType = libraryType, indexed = true)
        // CE (#371): the ViewModel decides, a saved flag would skip the load after process death.
        viewModel.loadItemsIfNeeded()
    }

    LibraryScreenLayout(
        libraryName = libraryName,
        state = state,
        onAction = { action ->
            when (action) {
                is LibraryAction.OnItemClick -> onItemClick(action.item)
                is LibraryAction.OnBackClick -> navigateBack()
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreenLayout(
    libraryName: String,
    state: LibraryState,
    onAction: (LibraryAction) -> Unit,
) {
    val contentPadding = PaddingValues(all = MaterialTheme.spacings.default)

    val items = state.items.collectAsLazyPagingItems()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showSortByDialog by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(libraryName) },
                navigationIcon = {
                    IconButton(onClick = { onAction(LibraryAction.OnBackClick) }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = stringResource(CoreR.string.navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onAction(LibraryAction.ToggleWatchedFilter(!state.filterWatched))
                        }
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (state.filterWatched) CoreR.drawable.ic_eye_off
                                    else CoreR.drawable.ic_eye
                                ),
                            contentDescription = stringResource(CoreR.string.toggle_watched),
                        )
                    }
                    IconButton(onClick = { showSortByDialog = true }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_down_up),
                            contentDescription = stringResource(CoreR.string.sort_by),
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column {
            ErrorGroup(
                loadStates = items.loadState,
                onRefresh = { items.refresh() },
                modifier = Modifier.padding(contentPadding + innerPadding),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns =
                        GridCellsAdaptiveWithMinColumns(
                            minSize = Layout.gridMinCell(currentWidthClass()),
                            minColumns = 2,
                        ),
                    modifier = Modifier.fillMaxSize(),
                    state = gridState,
                    contentPadding = contentPadding + innerPadding,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                ) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) {
                        val item = items[it]
                        if (item == null) {
                            // CE (#672): not loaded yet, for example right after a jump. A cell
                            // with no height would make the grid compose and load far ahead.
                            ItemCardSkeleton()
                        } else {
                            // No animateItem() (#257, #672): the grid pages and jumps.
                            ItemCard(
                                item = item,
                                direction = Direction.VERTICAL,
                                onClick = { onAction(LibraryAction.OnItemClick(item)) },
                            )
                        }
                    }
                }
                val layoutDirection = LocalLayoutDirection.current
                LetterFastScroller(
                    gridState = gridState,
                    itemCount = items.itemCount,
                    letterIndex = state.letterIndex,
                    modifier =
                        Modifier.align(Alignment.TopEnd)
                            .padding(
                                top = innerPadding.calculateTopPadding(),
                                bottom = innerPadding.calculateBottomPadding(),
                                end = innerPadding.calculateEndPadding(layoutDirection),
                            ),
                )
                if (items.itemCount == 0) {
                    when (items.loadState.refresh) {
                        is LoadState.Loading ->
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        is LoadState.NotLoading ->
                            LibraryEmptyState(
                                filterWatched = state.filterWatched,
                                onShowWatchedClick = {
                                    onAction(LibraryAction.ToggleWatchedFilter(false))
                                },
                                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                            )
                        is LoadState.Error -> Unit
                    }
                }
            }
        }
    }

    if (showSortByDialog) {
        SortByDialog(
            currentSortBy = state.sortBy,
            currentSortOrder = state.sortOrder,
            onUpdate = { sortBy, sortOrder ->
                onAction(LibraryAction.ChangeSorting(sortBy, sortOrder))
            },
            onDismissRequest = { showSortByDialog = false },
        )
    }
}

/** CE (#672): the shape of a vertical [ItemCard] while its page loads. */
@Composable
private fun ItemCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(Layout.cardVertical(currentWidthClass()))) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(0.66f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        // One line of title and the card's bottom spacer: most titles fit on one line, so the
        // row keeps its height when the page comes in (a two-line title still grows it).
        val titleLine =
            with(LocalDensity.current) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
        Spacer(modifier = Modifier.height(titleLine + 2.dp))
    }
}

@Composable
private fun LibraryEmptyState(
    filterWatched: Boolean,
    onShowWatchedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (filterWatched) {
        EmptyState(
            icon = CoreR.drawable.ic_eye_off,
            title = stringResource(CoreR.string.library_no_unwatched),
            modifier = modifier,
            actions = {
                FilledTonalButton(onClick = onShowWatchedClick) {
                    Text(stringResource(CoreR.string.show_watched))
                }
            },
        )
    } else {
        EmptyState(
            icon = CoreR.drawable.ic_library,
            title = stringResource(CoreR.string.nothing_here_yet),
            modifier = modifier,
        )
    }
}

@Composable
private fun ErrorGroup(
    loadStates: CombinedLoadStates,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showErrorDialog by rememberSaveable { mutableStateOf(false) }

    val loadStateError =
        when {
            loadStates.refresh is LoadState.Error -> {
                loadStates.refresh as LoadState.Error
            }
            loadStates.prepend is LoadState.Error -> {
                loadStates.prepend as LoadState.Error
            }
            loadStates.append is LoadState.Error -> {
                loadStates.append as LoadState.Error
            }
            else -> null
        }

    // CE (#281): the unreachable banner while the server does not answer, also with no failed load.
    ServerOrErrorCard(
        error = loadStateError?.error,
        onShowStacktrace = { showErrorDialog = true },
        onRetryClick = onRefresh,
        modifier = modifier,
    )
    loadStateError?.let {
        if (showErrorDialog) {
            ErrorDialog(
                exception = it.error,
                onDismissRequest = { showErrorDialog = false },
                onRetry = onRefresh,
            )
        }
    }
}

@PreviewScreenSizes
@Composable
private fun LibraryScreenLayoutPreview() {
    val items: Flow<PagingData<FindroidItem>> = flowOf(PagingData.from(dummyMovies))
    FindroidTheme {
        LibraryScreenLayout(
            libraryName = "Movies",
            state = LibraryState(items = items),
            onAction = {},
        )
    }
}
