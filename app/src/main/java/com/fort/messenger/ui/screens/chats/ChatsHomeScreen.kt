package com.fort.messenger.ui.screens.chats

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
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Badge
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.model.ChatConversation
import com.fort.messenger.ui.components.ChatFilter
import com.fort.messenger.ui.components.ConnectionCardBadge
import com.fort.messenger.ui.components.FilterChipBar
import com.fort.messenger.ui.components.MoodRingBadge
import com.fort.messenger.ui.components.PassCountdownChip
import com.fort.messenger.ui.components.QuietPresenceBanner
import com.fort.messenger.ui.components.SovereignCard
import com.fort.messenger.ui.components.SovereignTopBar
import com.fort.messenger.ui.theme.EmeraldVerified
import com.fort.messenger.ui.theme.RoseDestructive
import com.fort.messenger.ui.theme.RoyalBluePrimary
import com.fort.messenger.viewmodel.AppLanguage
import com.fort.messenger.viewmodel.FortMainViewModel

@Composable
fun ChatsHomeScreen(
    viewModel: FortMainViewModel,
    onNavigateToChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeCard = uiState.connectionCards.find { it.id == uiState.activeCardId }

    val filteredConversations = uiState.conversations.filter { conv ->
        when (uiState.selectedFilter) {
            ChatFilter.ALL -> true
            ChatFilter.UNREAD -> conv.unreadCount > 0
            ChatFilter.ROOMS -> conv.isRoom
            ChatFilter.EXPIRING -> conv.passTimeRemaining.contains("h", ignoreCase = true) || conv.passTimeRemaining.contains("1 Convo", ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            SovereignTopBar(
                title = if (uiState.currentLanguage == AppLanguage.BANGLA) "চ্যাটস" else "Chats",
                subtitle = if (uiState.currentLanguage == AppLanguage.BANGLA) "আপনার ব্যক্তিগত কথোপকথন" else "Your private conversations",
                activeCard = activeCard,
                onActiveCardClick = { /* Can switch persona from top bar */ },
                onScanQrClick = { viewModel.openPassScanner() }
            )
        },
        floatingActionButton = {
            if (uiState.conversations.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { viewModel.openNewChatMenu() },
                    containerColor = RoyalBluePrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "New chat")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.currentLanguage == AppLanguage.BANGLA) "নতুন চ্যাট" else "New chat",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Quiet Presence Banner
            item {
                QuietPresenceBanner(
                    moodState = uiState.moodState,
                    onOpenMoodPicker = { viewModel.openMoodPicker() },
                    language = uiState.currentLanguage
                )
            }

            if (uiState.conversations.isNotEmpty()) {
                item {
                    FilterChipBar(
                        selectedFilter = uiState.selectedFilter,
                        onFilterSelected = { viewModel.selectFilter(it) }
                    )
                }
            }

            if (filteredConversations.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.currentLanguage == AppLanguage.BANGLA) "কথোপকথন" else "Conversations",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (uiState.currentLanguage == AppLanguage.BANGLA) {
                                "${filteredConversations.size}টি কথোপকথন"
                            } else {
                                val count = filteredConversations.size
                                "$count ${if (count == 1) "conversation" else "conversations"}"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Conversation Rows
            if (filteredConversations.isEmpty()) {
                val hasNoConversations = uiState.conversations.isEmpty()
                item {
                    SovereignCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (hasNoConversations) 12.dp else 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        elevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ModeComment,
                                    contentDescription = null,
                                    tint = RoyalBluePrimary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = if (hasNoConversations) {
                                    if (uiState.currentLanguage == AppLanguage.BANGLA) "এখনও কোনো কথোপকথন নেই" else "No conversations yet"
                                } else {
                                    if (uiState.currentLanguage == AppLanguage.BANGLA) "এই ফিল্টারে কোনো চ্যাট নেই" else "No conversations match this filter"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (hasNoConversations) {
                                    if (uiState.currentLanguage == AppLanguage.BANGLA) {
                                        "বিশ্বাসের কাউকে সংযুক্ত করে ব্যক্তিগত চ্যাট শুরু করুন।"
                                    } else {
                                        "Connect with someone you trust to start a private chat."
                                    }
                                } else {
                                    if (uiState.currentLanguage == AppLanguage.BANGLA) {
                                        "অন্য ফিল্টার বেছে নিয়ে আপনার চ্যাটগুলো দেখুন।"
                                    } else {
                                        "Choose another filter to see your chats."
                                    }
                                },
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 20.sp
                            )

                            if (hasNoConversations) {
                                Spacer(modifier = Modifier.height(20.dp))
                                androidx.compose.material3.Button(
                                    onClick = { viewModel.openPassScanner() },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = RoyalBluePrimary,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.QrCodeScanner,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (uiState.currentLanguage == AppLanguage.BANGLA) "আমন্ত্রণ স্ক্যান করুন" else "Scan an invite",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { viewModel.openPassGenerator() },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.QrCode,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (uiState.currentLanguage == AppLanguage.BANGLA) "আমন্ত্রণ তৈরি করুন" else "Create an invite",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                androidx.compose.material3.TextButton(
                                    onClick = { viewModel.openSearchPeople() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text(
                                        text = if (uiState.currentLanguage == AppLanguage.BANGLA) {
                                            "Fort ID দিয়ে খুঁজুন (Knock First)"
                                        } else {
                                            "Find by Fort ID (Knock First)"
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(12.dp))
                                androidx.compose.material3.TextButton(
                                    onClick = { viewModel.selectFilter(ChatFilter.ALL) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text(
                                        text = if (uiState.currentLanguage == AppLanguage.BANGLA) "সব কথোপকথন দেখুন" else "Show all conversations",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredConversations, key = { it.id }) { conversation ->
                    ConversationRowItem(
                        conversation = conversation,
                        onClick = {
                            viewModel.openChat(conversation.id)
                            onNavigateToChat(conversation.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationRowItem(
    conversation: ChatConversation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SovereignCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with Mood Ring micro-badge anchored at bottom-right
            Box(
                modifier = Modifier.size(50.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (conversation.isRoom) Color(0xFFE0E7FF) else Color(0xFFEFF4FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = conversation.avatarEmoji,
                        fontSize = 22.sp
                    )
                }

                // Mood Ring micro-badge (20x20dp)
                if (conversation.moodEmoji != null) {
                    MoodRingBadge(
                        moodEmoji = conversation.moodEmoji,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = conversation.participantName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (conversation.isRoom) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEFF6FF))
                                    .border(0.5.dp, Color(0xFF93C5FD), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text("ROOM", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = RoyalBluePrimary)
                            }
                        } else {
                            ConnectionCardBadge(cardType = conversation.cardType)
                        }
                    }
                    Text(
                        text = conversation.lastMessageTime,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (conversation.isTyping) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = RoyalBluePrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "typing...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = RoyalBluePrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            // Delivery status tick if last message was sent by me
                            if (conversation.lastMessageIsMine) {
                                when (conversation.lastMessageDeliveryStatus) {
                                    "PENDING" -> Icon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = "Queued",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    "SENT" -> Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "Sent",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    "DELIVERED" -> Icon(
                                        imageVector = Icons.Outlined.DoneAll,
                                        contentDescription = "Delivered",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    "READ" -> Icon(
                                        imageVector = Icons.Outlined.DoneAll,
                                        contentDescription = "Read",
                                        tint = EmeraldVerified,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    else -> Icon(
                                        imageVector = Icons.Outlined.ErrorOutline,
                                        contentDescription = "Failed",
                                        tint = RoseDestructive,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            // Attachment Icon Preview
                            if (conversation.lastMessageAttachmentType != null) {
                                when (conversation.lastMessageAttachmentType) {
                                    "IMAGE" -> Icon(
                                        imageVector = Icons.Outlined.PhotoCamera,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    "LOCATION_PIN" -> Icon(
                                        imageVector = Icons.Outlined.LocationOn,
                                        contentDescription = null,
                                        tint = EmeraldVerified,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    else -> Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            Text(
                                text = conversation.lastMessage,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (conversation.isRoom) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = conversation.passTimeRemaining,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            PassCountdownChip(
                                timeRemaining = conversation.passTimeRemaining,
                                passType = conversation.passType
                            )
                        }

                        if (conversation.unreadCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(
                                containerColor = RoyalBluePrimary,
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = conversation.unreadCount.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
