package com.example.multivo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.multivo.data.Languages
import com.example.multivo.ui.theme.Motion
import com.example.multivo.ui.theme.MultivoBrush
import com.example.multivo.ui.theme.Online

// Avatar colors: tinted backgrounds that sit well on the dark plum screens.
private val avatarPalette = listOf(
    Color(0xFF3B2C66) to Color(0xFFD9CCFF),
    Color(0xFF4A2150) to Color(0xFFFFC7EA),
    Color(0xFF2A2A63) to Color(0xFFC8D1FF),
    Color(0xFF4B2A3F) to Color(0xFFFFD2C2),
    Color(0xFF1F3A4F) to Color(0xFFBFE6FF)
)

fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    online: Boolean? = null,
    ring: Boolean = false
) {
    val (bg, fg) = avatarPalette[(name.hashCode() and Int.MAX_VALUE) % avatarPalette.size]
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (ring) Modifier.border(2.dp, MultivoBrush.accent, CircleShape).padding(3.dp) else Modifier)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initialsOf(name),
                color = fg,
                style = when {
                    size >= 72.dp -> MaterialTheme.typography.headlineSmall
                    size >= 48.dp -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.labelMedium
                }
            )
        }
        if (online == true) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size / 4)
                    .clip(CircleShape)
                    .background(Online)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }
    }
}

@Composable
fun LanguageChip(code: String?, modifier: Modifier = Modifier) {
    if (code.isNullOrBlank()) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(Languages.nameOf(code), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/** Text field colors for the dark purple look. */
@Composable
fun multivoFieldColors(): TextFieldColors {
    val c = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = c.surfaceContainerHigh,
        unfocusedContainerColor = c.surfaceContainer,
        disabledContainerColor = c.surfaceContainer,
        focusedBorderColor = c.primary,
        unfocusedBorderColor = c.outlineVariant,
        focusedLabelColor = c.primary,
        unfocusedLabelColor = c.onSurfaceVariant,
        focusedLeadingIconColor = c.primary,
        unfocusedLeadingIconColor = c.onSurfaceVariant,
        cursorColor = c.secondary,
        focusedPlaceholderColor = c.onSurfaceVariant,
        unfocusedPlaceholderColor = c.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePicker(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "I read messages in"
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = Languages.nameOf(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Filled.Translate, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = MaterialTheme.shapes.small,
            colors = multivoFieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Languages.all.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.name) },
                    onClick = {
                        onSelected(language.code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Reveal(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MultivoBrush.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background))
            }
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MultivoBrush.accent)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = MaterialTheme.colorScheme.onSecondary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** Full-color pill button filled with the violet-to-pink gradient. Dims slowly when disabled. */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    content: @Composable RowScope.() -> Unit
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, Motion.slow(), label = "buttonAlpha")
    Box(
        modifier
            .heightIn(min = 52.dp)
            .alpha(alpha)
            .clip(shape)
            .background(MultivoBrush.accent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimary) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    content = content
                )
            }
        }
    }
}

/** Rounded dark card with a faint gradient fill and hairline border. */
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.outlineVariant,
        Motion.slow(), label = "cardBorder"
    )
    Column(
        modifier
            .clip(shape)
            .background(MultivoBrush.card)
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        content = content
    )
}

/** Small uppercase label with a gradient tick, used above groups of items. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(3.dp).clip(CircleShape).background(MultivoBrush.accent))
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Fills the screen with the plum backdrop. */
fun Modifier.screenBackdrop(): Modifier = this.background(MultivoBrush.screen)
