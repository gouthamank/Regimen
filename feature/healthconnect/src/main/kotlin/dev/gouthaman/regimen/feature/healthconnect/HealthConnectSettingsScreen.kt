package dev.gouthaman.regimen.feature.healthconnect

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.common.SessionFormat
import dev.gouthaman.regimen.common.healthConnectFromSettingsTransitionKey
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.component.EmptyState
import dev.gouthaman.regimen.designsystem.dialog.ConfirmDialog
import dev.gouthaman.regimen.domain.model.BiometricsBackfillResult
import dev.gouthaman.regimen.domain.model.HealthConnectConnectionState
import dev.gouthaman.regimen.domain.model.HealthConnectRetryFrequency
import dev.gouthaman.regimen.domain.model.HealthConnectStatus

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HealthConnectSettingsScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HealthConnectSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Tracked so the launcher's result callback can tell "the user declined" apart from "the
    // request never actually prompted at all" (e.g. a permission Android has marked USER_FIXED
    // after enough prior denials silently aborts the whole request with zero system UI).
    var lastRequestedPermissions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var permissionRequestFailed by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        if (lastRequestedPermissions.isNotEmpty() && !granted.containsAll(lastRequestedPermissions)) {
            permissionRequestFailed = true
        }
        viewModel.refreshStatus()
    }
    val permissionRequestFailedMessage =
        stringResource(R.string.health_connect_permission_request_failed_snackbar)
    val openSettingsLabel = stringResource(R.string.health_connect_open_settings_button)
    LaunchedEffect(permissionRequestFailed) {
        if (permissionRequestFailed) {
            val result = snackbarHostState.showSnackbar(
                message = permissionRequestFailedMessage,
                actionLabel = openSettingsLabel,
            )
            if (result == SnackbarResult.ActionPerformed) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    },
                )
            }
            permissionRequestFailed = false
        }
    }

    // Message resolution needs stringResource/pluralStringResource, so it happens here in
    // composition - the LaunchedEffect below only shows whatever this already resolved.
    var pendingPullResult by remember { mutableStateOf<BiometricsBackfillResult?>(null) }
    LaunchedEffect(Unit) {
        viewModel.pullResultEvents.collect { pendingPullResult = it }
    }
    val pullResultMessage = pendingPullResult?.let { result ->
        when {
            result.candidateCount == 0 ->
                stringResource(R.string.health_connect_pull_no_candidates_snackbar)

            result.pulledCount == 0 -> pluralStringResource(
                R.plurals.health_connect_pull_found_nothing_snackbar,
                result.candidateCount,
                result.candidateCount,
            )

            else -> pluralStringResource(
                R.plurals.health_connect_pull_found_data_snackbar,
                result.pulledCount,
                result.pulledCount,
            )
        }
    }
    LaunchedEffect(pullResultMessage) {
        pullResultMessage?.let {
            snackbarHostState.showSnackbar(it)
            pendingPullResult = null
        }
    }

    // Permission may have been granted/revoked via Health Connect's own Settings UI while this
    // screen was merely backgrounded, not navigated away from.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HealthConnectSettingsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onEnabledChange = viewModel::setHealthConnectEnabled,
        onBackgroundSyncEnabledChange = viewModel::setBackgroundSyncEnabled,
        onRetryFrequencyChange = viewModel::setRetryFrequency,
        onCheckNowClick = viewModel::pullNow,
        onDeleteDataClick = viewModel::deleteAllData,
        onGrantPermissionClick = {
            uiState.status?.let {
                lastRequestedPermissions = it.corePermissions
                permissionLauncher.launch(it.corePermissions)
            }
        },
        onEnableOptionalPermissionClick = {
            uiState.status?.let {
                val backgroundOnly = it.requiredPermissions - it.corePermissions
                lastRequestedPermissions = backgroundOnly
                permissionLauncher.launch(backgroundOnly)
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun HealthConnectSettingsScreen(
    uiState: HealthConnectSettingsUiState,
    snackbarHostState: SnackbarHostState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onBackgroundSyncEnabledChange: (Boolean) -> Unit,
    onRetryFrequencyChange: (HealthConnectRetryFrequency) -> Unit,
    onCheckNowClick: () -> Unit,
    onGrantPermissionClick: () -> Unit,
    onEnableOptionalPermissionClick: () -> Unit,
    onDeleteDataClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val rootModifier = with(sharedTransitionScope) {
        modifier
            .sharedBounds(
                rememberSharedContentState(key = healthConnectFromSettingsTransitionKey),
                animatedVisibilityScope = animatedVisibilityScope,
            )
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    }

    Scaffold(
        modifier = rootModifier,
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.health_connect_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentModifier = if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
                Modifier
                    .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                    .fillMaxSize()
            } else {
                Modifier.fillMaxSize()
            }
            Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            ) {
                val status = uiState.status
                val enabled = uiState.prefs.healthConnectEnabled
                val isUnavailable =
                    status?.connectionState == HealthConnectConnectionState.UNAVAILABLE
                val isActive = status?.connectionState == HealthConnectConnectionState.ACTIVE

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.health_connect_enable_headline),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                        enabled = !isUnavailable,
                    )
                }

                // Only reachable while opted out/unavailable - the periodic job and any active
                // pull are both off in every branch that shows this, so there's nothing else
                // competing for this data underneath the user.
                val hasStoredData = status?.lastPulledAt != null

                when {
                    isUnavailable -> {
                        EmptyState(
                            message = stringResource(R.string.health_connect_status_unavailable),
                            icon = Icons.Filled.MonitorHeart,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (hasStoredData) DeleteDataButton(onClick = onDeleteDataClick)
                    }

                    !enabled -> {
                        EmptyState(
                            message = stringResource(R.string.health_connect_intro_message),
                            icon = Icons.Filled.MonitorHeart,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (hasStoredData) DeleteDataButton(onClick = onDeleteDataClick)
                    }

                    !isActive -> EmptyState(
                        message = stringResource(R.string.health_connect_permission_required_message),
                        icon = Icons.Filled.MonitorHeart,
                        actionLabel = stringResource(R.string.health_connect_grant_permission_button),
                        onAction = onGrantPermissionClick,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    else -> {
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(24.dp))

                        StatusRow(
                            status = status,
                            isPulling = uiState.isPulling,
                            onCheckNowClick = onCheckNowClick,
                        )

                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(24.dp))

                        Text(
                            stringResource(R.string.health_connect_background_sync_section_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(16.dp))

                        val backgroundPermissionMissing =
                            status?.hasOptionalPermissionAvailable == true
                        if (backgroundPermissionMissing) {
                            EmptyState(
                                message = stringResource(R.string.health_connect_background_permission_message),
                                icon = Icons.Filled.MonitorHeart,
                                actionLabel = stringResource(R.string.health_connect_optional_permission_button),
                                onAction = onEnableOptionalPermissionClick,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            val backgroundSyncEnabled = uiState.prefs.backgroundSyncEnabled
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.health_connect_background_sync_toggle_headline),
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = backgroundSyncEnabled,
                                    onCheckedChange = onBackgroundSyncEnabledChange,
                                )
                            }
                            Spacer(Modifier.height(16.dp))

                            Text(
                                stringResource(R.string.health_connect_retry_frequency_headline),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(8.dp))
                            RetryFrequencySelector(
                                selected = uiState.prefs.retryFrequency,
                                onChange = onRetryFrequencyChange,
                                enabled = backgroundSyncEnabled,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteDataButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = { showConfirmDialog = true }) {
            Text(stringResource(R.string.health_connect_delete_data_button))
        }
    }
    if (showConfirmDialog) {
        ConfirmDialog(
            title = stringResource(R.string.health_connect_delete_data_dialog_title),
            text = stringResource(R.string.health_connect_delete_data_dialog_text),
            confirmLabel = stringResource(R.string.health_connect_delete_data_confirm_button),
            onConfirm = {
                showConfirmDialog = false
                onClick()
            },
            dismissLabel = stringResource(R.string.health_connect_delete_data_cancel_button),
            onDismiss = { showConfirmDialog = false },
            destructive = true,
        )
    }
}

@Composable
private fun StatusRow(
    status: HealthConnectStatus?,
    isPulling: Boolean,
    onCheckNowClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.health_connect_status_active),
                style = MaterialTheme.typography.titleMedium,
            )
            val sourceOrHint = status?.detectedSourceAppLabel
                ?.let { stringResource(R.string.health_connect_data_from, it) }
                ?: stringResource(R.string.health_connect_no_data_yet)
            Text(
                sourceOrHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            status?.lastPulledAt?.let {
                Text(
                    stringResource(
                        R.string.health_connect_last_checked_at,
                        SessionFormat.timeWithDateIfNotToday(it),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(onClick = onCheckNowClick, enabled = !isPulling) {
            if (isPulling) {
                ButtonProgressIndicator()
            } else {
                Text(stringResource(R.string.health_connect_check_now_button))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetryFrequencySelector(
    selected: HealthConnectRetryFrequency,
    onChange: (HealthConnectRetryFrequency) -> Unit,
    enabled: Boolean,
) {
    val options = HealthConnectRetryFrequency.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onChange(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(
                    stringResource(
                        when (option) {
                            HealthConnectRetryFrequency.ONE_HOUR -> R.string.health_connect_frequency_one_hour
                            HealthConnectRetryFrequency.SIX_HOURS -> R.string.health_connect_frequency_six_hours
                            HealthConnectRetryFrequency.DAILY -> R.string.health_connect_frequency_daily
                        }
                    )
                )
            }
        }
    }
}

/** Replaces a button's label with a spinner sized to sit on the same content baseline, matching
 * the button's own content color - same helper `AccountScreen` defines for itself. */
@Composable
private fun ButtonProgressIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = LocalContentColor.current,
    )
}
