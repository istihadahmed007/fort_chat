package com.fort.messenger.ui.screens.circles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GroupWork
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.model.PrivateRoom
import com.fort.messenger.model.SharingCircle
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.PersonAdd
import com.fort.messenger.ui.components.ConnectionCardBadge
import com.fort.messenger.ui.components.SovereignCard
import com.fort.messenger.ui.components.SovereignTopBar
import com.fort.messenger.ui.theme.AmberWarning
import com.fort.messenger.ui.theme.EmeraldVerified
import com.fort.messenger.ui.theme.RoseDestructive
import com.fort.messenger.ui.theme.RoyalBluePrimary
import com.fort.messenger.viewmodel.AppLanguage
import com.fort.messenger.viewmodel.FortMainViewModel

import com.fort.messenger.ui.components.iceBlueLiquidBackground
import com.fort.messenger.ui.components.isAppDarkTheme

enum class CircleScreenTab(val labelEn: String, val labelBn: String) {
    SHARING_CIRCLES("Sharing Circles", "শেয়ারিং সার্কেল"),
    PRIVATE_ROOMS("Private Rooms", "প্রাইভেট রুম")
}

@Composable
fun CirclesRoomsScreen(
    viewModel: FortMainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = isAppDarkTheme()
    var selectedTab by remember { mutableStateOf(CircleScreenTab.SHARING_CIRCLES) }

    Scaffold(
        topBar = {
            SovereignTopBar(
                title = if (uiState.currentLanguage == AppLanguage.BANGLA) "সার্কেল ও রুম" else "Circles & Rooms",
                subtitle = "Cryptographic Visibility Groups & Bounded Spaces"
            )
        },
        containerColor = Color.Transparent,
        modifier = modifier.iceBlueLiquidBackground(isDark)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Switcher
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = RoyalBluePrimary
            ) {
                CircleScreenTab.values().forEach { tab ->
                    val isSelected = tab == selectedTab
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = if (uiState.currentLanguage == AppLanguage.BANGLA) tab.labelBn else tab.labelEn,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                CircleScreenTab.SHARING_CIRCLES -> {
                    SharingCirclesTabContent(
                        circles = uiState.sharingCircles,
                        language = uiState.currentLanguage
                    )
                }
                CircleScreenTab.PRIVATE_ROOMS -> {
                    PrivateRoomsTabContent(
                        rooms = uiState.privateRooms,
                        language = uiState.currentLanguage,
                        onToggleTask = { roomId, taskId ->
                            viewModel.toggleRoomTask(roomId, taskId)
                        },
                        onInviteMember = { roomId, inviteeId ->
                            viewModel.inviteToRoom(roomId, inviteeId)
                        },
                        onLeaveRoom = { roomId ->
                            viewModel.leaveRoom(roomId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SharingCirclesTabContent(
    circles: List<SharingCircle>,
    language: AppLanguage
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Educational Callout Banner
        item {
            SovereignCard(
                backgroundColor = Color(0xFFF8FAFC),
                borderColor = Color(0xFFE2E8F0)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = RoyalBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (language == AppLanguage.BANGLA) "সার্কেল হলো পারমিশন বাউন্ডারি" else "Circles are Permission Containers",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (language == AppLanguage.BANGLA)
                                "সার্কেল কোনো গ্রুপ চ্যাট নয়। এটি নিয়ন্ত্রণ করে কারা আপনার মুড ও প্রাপ্যতা দেখতে পাবে।"
                            else
                                "Sharing Circles govern who reads your Mood Ring & availability broadcast. They are not automatic broadcast group chats.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(circles, key = { it.id }) { circle ->
            SovereignCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = circle.iconEmoji, fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = circle.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${circle.members.size} verified members",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Mood visibility status badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (circle.isMoodBroadcastEnabled) Color(0xFFEFF6FF) else Color(0xFFF1F5F9))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (circle.isMoodBroadcastEnabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                    contentDescription = null,
                                    tint = if (circle.isMoodBroadcastEnabled) RoyalBluePrimary else Color(0xFF64748B),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (circle.isMoodBroadcastEnabled) "Mood Active" else "Mood Hidden",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (circle.isMoodBroadcastEnabled) RoyalBluePrimary else Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = circle.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Members Roster Horizontal list
                    Text(
                        text = "MEMBERS ROSTER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        circle.members.forEach { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = member.avatarEmoji, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = member.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (member.moodEmoji != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = member.moodEmoji, fontSize = 13.sp)
                                    }
                                }
                                ConnectionCardBadge(cardType = member.cardType)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivateRoomsTabContent(
    rooms: List<PrivateRoom>,
    language: AppLanguage,
    onToggleTask: (String, String) -> Unit,
    onInviteMember: (String, String) -> Unit,
    onLeaveRoom: (String) -> Unit
) {
    var invitingRoomId by remember { mutableStateOf<String?>(null) }
    var inviteeUserId by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SovereignCard(
                backgroundColor = Color(0xFFF8FAFC),
                borderColor = Color(0xFFE2E8F0)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.MeetingRoom,
                        contentDescription = null,
                        tint = RoyalBluePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (language == AppLanguage.BANGLA) "লাইফসাইকেল-বাউন্ড প্রাইভেট রুম" else "Lifecycle-Bound Ephemeral Rooms",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (language == AppLanguage.BANGLA)
                                "নির্দিষ্ট উদ্দেশ্যে তৈরি রুম। মেয়াদ শেষে বা উদ্দেশ্য সম্পন্ন হলে রুমটি স্বয়ংক্রিয়ভাবে বন্ধ হয়ে যায়।"
                            else
                                "Purpose-driven rooms with integrated task checklists. Automatically closes when the lifecycle expires.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(rooms, key = { it.id }) { room ->
            SovereignCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = room.iconEmoji, fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = room.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = room.purpose,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Room Actions
                        Row {
                            IconButton(
                                onClick = { invitingRoomId = room.id },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PersonAdd,
                                    contentDescription = "Invite Member",
                                    tint = RoyalBluePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onLeaveRoom(room.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ExitToApp,
                                    contentDescription = "Leave Room",
                                    tint = RoseDestructive,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Expiry & Auto-closure warning banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEF3C7))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.HourglassBottom,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${room.expiryRemainingString} • ${room.autoCloseWarning}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = AmberWarning
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Shared Checklist Section
                    Text(
                        text = "SHARED ROOM CHECKLIST",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        room.tasks.forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleTask(room.id, task.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { onToggleTask(room.id, task.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = RoyalBluePrimary,
                                        uncheckedColor = Color(0xFF94A3B8)
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        fontSize = 13.sp,
                                        color = if (task.isCompleted) Color(0xFF94A3B8) else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                                    )
                                    Text(
                                        text = "Assigned: ${task.assignedTo}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Invite Member Dialog
    if (invitingRoomId != null) {
        AlertDialog(
            onDismissRequest = { invitingRoomId = null },
            title = { Text("Invite Member to Room") },
            text = {
                Column {
                    Text(
                        text = "Enter the peer user ID or contact pass token to grant bounded access to this private room.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inviteeUserId,
                        onValueChange = { inviteeUserId = it },
                        label = { Text("User ID or Handle") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inviteeUserId.isNotBlank()) {
                            onInviteMember(invitingRoomId!!, inviteeUserId.trim())
                            invitingRoomId = null
                            inviteeUserId = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Text("Send Invite")
                }
            },
            dismissButton = {
                TextButton(onClick = { invitingRoomId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
