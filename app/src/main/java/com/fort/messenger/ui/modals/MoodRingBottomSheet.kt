package com.fort.messenger.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GroupWork
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.model.DecayDuration
import com.fort.messenger.model.MoodEmotion
import com.fort.messenger.model.WhatINeed
import com.fort.messenger.ui.theme.RoyalBluePrimary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoodRingBottomSheet(
    onDismissRequest: () -> Unit,
    onBroadcast: (MoodEmotion, WhatINeed?, String, DecayDuration) -> Unit,
    onClear: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var selectedEmotion by remember { mutableStateOf(MoodEmotion.NEED_QUIET) }
    var selectedWhatINeed by remember { mutableStateOf<WhatINeed?>(WhatINeed.GIVE_ME_SPACE) }
    var selectedCircle by remember { mutableStateOf("Close Circle") }
    var selectedDuration by remember { mutableStateOf(DecayDuration.HOURS_2) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mood Ring & Quiet Presence",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Context-gated broadcast with automatic time decay",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Emotion Selector (PRD Section 4.3)
            Text(
                text = "1. SELECT YOUR CURRENT EMOTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoodEmotion.entries.chunked(2).forEach { rowEmotions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowEmotions.forEach { emotion ->
                            val isSelected = emotion == selectedEmotion
                            val bgColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                            val borderColor = if (isSelected) RoyalBluePrimary else Color(0xFFE2E8F0)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bgColor)
                                    .border(if (isSelected) 1.5.dp else 0.5.dp, borderColor, RoundedCornerShape(12.dp))
                                    .clickable { selectedEmotion = emotion }
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = emotion.emoji, fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = emotion.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) RoyalBluePrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = emotion.subtitle,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. "What I Need" Actionable Layer (PRD Section 4.3)
            Text(
                text = "2. WHAT I NEED (ACTIONABLE BOUNDARY)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WhatINeed.values().forEach { need ->
                    val isSelected = need == selectedWhatINeed
                    val bgColor = if (isSelected) RoyalBluePrimary else Color(0xFFF1F5F9)
                    val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgColor)
                            .clickable {
                                selectedWhatINeed = if (isSelected) null else need
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = need.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Audience Isolation (Sharing Circle)
            Text(
                text = "3. AUDIENCE ISOLATION (WHO SEES THIS)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Close Circle", "Work & Collaborators").forEach { circle ->
                    val isSelected = circle == selectedCircle
                    val bgColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                    val borderColor = if (isSelected) RoyalBluePrimary else Color(0xFFE2E8F0)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .border(if (isSelected) 1.5.dp else 0.5.dp, borderColor, RoundedCornerShape(10.dp))
                            .clickable { selectedCircle = circle }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.GroupWork,
                                contentDescription = null,
                                tint = if (isSelected) RoyalBluePrimary else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = circle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) RoyalBluePrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Temporal Decay (Auto-Decay Timer)
            Text(
                text = "4. TEMPORAL DECAY (QUIETLY VANISHES WITHOUT LOGS)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DecayDuration.values().forEach { duration ->
                    val isSelected = duration == selectedDuration
                    val bgColor = if (isSelected) RoyalBluePrimary else Color(0xFFF1F5F9)
                    val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .clickable { selectedDuration = duration }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = duration.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onClear,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear Presence", fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        onBroadcast(selectedEmotion, selectedWhatINeed, selectedCircle, selectedDuration)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1.5f)
                ) {
                    Text("Broadcast Presence", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
