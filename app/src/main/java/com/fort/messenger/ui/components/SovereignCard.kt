package com.fort.messenger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fort.messenger.ui.theme.IceGlassBorderDark
import com.fort.messenger.ui.theme.IceGlassBorderLight
import com.fort.messenger.ui.theme.IceGlassDark
import com.fort.messenger.ui.theme.IceGlassLight

@Composable
fun SovereignCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    elevation: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val defaultBorder = borderColor ?: if (isDark) IceGlassBorderDark else IceGlassBorderLight
    val gradientBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color(0xFF16233B).copy(alpha = 0.92f),
                Color(0xFF0F172A).copy(alpha = 0.96f)
            )
        } else {
            listOf(
                Color(0xFFFFFFFF).copy(alpha = 0.95f),
                Color(0xFFF1F6FD).copy(alpha = 0.90f)
            )
        }
    )

    Surface(
        modifier = modifier
            .shadow(elevation = elevation, shape = shape, spotColor = Color(0x141E40AF))
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, defaultBorder)
    ) {
        Box(
            modifier = Modifier
                .background(if (backgroundColor != null) Brush.linearGradient(listOf(backgroundColor, backgroundColor)) else gradientBrush)
                .padding(16.dp)
        ) {
            content()
        }
    }
}
