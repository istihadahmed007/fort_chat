package com.fort.messenger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fort.messenger.ui.theme.RoyalBluePrimary

enum class ChatFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    ROOMS("Rooms"),
    EXPIRING("Expiring")
}

@Composable
fun FilterChipBar(
    selectedFilter: ChatFilter,
    onFilterSelected: (ChatFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatFilter.values().forEach { filter ->
            val isSelected = filter == selectedFilter
            val bgColor = if (isSelected) RoyalBluePrimary else MaterialTheme.colorScheme.surface
            val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            val borderColor = if (isSelected) RoyalBluePrimary else MaterialTheme.colorScheme.outline

            // 48x48dp accessibility touch target requirement strictly observed
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter.label,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
    }
}
