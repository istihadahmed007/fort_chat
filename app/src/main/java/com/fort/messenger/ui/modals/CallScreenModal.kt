package com.fort.messenger.ui.modals

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.fort.messenger.model.CallSession
import com.fort.messenger.model.CallStatus
import com.fort.messenger.model.CallType
import com.fort.messenger.ui.theme.EmeraldVerified
import com.fort.messenger.ui.theme.IceBlueBorder
import com.fort.messenger.ui.theme.RoyalBluePrimary
import com.fort.messenger.webrtc.WebRtcCallManager
import kotlinx.coroutines.delay
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreenModal(
    session: CallSession,
    isIncomingPrompt: Boolean,
    webrtcManager: WebRtcCallManager?,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onPermissionGranted: () -> Unit = {},
    onMinimizeCall: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var callDurationSeconds by remember { mutableLongStateOf(0L) }

    // Call duration timer
    LaunchedEffect(session.status) {
        if (session.status == CallStatus.CONNECTED) {
            callDurationSeconds = 0L
            while (true) {
                delay(1000L)
                callDurationSeconds++
            }
        }
    }

    // Permission handling
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var permissionCallbackSent by remember(session.callId) { mutableStateOf(false) }

    val isPermissionGranted = hasAudioPermission && (session.callType != CallType.VIDEO || hasCameraPermission)

    LaunchedEffect(isPermissionGranted, session.callId) {
        if (isPermissionGranted && !permissionCallbackSent) {
            permissionCallbackSent = true
            permissionDenied = false
            onPermissionGranted()
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasAudioPermission = perms[Manifest.permission.RECORD_AUDIO] == true
        hasCameraPermission = perms[Manifest.permission.CAMERA] == true
        permissionDenied = !(hasAudioPermission && (session.callType != CallType.VIDEO || hasCameraPermission))
    }

    LaunchedEffect(session.callType, session.callId) {
        val needed = mutableListOf<String>()
        if (!hasAudioPermission) needed.add(Manifest.permission.RECORD_AUDIO)
        if (session.callType == CallType.VIDEO && !hasCameraPermission) needed.add(Manifest.permission.CAMERA)
        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        }
    }

    Dialog(
        onDismissRequest = { /* Modal must be dismissed via Hangup/Decline buttons */ },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A), // Deep navy dark glass
                            Color(0xFF1E293B),
                            Color(0xFF0D1B2A)
                        )
                    )
                )
        ) {
            // Optional WebRTC Video Stream Surface (Remote full screen)
            if (session.callType == CallType.VIDEO && session.isVideoEnabled && webrtcManager != null) {
                val remoteVideoTrack = webrtcManager.remoteVideoTrack.collectAsState().value
                if (remoteVideoTrack != null) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                setEnableHardwareScaler(true)
                                webrtcManager.rootEglBase?.eglBaseContext?.let { eglCtx ->
                                    init(eglCtx, null)
                                }
                                remoteVideoTrack.addSink(this)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // PiP Local Video View
                val localVideoTrack = webrtcManager.localVideoTrack.collectAsState().value
                if (localVideoTrack != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp, end = 20.dp)
                            .size(120.dp, 160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.5.dp, IceBlueBorder, RoundedCornerShape(16.dp))
                            .shadow(8.dp)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                SurfaceViewRenderer(ctx).apply {
                                    setMirror(true)
                                    setEnableHardwareScaler(true)
                                    webrtcManager.rootEglBase?.eglBaseContext?.let { eglCtx ->
                                        init(eglCtx, null)
                                    }
                                    localVideoTrack.addSink(this)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Central Frosted Ice-Glass Info Card
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Action Bar with Minimize Button & E2EE Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onMinimizeCall != null) {
                        IconButton(
                            onClick = onMinimizeCall,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B).copy(alpha = 0.75f))
                                .border(1.dp, IceBlueBorder.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Minimize Call",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = EmeraldVerified,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SOVEREIGN E2EE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldVerified,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF38BDF8).copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = if (session.callType == CallType.VIDEO) "HD VIDEO" else "AUDIO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFBAE6FD),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Peer Details & Call Status
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF38BDF8).copy(alpha = 0.35f),
                                        Color(0xFF1E40AF).copy(alpha = 0.15f)
                                    )
                                )
                            )
                            .border(2.dp, Color(0xFFBAE6FD).copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = session.peerDisplayName.take(2).uppercase().ifBlank { "FT" },
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF0F9FF)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = session.peerDisplayName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Status Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                    ) {
                        val statusText = when (session.status) {
                            CallStatus.OUTGOING_RINGING -> "Calling sovereign peer..."
                            CallStatus.INCOMING_RINGING -> "Incoming call..."
                            CallStatus.CONNECTING -> "Establishing WebRTC handshake..."
                            CallStatus.CONNECTED -> {
                                val mins = callDurationSeconds / 60
                                val secs = callDurationSeconds % 60
                                String.format("Encrypted Call • %02d:%02d", mins, secs)
                            }
                            CallStatus.RECONNECTING -> "Reconnecting secure stream..."
                            CallStatus.BUSY -> "Peer busy or unavailable"
                            CallStatus.DECLINED -> "Call declined"
                            CallStatus.ENDED -> "Call ended"
                            CallStatus.FAILED -> session.errorMessage ?: "Connection failed"
                            else -> "Connecting..."
                        }

                        Text(
                            text = statusText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (session.status) {
                                CallStatus.CONNECTED -> EmeraldVerified
                                CallStatus.RECONNECTING -> Color(0xFFF59E0B)
                                else -> Color(0xFFBAE6FD)
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    if (session.callType == CallType.VIDEO) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "HD Video • E2EE WebRTC",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Call Controls Bar
                if (isIncomingPrompt && session.status == CallStatus.INCOMING_RINGING) {
                    // Incoming Prompt: Accept or Decline
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decline
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onDeclineCall,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE11D48))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "Decline",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Decline", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        }

                        // Accept
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onAcceptCall,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldVerified)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Accept",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Accept", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        }
                    }
                } else {
                    // Active or Outgoing Call Controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.85f))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Feature Toggles Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Mute Toggle
                            IconButton(
                                onClick = onToggleMute,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(if (session.isMuted) Color(0xFFE11D48).copy(alpha = 0.25f) else Color(0xFF334155))
                            ) {
                                Icon(
                                    imageVector = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Mute",
                                    tint = if (session.isMuted) Color(0xFFE11D48) else Color.White
                                )
                            }

                            // Speaker Toggle
                            IconButton(
                                onClick = onToggleSpeaker,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(if (session.isSpeakerOn) RoyalBluePrimary.copy(alpha = 0.3f) else Color(0xFF334155))
                            ) {
                                Icon(
                                    imageVector = if (session.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = "Speaker",
                                    tint = if (session.isSpeakerOn) Color(0xFF38BDF8) else Color.White
                                )
                            }

                            // Video Toggle
                            if (session.callType == CallType.VIDEO) {
                                IconButton(
                                    onClick = onToggleVideo,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(if (!session.isVideoEnabled) Color(0xFFE11D48).copy(alpha = 0.25f) else Color(0xFF334155))
                                ) {
                                    Icon(
                                        imageVector = if (session.isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                        contentDescription = "Video Toggle",
                                        tint = if (!session.isVideoEnabled) Color(0xFFE11D48) else Color.White
                                    )
                                }

                                // Camera Switch Toggle
                                IconButton(
                                    onClick = onSwitchCamera,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF334155))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cameraswitch,
                                        contentDescription = "Switch Camera",
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // End Call Button
                        IconButton(
                            onClick = onEndCall,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE11D48))
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End Call",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // Permission Denial Explanation Overlay
            if (permissionDenied) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF20F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IceBlueBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🛡️ Permission Required",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (session.callType == CallType.VIDEO)
                                "Microphone and Camera permissions are required to start this encrypted video call. Fort cannot initialize media without your consent."
                            else
                                "Microphone permission is required to start this encrypted voice call. Fort cannot initialize media without your consent.",
                            fontSize = 14.sp,
                            color = Color(0xFFBAE6FD),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (isIncomingPrompt) onDeclineCall() else onEndCall()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel", color = Color(0xFFFDA4AF))
                            }
                            Button(
                                onClick = {
                                    val needed = mutableListOf<String>()
                                    if (!hasAudioPermission) needed.add(Manifest.permission.RECORD_AUDIO)
                                    if (session.callType == CallType.VIDEO && !hasCameraPermission) needed.add(Manifest.permission.CAMERA)
                                    if (needed.isNotEmpty()) {
                                        permissionsLauncher.launch(needed.toTypedArray())
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBluePrimary)
                            ) {
                                Text("Retry", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveCallTopBanner(
    session: CallSession,
    onExpandCall: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    var durationSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(session.status) {
        if (session.status == CallStatus.CONNECTED) {
            durationSeconds = 0L
            while (true) {
                delay(1000L)
                durationSeconds++
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp), spotColor = Color(0x332563EB))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF0F172A).copy(alpha = 0.95f),
                        Color(0xFF1E293B).copy(alpha = 0.95f)
                    )
                )
            )
            .border(1.dp, IceBlueBorder.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .clickable(onClick = onExpandCall)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Pulsing Green or Blue Dot Indicator
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (session.status == CallStatus.CONNECTED) EmeraldVerified else Color(0xFF38BDF8))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = session.peerDisplayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    val mins = durationSeconds / 60
                    val secs = durationSeconds % 60
                    val durationStr = if (session.status == CallStatus.CONNECTED) {
                        String.format("%02d:%02d • Return to call", mins, secs)
                    } else if (session.status == CallStatus.RECONNECTING) {
                        "Reconnecting • Return to call"
                    } else {
                        "Connecting • Return to call"
                    }
                    Text(
                        text = durationStr,
                        fontSize = 11.5.sp,
                        color = Color(0xFFBAE6FD)
                    )
                }
            }

            // Quick End Call Button
            IconButton(
                onClick = onEndCall,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE11D48))
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
