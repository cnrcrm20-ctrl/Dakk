package com.example.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {

    private val allNeeded = DeviceSetupState(needsNotificationPermission = true, needsInstallPermission = true, needsBatteryStep = true)
    private val nothingNeeded = DeviceSetupState(needsNotificationPermission = false, needsInstallPermission = false, needsBatteryStep = false)

    @Test
    fun newInstallSeesEverything() {
        assertEquals(SetupStep.entries.toList(), Onboarding.steps(newInstall = true, device = allNeeded))
    }

    @Test
    fun existingUserSeesOnlyNewSteps() {
        val steps = Onboarding.steps(newInstall = false, device = nothingNeeded)
        assertEquals(listOf(SetupStep.BACKUP, SetupStep.DONE), steps)
        assertTrue(steps.none { it.newOnly })
    }

    @Test
    fun pendingAfterSignIn() {
        val steps = Onboarding.steps(newInstall = true, device = nothingNeeded)
        val pending = Onboarding.postAuthPending(steps, setOf("welcome"))
        assertEquals(SetupStep.RESTORE, pending.first())
        // Hepsi bittiyse sihirbaz açılmaz.
        assertTrue(Onboarding.postAuthPending(steps, SetupStep.entries.map { it.id }.toSet()).isEmpty())
        // Yalnızca "Hazırsın" kaldıysa da açılmaz.
        val allButDone = SetupStep.entries.filter { it != SetupStep.DONE }.map { it.id }.toSet()
        assertTrue(Onboarding.postAuthPending(steps, allButDone).isEmpty())
    }

    @Test
    fun backNeverReturnsToSignIn() {
        val steps = Onboarding.steps(newInstall = true, device = allNeeded)
        assertNull(Onboarding.previous(steps, SetupStep.RESTORE))
        assertEquals(SetupStep.RESTORE, Onboarding.previous(steps, SetupStep.PROFILE))
        assertNull(Onboarding.next(steps, SetupStep.DONE))
    }
}
