package com.fort.messenger.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.ui.modals.CallScreenModal
import com.fort.messenger.ui.modals.ContactPassGeneratorSheet
import com.fort.messenger.ui.modals.LocationShareBottomSheet
import com.fort.messenger.ui.modals.MoodRingBottomSheet
import com.fort.messenger.ui.modals.NewChatBottomSheet
import com.fort.messenger.ui.modals.PassClaimPreviewDialog
import com.fort.messenger.ui.modals.PassScannerModal
import com.fort.messenger.ui.modals.SearchPeopleModal
import com.fort.messenger.ui.screens.chat.ConversationScreen
import com.fort.messenger.ui.screens.chats.ChatsHomeScreen
import com.fort.messenger.ui.screens.circles.CirclesRoomsScreen
import com.fort.messenger.ui.screens.requests.KnockFirstRequestsScreen
import com.fort.messenger.ui.screens.you.YouAccessMapScreen
import com.fort.messenger.ui.theme.RoyalBluePrimary
import com.fort.messenger.ui.screens.auth.AuthScreen
import com.fort.messenger.viewmodel.FortMainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FortNavGraph(
    viewModel: FortMainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentDestination by remember { mutableStateOf(FortDestination.CHATS) }

    // Splash loading state while inspecting Room DB session
    if (uiState.isInitializing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = RoyalBluePrimary)
        }
        return
    }

    // Unauthenticated state -> Render real Onboarding & Auth Flow
    if (uiState.currentUserAccount == null) {
        AuthScreen(viewModel = viewModel, modifier = modifier)
        return
    }

    // If an individual conversation is open, render ConversationScreen
    if (uiState.currentOpenChatId != null) {
        ConversationScreen(
            conversationId = uiState.currentOpenChatId!!,
            viewModel = viewModel,
            onNavigateBack = { viewModel.closeChat() },
            modifier = modifier
        )
        return
    }

    Scaffold(
        bottomBar = {
            FortBottomBar(
                currentDestination = currentDestination,
                onNavigate = { currentDestination = it },
                requestsBadgeCount = uiState.inboundRequests.size,
                language = uiState.currentLanguage
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentDestination) {
                FortDestination.CHATS -> {
                    ChatsHomeScreen(
                        viewModel = viewModel,
                        onNavigateToChat = { chatId -> viewModel.openChat(chatId) },
                        onNavigateToYou = { currentDestination = FortDestination.YOU }
                    )
                }
                FortDestination.CIRCLES -> {
                    CirclesRoomsScreen(viewModel = viewModel)
                }
                FortDestination.REQUESTS -> {
                    KnockFirstRequestsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentDestination = FortDestination.CHATS }
                    )
                }
                FortDestination.YOU -> {
                    YouAccessMapScreen(viewModel = viewModel)
                }
            }

            // In-App Toast Alert
            AnimatedVisibility(
                visible = uiState.toastMessage != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
            ) {
                if (uiState.toastMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = uiState.toastMessage!!,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    LaunchedEffect(uiState.toastMessage) {
                        delay(2500)
                        viewModel.clearToast()
                    }
                }
            }
        }
    }

    // Phase 2 Modals triggered globally
    if (uiState.isMoodPickerOpen) {
        MoodRingBottomSheet(
            onDismissRequest = { viewModel.closeMoodPicker() },
            onBroadcast = { emotion, whatINeed, circle, duration ->
                viewModel.broadcastMood(emotion, whatINeed, circle, duration)
            },
            onClear = { viewModel.clearMoodRing() }
        )
    }

    if (uiState.isPassGeneratorOpen) {
        ContactPassGeneratorSheet(
            onDismissRequest = { viewModel.closePassGenerator() },
            onGeneratePass = { cardType, duration ->
                viewModel.generateNewPass(cardType, duration)
            },
            generatedQrBitmap = uiState.generatedPassQrBitmap,
            activePass = uiState.activeGeneratedPass,
            isGenerating = uiState.isGeneratingPass,
            errorMessage = uiState.passGenerationError
        )
    }

    if (uiState.isNewChatMenuOpen) {
        NewChatBottomSheet(
            onDismissRequest = { viewModel.closeNewChatMenu() },
            onScanInvitation = { viewModel.openPassScanner() },
            onCreateInvitation = { viewModel.openPassGenerator() },
            onFindByFortId = { viewModel.openSearchPeople() }
        )
    }

    if (uiState.isSearchPeopleOpen) {
        SearchPeopleModal(
            onDismissRequest = { viewModel.closeSearchPeople() },
            onSearch = { query, mode -> viewModel.searchPeople(query, mode) },
            searchResults = uiState.searchResults,
            isSearching = uiState.isSearchingPeople,
            searchError = uiState.searchPeopleError,
            onOpenChat = { peerUserId ->
                viewModel.closeSearchPeople()
                currentDestination = FortDestination.CHATS
                val chatId = if (peerUserId.startsWith("conv_")) peerUserId else "conv_$peerUserId"
                viewModel.openChat(chatId)
            },
            onKnockFirst = { user, intro -> viewModel.sendKnockFirstRequest(user, intro) }
        )
    }

    if (uiState.isPassScannerOpen) {
        PassScannerModal(
            onDismissRequest = { viewModel.closePassScanner() },
            onQrPayloadDetected = { qrData -> viewModel.onPassScanned(qrData) }
        )
    }

    if (uiState.scannedPassPayload != null) {
        PassClaimPreviewDialog(
            payload = uiState.scannedPassPayload!!,
            isClaiming = uiState.isClaimingPass,
            errorMessage = uiState.passClaimError,
            onAccept = { viewModel.claimScannedPass(uiState.scannedPassPayload!!) },
            onDismiss = { viewModel.dismissScannedPassPreview() }
        )
    }

    if (uiState.activeCallSession != null || uiState.incomingCallSession != null) {
        val session = uiState.activeCallSession ?: uiState.incomingCallSession!!
        val isIncoming = uiState.incomingCallSession != null && uiState.activeCallSession == null
        CallScreenModal(
            session = session,
            isIncomingPrompt = isIncoming,
            webrtcManager = viewModel.webrtcManager,
            onAcceptCall = { viewModel.acceptIncomingCall() },
            onDeclineCall = { viewModel.declineIncomingCall() },
            onEndCall = { viewModel.endCall() },
            onToggleMute = { viewModel.toggleMute() },
            onToggleSpeaker = { viewModel.toggleSpeaker() },
            onToggleVideo = { viewModel.toggleVideo() },
            onSwitchCamera = { viewModel.switchCamera() },
            onPermissionGranted = { viewModel.onCallPermissionsGranted() }
        )
    }

    if (uiState.isLocationShareModalOpen) {
        LocationShareBottomSheet(
            onDismissRequest = { viewModel.closeLocationShareModal() },
            activeLiveSession = uiState.activeLiveLocation,
            onSendStaticPin = { lat, lng, label -> viewModel.sendLocationPin(lat, lng, label) },
            onStartLiveShare = { duration, lat, lng -> viewModel.startLiveLocationSharing(duration, lat, lng) },
            onStopLiveShare = { viewModel.stopLiveLocationSharing() }
        )
    }
}
