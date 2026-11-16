package dev.gouthaman.regimen.feature.account

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.AuthException
import dev.gouthaman.regimen.domain.model.SecondaryDeviceReason
import dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason
import dev.gouthaman.regimen.domain.model.SyncReplaceException
import dev.gouthaman.regimen.domain.model.SyncStatus
import dev.gouthaman.regimen.domain.usecase.CancelPeriodicSyncUseCase
import dev.gouthaman.regimen.domain.usecase.ClaimPrimaryUseCase
import dev.gouthaman.regimen.domain.usecase.CloudWorkoutCountUseCase
import dev.gouthaman.regimen.domain.usecase.DeleteCloudDataUseCase
import dev.gouthaman.regimen.domain.usecase.EnsurePrimaryClaimedUseCase
import dev.gouthaman.regimen.domain.usecase.GetLastSyncStatusUseCase
import dev.gouthaman.regimen.domain.usecase.GetNextScheduledSyncAtUseCase
import dev.gouthaman.regimen.domain.usecase.GetSecondaryDeviceReasonUseCase
import dev.gouthaman.regimen.domain.usecase.LocalWorkoutCountUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveAccountStatusUseCase
import dev.gouthaman.regimen.domain.usecase.PullCloudDataUseCase
import dev.gouthaman.regimen.domain.usecase.ReauthenticateUseCase
import dev.gouthaman.regimen.domain.usecase.SchedulePeriodicSyncUseCase
import dev.gouthaman.regimen.domain.usecase.SignInUseCase
import dev.gouthaman.regimen.domain.usecase.SignOutUseCase
import dev.gouthaman.regimen.domain.usecase.SyncNowUseCase
import dev.gouthaman.regimen.testing.FakeAuthRepository
import dev.gouthaman.regimen.testing.FakeSyncDeviceRepository
import dev.gouthaman.regimen.testing.FakeSyncPushRepository
import dev.gouthaman.regimen.testing.FakeSyncReplaceRepository
import dev.gouthaman.regimen.testing.FakeSyncScheduleRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        repo: FakeAuthRepository,
        syncDeviceRepo: FakeSyncDeviceRepository = FakeSyncDeviceRepository(),
        syncScheduleRepo: FakeSyncScheduleRepository = FakeSyncScheduleRepository(),
        syncPushRepo: FakeSyncPushRepository = FakeSyncPushRepository(),
        syncReplaceRepo: FakeSyncReplaceRepository = FakeSyncReplaceRepository(),
    ) = AccountViewModel(
        observeAccountStatus = ObserveAccountStatusUseCase(repo),
        signInUseCase = SignInUseCase(repo),
        signOutUseCase = SignOutUseCase(repo),
        reauthenticateUseCase = ReauthenticateUseCase(repo),
        deleteCloudDataUseCase = DeleteCloudDataUseCase(repo),
        ensurePrimaryClaimedUseCase = EnsurePrimaryClaimedUseCase(syncDeviceRepo),
        schedulePeriodicSyncUseCase = SchedulePeriodicSyncUseCase(syncScheduleRepo),
        cancelPeriodicSyncUseCase = CancelPeriodicSyncUseCase(syncScheduleRepo),
        syncNowUseCase = SyncNowUseCase(syncPushRepo),
        getLastSyncStatusUseCase = GetLastSyncStatusUseCase(syncPushRepo),
        getSecondaryDeviceReasonUseCase = GetSecondaryDeviceReasonUseCase(syncDeviceRepo),
        pullCloudDataUseCase = PullCloudDataUseCase(syncReplaceRepo),
        claimPrimaryUseCase = ClaimPrimaryUseCase(syncReplaceRepo),
        localWorkoutCountUseCase = LocalWorkoutCountUseCase(syncReplaceRepo),
        cloudWorkoutCountUseCase = CloudWorkoutCountUseCase(syncReplaceRepo),
        getNextScheduledSyncAtUseCase = GetNextScheduledSyncAtUseCase(syncScheduleRepo),
    )

    @Test
    fun `starts signed out`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())
        viewModel.uiState.test {
            assertNull(awaitItem().account)
        }
    }

    @Test
    fun `sign in updates state with the returned account`() = runTest {
        val account = AuthAccount(uid = "u1", email = "a@b.com", displayName = "A B")
        val repo = FakeAuthRepository(signInResult = Result.success(account))
        val viewModel = viewModel(repo)

        viewModel.signIn()

        viewModel.uiState.test {
            assertEquals(account, awaitItem().account)
        }
    }

    @Test
    fun `sign in failure with an AuthException surfaces its reason`() = runTest {
        val repo = FakeAuthRepository(
            signInResult = Result.failure(AuthException(AuthErrorReason.NO_CREDENTIALS))
        )
        val viewModel = viewModel(repo)

        viewModel.signIn()

        viewModel.uiState.test {
            assertEquals(AuthErrorReason.NO_CREDENTIALS, awaitItem().errorReason)
        }
    }

    @Test
    fun `sign in success triggers a primary-device claim`() = runTest {
        val account = AuthAccount(uid = "u1", email = "a@b.com", displayName = "A B")
        val repo = FakeAuthRepository(signInResult = Result.success(account))
        val syncDeviceRepo = FakeSyncDeviceRepository()
        val viewModel = viewModel(repo, syncDeviceRepo)

        viewModel.signIn()

        assertTrue(syncDeviceRepo.ensurePrimaryClaimedCalled)
    }

    @Test
    fun `sign in failure does not trigger a primary-device claim`() = runTest {
        val repo = FakeAuthRepository(signInResult = Result.failure(RuntimeException("boom")))
        val syncDeviceRepo = FakeSyncDeviceRepository()
        val viewModel = viewModel(repo, syncDeviceRepo)

        viewModel.signIn()

        assertEquals(false, syncDeviceRepo.ensurePrimaryClaimedCalled)
    }

    @Test
    fun `sign in success schedules the periodic sync job`() = runTest {
        val account = AuthAccount(uid = "u1", email = "a@b.com", displayName = "A B")
        val repo = FakeAuthRepository(signInResult = Result.success(account))
        val syncScheduleRepo = FakeSyncScheduleRepository()
        val viewModel = viewModel(repo, syncScheduleRepo = syncScheduleRepo)

        viewModel.signIn()

        assertTrue(syncScheduleRepo.scheduleCalled)
    }

    @Test
    fun `sign in failure does not schedule the periodic sync job`() = runTest {
        val repo = FakeAuthRepository(signInResult = Result.failure(RuntimeException("boom")))
        val syncScheduleRepo = FakeSyncScheduleRepository()
        val viewModel = viewModel(repo, syncScheduleRepo = syncScheduleRepo)

        viewModel.signIn()

        assertEquals(false, syncScheduleRepo.scheduleCalled)
    }

    @Test
    fun `sign in failure with an untyped exception falls back to UNKNOWN`() = runTest {
        val repo = FakeAuthRepository(signInResult = Result.failure(RuntimeException("boom")))
        val viewModel = viewModel(repo)

        viewModel.signIn()

        viewModel.uiState.test {
            assertEquals(AuthErrorReason.UNKNOWN, awaitItem().errorReason)
        }
    }

    @Test
    fun `sign out clears the account`() = runTest {
        val account = AuthAccount(uid = "u1", email = "a@b.com", displayName = "A B")
        val repo = FakeAuthRepository().apply { seedSignedIn(account) }
        val viewModel = viewModel(repo)

        viewModel.signOut()

        viewModel.uiState.test {
            assertNull(awaitItem().account)
        }
        assert(repo.signOutCalled)
    }

    @Test
    fun `sign out cancels the periodic sync job`() = runTest {
        val account = AuthAccount(uid = "u1", email = "a@b.com", displayName = "A B")
        val repo = FakeAuthRepository().apply { seedSignedIn(account) }
        val syncScheduleRepo = FakeSyncScheduleRepository()
        val viewModel = viewModel(repo, syncScheduleRepo = syncScheduleRepo)

        viewModel.signOut()

        assertTrue(syncScheduleRepo.cancelCalled)
    }

    @Test
    fun `delete cloud data re-authenticates, then deletes`() = runTest {
        val repo = FakeAuthRepository()
        val viewModel = viewModel(repo)

        viewModel.deleteCloudData()

        assertTrue(repo.reauthenticateCalled)
        assertTrue(repo.deleteCloudDataCalled)
    }

    @Test
    fun `delete cloud data with the wrong account surfaces it and deletes nothing`() = runTest {
        val repo = FakeAuthRepository(
            reauthenticateResult = Result.failure(AuthException(AuthErrorReason.WRONG_ACCOUNT))
        )
        val viewModel = viewModel(repo)

        viewModel.deleteCloudData()

        assertFalse(repo.deleteCloudDataCalled)
        viewModel.uiState.test {
            assertEquals(AuthErrorReason.WRONG_ACCOUNT, awaitItem().errorReason)
        }
    }

    @Test
    fun `dismissing the re-authentication sheet deletes nothing and shows no error`() = runTest {
        val repo = FakeAuthRepository(
            reauthenticateResult = Result.failure(AuthException(AuthErrorReason.CANCELLED))
        )
        val viewModel = viewModel(repo)

        viewModel.deleteCloudData()

        assertFalse(repo.deleteCloudDataCalled)
        viewModel.uiState.test {
            assertNull(awaitItem().errorReason)
        }
    }

    @Test
    fun `a delete whose account removal needs a newer sign-in surfaces REAUTH_REQUIRED`() =
        runTest {
            val repo = FakeAuthRepository(
                deleteCloudDataResult = Result.failure(AuthException(AuthErrorReason.REAUTH_REQUIRED))
            )
            val viewModel = viewModel(repo)

            viewModel.deleteCloudData()

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(SyncReplaceErrorReason.REAUTH_REQUIRED, state.replaceErrorReason)
                assertNull(state.runningOperation)
            }
        }

    @Test
    fun `loads the persisted sync status on init`() = runTest {
        val repo = FakeAuthRepository()
        val lastStatus = SyncStatus(lastSyncedAt = 1_000L, isFullyUpToDate = true, lastError = null)
        val syncPushRepo = FakeSyncPushRepository(lastStatus = lastStatus)
        val viewModel = viewModel(repo, syncPushRepo = syncPushRepo)

        viewModel.uiState.test {
            assertEquals(lastStatus, awaitItem().syncStatus)
        }
    }

    @Test
    fun `sync now invokes the push use case and stores its result`() = runTest {
        val repo = FakeAuthRepository()
        val status = SyncStatus(lastSyncedAt = 1_000L, isFullyUpToDate = true, lastError = null)
        val syncPushRepo = FakeSyncPushRepository(status)
        val viewModel = viewModel(repo, syncPushRepo = syncPushRepo)

        viewModel.syncNow()

        assertTrue(syncPushRepo.pushCalled)
        viewModel.uiState.test {
            assertEquals(status, awaitItem().syncStatus)
        }
    }

    @Test
    fun `sync now does not overwrite a real prior status with the not-primary no-op`() = runTest {
        val repo = FakeAuthRepository()
        val lastStatus = SyncStatus(lastSyncedAt = 1_000L, isFullyUpToDate = true, lastError = null)
        val notPrimaryResult =
            SyncStatus(lastSyncedAt = null, isFullyUpToDate = false, lastError = null)
        val syncPushRepo =
            FakeSyncPushRepository(result = notPrimaryResult, lastStatus = lastStatus)
        val viewModel = viewModel(repo, syncPushRepo = syncPushRepo)

        viewModel.syncNow()

        assertTrue(syncPushRepo.pushCalled)
        viewModel.uiState.test {
            assertEquals(lastStatus, awaitItem().syncStatus)
        }
    }

    @Test
    fun `loads the next scheduled sync time on init`() = runTest {
        val repo = FakeAuthRepository()
        val syncScheduleRepo = FakeSyncScheduleRepository(nextScheduledSyncAtResult = 5_000L)
        val viewModel = viewModel(repo, syncScheduleRepo = syncScheduleRepo)

        viewModel.uiState.test {
            assertEquals(5_000L, awaitItem().nextScheduledSyncAt)
        }
    }

    @Test
    fun `refreshing on resume updates secondary-device status and next scheduled sync time`() =
        runTest {
            val repo = FakeAuthRepository()
            val syncDeviceRepo = FakeSyncDeviceRepository(secondaryDeviceReasonResult = null)
            val syncScheduleRepo = FakeSyncScheduleRepository(nextScheduledSyncAtResult = null)
            val viewModel = viewModel(repo, syncDeviceRepo, syncScheduleRepo = syncScheduleRepo)

            syncDeviceRepo.secondaryDeviceReasonResult = SecondaryDeviceReason.COMPETING_PRIMARY
            syncScheduleRepo.nextScheduledSyncAtResult = 9_000L
            viewModel.refreshOnResume()

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(SecondaryDeviceReason.COMPETING_PRIMARY, state.secondaryDeviceReason)
                assertEquals(9_000L, state.nextScheduledSyncAt)
            }
        }

    @Test
    fun `loads secondary-device status on init`() = runTest {
        val repo = FakeAuthRepository()
        val syncDeviceRepo =
            FakeSyncDeviceRepository(secondaryDeviceReasonResult = SecondaryDeviceReason.COMPETING_PRIMARY)
        val viewModel = viewModel(repo, syncDeviceRepo)

        viewModel.uiState.test {
            assertEquals(SecondaryDeviceReason.COMPETING_PRIMARY, awaitItem().secondaryDeviceReason)
        }
    }

    @Test
    fun `requesting pull cloud data opens the confirmation and fills in the cloud count`() =
        runTest {
            val repo = FakeAuthRepository()
            val syncReplaceRepo =
                FakeSyncReplaceRepository(cloudWorkoutCountResult = Result.success(7))
            val viewModel = viewModel(repo, syncReplaceRepo = syncReplaceRepo)

            viewModel.requestPullCloudData()

            viewModel.uiState.test {
                assertEquals(ReplaceConfirmation(workoutCount = 7), awaitItem().pullConfirmation)
            }
        }

    @Test
    fun `a failed cloud count closes the pull confirmation with its reason`() = runTest {
        val repo = FakeAuthRepository()
        val syncReplaceRepo = FakeSyncReplaceRepository(
            cloudWorkoutCountResult = Result.failure(
                SyncReplaceException(SyncReplaceErrorReason.NETWORK)
            )
        )
        val viewModel = viewModel(repo, syncReplaceRepo = syncReplaceRepo)

        viewModel.requestPullCloudData()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.pullConfirmation)
            assertEquals(SyncReplaceErrorReason.NETWORK, state.replaceErrorReason)
        }
    }

    @Test
    fun `dismissing the pull confirmation clears it without calling pull`() = runTest {
        val repo = FakeAuthRepository()
        val syncReplaceRepo = FakeSyncReplaceRepository()
        val viewModel = viewModel(repo, syncReplaceRepo = syncReplaceRepo)

        viewModel.requestPullCloudData()
        viewModel.dismissPullConfirmation()

        assertFalse(syncReplaceRepo.pullCalled)
        viewModel.uiState.test {
            assertNull(awaitItem().pullConfirmation)
        }
    }

    @Test
    fun `confirming pull cloud data runs the pull and clears the confirmation`() = runTest {
        val repo = FakeAuthRepository()
        val syncReplaceRepo = FakeSyncReplaceRepository()
        val viewModel = viewModel(repo, syncReplaceRepo = syncReplaceRepo)

        viewModel.requestPullCloudData()
        viewModel.confirmPullCloudData()

        assertTrue(syncReplaceRepo.pullCalled)
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.pullConfirmation)
            assertNull(state.runningOperation)
        }
    }

    @Test
    fun `pull cloud data failure surfaces its reason`() = runTest {
        val repo = FakeAuthRepository()
        val syncReplaceRepo = FakeSyncReplaceRepository(
            pullResult = Result.failure(SyncReplaceException(SyncReplaceErrorReason.WORKOUT_IN_PROGRESS))
        )
        val viewModel = viewModel(repo, syncReplaceRepo = syncReplaceRepo)

        viewModel.confirmPullCloudData()

        viewModel.uiState.test {
            assertEquals(SyncReplaceErrorReason.WORKOUT_IN_PROGRESS, awaitItem().replaceErrorReason)
        }
    }

    @Test
    fun `requesting claim primary fills in the local workout count`() = runTest {
        val repo = FakeAuthRepository()
        val syncReplaceRepo = FakeSyncReplaceRepository(localWorkoutCountResult = 5)
        val viewModel = viewModel(repo, syncReplaceRepo = syncReplaceRepo)

        viewModel.requestClaimPrimary()

        viewModel.uiState.test {
            assertEquals(ReplaceConfirmation(workoutCount = 5), awaitItem().claimConfirmation)
        }
    }

    @Test
    fun `confirming claim primary runs the claim and clears secondary-device status`() = runTest {
        val repo = FakeAuthRepository()
        val syncDeviceRepo =
            FakeSyncDeviceRepository(secondaryDeviceReasonResult = SecondaryDeviceReason.COMPETING_PRIMARY)
        val syncReplaceRepo = FakeSyncReplaceRepository()
        val viewModel = viewModel(repo, syncDeviceRepo, syncReplaceRepo = syncReplaceRepo)
        syncDeviceRepo.secondaryDeviceReasonResult = null

        viewModel.confirmClaimPrimary()

        assertTrue(syncReplaceRepo.claimCalled)
        viewModel.uiState.test {
            assertNull(awaitItem().secondaryDeviceReason)
        }
    }

    @Test
    fun `confirming claim primary reschedules the periodic sync job`() = runTest {
        val repo = FakeAuthRepository()
        val syncScheduleRepo = FakeSyncScheduleRepository()
        val viewModel = viewModel(repo, syncScheduleRepo = syncScheduleRepo)

        viewModel.confirmClaimPrimary()

        // A device that was secondary already had its own periodic job self-cancel - nothing
        // else re-schedules one once it becomes primary again, so this must.
        assertTrue(syncScheduleRepo.scheduleCalled)
    }

    @Test
    fun `claim primary failure surfaces its reason and does not reschedule`() = runTest {
        val repo = FakeAuthRepository()
        val syncDeviceRepo =
            FakeSyncDeviceRepository(secondaryDeviceReasonResult = SecondaryDeviceReason.COMPETING_PRIMARY)
        val syncScheduleRepo = FakeSyncScheduleRepository()
        val syncReplaceRepo = FakeSyncReplaceRepository(
            claimResult = Result.failure(SyncReplaceException(SyncReplaceErrorReason.PUSH_IN_PROGRESS))
        )
        val viewModel = viewModel(
            repo,
            syncDeviceRepo,
            syncScheduleRepo = syncScheduleRepo,
            syncReplaceRepo = syncReplaceRepo
        )

        viewModel.confirmClaimPrimary()

        assertFalse(syncScheduleRepo.scheduleCalled)
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(SyncReplaceErrorReason.PUSH_IN_PROGRESS, state.replaceErrorReason)
            assertEquals(SecondaryDeviceReason.COMPETING_PRIMARY, state.secondaryDeviceReason)
        }
    }
}
