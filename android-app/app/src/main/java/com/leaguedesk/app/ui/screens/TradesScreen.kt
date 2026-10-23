package com.leaguedesk.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaguedesk.app.data.AcceptOption
import com.leaguedesk.app.data.LeagueState
import com.leaguedesk.app.data.PendingTrade
import com.leaguedesk.app.data.TradeSide
import com.leaguedesk.app.ui.Avatar
import com.leaguedesk.app.ui.EmptyState
import com.leaguedesk.app.ui.LDButton
import com.leaguedesk.app.ui.ButtonVariant
import com.leaguedesk.app.ui.Tag
import com.leaguedesk.app.ui.TagTone
import com.leaguedesk.app.ui.ValueBar
import com.leaguedesk.app.ui.expiresIn
import com.leaguedesk.app.ui.fmt1
import com.leaguedesk.app.ui.timeAgo
import com.leaguedesk.app.ui.theme.LD
import com.leaguedesk.app.ui.theme.hexColor

@Composable
fun TradesScreen(
    state: LeagueState,
    lm: Boolean,
    accountIsLM: Boolean,
    busy: Boolean,
    onNewTrade: () -> Unit,
    onAccept: (PendingTrade, AcceptOption) -> Unit,
    onCancel: (PendingTrade) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Trade desk", color = LD.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
                    Text(
                        if (state.pendingTrades.isEmpty()) {
                            "No offers on the board right now."
                        } else {
                            "${state.pendingTrades.size} offers waiting on a response or processing."
                        },
                        color = LD.Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                if (state.myTeamId != null) {
                    LDButton("New", onNewTrade, icon = Icons.Rounded.Add, compact = true)
                }
            }
        }

        if (state.pendingTrades.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.SwapHoriz,
                    title = "Nothing on the board",
                    body = "Build an offer from the Teams tab. Pull the header refresh after sending one.",
                    action = if (state.myTeamId != null) {
                        {
                            LDButton(
                                "Propose a trade",
                                onNewTrade,
                                icon = Icons.Rounded.SwapHoriz,
                            )
                        }
                    } else null,
                )
            }
        } else {
            items(state.pendingTrades, key = { it.id }) { trade ->
                TradeCard(
                    trade = trade,
                    state = state,
                    lm = lm,
                    accountIsLM = accountIsLM,
                    busy = busy,
                    onAccept = onAccept,
                    onCancel = onCancel,
                )
            }
        }

        item {
            Text(
                "Accepting for a team you don't own requires LM mode. The app raises the flag, " +
                    "sends the accept, then restores the original value.",
                color = LD.Dim,
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun TradeCard(
    trade: PendingTrade,
    state: LeagueState,
    lm: Boolean,
    accountIsLM: Boolean,
    busy: Boolean,
    onAccept: (PendingTrade, AcceptOption) -> Unit,
    onCancel: (PendingTrade) -> Unit,
) {
    val ownOptions = trade.acceptOptions.filter { !it.requiresLM }
    val lmOptions = trade.acceptOptions.filter { it.requiresLM }
    val showLM = lm || accountIsLM
    val direction = when (trade.direction) {
        "incoming" -> "Incoming offer"
        "outgoing" -> "Your offer"
        else -> "League trade"
    }
    val time = if (trade.status == "pending") {
        expiresIn(trade.expirationDate) ?: timeAgo(trade.lastEventDate)
    } else {
        timeAgo(trade.lastEventDate)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = LD.Glass,
        border = BorderStroke(1.dp, LD.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (trade.status == "processing") LD.Accent2 else LD.Warn),
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(direction, color = LD.Text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Tag(
                            if (trade.direction == "incoming") "to you" else if (trade.direction == "outgoing") "from you" else "league",
                            if (trade.direction == "incoming") TagTone.GOOD else if (trade.direction == "outgoing") TagTone.ACCENT else TagTone.DEFAULT,
                        )
                        if (ownOptions.isNotEmpty() || (showLM && lmOptions.isNotEmpty())) {
                            Tag(
                                if (trade.canCancel) "waiting" else "action",
                                TagTone.DEFAULT,
                            )
                        }
                    }
                    Text(
                        "${trade.proposerTeam} proposed · $time",
                        color = LD.Dim,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                if (trade.status == "processing") {
                    Tag("processing", TagTone.ACCENT)
                }
            }

            trade.sides.take(2).forEachIndexed { index, side ->
                if (index > 0) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = null, tint = LD.Dim, modifier = Modifier.size(16.dp))
                    }
                }
                SideBlock(side)
            }

            ValueBar(trade.sides.getOrNull(0)?.value ?: 0.0, trade.sides.getOrNull(1)?.value ?: 0.0)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${trade.sides.getOrNull(0)?.teamName.orEmpty()} ${fmt1(trade.sides.getOrNull(0)?.value)}",
                    color = LD.Dim,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${fmt1(trade.sides.getOrNull(1)?.value)} ${trade.sides.getOrNull(1)?.teamName.orEmpty()}",
                    color = LD.Dim,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ownOptions.forEach { option ->
                    LDButton(
                        text = "Accept",
                        onClick = { onAccept(trade, option) },
                        icon = Icons.Rounded.Check,
                        compact = true,
                        enabled = !busy,
                    )
                }
                if (showLM) {
                    lmOptions.forEach { option ->
                        LDButton(
                            text = "Accept as ${teamAbbrev(state, option.teamId)}",
                            onClick = { onAccept(trade, option) },
                            icon = Icons.Rounded.Shield,
                            variant = ButtonVariant.SOFT,
                            compact = true,
                            enabled = !busy,
                        )
                    }
                }
                if (trade.canCancel) {
                    LDButton(
                        text = "Cancel",
                        onClick = { onCancel(trade) },
                        icon = Icons.Rounded.Close,
                        variant = ButtonVariant.GHOST,
                        compact = true,
                        enabled = !busy,
                    )
                }
                if (ownOptions.isEmpty() && (!showLM || lmOptions.isEmpty()) && trade.status == "pending") {
                    Text(
                        "Waiting on " + trade.needsAcceptFrom.joinToString(", ") { teamAbbrev(state, it) },
                        color = LD.Dim,
                        fontSize = 9.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SideBlock(side: TradeSide) {
    val accent = hexColor(side.accent.a)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (side.isMine) LD.Accent2.copy(alpha = 0.045f) else LD.Glass)
            .border(1.dp, if (side.isMine) LD.Accent2.copy(alpha = 0.24f) else LD.Line, RoundedCornerShape(14.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Avatar(side.teamLogo, side.teamName.take(3), 22.dp, 6.dp, accent)
            Text(
                side.teamName + if (side.isMine) " (you)" else "",
                color = LD.Text,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (side.accepted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, null, tint = LD.Good, modifier = Modifier.size(10.dp))
                    Text(" accepted", color = LD.Good, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        side.players.forEach { player ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(LD.Glass)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Avatar(player.headshot, player.name.take(2), 26.dp, 7.dp, accent)
                Text(
                    player.name,
                    color = LD.Text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(player.position, color = LD.Dim, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Text(
                    fmt1(player.fppg),
                    color = LD.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private fun teamAbbrev(state: LeagueState, teamId: Int): String =
    state.teams.firstOrNull { it.id == teamId }?.abbrev ?: "T$teamId"
