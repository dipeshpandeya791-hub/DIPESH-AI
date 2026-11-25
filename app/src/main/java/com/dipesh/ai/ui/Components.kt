package com.dipesh.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dipesh.ai.Status

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalDipeshColors.current
    var m = modifier.glass(shape, c)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(contentPadding), content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = LocalDipeshColors.current.textMuted
    )
}

@Composable
fun GlassChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalDipeshColors.current
    val shape = RoundedCornerShape(50)
    val base = modifier.clip(shape)
    val styled = if (selected) {
        base.background(Brush.linearGradient(listOf(c.violet, c.blue)))
    } else {
        base.background(c.glass).border(1.dp, c.glassBorderLo, shape)
    }
    Box(styled.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else c.text
        )
    }
}

@Composable
fun GradientButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    val c = LocalDipeshColors.current
    val shape = RoundedCornerShape(16.dp)
    val brush = if (danger) Brush.linearGradient(listOf(c.danger, c.danger.copy(alpha = 0.7f)))
    else Brush.linearGradient(listOf(c.violet, c.blue))
    Box(
        modifier.clip(shape).background(brush).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
fun OutlineButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalDipeshColors.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.clip(shape).border(1.dp, c.glassBorderHi.copy(alpha = 0.5f), shape).background(c.glass)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.text)
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: Boolean = false
) {
    val c = LocalDipeshColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.text, unfocusedTextColor = c.text,
            focusedBorderColor = c.violet, unfocusedBorderColor = c.glassBorderHi.copy(alpha = 0.4f),
            focusedLabelColor = c.violet, unfocusedLabelColor = c.textMuted,
            cursorColor = c.violet,
            focusedContainerColor = c.glass, unfocusedContainerColor = c.glass
        )
    )
}

@Composable
fun StatusChip(status: Status, modifier: Modifier = Modifier) {
    val c = LocalDipeshColors.current
    val (label, color) = when (status) {
        Status.IDLE -> "READY" to c.success
        Status.LISTENING -> "LISTENING" to c.cyan
        Status.THINKING -> "THINKING" to c.violet
        Status.SPEAKING -> "SPEAKING" to c.blue
    }
    Row(
        modifier.glass(RoundedCornerShape(50), c, elevation = 4).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.text)
    }
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = LocalDipeshColors.current.text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalDipeshColors.current.textMuted)
            }
        }
        trailing()
    }
}
