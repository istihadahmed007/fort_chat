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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
                subtitle = "Bounded & Private Comms",
                activeCard = activeCard,
                onActiveCardClick = { /* Can switch persona from top bar */ },
                onScanQrClick = { viewModel.openPassGenerator() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openPassGenerator() },
                containerColor = RoyalBluePrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "New Contact Pass")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.currentLanguage == AppLanguage.BANGLA) "+ নতুন পাস" else "+ New Pass",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
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
                    onOpenMoodPicker = { viewModel.openMoodPicker() }
                )
            }

            // Filter Chips Bar
            item {
                FilterChipBar(
                    selectedFilter = uiState.selectedFilter,
                    onFilterSelected = { viewModel.selectFilter(it) }
                )
            }

            // Conversation list header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.currentLanguage == AppLanguage.BANGLA) "সক্রিয় চ্যাটসমূহ" else "Active Conversations",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${filteredConversations.size} passes",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Conversation Rows
            if (filteredConversations.isEmpty()) {
                item {
                    SovereignCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "🕊️", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No conversations in this filter",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create a bounded pass or check your inbound queue",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        .background(Color(0xFFEFF4FF)),
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
                        ConnectionCardBadge(cardType = conversation.cardType)
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
                    Text(
                        text = conversation.lastMessage,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PassCountdownChip(
                            timeRemaining = conversation.passTimeRemaining,
                            passType = conversation.passType
                        )

                        if (conversation.unreadCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge(
                                containerColor = RoyalBluePrimary,
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = conversation.unreadCount.toString(),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
