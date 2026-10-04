package com.fort.messenger.ui.modals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.ui.theme.RoyalBluePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatBottomSheet(
    onDismissRequest: () -> Unit,
    onScanInvitation: () -> Unit,
    onCreateInvitation: () -> Unit,
    onFindByFortId: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = "Start a Sovereign Chat",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Connect privately with end-to-end encryption",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            NewChatActionOption(
                icon = Icons.Outlined.QrCodeScanner,
                iconTint = RoyalBluePrimary,
                title = "Scan QR Invitation",
                subtitle = "Scan a peer's pass using camera or enter invite code",
                onClick = {
                    onDismissRequest()
                    onScanInvitation()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            NewChatActionOption(
                icon = Icons.Outlined.QrCode,
                iconTint = Color(0xFF0D9488),
                title = "Create Invitation Pass",
                subtitle = "Generate a single-use or timed QR pass to share",
                onClick = {
                    onDismissRequest()
                    onCreateInvitation()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            NewChatActionOption(
                icon = Icons.Outlined.PersonSearch,
                iconTint = Color(0xFF6366F1),
                title = "Find by Fort ID (Knock-First)",
                subtitle = "Request access to peer using their sovereign ID or handle",
                onClick = {
                    onDismissRequest()
                    onFindByFortId()
                }
            )
        }
    }
}

@Composable
private fun NewChatActionOption(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
