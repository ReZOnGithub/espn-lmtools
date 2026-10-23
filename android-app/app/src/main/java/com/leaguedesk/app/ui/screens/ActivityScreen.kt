package com.leaguedesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaguedesk.app.data.ActivityItem
import com.leaguedesk.app.data.LeagueState
import com.leaguedesk.app.data.TradePlayer
import com.leaguedesk.app.ui.Avatar
import com.leaguedesk.app.ui.EmptyState
import com.leaguedesk.app.ui.Tag
import com.leaguedesk.app.ui.TagTone
import com.leaguedesk.app.ui.timeAgo
import com.leaguedesk.app.ui.theme.LD
import com.leaguedesk.app.ui.theme.hexColor

@Composable
fun ActivityScreen(state: LeagueState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text(
                    "League activity",
                    color = LD.Text,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                )
                Text(
                    "Processed trades and roster moves, newest first.",
                    color = LD.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        if (state.activity.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Timeline,
                    title = "Nothing here yet",
                    body = "Completed trades, drops, and adds will appear in this timeline.",
                )
            }
        } else {
            items(state.activity, key = { it.id }) { item ->
                ActivityRow(item, state)
            }
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, state: LeagueState) {
    val isTrade = item.kind == "trade"
    val accent = if (isTrade) LD.Accent2 else LD.Warn
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.08f))
                .border(1.dp, accent.copy(alpha = 0.27f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isTrade) Icons.Rounded.SwapHoriz else Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, color = LD.Text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Tag(item.kind, if (isTrade) TagTone.ACCENT else TagTone.WARN)
                Text(
                    timeAgo(item.date),
                    color = LD.Dim,
                    fontSize = 9.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            Text(item.subtitle, color = LD.Dim, fontSize = 10.sp)

            item.sides.forEach { side ->
                val sideAccent = hexColor(
                    state.teams.firstOrNull { it.id == side.teamId }?.accent?.a,
                    LD.Accent,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LD.Glass)
                        .border(1.dp, LD.Line, RoundedCornerShape(12.dp))
                        .padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Avatar(side.teamLogo, side.teamName.take(3), 18.dp, 5.dp, sideAccent)
                        Text(
                            side.teamName,
                            color = LD.Muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    side.players.forEach { player ->
                        PlayerChip(player, sideAccent, state)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerChip(player: TradePlayer, accent: androidx.compose.ui.graphics.Color, state: LeagueState) {
    val destination = when {
        player.toTeamId != null && player.toTeamId != 0 ->
            "→ " + (state.teams.firstOrNull { it.id == player.toTeamId }?.abbrev ?: "T${player.toTeamId}")
        player.fromTeamId != null -> "released"
        else -> ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(LD.Glass)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Avatar(player.headshot, player.name.take(2), 20.dp, 5.dp, accent)
        Text(
            player.name,
            color = LD.Text,
            fontSize = 9.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (destination.isNotBlank()) {
            Text(destination, color = LD.Dim, fontSize = 8.5.sp)
        }
    }
}
