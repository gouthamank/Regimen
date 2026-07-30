package dev.gouthaman.regimen.feature.account

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.common.accountFromSettingsTransitionKey
import dev.gouthaman.regimen.common.text
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.dialog.ConfirmDialog

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AccountScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AccountScreen(
        uiState = uiState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onSignIn = viewModel::signIn,
        onSignOut = viewModel::signOut,
        onDeleteCloudData = viewModel::deleteCloudData,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun AccountScreen(
    uiState: AccountUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteCloudData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val rootModifier = with(sharedTransitionScope) {
        modifier
            .sharedBounds(
                rememberSharedContentState(key = accountFromSettingsTransitionKey),
                animatedVisibilityScope = animatedVisibilityScope,
            )
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    }

    Scaffold(
        modifier = rootModifier,
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
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
                    .padding(24.dp)
            ) {
                val account = uiState.account
                if (account == null) {
                    Text(stringResource(R.string.account_signed_out_message))
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onSignIn,
                        enabled = uiState.isSignInAvailable && uiState.busyAction == null,
                    ) {
                        if (uiState.busyAction == AccountAction.SIGN_IN) {
                            ButtonProgressIndicator()
                        } else {
                            Text(stringResource(R.string.account_sign_in_button))
                        }
                    }

                    if (!uiState.isSignInAvailable) {
                        Text(
                            stringResource(R.string.account_play_services_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    uiState.errorReason?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it.text(), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text(
                        account.displayName ?: account.email.orEmpty(),
                        style = MaterialTheme.typography.titleMedium
                    )
                    account.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(24.dp))

                    Text(
                        stringResource(R.string.account_danger_zone_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.account_sign_out_headline))
                            Text(
                                stringResource(R.string.account_sign_out_description),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(
                            onClick = { showSignOutDialog = true },
                            enabled = uiState.busyAction == null,
                        ) {
                            if (uiState.busyAction == AccountAction.SIGN_OUT) {
                                ButtonProgressIndicator()
                            } else {
                                Text(stringResource(R.string.account_sign_out_button))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.account_delete_cloud_data_headline))
                            Text(
                                stringResource(R.string.account_delete_cloud_data_description),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            enabled = uiState.busyAction == null,
                        ) {
                            if (uiState.busyAction == AccountAction.DELETE_CLOUD_DATA) {
                                ButtonProgressIndicator()
                            } else {
                                Text(stringResource(R.string.account_delete_cloud_data_button))
                            }
                        }
                    }
                    uiState.errorReason?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it.text(), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        ConfirmDialog(
            title = stringResource(R.string.account_sign_out_dialog_title),
            text = stringResource(R.string.account_sign_out_dialog_text),
            confirmLabel = stringResource(R.string.account_sign_out_button),
            onConfirm = { showSignOutDialog = false; onSignOut() },
            dismissLabel = stringResource(R.string.account_cancel_button),
            onDismiss = { showSignOutDialog = false },
            destructive = true,
        )
    }
    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.account_delete_cloud_data_dialog_title),
            text = stringResource(R.string.account_delete_cloud_data_dialog_text),
            confirmLabel = stringResource(R.string.account_delete_cloud_data_button),
            onConfirm = { showDeleteDialog = false; onDeleteCloudData() },
            dismissLabel = stringResource(R.string.account_cancel_button),
            onDismiss = { showDeleteDialog = false },
            destructive = true,
        )
    }
}

/** Replaces a button's label with a spinner sized to sit on the same content baseline, matching
 * the button's own content color so it looks native to filled and text buttons alike. */
@Composable
private fun ButtonProgressIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = LocalContentColor.current,
    )
}