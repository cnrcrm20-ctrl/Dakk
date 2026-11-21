package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.NotesCard
import com.example.ui.theme.DakkDarkBg
import com.example.ui.theme.DakkTextSecondaryDark
import com.example.ui.theme.DakkYellow
import com.example.util.WhatsNew

/** Güncellemeden sonraki ilk açılışta bir kez gösterilen tam ekran "Yenilikler". Tamam ya da geri ile kapanır. */
@Composable
fun WhatsNewScreen(notes: WhatsNew.Notes, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1D1806), DakkDarkBg, DakkDarkBg)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 36.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(DakkYellow),
                contentAlignment = Alignment.Center
            ) { Text("✨", fontSize = 40.sp) }
            Spacer(Modifier.height(18.dp))
            Text("Dakk ${notes.version}", color = DakkYellow, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Yenilikler", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(
                "Uygulaman güncellendi. İşte bu sürümde gelenler:",
                color = DakkTextSecondaryDark,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            NotesCard(notes.blocks)
        }
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = DakkYellow, contentColor = Color.Black),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp)
                .height(56.dp)
        ) { Text("Tamam", fontWeight = FontWeight.Bold, fontSize = 17.sp) }
    }
}
