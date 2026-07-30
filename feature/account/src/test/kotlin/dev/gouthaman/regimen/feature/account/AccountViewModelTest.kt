package dev.gouthaman.regimen.feature.account

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.AuthException
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
import dev.gouthaman.regimen.domain.usecase.HasCompetingPrimaryUseCase
import dev.gouthaman.regimen.domain.usecase.LocalWorkoutCountUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveAccountStatusUseCase
import dev.gouthaman.regimen.domain.usecase.PullCloudDataUseCase
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
        deleteCloudDataUseCase = DeleteCloudDataUseCase(repo),
        ensurePrimaryClaimedUseCase = EnsurePrimaryClaimedUseCase(syncDeviceRepo),
        schedulePeriodicSyncUseCase = SchedulePeriodicSyncUseCase(syncScheduleRepo),
        cancelPeriodicSyncUseCase = CancelPeriodicSyncUseCase(syncScheduleRepo),
        syncNowUseCase = SyncNowUseCase(syncPushRepo),
        getLastSyncStatusUseCase = GetLastSyncStatusUseCase(syncPushRepo),
        hasCompetingPrimaryUseCase = HasCompetingPrimaryUseCase(syncDeviceRepo),
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
    fun `delete cloud data invokes the repo`() = runTest {
        val repo = FakeAuthRepository()
        val viewModel = viewModel(repo)

        viewModel.deleteCloudData()

        assert(repo.deleteCloudDataCalled)
    }

    @Test
    fun `delete cloud data failure with a stale session surfaces REAUTH_REQUIRED`() = runTest {
        val repo = FakeAuthRepository(
            deleteCloudDataResult = Result.failure(AuthException(AuthErrorReason.REAUTH_REQUIRED))
        )
        val viewModel = viewModel(repo)

        viewModel.deleteCloudData()

        viewModel.uiState.test {
            assertEquals(AuthErrorReason.REAUTH_REQUIRED, awaitItem().errorReason)
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
            val syncDeviceRepo = FakeSyncDeviceRepository(hasCompetingPrimaryResult = false)
            val syncScheduleRepo = FakeSyncScheduleRepository(nextScheduledSyncAtResult = null)
            val viewModel = viewModel(repo, syncDeviceRepo, syncScheduleRepo = syncScheduleRepo)

            syncDeviceRepo.hasCompetingPrimaryResult = true
            syncScheduleRepo.nextScheduledSyncAtResult = 9_000L
            viewModel.refreshOnResume()

            viewModel.uiState.test {
                val state = awaitItem()
                assertTrue(state.isSecondaryDevice)
                assertEquals(9_000L, state.nextScheduledSyncAt)
            }
        }

    @Test
    fun `loads secondary-device status on init`() = runTest {
        val repo = FakeAuthRepository()
        val syncDeviceRepo = FakeSyncDeviceRepository(hasCompetingPrimaryResult = true)
        val viewModel = viewModel(repo, syncDeviceRepo)

        viewModel.uiState.test {
            assertTrue(awaitItem().isSecondaryDevice)
        }
    }

    @Test
    fun `requesting pull cloud data populates the confirmation with both counts`() = runTest {
        val repo = FakeAuthRepository()
        val syncReplaceRepo =
            FakeSyncReplaceRepository(localWorkoutCountResult = 3, cloudWorkoutCountResult = 7)
        val viewModel = viewModel(repo, syncReplaceRepo = syncReplaceRepo)

        viewModel.requestPullCloudData()

        viewModel.uiState.test {
            val confirmation = awaitItem().pullConfirmation
            assertEquals(3, confirmation?.localWorkoutCount)
            assertEquals(7, confirmation?.cloudWorkoutCount)
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
    fun `confirming pull cloud data invokes the use case and clears the confirmation`() = runTest {
        val repo = FakeAuthRepository()
        val syncReplaceRepo = FakeSyncReplaceRepository()
        val viewModel = viewModel(repo, syncReplaceRepo = syncReplaceRepo)

        viewModel.requestPullCloudData()
        viewModel.confirmPullCloudData()

        assertTrue(syncReplaceRepo.pullCalled)
        viewModel.uiState.test {
            assertNull(awaitItem().pullConfirmation)
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
    fun `requesting claim primary populates the confirmation with both counts`() = runTest {
        val repo = FakeAuthRepository()
        val syncReplaceRepo =
            FakeSyncReplaceRepository(localWorkoutCountResult = 5, cloudWorkoutCountResult = 0)
        val viewModel = viewModel(repo, syncReplaceRepo = syncReplaceRepo)

        viewModel.requestClaimPrimary()

        viewModel.uiState.test {
            val confirmation = awaitItem().claimConfirmation
            assertEquals(5, confirmation?.localWorkoutCount)
            assertEquals(0, confirmation?.cloudWorkoutCount)
        }
    }

    @Test
    fun `confirming claim primary invokes the use case and clears secondary-device status`() =
        runTest {
            val repo = FakeAuthRepository()
            val syncDeviceRepo = FakeSyncDeviceRepository(hasCompetingPrimaryResult = true)
            val syncReplaceRepo = FakeSyncReplaceRepository()
            val viewModel = viewModel(repo, syncDeviceRepo, syncReplaceRepo = syncReplaceRepo)

            viewModel.confirmClaimPrimary()

            assertTrue(syncReplaceRepo.claimCalled)
            viewModel.uiState.test {
                assertFalse(awaitItem().isSecondaryDevice)
            }
        }

    @Test
    fun `confirming claim primary reschedules the periodic sync job`() = runTest {
        val repo = FakeAuthRepository()
        val syncReplaceRepo = FakeSyncReplaceRepository()
        val syncScheduleRepo = FakeSyncScheduleRepository()
        val viewModel =
            viewModel(repo, syncReplaceRepo = syncReplaceRepo, syncScheduleRepo = syncScheduleRepo)

        viewModel.confirmClaimPrimary()

        // A device that was secondary already had its own periodic job self-cancel - nothing
        // else re-schedules one once it becomes primary again, so this must.
        assertTrue(syncScheduleRepo.scheduleCalled)
    }

    @Test
    fun `claim primary failure surfaces its reason and does not clear secondary-device status`() =
        runTest {
            val repo = FakeAuthRepository()
            val syncDeviceRepo = FakeSyncDeviceRepository(hasCompetingPrimaryResult = true)
            val syncReplaceRepo = FakeSyncReplaceRepository(
                claimResult = Result.failure(SyncReplaceException(SyncReplaceErrorReason.PUSH_IN_PROGRESS))
            )
            val viewModel = viewModel(repo, syncDeviceRepo, syncReplaceRepo = syncReplaceRepo)

            viewModel.confirmClaimPrimary()

            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(SyncReplaceErrorReason.PUSH_IN_PROGRESS, state.replaceErrorReason)
                assertTrue(state.isSecondaryDevice)
            }
        }
}
