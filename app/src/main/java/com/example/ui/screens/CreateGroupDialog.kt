package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.ContactEntity
import com.example.ui.DakkViewModel
import com.example.ui.Screen
import com.example.ui.theme.*

@Composable
fun CreateGroupDialog(
    viewModel: DakkViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var groupName by remember { mutableStateOf("") }
    val contacts by viewModel.activeContacts.collectAsStateWithLifecycle()
    val availableContacts = remember(contacts) { contacts.filter { !it.isGroup } }
    val selectedContacts = remember { mutableStateListOf<ContactEntity>() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = DakkDarkSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("dialog_create_group")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(DakkYellow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, tint = Color.Black)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Yeni Grup Oluştur",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Arkadaşlarınla toplu P2P sohbet et",
                            fontSize = 12.sp,
                            color = DakkTextSecondaryDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = { Text("Grup Konusu / Adı (Örn: Tayfa 🐥)", fontSize = 13.sp, color = DakkTextTertiaryDark) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DakkYellow,
                        unfocusedBorderColor = DakkDarkBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "KATILIMCILARI SEÇ (${selectedContacts.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DakkTextTertiaryDark,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    items(availableContacts, key = { it.userId }) { contact ->
                        val isSelected = selectedContacts.contains(contact)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isSelected) selectedContacts.remove(contact)
                                    else selectedContacts.add(contact)
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(DakkOrange),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.alias.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = contact.alias,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )

                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    if (isSelected) selectedContacts.remove(contact)
                                    else selectedContacts.add(contact)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = DakkYellow,
                                    checkmarkColor = Color.Black
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("İptal", color = Color.LightGray)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (groupName.isBlank()) {
                                Toast.makeText(context, "Lütfen bir grup adı yazın", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.createGroup(groupName, selectedContacts) { newGroup ->
                                onDismiss()
                                viewModel.navigateTo(Screen.ChatDetail(newGroup))
                                Toast.makeText(context, "'${newGroup.alias}' grubu oluşturuldu!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DakkYellow)
                    ) {
                        Text("Grubu Oluştur", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
