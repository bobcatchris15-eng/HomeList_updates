package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChoreEntity
import com.example.data.DateStreakUtils
import com.example.data.ReminderTriggerType
import com.example.reminders.ReminderNotificationHelper
import com.example.ui.AppTab
import com.example.ui.HomeListViewModel
import com.example.ui.components.MeshStatusPill
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.ChoreEditorSheet
import com.example.ui.screens.EventEditorSheet
import com.example.ui.screens.HouseholdMeshScreen
import com.example.ui.screens.KitchenScreen
import com.example.ui.screens.LivePushReminderSheet
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PlaceEditorSheet
import com.example.ui.screens.ReminderEditorSheet
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.screens.WallDisplayScreen
import com.example.ui.theme.HomeListTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HomeListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeListApp(viewModel = viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeListApp(viewModel: HomeListViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pinnedCoords by viewModel.pinnedCoordinates.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by remember { mutableStateOf(AppTab.TODAY) }
    var showWallDisplay by remember { mutableStateOf(false) }

    // Modal editors
    var showFabMenu by remember { mutableStateOf(false) }
    var showChoreEditor by remember { mutableStateOf(false) }
    var editingChore by remember { mutableStateOf<ChoreEntity?>(null) }
    var showReminderEditor by remember { mutableStateOf(false) }
    var reminderInitialTitle by remember { mutableStateOf("") }
    var reminderInitialTrigger by remember { mutableStateOf(ReminderTriggerType.TIME) }
    var showLivePushSheet by remember { mutableStateOf(false) }
    var showPlaceEditor by remember { mutableStateOf(false) }
    var showEventEditor by remember { mutableStateOf(false) }
    var eventInitialDate by remember { mutableStateOf(DateStreakUtils.todayKey()) }

    // Live runtime permission state
    var hasNotifications by remember {
        mutableStateOf(ReminderNotificationHelper.hasNotificationPermission(context))
    }
    var hasLocation by remember {
        mutableStateOf(viewModel.locationManager.hasForegroundLocationPermission())
    }
    var hasCalendar by remember {
        mutableStateOf(viewModel.calendarBridge.hasReadPermission())
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifications = granted || ReminderNotificationHelper.hasNotificationPermission(context)
        if (granted) viewModel.showMessage("Reminder alerts enabled!")
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasLocation = results.values.any { it } || viewModel.locationManager.hasForegroundLocationPermission()
        if (hasLocation) {
            viewModel.refreshLocationChecks()
            viewModel.showMessage("Location triggers ready!")
        }
    }

    val calendarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCalendar = results[Manifest.permission.READ_CALENDAR] == true || viewModel.calendarBridge.hasReadPermission()
        if (hasCalendar) {
            viewModel.loadDeviceCalendars()
            viewModel.showMessage("Calendar access granted — tap Connect Google to pick a calendar.")
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    val visibleTabs = remember(state.isParent) {
        listOf(AppTab.TODAY, AppTab.KITCHEN, AppTab.REMINDERS, AppTab.CALENDAR, AppTab.HOUSEHOLD)
    }

    BackHandler(enabled = state.isConfigured && !showWallDisplay && currentTab != AppTab.TODAY) {
        currentTab = AppTab.TODAY
    }

    HomeListTheme(darkTheme = state.isDarkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedWidth = maxWidth >= 700.dp

            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                !state.isConfigured -> {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { _ ->
                        OnboardingScreen(
                            discoveredNodes = state.discoveredNodes,
                            isJoining = state.isJoiningMesh,
                            isTablet = isExpandedWidth,
                            onFoundHousehold = { familyName, displayName, colorKey, tablet ->
                                viewModel.foundHousehold(familyName, displayName, colorKey, tablet)
                            },
                            onJoinHousehold = { addr, code, displayName, role, colorKey, tablet ->
                                viewModel.joinHouseholdViaAddress(addr, code, displayName, role, colorKey, tablet)
                            }
                        )
                    }
                }

                showWallDisplay -> {
                    WallDisplayScreen(
                        state = state,
                        onSyncMeshNow = { viewModel.syncMeshNow() },
                        onExit = { showWallDisplay = false }
                    )
                }

                else -> {
                    Scaffold(
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            TopAppBar(
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background
                                ),
                                title = {
                                    Column {
                                        Text(
                                            text = state.config?.householdName ?: "HomeList",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            text = if (state.isParent) "Parent Mode" else "Child Mode",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                actions = {
                                    MeshStatusPill(
                                        status = state.meshStatus,
                                        peerCount = state.peers.size,
                                        onClick = { viewModel.syncMeshNow() }
                                    )
                                    IconButton(
                                        onClick = { showWallDisplay = true },
                                        modifier = Modifier.testTag("top_bar_wall_display_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Tv,
                                            contentDescription = "Open Wall Display"
                                        )
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            if (!isExpandedWidth) {
                                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                    visibleTabs.forEach { tab ->
                                        val selected = currentTab == tab
                                        val label = if (!state.isParent && tab == AppTab.CALENDAR) "My Week"
                                        else if (!state.isParent && tab == AppTab.HOUSEHOLD) "Mesh"
                                        else tab.label
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { currentTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = tabIcon(tab, selected),
                                                    contentDescription = label
                                                )
                                            },
                                            label = { Text(label) },
                                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        },
                        floatingActionButton = {
                            if (currentTab != AppTab.HOUSEHOLD && currentTab != AppTab.KITCHEN) {
                                Box {
                                    FloatingActionButton(
                                        onClick = {
                                            when {
                                                currentTab == AppTab.REMINDERS -> {
                                                    reminderInitialTitle = ""
                                                    reminderInitialTrigger = ReminderTriggerType.TIME
                                                    showReminderEditor = true
                                                }
                                                currentTab == AppTab.CALENDAR -> {
                                                    eventInitialDate = DateStreakUtils.todayKey()
                                                    showEventEditor = true
                                                }
                                                state.isParent -> showFabMenu = true
                                                else -> {
                                                    reminderInitialTitle = ""
                                                    reminderInitialTrigger = ReminderTriggerType.TIME
                                                    showReminderEditor = true
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(18.dp),
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.testTag("primary_add_fab")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Add item")
                                    }

                                    DropdownMenu(
                                        expanded = showFabMenu,
                                        onDismissRequest = { showFabMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Push Live Reminder") },
                                            onClick = {
                                                showFabMenu = false
                                                showLivePushSheet = true
                                            },
                                            modifier = Modifier.testTag("fab_menu_live_push")
                                        )
                                        DropdownMenuItem(
                                            text = { Text("New Chore") },
                                            onClick = {
                                                showFabMenu = false
                                                editingChore = null
                                                showChoreEditor = true
                                            },
                                            modifier = Modifier.testTag("fab_menu_chore")
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Time / Location Reminder") },
                                            onClick = {
                                                showFabMenu = false
                                                reminderInitialTitle = ""
                                                reminderInitialTrigger = ReminderTriggerType.TIME
                                                showReminderEditor = true
                                            },
                                            modifier = Modifier.testTag("fab_menu_reminder")
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Family Calendar Event") },
                                            onClick = {
                                                showFabMenu = false
                                                eventInitialDate = DateStreakUtils.todayKey()
                                                showEventEditor = true
                                            },
                                            modifier = Modifier.testTag("fab_menu_event")
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (isExpandedWidth) {
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxHeight()
                                ) {
                                    visibleTabs.forEach { tab ->
                                        val selected = currentTab == tab
                                        val label = if (!state.isParent && tab == AppTab.CALENDAR) "My Week"
                                        else if (!state.isParent && tab == AppTab.HOUSEHOLD) "Mesh"
                                        else tab.label
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { currentTab = tab },
                                            icon = {
                                                Icon(tabIcon(tab, selected), contentDescription = label)
                                            },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 720.dp)
                                ) {
                                    when (currentTab) {
                                        AppTab.TODAY -> TodayScreen(
                                            state = state,
                                            onToggleChore = { viewModel.toggleChoreCompletion(it) },
                                            onSendNudge = { chore, member -> viewModel.sendChoreNudge(chore, member) },
                                            onEditChore = { chore ->
                                                editingChore = chore
                                                showChoreEditor = true
                                            },
                                            onCompleteNudge = { viewModel.completeNudgeAndChore(it) },
                                            onDismissNudge = { viewModel.dismissNudge(it) },
                                            onOpenAddChore = {
                                                editingChore = null
                                                showChoreEditor = true
                                            },
                                            onOpenLivePush = { showLivePushSheet = true },
                                            onNavigateKitchen = { currentTab = AppTab.KITCHEN },
                                            onNavigateReminders = { currentTab = AppTab.REMINDERS },
                                            onNavigateCalendar = { currentTab = AppTab.CALENDAR },
                                            onDismissUpdateBanner = { viewModel.dismissUpdateBanner(it) }
                                        )

                                        AppTab.KITCHEN -> KitchenScreen(
                                            state = state,
                                            onProposeMeal = { dateKey, mealName, mealSlot, notes ->
                                                viewModel.proposeDinnerOption(dateKey, mealName, mealSlot, notes)
                                            },
                                            onUpdateMeal = { viewModel.updateDinnerOption(it) },
                                            onToggleMealVote = { viewModel.toggleDinnerVote(it) },
                                            onSetMealWinner = { viewModel.setDinnerWinner(it) },
                                            onDeleteMealOption = { viewModel.deleteDinnerOption(it) },
                                            onAddShoppingItem = { name -> viewModel.addGroceryItem(name) },
                                            onToggleShoppingItem = { viewModel.toggleGroceryItem(it) },
                                            onDeleteShoppingItem = { viewModel.deleteGroceryItem(it) },
                                            onClearCheckedShopping = { viewModel.clearCheckedGroceries() }
                                        )

                                        AppTab.REMINDERS -> RemindersScreen(
                                            state = state,
                                            hasNotificationPermission = hasNotifications,
                                            hasLocationPermission = hasLocation,
                                            onRequestNotificationPermission = {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                            },
                                            onRequestLocationPermission = {
                                                locationLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
                                            },
                                            onRefreshLocation = {
                                                if (!hasLocation) {
                                                    locationLauncher.launch(
                                                        arrayOf(
                                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                                        )
                                                    )
                                                } else {
                                                    viewModel.refreshLocationChecks()
                                                    viewModel.showMessage("Checked location against saved places")
                                                }
                                            },
                                            onOpenAddReminder = {
                                                reminderInitialTitle = ""
                                                reminderInitialTrigger = ReminderTriggerType.TIME
                                                showReminderEditor = true
                                            },
                                            onOpenAddPlace = {
                                                viewModel.clearPinnedCoordinates()
                                                showPlaceEditor = true
                                            },
                                            onToggleReminder = { viewModel.toggleReminderCompleted(it) },
                                            onDeleteReminder = { viewModel.deleteReminder(it) },
                                            onDeletePlace = { viewModel.deletePlace(it) }
                                        )

                                        AppTab.CALENDAR -> CalendarScreen(
                                            state = state,
                                            hasCalendarPermission = hasCalendar,
                                            onRequestCalendarPermission = {
                                                calendarLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.READ_CALENDAR,
                                                        Manifest.permission.WRITE_CALENDAR
                                                    )
                                                )
                                            },
                                            onLoadDeviceCalendars = { viewModel.loadDeviceCalendars() },
                                            onSelectGoogleCalendar = { viewModel.selectAndSyncGoogleCalendar(it) },
                                            onRefreshGoogleCalendar = { viewModel.syncConfiguredGoogleCalendar() },
                                            onImportIcsUrl = { viewModel.importIcsFeed(it) },
                                            onOpenAddEvent = { date ->
                                                eventInitialDate = date
                                                showEventEditor = true
                                            },
                                            onDeleteEvent = { viewModel.deleteCalendarEvent(it) },
                                            onBuildExportIntent = { viewModel.buildGoogleCalendarExportIntent(it) },
                                            onOpenWallDisplay = { showWallDisplay = true }
                                        )

                                        AppTab.HOUSEHOLD -> HouseholdMeshScreen(
                                            state = state,
                                            onSyncMeshNow = { viewModel.syncMeshNow() },
                                            onUpdatePrivateCheckIn = { accept, mins ->
                                                viewModel.updatePrivateCheckInSettings(accept, mins)
                                            },
                                            onUpdateCustomPrivateHost = { viewModel.updateCustomPrivateHost(it) },
                                            onAddManualMember = { name, role, color ->
                                                viewModel.addManualMember(name, role, color)
                                            },
                                            onCheckForUpdates = { viewModel.checkForUpdates(silent = false) },
                                            onUpdateReleaseUrl = { viewModel.updateReleaseUrl(it) },
                                            onToggleDarkTheme = { viewModel.toggleDarkTheme() },
                                            onLeaveHousehold = {
                                                currentTab = AppTab.TODAY
                                                viewModel.leaveHousehold()
                                            },
                                            onShowMessage = { viewModel.showMessage(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showChoreEditor) {
                        ChoreEditorSheet(
                            editingChore = editingChore,
                            members = state.members,
                            onDismiss = {
                                showChoreEditor = false
                                editingChore = null
                            },
                            onSave = { viewModel.saveChore(it) },
                            onDelete = { viewModel.deleteChore(it) }
                        )
                    }

                    if (showReminderEditor) {
                        ReminderEditorSheet(
                            members = state.members,
                            places = state.places,
                            defaultAssigneeId = if (state.isParent) null else state.config?.selfMemberId,
                            initialTitle = reminderInitialTitle,
                            initialTriggerType = reminderInitialTrigger,
                            onDismiss = {
                                showReminderEditor = false
                                reminderInitialTitle = ""
                                reminderInitialTrigger = ReminderTriggerType.TIME
                            },
                            onSave = { viewModel.saveReminder(it) }
                        )
                    }

                    if (showLivePushSheet) {
                        LivePushReminderSheet(
                            members = state.members,
                            onDismiss = { showLivePushSheet = false },
                            onSend = { message, targetId, targetLabel, isUrgent ->
                                viewModel.sendLivePushReminder(message, targetId, targetLabel, isUrgent)
                            }
                        )
                    }

                    if (showPlaceEditor) {
                        PlaceEditorSheet(
                            pinnedCoords = pinnedCoords,
                            hasLocationPermission = hasLocation,
                            onRequestLocationPermission = {
                                locationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            onPinCurrentGps = { viewModel.pinCurrentGpsLocation() },
                            onDismiss = { showPlaceEditor = false },
                            onSave = { viewModel.savePlace(it) }
                        )
                    }

                    if (showEventEditor) {
                        EventEditorSheet(
                            initialDateKey = eventInitialDate,
                            googleCalendarConnected = (state.config?.googleCalendarId ?: -1L) > 0L && hasCalendar,
                            onDismiss = { showEventEditor = false },
                            onSave = { event, exportToGoogle ->
                                viewModel.saveCalendarEvent(event, exportToGoogle)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: AppTab, selected: Boolean) = when (tab) {
    AppTab.TODAY -> if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle
    AppTab.KITCHEN -> if (selected) Icons.Filled.Restaurant else Icons.Outlined.Restaurant
    AppTab.REMINDERS -> if (selected) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone
    AppTab.CALENDAR -> if (selected) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth
    AppTab.HOUSEHOLD -> if (selected) Icons.Filled.Groups else Icons.Outlined.Groups
}
