package com.fort.messenger.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.model.CallType
import com.fort.messenger.model.ChatMessage
import com.fort.messenger.model.LocationPin
import com.fort.messenger.ui.components.ConnectionCardBadge
import com.fort.messenger.ui.components.FortChatLogoBadge
import com.fort.messenger.ui.components.FortPrivateConversationBanner
import com.fort.messenger.ui.components.MoodRingBadge
import com.fort.messenger.ui.components.PassCountdownChip
import com.fort.messenger.ui.components.PrivateByDefaultBadge
import com.fort.messenger.ui.components.iceBlueLiquidBackground
import com.fort.messenger.ui.modals.PrivacyCheckDialog
import com.fort.messenger.ui.modals.ShareCheckModal
import com.fort.messenger.ui.theme.EmeraldVerified
import com.fort.messenger.ui.theme.IceBlueBorder
import com.fort.messenger.ui.theme.IceBlueTint
import com.fort.messenger.ui.theme.RoseDestructive
import com.fort.messenger.ui.theme.RoyalBluePrimary
import com.fort.messenger.viewmodel.AppLanguage
import com.fort.messenger.viewmodel.FortMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    conversationId: String,
    viewModel: FortMainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = com.fort.messenger.ui.components.isAppDarkTheme()
    val isBangla = uiState.currentLanguage == AppLanguage.BANGLA
    val conversation = uiState.conversations.find { it.id == conversationId }
    var inputText by remember(conversationId) { mutableStateOf(viewModel.getDraft(conversationId)) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMessageForActions by remember { mutableStateOf<ChatMessage?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingText by remember { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showCallMenu by remember { mutableStateOf(false) }

    // Auto mark as read on entering chat
    LaunchedEffect(conversationId) {
        viewModel.markConversationAsRead(conversationId)
    }

    // Reset typing status on exit
    DisposableEffect(conversationId) {
        onDispose {
            viewModel.onUserTyping(conversationId, false)
        }
    }

    if (conversation == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (isBangla) "কথোপকথনটি পাওয়া যায়নি" else "Conversation not found")
        }
        return
    }

    // Filter messages if search is active
    val displayMessages = if (searchQuery.isNotBlank()) {
        conversation.messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    } else {
        conversation.messages
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isDark) Color(0xEB0D1527)
                        else Color.White.copy(alpha = 0.85f)
                    )
                    .border(
                        0.5.dp,
                        if (isDark) Color(0xFF1E2B47) else Color(0xFFE2E8F0)
                    )
                    .statusBarsPadding()
            ) {
                var showOverflowMenu by remember { mutableStateOf(false) }

                com.fort.messenger.ui.components.FortConversationTopBar(
                    participantName = conversation.participantName,
                    avatarEmoji = conversation.avatarEmoji,
                    onBack = onNavigateBack,
                    onVideoCall = {
                        val peerUserId = conversationId.removePrefix("conv_")
                        viewModel.startCall(peerUserId, conversation.participantName, CallType.VIDEO)
                    },
                    onAudioCall = {
                        val peerUserId = conversationId.removePrefix("conv_")
                        viewModel.startCall(peerUserId, conversation.participantName, CallType.AUDIO)
                    },
                    onOverflow = { showOverflowMenu = true }
                )

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "বার্তা অনুসন্ধান" else "Search Messages") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = RoyalBluePrimary) },
                        onClick = {
                            showOverflowMenu = false
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "গোপনীয়তা চেক" else "Privacy Check") },
                        leadingIcon = { Icon(Icons.Outlined.Shield, contentDescription = null, tint = RoyalBluePrimary) },
                        onClick = {
                            showOverflowMenu = false
                            viewModel.openPrivacyCheck()
                        }
                    )
                }

                // In-Conversation Search Bar
                AnimatedVisibility(visible = isSearchActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search encrypted messages...", fontSize = 12.sp) },
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // In-Conversation Search Bar
                AnimatedVisibility(visible = isSearchActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search encrypted messages...", fontSize = 12.sp) },
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Active Live Location Sharing Indicator Banner
                val activeLive = uiState.activeLiveLocation
                if (activeLive != null && !activeLive.isStopped) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EmeraldVerified.copy(alpha = 0.12f))
                            .border(width = 0.5.dp, color = EmeraldVerified.copy(alpha = 0.3f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = EmeraldVerified,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sharing live location with peer",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldVerified
                            )
                        }

                        TextButton(
                            onClick = { viewModel.stopLiveLocationSharing() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Stop", color = Color(0xFFE11D48), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        0.5.dp,
                        if (isDark) Color(0xFF1E2B47) else Color(0xFFE2E8F0)
                    )
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                // Replying to Quote Preview Banner
                AnimatedVisibility(visible = uiState.replyingToMessage != null) {
                    val rep = uiState.replyingToMessage
                    if (rep != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                .border(width = 0.5.dp, color = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .background(RoyalBluePrimary, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (isBangla) "${rep.senderName}-কে উত্তর দিচ্ছেন" else "Replying to ${rep.senderName}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoyalBluePrimary
                                    )
                                    Text(
                                        text = rep.text,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.setReplyingTo(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Reply", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Quick Emoji Reactions Row
                AnimatedVisibility(visible = showEmojiPicker) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDark) Color(0xFF131D31) else Color(0xFFEFF6FF))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("❤️", "👍", "😂", "🔥", "🤍", "🛡️", "👋", "✨").forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 22.sp,
                                modifier = Modifier
                                    .clickable {
                                        inputText += emoji
                                        viewModel.setDraft(conversationId, inputText)
                                        viewModel.onUserTyping(conversationId, true)
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }

                // Attachment menu
                DropdownMenu(
                    expanded = showAttachmentMenu,
                    onDismissRequest = { showAttachmentMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "ক্যামেরা ছবি (EXIF স্ক্রাবড)" else "Sanitized Camera Photo (EXIF Scrubbed)") },
                        leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = RoyalBluePrimary) },
                        onClick = {
                            showAttachmentMenu = false
                            viewModel.openShareCheck()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "ব্যক্তিগত অবস্থান শেয়ার (পিন / লাইভ)" else "Share Private Location (Pin / Live)") },
                        leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = RoyalBluePrimary) },
                        onClick = {
                            showAttachmentMenu = false
                            viewModel.openLocationShareModal()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "ফাইল / ডকুমেন্ট পাঠান" else "Send File / Document") },
                        leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = RoyalBluePrimary) },
                        onClick = {
                            showAttachmentMenu = false
                            viewModel.sendMessage(
                                text = "📄 sovereign_enclave_manifest.pdf",
                                attachmentUri = "content://fort/manifest.pdf",
                                attachmentType = "FILE",
                                attachmentName = "sovereign_enclave_manifest.pdf",
                                attachmentSize = 48200L
                            )
                        }
                    )
                }

                // Floating Pill Composer matching Phone 3
                com.fort.messenger.ui.components.FortConversationPillComposer(
                    text = inputText,
                    onTextChanged = {
                        inputText = it
                        viewModel.setDraft(conversationId, it)
                        viewModel.onUserTyping(conversationId, it.isNotBlank())
                    },
                    onSend = {
                        val textToSend = inputText
                        inputText = ""
                        viewModel.setDraft(conversationId, "")
                        viewModel.onUserTyping(conversationId, false)
                        viewModel.sendMessage(textToSend)
                    },
                    onAttach = { showAttachmentMenu = true },
                    onEmojiToggle = { showEmojiPicker = !showEmojiPicker },
                    onMic = { viewModel.sendMessage("🎙️ [Voice note: 0:04]") }
                )
            }
        },
        containerColor = Color.Transparent,
        modifier = modifier.iceBlueLiquidBackground(isDark)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Contact Pass Bounded Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEFF6FF))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = RoyalBluePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${conversation.passType.label}: ${conversation.passTimeRemaining}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RoyalBluePrimary
                        )
                    }

                    Text(
                        text = "Sender-Authenticated E2EE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E3A8A)
                    )
                }
            }

            // Quick Support Response Banner if peer has active mood
            if (conversation.moodEmoji != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${conversation.participantName} is ${conversation.moodEmoji} • ${conversation.moodWhatINeed ?: "Need quiet"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEFF6FF))
                                .clickable { viewModel.sendSupportResponse("🤍") }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "I'm here 🤍",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RoyalBluePrimary
                            )
                        }
                    }
                }
            }

            // Outbox Retry Banner if any message is pending or failed
            val hasPending = conversation.messages.any { it.deliveryStatus == "PENDING" || it.deliveryStatus == "FAILED" }
            if (hasPending) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEF3C7))
                        .clickable { viewModel.retryPendingOutbox() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CloudQueue, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Messages queued offline. Tap to sync.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFB45309)
                            )
                        }
                        Text("Retry Now", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RoyalBluePrimary)
                    }
                }
            }

            // Messages LazyColumn
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Floating E2EE pill badge matching Phone 3
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        com.fort.messenger.ui.components.FortEncryptedPillBadge()
                    }
                }

                // Centered "Today" date indicator
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isBangla) "আজ" else "Today",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) com.fort.messenger.ui.theme.TextMutedDark else com.fort.messenger.ui.theme.ShowcaseMutedText
                        )
                    }
                }

                items(displayMessages, key = { it.id }) { message ->
                    ChatMessageBubble(
                        message = message,
                        onMessageClick = { selectedMessageForActions = message },
                        onReplyClick = { viewModel.setReplyingTo(message) },
                        onReactionClick = { emoji -> viewModel.addReaction(message.id, emoji) }
                    )
                }
            }
        }
    }

    // Message Action Sheet / Context Modal
    if (selectedMessageForActions != null) {
        val msg = selectedMessageForActions!!
        AlertDialog(
            onDismissRequest = { selectedMessageForActions = null },
            title = {
                Text("Message Actions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column {
                    // Reactions Row
                    Text("Reactions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("❤️", "👍", "😂", "😮", "😢", "🛡️").forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF))
                                    .clickable {
                                        viewModel.addReaction(msg.id, emoji)
                                        selectedMessageForActions = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emoji, fontSize = 18.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reply Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.setReplyingTo(msg)
                                selectedMessageForActions = null
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Reply, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Quote / Reply", fontSize = 13.sp)
                    }

                    // Edit Action (If user's own message and not deleted)
                    if (msg.isMine && !msg.isDeleted) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    editingText = msg.text
                                    showEditDialog = true
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = RoyalBluePrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Edit Message", fontSize = 13.sp)
                        }

                        // Delete Action
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.deleteMessage(msg.id)
                                    selectedMessageForActions = null
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = RoseDestructive, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Delete Message", color = RoseDestructive, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForActions = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Edit Message Dialog
    if (showEditDialog && selectedMessageForActions != null) {
        val targetMsg = selectedMessageForActions!!
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.editMessage(targetMsg.id, editingText)
                        showEditDialog = false
                        selectedMessageForActions = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                ) {
                    Text("Save Edit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.isPrivacyCheckOpen) {
        PrivacyCheckDialog(
            conversation = conversation,
            peerConnection = uiState.currentPeerConnection,
            onVerifySafetyNumber = { viewModel.markPeerSafetyNumberVerified() },
            onDismissRequest = { viewModel.closePrivacyCheck() },
            activeExpirySetting = uiState.activeChatExpirySetting
        )
    }

    if (uiState.isShareCheckOpen) {
        ShareCheckModal(
            items = uiState.shareCheckItems,
            onScrubItem = { viewModel.scrubSensitiveItem(it) },
            onScrubAll = { viewModel.scrubAllSensitiveItems() },
            onDispatch = { viewModel.dispatchScrubbedMedia() },
            onDismissRequest = { viewModel.closeShareCheck() }
        )
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onMessageClick: () -> Unit,
    onReplyClick: () -> Unit,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isMine = message.isMine
    val context = LocalContext.current
    val isDark = com.fort.messenger.ui.components.isAppDarkTheme()
    val bubbleColor = if (isMine) {
        if (isDark) com.fort.messenger.ui.theme.SkyBlueBubbleDark else com.fort.messenger.ui.theme.SkyBlueBubbleLight
    } else {
        if (isDark) Color(0xFF1E293B) else Color.White.copy(alpha = 0.92f)
    }
    val bubbleBorder = if (isMine) {
        if (isDark) Color(0xFF2563EB).copy(alpha = 0.4f) else Color(0xFF93C5FD).copy(alpha = 0.6f)
    } else {
        if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    }
    val textColor = if (isMine) {
        if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText
    } else {
        if (isDark) com.fort.messenger.ui.theme.TextPrimaryDark else com.fort.messenger.ui.theme.ShowcaseNavyText
    }
    val timeColor = if (isMine) {
        if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
    } else {
        if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    }
    val alignment = if (isMine) Alignment.End else Alignment.Start

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 1.5.dp,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isMine) 18.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 18.dp
                    ),
                    spotColor = Color(0x141E40AF)
                )
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isMine) 18.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 18.dp
                    )
                )
                .background(bubbleColor)
                .border(
                    width = 0.8.dp,
                    color = bubbleBorder,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isMine) 18.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 18.dp
                    )
                )
                .clickable { onMessageClick() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                // Quoted Reply Preview inside bubble
                if (message.replyToMessageId != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMine) Color(0xFF1E40AF) else Color(0xFFF1F5F9))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text(
                                text = message.replyToSenderName ?: "Peer",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMine) Color(0xFF93C5FD) else RoyalBluePrimary
                            )
                            Text(
                                text = message.replyToText ?: "Quoted message",
                                fontSize = 11.sp,
                                color = if (isMine) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Location Pin or Media Attachment Header
                if (message.attachmentType == "LOCATION_PIN") {
                    val coords = message.attachmentName?.split(",") ?: emptyList()
                    val lat = coords.getOrNull(0)?.trim()?.toDoubleOrNull()
                    val lng = coords.getOrNull(1)?.trim()?.toDoubleOrNull()

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isMine) Color(0xFF1E3A8A) else Color(0xFFEFF6FF))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = if (isMine) Color(0xFF38BDF8) else RoyalBluePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Location Pin",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMine) Color.White else RoyalBluePrimary
                                )
                            }
                            if (lat != null && lng != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "GPS: %.4f, %.4f".format(lat, lng),
                                    fontSize = 11.sp,
                                    color = if (isMine) Color(0xFFDBEAFE) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        LocationPin(lat, lng, "Fort Location Pin").openInMapsApp(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isMine) Color(0xFF2563EB) else RoyalBluePrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open in Maps", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else if (message.attachmentType != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMine) Color(0xFF1E3A8A) else Color(0xFFEFF6FF))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (message.attachmentType == "IMAGE") Icons.Outlined.Image else Icons.Outlined.Description,
                                contentDescription = null,
                                tint = if (isMine) Color.White else RoyalBluePrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = message.attachmentName ?: "Attachment",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMine) Color.White else RoyalBluePrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (message.isScrubbedMedia) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = EmeraldVerified,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "METADATA SANITIZED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldVerified,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = textColor,
                    lineHeight = 20.sp
                )

                if (message.isEdited) {
                    Text(
                        text = "(edited)",
                        fontSize = 9.sp,
                        color = timeColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.timestamp,
                        fontSize = 10.sp,
                        color = timeColor
                    )
                    if (isMine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.deliveryStatus) {
                            "PENDING" -> Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = "Queued",
                                tint = timeColor,
                                modifier = Modifier.size(12.dp)
                            )
                            "SENT" -> Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Sent",
                                tint = timeColor,
                                modifier = Modifier.size(12.dp)
                            )
                            "DELIVERED" -> Icon(
                                imageVector = Icons.Outlined.DoneAll,
                                contentDescription = "Delivered",
                                tint = timeColor,
                                modifier = Modifier.size(12.dp)
                            )
                            "READ" -> Icon(
                                imageVector = Icons.Outlined.DoneAll,
                                contentDescription = "Read",
                                tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                modifier = Modifier.size(13.dp)
                            )
                            else -> Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = "Failed",
                                tint = RoseDestructive,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Reaction chips below message bubble
        if (message.reactions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                message.reactions.forEach { (emoji, count) ->
                    val isMineReacted = message.myReactions.contains(emoji)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isMineReacted) Color(0xFFDBEAFE) else Color(0xFFF1F5F9))
                            .border(0.5.dp, if (isMineReacted) RoyalBluePrimary else Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                            .clickable { onReactionClick(emoji) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$emoji $count",
                            fontSize = 11.sp,
                            fontWeight = if (isMineReacted) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }
    }
}
