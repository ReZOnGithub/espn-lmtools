package com.leaguedesk.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.leaguedesk.app.data.Notice
import com.leaguedesk.app.data.NoticeTone
import com.leaguedesk.app.data.Player
import com.leaguedesk.app.data.Team
import com.leaguedesk.app.ui.theme.LD
import com.leaguedesk.app.ui.theme.hexColor

enum class ButtonVariant { PRIMARY, SOFT, GHOST, DANGER }
enum class TagTone { DEFAULT, GOOD, WARN, BAD, ACCENT }

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1020), LD.Bg, LD.Bg),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .padding(start = 220.dp, top = 0.dp)
                .background(
                    Brush.radialGradient(listOf(LD.Accent.copy(alpha = 0.20f), Color.Transparent)),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .padding(top = 260.dp)
                .background(
                    Brush.radialGradient(listOf(LD.Accent2.copy(alpha = 0.10f), Color.Transparent)),
                    CircleShape,
                ),
        )
        content()
    }
}

@Composable
fun Avatar(
    url: String?,
    label: String,
    size: Dp,
    corner: Dp = 12.dp,
    accent: Color = LD.Accent,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(accent.copy(alpha = 0.13f))
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase().take(3),
            color = accent,
            fontSize = (size.value * 0.30f).sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (!url.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (painter.state is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

@Composable
fun Tag(
    text: String,
    tone: TagTone = TagTone.DEFAULT,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val color = when (tone) {
        TagTone.DEFAULT -> LD.Muted
        TagTone.GOOD -> LD.Good
        TagTone.WARN -> LD.Warn
        TagTone.BAD -> LD.Bad
        TagTone.ACCENT -> LD.Accent2
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(10.dp))
        }
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun LDButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    val background: Brush = when (variant) {
        ButtonVariant.PRIMARY -> Brush.linearGradient(listOf(LD.Accent, Color(0xFF6F5CFF)))
        ButtonVariant.SOFT -> Brush.linearGradient(listOf(LD.GlassStrong, LD.Glass))
        ButtonVariant.GHOST -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
        ButtonVariant.DANGER -> Brush.linearGradient(
            listOf(LD.Bad.copy(alpha = 0.13f), LD.Bad.copy(alpha = 0.07f)),
        )
    }
    val contentColor = when (variant) {
        ButtonVariant.PRIMARY -> LD.White
        ButtonVariant.SOFT -> LD.Text
        ButtonVariant.GHOST -> LD.Muted
        ButtonVariant.DANGER -> LD.Bad
    }
    val borderColor = when (variant) {
        ButtonVariant.PRIMARY -> Color.Transparent
        ButtonVariant.SOFT -> LD.Line
        ButtonVariant.GHOST -> LD.LineStrong
        ButtonVariant.DANGER -> LD.Bad.copy(alpha = 0.35f)
    }
    Box(
        modifier = modifier
            .height(if (compact) 34.dp else 46.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled && !loading) { onClick() }
            .padding(horizontal = if (compact) 11.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(if (compact) 13.dp else 16.dp),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
                icon != null -> Icon(
                    icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                )
            }
            if (!loading || compact) {
                Text(
                    text = text,
                    color = if (enabled) contentColor else contentColor.copy(alpha = 0.45f),
                    fontSize = if (compact) 11.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun IconAction(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    tint: Color = LD.Muted,
    container: Color = LD.Glass,
    border: Color = LD.Line,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape((size.value * 0.30f).dp))
            .background(container)
            .border(1.dp, border, RoundedCornerShape((size.value * 0.30f).dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun ValueBar(left: Double, right: Double, modifier: Modifier = Modifier) {
    val total = left + right
    val fraction = if (total > 0) (left / total).toFloat() else 0.5f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(LD.GlassStrong),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .weight(fraction.coerceAtLeast(0.0001f))
                .background(LD.Accent),
        )
        Box(
            Modifier
                .fillMaxHeight()
                .weight((1f - fraction).coerceAtLeast(0.0001f))
                .background(LD.Accent2),
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(LD.Glass)
            .border(1.dp, LD.Line, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            label.uppercase(),
            color = LD.Dim,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
        Text(
            value,
            color = LD.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-0.8).sp,
            maxLines = 1,
        )
        if (sub != null) {
            Text(sub, color = LD.Dim, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
fun PlayerRow(
    player: Player,
    selected: Boolean,
    onPress: () -> Unit,
    onToggle: () -> Unit,
) {
    val accent = LD.Accent
    Surface(
        onClick = onPress,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) LD.Accent2.copy(alpha = 0.055f) else LD.Glass,
        border = BorderStroke(1.dp, if (selected) LD.Accent2 else LD.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Avatar(player.headshot, player.initials, 52.dp, 13.dp, accent)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        player.name,
                        color = LD.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (player.injured && player.injuryStatus.isNotBlank()) {
                        Tag(shortInjury(player.injuryStatus), TagTone.BAD)
                    }
                }
                Text(
                    "${player.position} · ${player.proTeam.ifBlank { "FA" }} · ${player.slot}",
                    color = LD.Dim,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${pct(player.ownership.owned)} owned", color = LD.Dim, fontSize = 9.5.sp)
                    if (player.tradeLocked) {
                        Tag("locked", TagTone.WARN)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmt1(player.fppg),
                    color = LD.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-0.6).sp,
                )
                Text("FPPG", color = LD.Dim, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) LD.Accent2 else LD.GlassStrong)
                        .border(1.dp, if (selected) Color.Transparent else LD.LineStrong, RoundedCornerShape(8.dp))
                        .clickable { onToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (selected) Icons.Rounded.Remove else Icons.Rounded.Add,
                        contentDescription = if (selected) "Remove from trade" else "Add to trade",
                        tint = if (selected) LD.Bg else LD.Muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun NoticeBar(notice: Notice, onDismiss: () -> Unit) {
    val (color, icon) = when (notice.tone) {
        NoticeTone.SUCCESS -> LD.Good to Icons.Rounded.Check
        NoticeTone.ERROR -> LD.Bad to Icons.Rounded.Warning
        NoticeTone.INFO -> LD.Accent2 to Icons.Rounded.Info
    }
    Surface(
        color = LD.Panel2,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(
                notice.text,
                color = LD.Text,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f),
            )
            IconAction(
                Icons.Rounded.Close,
                onDismiss,
                "Dismiss",
                size = 28.dp,
                container = Color.Transparent,
                border = Color.Transparent,
                tint = LD.Dim,
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LD.Glass)
            .border(1.dp, LD.LineStrong, RoundedCornerShape(20.dp))
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(LD.Accent.copy(alpha = 0.12f))
                .border(1.dp, LD.Accent.copy(alpha = 0.3f), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = LD.Accent, modifier = Modifier.size(26.dp))
        }
        Text(
            title,
            color = LD.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            body,
            color = LD.Dim,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = if (action != null) 16.dp else 0.dp),
        )
        action?.invoke()
    }
}

@Composable
fun GroupLabel(text: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text.uppercase(),
            color = LD.Dim,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
        )
        Text("$count", color = LD.Dim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        Box(Modifier.height(1.dp).weight(1f).background(LD.Line))
    }
}
