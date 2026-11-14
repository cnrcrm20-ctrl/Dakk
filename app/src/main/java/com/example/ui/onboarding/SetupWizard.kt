package com.example.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.DakkViewModel
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkDarkBorder
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow
import com.example.util.DeviceTweaks

/** Cihazın şu anki izin durumuna göre sihirbaz adımları. */
fun deviceSetupState(context: android.content.Context): DeviceSetupState = DeviceSetupState(
    needsNotificationPermission = DeviceTweaks.needsNotificationPermission(context),
    needsInstallPermission = !DeviceTweaks.canInstallPackages(context),
    needsBatteryStep = DeviceTweaks.isXiaomiFamily() || !DeviceTweaks.isIgnoringBatteryOptimizations(context)
)

/** Üstte ilerleme noktaları, solda "Geri", sağda "Atla". */
@Composable
fun WizardTopBar(total: Int, index: Int, onBack: (() -> Unit)?, onSkip: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(88.dp)) {
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DakkTextSecondaryDark, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Geri", color = DakkTextSecondaryDark)
                }
            }
        }
        ProgressDots(total, index, Modifier.weight(1f))
        Box(Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) {
            if (onSkip != null) {
                TextButton(onClick = onSkip) { Text("Atla", color = DakkTextSecondaryDark, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
fun ProgressDots(total: Int, index: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val width by animateDpAsState(if (i == index) 20.dp else 7.dp, tween(250), label = "dot")
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width, height = 7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            i == index -> DakkYellow
                            i < index -> DakkYellow.copy(alpha = 0.45f)
                            else -> DakkDarkBorder
                        }
                    )
            )
        }
    }
}

/** Yeni kurulumun ilk ekranı (oturum açmadan önce). */
@Composable
fun WelcomeStep(steps: List<SetupStep>, onStart: () -> Unit) {
    StepFrame(
        top = { WizardTopBar(steps.size, steps.indexOf(SetupStep.WELCOME).coerceAtLeast(0), null, null) },
        primary = "Başlayalım",
        onPrimary = onStart
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(3.dp, DakkYellow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.dakk_logo), contentDescription = "Dakk", modifier = Modifier.size(94.dp))
        }
        Spacer(Modifier.height(28.dp))
        StepTitle("Dakk'a hoş geldin 👋")
        StepText(
            "Hızlı, sade ve uçtan uca şifreli mesajlaşma. Mesajların telefonunda durur; sunucu yalnızca " +
                "ulaştırana kadar tutar. Birkaç kısa adımda hazırız."
        )
    }
}

/** Kurulum sihirbazı: oturum açıldıktan sonraki adımlar. */
@Composable
fun SetupWizard(viewModel: DakkViewModel, steps: List<SetupStep>, start: SetupStep) {
    var currentId by rememberSaveable { mutableStateOf(start.id) }
    val current = steps.firstOrNull { it.id == currentId } ?: start
    val index = steps.indexOf(current)

    fun advance() {
        viewModel.markWizardStep(current.id)
        val next = Onboarding.next(steps, current)
        if (next == null) viewModel.finishWizard(SetupStep.entries.map { it.id }) else currentId = next.id
    }

    val back = Onboarding.previous(steps, current)?.let { prev -> { currentId = prev.id } }
    BackHandler(enabled = back != null) { back?.invoke() }
    val skip = if (current.skippable) ({ advance() }) else null

    AnimatedContent(
        targetState = current,
        transitionSpec = {
            val forward = steps.indexOf(targetState) >= steps.indexOf(initialState)
            (slideInHorizontally(tween(260)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(260)))
                .togetherWith(slideOutHorizontally(tween(200)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
        },
        label = "wizard"
    ) { step ->
        val top: @Composable () -> Unit = { WizardTopBar(steps.size, index, back, skip) }
        when (step) {
            SetupStep.RESTORE -> RestoreStep(viewModel, top, ::advance)
            SetupStep.PROFILE -> ProfileStep(viewModel, top, ::advance)
            SetupStep.NOTIFICATIONS -> NotificationStep(top, ::advance)
            SetupStep.BACKUP -> BackupStep(viewModel, top, ::advance)
            SetupStep.UPDATES -> UpdatesStep(top, ::advance)
            SetupStep.BATTERY -> BatteryStep(top, ::advance)
            SetupStep.DONE -> DoneStep(viewModel.isNewInstall, top, ::advance)
            SetupStep.WELCOME, SetupStep.GOOGLE -> Unit
        }
    }
}

@Composable
private fun RestoreStep(viewModel: DakkViewModel, top: @Composable () -> Unit, onNext: () -> Unit) {
    val requested by viewModel.restorePending.collectAsStateWithLifecycle()
    StepFrame(top = top, primary = "Devam", onPrimary = onNext) {
        StepEmoji(if (requested) "🔄" else "✨")
        StepTitle(if (requested) "Sohbet geçmişin yolda" else "Tertemiz bir başlangıç")
        StepText(
            if (requested) {
                "Bu hesabın yeni bir telefona kurulduğunu fark ettik. Sohbet yedeği açık olan arkadaşlarının " +
                    "telefonları, Dakk'ı açtıklarında ortak sohbetlerinizi sana şifreli olarak otomatik geri gönderir. " +
                    "Sohbeti açtığında geçmiş yerine oturur; senin bir şey yapmana gerek yok."
            } else {
                "Bu telefonda geri yüklenecek bir geçmiş yok. Sohbetlerin bundan sonra telefonunda saklanacak."
            }
        )
        if (requested) {
            Spacer(Modifier.height(16.dp))
            InfoCard("🔐", "Güvenlik numaran değişti. Arkadaşların sohbette küçük bir not görecek; bu yeniden kurulumda normaldir.")
        }
    }
}

@Composable
private fun ProfileStep(viewModel: DakkViewModel, top: @Composable () -> Unit, onNext: () -> Unit) {
    val me by viewModel.myProfile.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val initial = me?.displayName?.ifBlank { null } ?: user?.displayName.orEmpty()
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    StepFrame(
        top = top,
        primary = "Kaydet ve devam et",
        primaryEnabled = name.isNotBlank(),
        onPrimary = {
            if (name.trim() != initial.trim()) viewModel.updateDisplayName(name)
            onNext()
        }
    ) {
        StepEmoji("🙂")
        StepTitle("Seni nasıl görelim?")
        StepText("Arkadaşların seni bu adla görür. Sonra Ayarlar'dan değiştirebilirsin.")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 60) name = it },
            singleLine = true,
            label = { Text("Görünen ad") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DakkYellow,
                cursorColor = DakkYellow,
                focusedLabelColor = DakkYellow
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NotificationStep(top: @Composable () -> Unit, onNext: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onNext() }
    StepFrame(
        top = top,
        primary = "Bildirimlere izin ver",
        onPrimary = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else onNext()
        },
        secondary = "Şimdi değil",
        onSecondary = onNext
    ) {
        StepEmoji("🔔")
        StepTitle("Mesajlar kaçmasın")
        StepText(
            "Arkadaşların yazınca haber verelim mi? Bildirimdeki mesaj yalnızca senin telefonunda çözülür; " +
                "Dakk ve sunucu içeriğini göremez."
        )
        Spacer(Modifier.height(16.dp))
        InfoCard("💡", "Birazdan Android küçük bir izin penceresi açacak. \"İzin ver\"e dokunman yeterli.")
    }
}

@Composable
private fun BackupStep(viewModel: DakkViewModel, top: @Composable () -> Unit, onNext: () -> Unit) {
    val enabled by viewModel.chatBackup.collectAsStateWithLifecycle()
    StepFrame(top = top, primary = "Devam", onPrimary = onNext) {
        StepEmoji("💛")
        StepTitle("Sohbet yedeği")
        StepText(
            "Telefonunu değiştirir ya da Dakk'ı yeniden kurarsan, arkadaşlarının telefonları ortak sohbetlerinizi " +
                "sana otomatik ve şifreli olarak geri gönderir. Sen de onlar için aynısını yaparsın. Kimseye ayrıca " +
                "sorulmaz; sunucu içeriği göremez."
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DakkDarkSurface)
                .clickable { viewModel.setChatBackup(!enabled) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Sohbet yedeği", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(if (enabled) "Açık · önerilir" else "Kapalı", color = DakkTextSecondaryDark, fontSize = 13.sp)
            }
            Switch(
                checked = enabled,
                onCheckedChange = { viewModel.setChatBackup(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = DakkYellow, checkedThumbColor = Color.Black)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Ayarlar → Sohbet yedeği'nden istediğin zaman kapatabilirsin. Ayrıntılar Gizlilik Politikası'nda.",
            color = DakkTextTertiaryDark,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun UpdatesStep(top: @Composable () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(DeviceTweaks.canInstallPackages(context)) }
    LifecycleResumeEffect(Unit) {
        granted = DeviceTweaks.canInstallPackages(context)
        onPauseOrDispose { }
    }
    StepFrame(
        top = top,
        primary = if (granted) "İzin verildi ✓ Devam" else "İzni aç",
        onPrimary = {
            if (granted) onNext() else DeviceTweaks.open(context, DeviceTweaks.installPermissionIntent(context))
        },
        secondary = if (granted) null else "Şimdi değil",
        onSecondary = onNext
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(3.dp, DakkYellow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.dakk_logo), contentDescription = null, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(18.dp))
        StepTitle("Dakk Güncelleyici")
        StepText(
            "Yeni sürümler tek dokunuşla gelsin. Dakk Güncelleyici yalnızca resmi Dakk imzalı sürümleri kurar; " +
                "Android 12 ve üzerinde kurulum için ayrıca onay bile sormaz."
        )
        Spacer(Modifier.height(16.dp))
        InfoCard("🛡️", "Açılan ekranda \"Bu kaynaktan izin ver\" anahtarını aç, sonra geri dön.")
    }
}

@Composable
private fun BatteryStep(top: @Composable () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(DeviceTweaks.isIgnoringBatteryOptimizations(context)) }
    LifecycleResumeEffect(Unit) {
        exempt = DeviceTweaks.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }
    val xiaomi = remember { DeviceTweaks.isXiaomiFamily() }
    StepFrame(top = top, primary = "Devam", onPrimary = onNext) {
        StepEmoji("🔋")
        StepTitle("Mesajlar gecikmesin")
        StepText("Pil tasarrufu bazen bildirimleri geciktirir. Dakk çok az pil kullanır; muaf tutman yeterli.")
        Spacer(Modifier.height(18.dp))
        if (exempt) {
            InfoCard("✅", "Dakk pil optimizasyonundan muaf.")
        } else {
            OutlinedButton(
                onClick = { DeviceTweaks.open(context, DeviceTweaks.batteryOptimizationIntent(context)) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Pil optimizasyonundan muaf tut", color = DakkYellow, fontWeight = FontWeight.SemiBold) }
        }
        if (xiaomi) {
            Spacer(Modifier.height(16.dp))
            AutostartGuide()
        }
    }
}

/** Xiaomi / Redmi / POCO için otomatik başlatma rehberi. */
@Composable
fun AutostartGuide() {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DakkDarkSurface)
            .padding(16.dp)
    ) {
        Text("📱 Xiaomi · Redmi · POCO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        listOf(
            "Aşağıdaki düğmeye dokun.",
            "Listede Dakk'ı bul ve \"Otomatik başlat\" anahtarını aç.",
            "Ayrıca Pil tasarrufu → Dakk → \"Kısıtlama yok\" seç.",
            "Geri dönüp Devam'a dokun."
        ).forEachIndexed { i, line ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text("${i + 1}.", color = DakkYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(22.dp))
                Text(line, color = DakkTextSecondaryDark, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { DeviceTweaks.openFirst(context, DeviceTweaks.autostartIntents(context)) },
            colors = ButtonDefaults.buttonColors(containerColor = DakkYellow.copy(alpha = 0.18f), contentColor = DakkYellow),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Otomatik başlatmayı aç", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun DoneStep(newInstall: Boolean, top: @Composable () -> Unit, onNext: () -> Unit) {
    StepFrame(top = top, primary = if (newInstall) "Dakk'ı aç" else "Tamam", onPrimary = onNext) {
        StepEmoji("🎉")
        StepTitle("Hazırsın!")
        StepText(
            if (newInstall) "Şimdi sana 30 saniyelik kısa bir tur gösterelim. İstersen Ayarlar'dan tekrar izleyebilirsin."
            else "Yeni ayarların kaydedildi. İzinleri istediğin zaman Ayarlar → İzinler'den değiştirebilirsin."
        )
    }
}

@Composable
private fun StepFrame(
    top: @Composable () -> Unit,
    primary: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DakkDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        top()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(primary, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            if (secondary != null) {
                TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                    Text(secondary, color = DakkTextSecondaryDark)
                }
            }
        }
    }
}

@Composable
private fun StepEmoji(emoji: String) {
    Box(
        Modifier
            .size(104.dp)
            .clip(CircleShape)
            .background(DakkYellow.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) { Text(emoji, fontSize = 52.sp) }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun StepTitle(text: String) {
    Text(text, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun StepText(text: String) {
    Text(text, color = DakkTextSecondaryDark, fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
}

@Composable
fun InfoCard(emoji: String, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DakkDarkSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Text(text, color = DakkTextSecondaryDark, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** Sistem izin penceresinden önce gösterilen sevimli açıklama kartı. */
@Composable
fun PermissionExplainer(
    emoji: String,
    title: String,
    text: String,
    confirm: String = "İzin ver",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DakkDarkSurface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(DakkYellow.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 40.sp) }
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(text, color = DakkTextSecondaryDark, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text(confirm, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Şimdi değil", color = DakkTextSecondaryDark)
            }
        }
    }
}
