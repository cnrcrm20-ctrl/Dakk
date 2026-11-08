package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.config.Announcement
import com.example.config.ConfigRepository
import com.example.ui.DakkViewModel
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkDarkSurfaceVariant
import com.example.ui.theme.DakkStatusFailed
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// -------------------------------------------------------------------------------------------
// Şema modeli — panelin içeriği uygulamada değil, Firestore'daki app_config/admin_panel
// belgesinde (kaynağı: depodaki cloud/admin_panel.json) tanımlıdır. Uygulama yalnızca çizer.
// -------------------------------------------------------------------------------------------

private data class PanelOption(val value: String, val label: String)

private data class PanelField(
    val key: String,
    val doc: String,
    val type: String,
    val label: String,
    val help: String,
    val placeholder: String,
    val default: Any?,
    val options: List<PanelOption>
) {
    val id get() = "$doc:$key"
}

private data class PanelSection(val title: String, val description: String, val fields: List<PanelField>)

private data class PanelSchema(val title: String, val subtitle: String, val sections: List<PanelSection>)

private fun parseSchema(json: String): PanelSchema {
    val root = JSONObject(json)
    val sections = root.optJSONArray("sections") ?: JSONArray()
    return PanelSchema(
        title = root.optString("title", "Kurucu Paneli"),
        subtitle = root.optString("subtitle"),
        sections = (0 until sections.length()).mapNotNull { i ->
            val s = sections.optJSONObject(i) ?: return@mapNotNull null
            val fields = s.optJSONArray("fields") ?: JSONArray()
            PanelSection(
                title = s.optString("title"),
                description = s.optString("description"),
                fields = (0 until fields.length()).mapNotNull { j ->
                    val f = fields.optJSONObject(j) ?: return@mapNotNull null
                    val opts = f.optJSONArray("options") ?: JSONArray()
                    PanelField(
                        key = f.optString("key"),
                        doc = f.optString("doc", ConfigRepository.DOC_PUBLIC)
                            .takeIf { it == ConfigRepository.DOC_PUBLIC || it == ConfigRepository.DOC_PRIVATE }
                            ?: ConfigRepository.DOC_PUBLIC,
                        type = f.optString("type", "text"),
                        label = f.optString("label"),
                        help = f.optString("help"),
                        placeholder = f.optString("placeholder"),
                        default = f.opt("default")?.takeIf { it != JSONObject.NULL },
                        options = (0 until opts.length()).mapNotNull { k ->
                            opts.optJSONObject(k)?.let { o -> PanelOption(o.optString("value"), o.optString("label")) }
                        }
                    )
                }
            )
        }
    )
}

private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("tr-TR"))

private fun formatDate(ms: Long): String = if (ms <= 0) "" else dateFormat.format(Date(ms))
private fun parseDate(text: String): Long? =
    if (text.isBlank()) 0L else runCatching { dateFormat.parse(text.trim())?.time }.getOrNull()

// -------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FounderPanelScreen(viewModel: DakkViewModel) {
    val repo = viewModel.configRepo
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var schema by remember { mutableStateOf<PanelSchema?>(null) }
    var needsBootstrap by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    val values = remember { mutableStateMapOf<String, Any?>() }
    val original = remember { mutableStateMapOf<String, Any?>() }

    BackHandler { viewModel.closeSecondaryScreen() }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        try {
            val json = repo.loadPanelSchema()
            if (json == null) {
                needsBootstrap = true
                schema = null
            } else {
                needsBootstrap = false
                schema = parseSchema(json)
                val pub = repo.loadRaw(ConfigRepository.DOC_PUBLIC)
                val priv = repo.loadRaw(ConfigRepository.DOC_PRIVATE)
                values.clear()
                original.clear()
                schema?.sections?.flatMap { it.fields }?.forEach { f ->
                    if (f.key.isBlank()) return@forEach
                    val source = if (f.doc == ConfigRepository.DOC_PRIVATE) priv else pub
                    val v = source[f.key] ?: f.default
                    values[f.id] = v
                    original[f.id] = source[f.key]
                }
            }
        } catch (e: Exception) {
            error = "Panel yüklenemedi: ${e.message ?: "bilinmeyen hata"}"
        }
        loading = false
    }

    fun installSchema() {
        scope.launch {
            loading = true
            try {
                val json = repo.downloadPanelSchema()
                parseSchema(json)
                repo.savePanelSchema(json)
                message = "Panel şeması güncellendi"
                reloadKey++
            } catch (e: Exception) {
                error = "Panel kurulamadı: ${e.message ?: "bilinmeyen hata"}"
                loading = false
            }
        }
    }

    fun changedByDoc(): Map<String, Map<String, Any?>> {
        val fields = schema?.sections?.flatMap { it.fields }.orEmpty()
            .filter { it.type !in setOf("announcement", "stat", "note") && it.key.isNotBlank() }
        return fields.filter { values[it.id] != original[it.id] }
            .groupBy { it.doc }
            .mapValues { (_, list) -> list.associate { it.key to values[it.id] } }
    }

    fun save() {
        scope.launch {
            saving = true
            try {
                changedByDoc().forEach { (doc, map) -> repo.saveValues(doc, map) }
                values.forEach { (k, v) -> original[k] = v }
                viewModel.onFounderConfigSaved()
                message = "Kaydedildi. Değişiklikler tüm kullanıcılara birkaç saniye içinde ulaşır."
            } catch (e: Exception) {
                message = "Kaydedilemedi: ${e.message ?: "bilinmeyen hata"}"
            }
            saving = false
        }
    }

    val dirty = schema != null && changedByDoc().isNotEmpty()

    Scaffold(
        containerColor = DakkDarkBg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeSecondaryScreen() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                title = { Text(schema?.title ?: "Kurucu Paneli", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Diğer") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Paneli GitHub'dan güncelle") },
                            leadingIcon = { Icon(Icons.Default.CloudDownload, null) },
                            onClick = { menuOpen = false; installSchema() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DakkDarkSurface)
            )
        },
        bottomBar = {
            if (schema != null) {
                Surface(color = DakkDarkSurface) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (dirty) "Kaydedilmemiş değişiklikler var" else "Tüm değişiklikler kayıtlı",
                            color = if (dirty) DakkYellow else DakkTextSecondaryDark,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { save() },
                            enabled = dirty && !saving,
                            colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black)
                        ) { Text(if (saving) "Kaydediliyor…" else "Kaydet") }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loading -> CircularProgressIndicator(color = DakkYellow, modifier = Modifier.align(Alignment.Center))
                error != null -> CenterMessage(error!!, "Tekrar dene") { reloadKey++ }
                needsBootstrap -> CenterMessage(
                    "Kurucu paneli henüz kurulmamış. Panel tanımı depodaki cloud/admin_panel.json dosyasından indirilip Firestore'a yazılacak.",
                    "Paneli kur"
                ) { installSchema() }
                else -> schema?.let { s ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (s.subtitle.isNotBlank()) Text(s.subtitle, color = DakkTextSecondaryDark, fontSize = 13.sp)
                        message?.let {
                            Surface(color = DakkYellow.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
                                Text(it, color = DakkYellow, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                            }
                        }
                        s.sections.forEach { section ->
                            SectionCard(section) { field ->
                                FieldEditor(
                                    field = field,
                                    value = values[field.id],
                                    viewModel = viewModel,
                                    repo = repo,
                                    onChange = { values[field.id] = it },
                                    onAnnouncementSaved = { saved ->
                                        values[field.id] = saved
                                        original[field.id] = saved
                                        message = "Duyuru kaydedildi"
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = DakkTextSecondaryDark, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black)
        ) { Text(action) }
    }
}

@Composable
private fun SectionCard(section: PanelSection, content: @Composable (PanelField) -> Unit) {
    Surface(color = DakkDarkSurface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(section.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (section.description.isNotBlank()) {
                Text(section.description, color = DakkTextSecondaryDark, fontSize = 13.sp)
            }
            section.fields.forEach { content(it) }
        }
    }
}

@Composable
private fun panelFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DakkYellow,
    focusedLabelColor = DakkYellow,
    cursorColor = DakkYellow
)

@Composable
private fun FieldEditor(
    field: PanelField,
    value: Any?,
    viewModel: DakkViewModel,
    repo: ConfigRepository,
    onChange: (Any?) -> Unit,
    onAnnouncementSaved: (Map<String, Any>) -> Unit
) {
    when (field.type) {
        "switch" -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(field.label, color = Color.White, fontSize = 15.sp)
                if (field.help.isNotBlank()) Text(field.help, color = DakkTextSecondaryDark, fontSize = 12.sp)
            }
            Switch(
                checked = value as? Boolean ?: false,
                onCheckedChange = { onChange(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = DakkYellow, checkedThumbColor = Color.Black)
            )
        }

        "number" -> OutlinedTextField(
            value = (value as? Number)?.toLong()?.toString() ?: (value as? String).orEmpty(),
            onValueChange = { text ->
                val digits = text.filter { it.isDigit() }.take(9)
                onChange(digits.toLongOrNull() ?: 0L)
            },
            label = { Text(field.label) },
            supportingText = optionalText(field.help),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = panelFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        "select" -> SelectField(field, value as? String ?: field.default as? String ?: "", onChange)

        "secret" -> {
            var visible by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = value as? String ?: "",
                onValueChange = { onChange(it.trim()) },
                label = { Text(field.label) },
                placeholder = optionalText(field.placeholder),
                supportingText = optionalText(field.help),
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Göster/gizle")
                    }
                },
                colors = panelFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        "multiline", "text", "url", "version" -> OutlinedTextField(
            value = value as? String ?: "",
            onValueChange = { onChange(if (field.type == "multiline") it else it.trim()) },
            label = { Text(field.label) },
            placeholder = optionalText(field.placeholder),
            supportingText = optionalText(field.help),
            singleLine = field.type != "multiline",
            minLines = if (field.type == "multiline") 4 else 1,
            maxLines = if (field.type == "multiline") 12 else 1,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (field.type == "url") KeyboardType.Uri else KeyboardType.Text
            ),
            colors = panelFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        "stat" -> StatField(field, repo)

        "note" -> Text(field.help.ifBlank { field.label }, color = DakkTextSecondaryDark, fontSize = 13.sp)

        "announcement" -> AnnouncementEditor(
            initial = (value as? Map<*, *>)?.let { Announcement.fromMap(it) } ?: Announcement(),
            field = field,
            viewModel = viewModel,
            repo = repo,
            onSaved = onAnnouncementSaved
        )

        else -> Text("Bilinmeyen alan türü: ${field.type} (${field.key})", color = DakkTextTertiaryDark, fontSize = 12.sp)
    }
}

@Composable
private fun SelectField(field: PanelField, value: String, onChange: (Any?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = field.options.firstOrNull { it.value == value }
    Column(Modifier.fillMaxWidth()) {
        Text(field.label, color = DakkTextSecondaryDark, fontSize = 12.sp)
        Box {
            Surface(
                color = DakkDarkSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clickable { open = true }
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selected?.label ?: value.ifBlank { "Seçin" }, color = Color.White, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null, tint = DakkTextSecondaryDark)
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                field.options.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { open = false; onChange(option.value) })
                }
            }
        }
        if (field.help.isNotBlank()) Text(field.help, color = DakkTextSecondaryDark, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StatField(field: PanelField, repo: ConfigRepository) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(field.label, color = Color.White, fontSize = 15.sp)
            Text(result ?: field.help.ifBlank { "Saymak için dokunun" }, color = DakkTextSecondaryDark, fontSize = 13.sp)
        }
        OutlinedButton(
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true
                    result = try {
                        when (field.key) {
                            "user_count" -> "${repo.countUsers()} kullanıcı"
                            else -> "Desteklenmeyen istatistik"
                        }
                    } catch (e: Exception) {
                        "Alınamadı: ${e.message ?: "hata"}"
                    }
                    busy = false
                }
            }
        ) { Text(if (busy) "…" else "Say") }
    }
}

@Composable
private fun AnnouncementEditor(
    initial: Announcement,
    field: PanelField,
    viewModel: DakkViewModel,
    repo: ConfigRepository,
    onSaved: (Map<String, Any>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember(initial) { mutableStateOf(initial.title) }
    var body by remember(initial) { mutableStateOf(initial.body) }
    var imageUrl by remember(initial) { mutableStateOf(initial.imageUrl) }
    var buttonText by remember(initial) { mutableStateOf(initial.buttonText) }
    var buttonUrl by remember(initial) { mutableStateOf(initial.buttonUrl) }
    var start by remember(initial) { mutableStateOf(formatDate(initial.startAt)) }
    var end by remember(initial) { mutableStateOf(formatDate(initial.endAt)) }
    var minVersion by remember(initial) { mutableStateOf(initial.minVersion) }
    var dismissible by remember(initial) { mutableStateOf(initial.dismissible) }
    var status by remember { mutableStateOf<String?>(null) }
    var live by remember(initial) { mutableStateOf(initial.active) }

    fun draft(): Announcement? {
        val s = parseDate(start)
        val e = parseDate(end)
        if (s == null || e == null) {
            status = "Tarih biçimi GG.AA.YYYY SS:DD olmalı (ör. 15.11.2026 20:00)"
            return null
        }
        return Announcement(
            id = initial.id,
            active = initial.active,
            title = title.trim(),
            body = body.trim(),
            imageUrl = imageUrl.trim(),
            buttonText = buttonText.trim(),
            buttonUrl = buttonUrl.trim(),
            startAt = s,
            endAt = e,
            minVersion = minVersion.trim(),
            dismissible = dismissible
        )
    }

    fun persist(a: Announcement, done: String) {
        scope.launch {
            try {
                val map = a.toMap()
                repo.saveValues(field.doc, mapOf(field.key to map))
                live = a.active
                status = done
                onSaved(map)
            } catch (e: Exception) {
                status = "Kaydedilemedi: ${e.message ?: "hata"}"
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (field.help.isNotBlank()) Text(field.help, color = DakkTextSecondaryDark, fontSize = 12.sp)
        Surface(
            color = if (live) DakkYellow.copy(alpha = 0.15f) else DakkDarkSurfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                if (live) "● Yayında" else "○ Yayında değil",
                color = if (live) DakkYellow else DakkTextSecondaryDark,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        AnnField("Başlık", title) { title = it }
        AnnField("Metin", body, multiline = true) { body = it }
        AnnField("Görsel adresi (isteğe bağlı, https://…)", imageUrl) { imageUrl = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { AnnField("Buton yazısı", buttonText) { buttonText = it } }
            Box(Modifier.weight(1f)) { AnnField("Buton bağlantısı", buttonUrl) { buttonUrl = it } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { AnnField("Başlangıç (GG.AA.YYYY SS:DD)", start) { start = it } }
            Box(Modifier.weight(1f)) { AnnField("Bitiş (boş = süresiz)", end) { end = it } }
        }
        AnnField("En düşük sürüm (boş = herkes)", minVersion) { minVersion = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kullanıcı kapatabilsin", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = dismissible,
                onCheckedChange = { dismissible = it },
                colors = SwitchDefaults.colors(checkedTrackColor = DakkYellow, checkedThumbColor = Color.Black)
            )
        }
        status?.let { Text(it, color = DakkYellow, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { draft()?.let { viewModel.previewAnnouncement(it) } }) { Text("Önizle") }
            Spacer(Modifier.weight(1f))
            if (live) {
                TextButton(onClick = { draft()?.let { persist(it.copy(active = false), "Duyuru yayından kaldırıldı") } }) {
                    Text("Kaldır", color = DakkStatusFailed)
                }
            }
            Button(
                onClick = {
                    draft()?.let {
                        if (it.title.isBlank() && it.body.isBlank() && it.imageUrl.isBlank()) {
                            status = "Başlık, metin veya görselden en az biri gerekli"
                        } else {
                            persist(
                                it.copy(id = "a${System.currentTimeMillis()}", active = true),
                                "Duyuru yayınlandı. Her kullanıcıya bir kez gösterilir."
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black)
            ) { Text("Yayınla") }
        }
        Text(
            "Yayınla her basışta yeni bir duyuru oluşturur; daha önce kapatan kullanıcılar da yeniden görür.",
            color = DakkTextTertiaryDark,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun AnnField(label: String, value: String, multiline: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        maxLines = if (multiline) 10 else 1,
        colors = panelFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun optionalText(text: String): (@Composable () -> Unit)? =
    if (text.isBlank()) null else { { Text(text) } }
