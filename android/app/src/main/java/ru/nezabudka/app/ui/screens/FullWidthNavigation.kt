package ru.nezabudka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nezabudka.app.ui.AppTheme

@Composable
internal fun FullWidthNavigation(
    destinations: List<Triple<String, String, ImageVector>>, currentRoute: String?,
    showLabels: Boolean, open: (String) -> Unit,
) {
    val theme = AppTheme.tokens
    val labelHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
        MaterialTheme.typography.labelSmall.lineHeight.toDp()
    }
    val height = if (showLabels) maxOf(68.dp, 43.dp + labelHeight) else 68.dp
    Column(Modifier.fillMaxWidth().background(theme.colors.navigation)) {
        HorizontalDivider(color = theme.colors.outline.copy(alpha = 0.5f), thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth().height(height).selectableGroup()) {
            destinations.forEach { (route, label, icon) ->
                val selected = currentRoute == route
                // Update together with the route: no outgoing-tab fade or glyph remeasurement.
                val color = if (selected) theme.colors.accent else theme.colors.textSecondary
                val background = if (selected) theme.colors.selected else Color.Transparent
                Column(Modifier.weight(1f).fillMaxHeight()
                    .selectable(selected = selected, role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = { open(route) })
                    .padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Box(Modifier.width(56.dp).height(30.dp).clip(RoundedCornerShape(theme.shapes.tag))
                        .background(background), contentAlignment = Alignment.Center) {
                        Icon(icon, if (showLabels) null else label, Modifier.size(24.dp), tint = color)
                    }
                    if (showLabels) {
                        Spacer(Modifier.height(3.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
