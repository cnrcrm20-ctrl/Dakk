package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.PollView
import com.example.ui.theme.DakkDarkBorder
import com.example.ui.theme.DakkDarkSurface
import com.example.ui.theme.DakkDarkSurfaceVariant
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkTextTertiaryDark
import com.example.ui.theme.DakkYellow
import com.example.util.Polls

/** Baloncuk içindeki anket: seçenekler, canlı oy sayıları, oy verenler ve maç anketinde "Takım kur". */
@Composable
fun PollContent(
    poll: PollView,
    enabled: Boolean,
    nameOf: (String) -> String,
    onVote: (Int) -> Unit,
    onBuildTeams: () -> Unit
) {
    val total = poll.voterCount
    Column(Modifier.pollWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (poll.isMatch) {
                Icon(Icons.Default.SportsSoccer, null, tint = DakkYellow, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                if (poll.isMatch) "MAÇ ANKETİ" else "ANKET",
                color = DakkYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(poll.question, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
        Text(
            if (poll.multi) "Birden fazla seçebilirsin" else "Bir seçenek seç",
            color = DakkTextTertiaryDark,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        poll.options.forEachIndexed { index, option ->
            val voters = poll.votes[index].orEmpty()
            val chosen = index in poll.myChoices
            val fraction by animateFloatAsState(
                targetValue = if (total == 0) 0f else voters.size.toFloat() / total,
                animationSpec = tween(350),
                label = "poll$index"
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { onVote(index) }
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                ) {
                    Box(Modifier.matchParentSize()) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(if (chosen) DakkYellow.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f))
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(if (poll.multi) RoundedCornerShape(5.dp) else CircleShape)
                                .background(if (chosen) DakkYellow else Color.Transparent)
                                .border(
                                    1.5.dp,
                                    if (chosen) DakkYellow else DakkTextSecondaryDark,
                                    if (poll.multi) RoundedCornerShape(5.dp) else CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (chosen) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(option, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(
                            "${voters.size}",
                            color = if (chosen) DakkYellow else DakkTextSecondaryDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (voters.isNotEmpty()) {
                    val names = voters.map(nameOf)
                    val label = if (names.size <= 3) names.joinToString(", ")
                    else names.take(3).joinToString(", ") + " +${names.size - 3}"
                    Text(
                        label,
                        color = DakkTextTertiaryDark,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp, top = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (total == 0) "Henüz oy yok" else "$total kişi oy verdi",
            color = DakkTextSecondaryDark,
            fontSize = 12.sp
        )
        if (poll.isMatch) {
            val coming = poll.votes[0].orEmpty().size
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onBuildTeams,
                enabled = enabled && coming >= 2,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DakkYellow,
                    contentColor = Color.Black,
                    disabledContainerColor = DakkDarkSurfaceVariant,
                    disabledContentColor = DakkTextTertiaryDark
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.SportsSoccer, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (coming >= 2) "Takım kur ($coming kişi)" else "Takım kur (en az 2 kişi)", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun Modifier.pollWidth(): Modifier = this.then(Modifier.width(260.dp))

/** Anket oluşturma penceresi: normal anket veya hazır "Maç anketi". */
@Composable
fun CreatePollDialog(onDismiss: () -> Unit, onCreate: (Polls.Spec) -> Unit) {
    var isMatch by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var multi by remember { mutableStateOf(false) }
    val options = remember { mutableStateListOf("", "") }

    fun applyMatch(match: Boolean) {
        isMatch = match
        if (match) {
            question = Polls.MATCH_QUESTION
            options.clear(); options.addAll(Polls.MATCH_OPTIONS)
            multi = false
        } else {
            question = ""
            options.clear(); options.addAll(listOf("", ""))
        }
    }

    val spec = Polls.build(question, options.toList(), multi, isMatch)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = DakkDarkSurface,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp)
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("Anket oluştur", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PollTypeChip("📊 Normal", selected = !isMatch) { if (isMatch) applyMatch(false) }
                    PollTypeChip("⚽ Maç anketi", selected = isMatch) { if (!isMatch) applyMatch(true) }
                }
                if (isMatch) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "\"${Polls.MATCH_OPTIONS.first()}\" diyenleri tek dokunuşla rastgele iki takıma bölebilirsin.",
                        color = DakkTextSecondaryDark,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it.take(Polls.MAX_QUESTION) },
                    label = { Text("Soru") },
                    colors = pollFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Seçenekler", color = DakkTextSecondaryDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                options.forEachIndexed { index, value ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { options[index] = it.take(Polls.MAX_OPTION) },
                            placeholder = { Text("Seçenek ${index + 1}") },
                            singleLine = true,
                            colors = pollFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        if (options.size > Polls.MIN_OPTIONS && !(isMatch && index == 0)) {
                            IconButton(onClick = { options.removeAt(index) }) {
                                Icon(Icons.Default.Close, contentDescription = "Seçeneği kaldır", tint = DakkTextSecondaryDark)
                            }
                        }
                    }
                }
                if (options.size < Polls.MAX_OPTIONS) {
                    TextButton(onClick = { options.add("") }) {
                        Icon(Icons.Default.Add, null, tint = DakkYellow)
                        Spacer(Modifier.width(6.dp))
                        Text("Seçenek ekle", color = DakkYellow)
                    }
                }
                if (!isMatch) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text("Birden fazla seçime izin ver", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = multi,
                            onCheckedChange = { multi = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = DakkYellow, checkedThumbColor = Color.Black)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Oylar şifrelidir; grup üyeleri kimin neyi seçtiğini görür, sunucu göremez.", color = DakkTextTertiaryDark, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Vazgeç", color = DakkTextSecondaryDark) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { spec?.let(onCreate) },
                        enabled = spec != null,
                        colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black)
                    ) { Text("Gönder", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun PollTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = DakkYellow,
            selectedLabelColor = Color.Black,
            containerColor = DakkDarkSurfaceVariant,
            labelColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = DakkDarkBorder,
            selectedBorderColor = DakkYellow
        )
    )
}

@Composable
private fun pollFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DakkYellow,
    focusedLabelColor = DakkYellow,
    cursorColor = DakkYellow,
    unfocusedBorderColor = DakkDarkBorder
)
