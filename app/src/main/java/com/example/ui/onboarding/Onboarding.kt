package com.example.ui.onboarding

/** Kurulum sihirbazı adımları. [newOnly]: yalnızca yeni kurulumlarda gösterilir. */
enum class SetupStep(val id: String, val newOnly: Boolean, val skippable: Boolean) {
    WELCOME("welcome", newOnly = true, skippable = false),
    GOOGLE("google", newOnly = true, skippable = false),
    RESTORE("restore", newOnly = true, skippable = true),
    PROFILE("profile", newOnly = true, skippable = true),
    NOTIFICATIONS("notifications", newOnly = false, skippable = true),
    BACKUP("backup", newOnly = false, skippable = true),
    UPDATES("updates", newOnly = false, skippable = true),
    BATTERY("battery", newOnly = false, skippable = true),
    DONE("done", newOnly = false, skippable = false)
}

/** Cihazın o anki izin durumu. */
data class DeviceSetupState(
    val needsNotificationPermission: Boolean,
    val needsInstallPermission: Boolean,
    val needsBatteryStep: Boolean
)

object Onboarding {

    /**
     * Gösterilecek adımlar. Yeni kurulumda hepsi; var olan kullanıcıda yalnızca yeni (henüz görmediği) adımlar.
     * Zaten verilmiş izinlerin adımları atlanır.
     */
    fun steps(newInstall: Boolean, device: DeviceSetupState): List<SetupStep> = SetupStep.entries.filter { step ->
        when {
            step.newOnly && !newInstall -> false
            step == SetupStep.NOTIFICATIONS -> device.needsNotificationPermission
            step == SetupStep.UPDATES -> device.needsInstallPermission
            step == SetupStep.BATTERY -> device.needsBatteryStep
            else -> true
        }
    }

    /** Oturum açıldıktan sonra gösterilecek, henüz tamamlanmamış adımlar. */
    fun postAuthPending(all: List<SetupStep>, done: Set<String>): List<SetupStep> {
        val pending = all.filter { it.id !in done && it != SetupStep.WELCOME && it != SetupStep.GOOGLE }
        // Yalnızca "Hazırsın" kaldıysa sihirbaz yeniden açılmaz.
        return if (pending.all { it == SetupStep.DONE }) emptyList() else pending
    }

    /** Sihirbazda bir önceki adım (oturum açma adımına geri dönülmez). */
    fun previous(all: List<SetupStep>, current: SetupStep): SetupStep? {
        val i = all.indexOf(current)
        if (i <= 0) return null
        val prev = all[i - 1]
        return if (prev == SetupStep.GOOGLE || prev == SetupStep.WELCOME) null else prev
    }

    fun next(all: List<SetupStep>, current: SetupStep): SetupStep? = all.getOrNull(all.indexOf(current) + 1)
}
