package com.sliide.kmp.ui.users

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sliide.kmp.presentation.users.UserListEvent
import com.sliide.kmp.presentation.users.UserListUiState
import com.sliide.kmp.presentation.users.UserListViewModel
import com.sliide.kmp.presentation.users.UserUiModel
import com.sliide.kmp.ui.components.AppIcons
import com.sliide.kmp.ui.components.EmptyState
import com.sliide.kmp.ui.components.ErrorState
import com.sliide.kmp.ui.components.StaleDataBanner
import com.sliide.kmp.ui.components.UserListSkeleton
import com.sliide.kmp.ui.components.deleteFailureMessage
import org.koin.compose.viewmodel.koinViewModel

/** Below this width there is not enough room for a list and a detail pane side by side. */
private val TwoPaneBreakpoint = 720.dp
private val ListPaneWidth = 400.dp

/** Enough room below scrollable content for the extended FAB plus its margin. */
private val FabClearance = 96.dp

/**
 * Measures the available width and hands the decision down. Keeping the measurement in its own
 * composable means the screen body is not nested inside a `BoxWithConstraints` scope, and the
 * layout decision arrives as a plain parameter that a preview can set by hand.
 */
@Composable
fun UserListScreen(viewModel: UserListViewModel = koinViewModel()) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        UserListScreenContent(
            isTwoPane = maxWidth >= TwoPaneBreakpoint,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserListScreenContent(
    isTwoPane: Boolean,
    viewModel: UserListViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var isAddSheetVisible by rememberSaveable { mutableStateOf(false) }
    val appBarState = rememberTopAppBarState()
    // Skips the half-expanded stop so the sheet opens tall enough to show the delete action
    // without the user having to drag it up first.
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(appBarState)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UserListEvent.DeleteFailed ->
                    snackbarHostState.showSnackbar(
                        event.error.deleteFailureMessage(event.userName),
                    )
            }
        }
    }

    // A selection is a two-pane concept: it has a pane of its own to live in. Collapsing to a
    // single pane would otherwise promote it into a modal the user never opened - and if they
    // were part-way through adding a user, stack it on top of that half-filled form.
    LaunchedEffect(isTwoPane) {
        if (!isTwoPane) {
            viewModel.onDetailDismissed()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Users") },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = {
            Column {
                SnackbarHost(snackbarHostState)
                UndoSnackbar(pending = state.pendingDeletion, onUndo = viewModel::onUndoDeletion)
            }
        },
        floatingActionButton = {
            // Only the single-pane layout gets a screen-level FAB. On two panes it would
            // float over the detail, covering whichever row happened to be beneath it, so
            // it is anchored inside the list pane instead.
            if (!isTwoPane) {
                AddUserFab(
                    expanded = appBarState.collapsedFraction < 0.5f,
                    onClick = { isAddSheetVisible = true },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = state.staleDataWarning != null) {
                        state.staleDataWarning?.let { error ->
                            StaleDataBanner(
                                error = error,
                                onDismiss = viewModel::onStaleWarningDismissed,
                                onRetry = { viewModel.refresh() },
                            )
                        }
                    }

                    Crossfade(targetState = state.contentMode, label = "listContent") { mode ->
                        when (mode) {
                            ContentMode.Loading -> UserListSkeleton(modifier = Modifier.fillMaxSize())

                            ContentMode.Error ->
                                state.blockingError?.let { error ->
                                    ErrorState(error = error, onRetry = { viewModel.refresh() })
                                }

                            ContentMode.Empty -> EmptyState(onRefresh = { viewModel.refresh() })

                            ContentMode.Content ->
                                if (isTwoPane) {
                                    TwoPaneContent(
                                        state = state,
                                        viewModel = viewModel,
                                        onAddUser = { isAddSheetVisible = true },
                                    )
                                } else {
                                    UserList(
                                        users = state.users,
                                        selectedUserId = null,
                                        onUserClick = viewModel::onUserSelected,
                                        onUserLongClick = viewModel::onDeleteRequested,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                        }
                    }
                }

                // On a compact layout the detail is a sheet rather than a second pane.
                state.selectedUser?.takeIf { !isTwoPane }?.let { selected ->
                    ModalBottomSheet(
                        onDismissRequest = viewModel::onDetailDismissed,
                        sheetState = detailSheetState,
                    ) {
                        UserDetailContent(
                            user = selected,
                            onDelete = { viewModel.onDeleteRequested(selected.id) },
                            modifier = Modifier.navigationBarsPadding(),
                        )
                    }
                }
            }
        }
    }

    state.confirmingDeletionFor?.let { target ->
        DeleteConfirmationDialog(
            user = target,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteDismissed,
        )
    }

    if (isAddSheetVisible) {
        // No success snackbar on purpose: the new row animating in at the top is the feedback.
        AddUserSheet(onDismiss = { isAddSheetVisible = false })
    }
}

@Composable
private fun TwoPaneContent(
    state: UserListUiState,
    viewModel: UserListViewModel,
    onAddUser: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(ListPaneWidth).fillMaxHeight()) {
            UserList(
                users = state.users,
                selectedUserId = state.selectedUserId,
                onUserClick = viewModel::onUserSelected,
                onUserLongClick = viewModel::onDeleteRequested,
                modifier = Modifier.fillMaxSize(),
            )
            AddUserFab(
                expanded = true,
                onClick = onAddUser,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        VerticalDivider()
        UserDetailPane(
            user = state.selectedUser,
            onDelete = { state.selectedUser?.let { viewModel.onDeleteRequested(it.id) } },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun AddUserFab(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(AppIcons.Add, contentDescription = null) },
        text = { Text("Add user") },
        // Collapses to a plain FAB once the list is scrolled, to free up screen.
        expanded = expanded,
        modifier = modifier,
    )
}

@Composable
private fun UserList(
    users: List<UserUiModel>,
    selectedUserId: Long?,
    onUserClick: (Long) -> Unit,
    onUserLongClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // A user created in-app lands at index 0; scroll so they actually see it happen.
    val firstId = users.firstOrNull()?.id
    LaunchedEffect(firstId) {
        if (firstId != null && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        // Bottom inset clears the FAB, so the last row is never parked underneath it.
        contentPadding =
            PaddingValues(
                top = 8.dp,
                bottom = FabClearance,
                start = 4.dp,
                end = 4.dp,
            ),
    ) {
        items(items = users, key = { it.id }) { user ->
            UserRow(
                user = user,
                isSelected = user.id == selectedUserId,
                onClick = { onUserClick(user.id) },
                onLongClick = { onUserLongClick(user.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    user: UserUiModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // No body text: the title already states the action, and explaining the undo window up
    // front pre-empts an affordance the user has not been offered yet.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${user.name}?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private enum class ContentMode { Loading, Error, Empty, Content }

private val UserListUiState.contentMode: ContentMode
    get() =
        when {
            isInitialLoading && users.isEmpty() -> ContentMode.Loading
            blockingError != null && users.isEmpty() -> ContentMode.Error
            isEmpty -> ContentMode.Empty
            else -> ContentMode.Content
        }
