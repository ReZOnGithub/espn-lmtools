package com.leaguedesk.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaguedesk.app.data.Player
import com.leaguedesk.app.data.Team
import com.leaguedesk.app.ui.Avatar
import com.leaguedesk.app.ui.ButtonVariant
import com.leaguedesk.app.ui.IconAction
import com.leaguedesk.app.ui.LDButton
import com.leaguedesk.app.ui.Tag
import com.leaguedesk.app.ui.TagTone
import com.leaguedesk.app.ui.capitalize
import com.leaguedesk.app.ui.fmt1
import com.leaguedesk.app.ui.pct
import com.leaguedesk.app.ui.shortInjury
import com.leaguedesk.app.ui.theme.LD
import com.leaguedesk.app.ui.theme.hexColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    player: Player?,
    team: Team?,
    selected: Boolean,
    lm: Boolean,
    accountIsLM: Boolean,
    onDismiss: () -> Unit,
    onToggleTrade: () -> Unit,
    onDrop: () -> Unit,
    onNeedLM: () -> Unit,
) {
    if (player == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = hexColor(team?.accent?.a)
    val isMine = team?.isMine == true
    val canDrop = isMine || lm || accountIsLM

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LD.Panel,
        contentColor = LD.Text,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PLAYER PROFILE",
                    color = LD.Dim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f),
                )
                IconAction(Icons.Rounded.Close, onDismiss, "Close player", size = 34.dp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.copy(alpha = 0.10f))
                    .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Avatar(player.headshot, player.initials, 96.dp, 24.dp, accent)
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Tag(player.position, TagTone.ACCENT)
                        Tag(player.proTeam.ifBlank { "Free agent" })
                        if (isMine) Tag("Your roster")
                    }
                    Text(
                        player.name,
                        color = LD.Text,
                        fontSize = 25.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.9).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "${player.proTeamName.ifBlank { "—" }} · ${player.slot} · ${team?.name.orEmpty()}",
                        color = LD.Muted,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (player.injured && player.injuryStatus.isNotBlank()) {
                    Tag(shortInjury(player.injuryStatus), TagTone.BAD, Icons.Rounded.MedicalServices)
                } else {
                    Tag("Healthy", TagTone.GOOD, Icons.Rounded.Check)
                }
                if (player.tradeLocked) Tag("Trade locked", TagTone.WARN)
                if (!player.droppable) Tag("Undroppable", TagTone.WARN)
                if (player.pendingTransactions.isNotEmpty()) Tag("Pending move", TagTone.ACCENT)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerMetric(
                    "Fantasy PPG",
                    fmt1(player.fppg),
                    if (player.gamesPlayed != null) "${player.gamesPlayed} game${if (player.gamesPlayed == 1) "" else "s"}" else "season",
                    Modifier.weight(1f),
                )
                PlayerMetric("Projected", fmt1(player.projFppg), "per game", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerMetric("Rostered", pct(player.ownership.owned), "${pct(player.ownership.started)} started", Modifier.weight(1f))
                PlayerMetric("Last season", fmt1(player.lastFppg), player.lastSeasonLabel ?: "—", Modifier.weight(1f))
            }

            if (player.statLine.isNotEmpty()) {
                Column {
                    Label("Per game · ${player.statsSeason ?: "season"}")
                    player.statLine.chunked(5).forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            row.forEach { stat ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(LD.Glass)
                                        .border(1.dp, LD.Line, RoundedCornerShape(9.dp))
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(stat.value, color = LD.Text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        stat.label,
                                        color = LD.Dim,
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.4.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (player.outlook.isNotBlank()) {
                Column {
                    Label("Season outlook")
                    Text(
                        player.outlook,
                        color = LD.Muted,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            Column {
                Label("Roster")
                val details = listOf(
                    "Fantasy team" to (team?.name ?: "—"),
                    "Slot" to player.slot,
                    "Acquired" to capitalize(player.acquisitionType),
                    "ADP" to (player.ownership.adp?.toString() ?: "—"),
                    "Auction" to (player.ownership.auction?.toString() ?: "—"),
                    "Ownership Î”" to (player.ownership.change?.let {
                        (if (it > 0) "+" else "") + it + "%"
                    } ?: "—"),
                )
                details.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        pair.forEach { (label, value) ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LD.Glass)
                                    .border(1.dp, LD.Line, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                            ) {
                                Text(
                                    label.uppercase(),
                                    color = LD.Dim,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                )
                                Text(
                                    value,
                                    color = LD.Text,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                LDButton(
                    text = if (selected) "Remove from trade" else "Add to trade",
                    onClick = onToggleTrade,
                    variant = ButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f),
                )
                LDButton(
                    text = if (isMine) "Drop" else "Drop via LM",
                    onClick = if (canDrop) onDrop else onNeedLM,
                    icon = Icons.Rounded.DeleteOutline,
                    variant = if (canDrop) ButtonVariant.DANGER else ButtonVariant.GHOST,
                    modifier = Modifier.weight(1f),
                )
            }

            if (!canDrop) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(LD.Warn.copy(alpha = 0.06f))
                        .border(1.dp, LD.Warn.copy(alpha = 0.25f), RoundedCornerShape(11.dp))
                        .padding(11.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = LD.Warn, modifier = Modifier.size(15.dp))
                    Text(
                        "Dropping from another roster requires LM mode. Arm it from the header or Settings.",
                        color = LD.Muted,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 8.dp)
            .width(38.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(LD.LineStrong),
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        color = LD.Dim,
        fontSize = 9.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun PlayerMetric(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(LD.Glass)
            .border(1.dp, LD.Line, RoundedCornerShape(13.dp))
            .padding(12.dp),
    ) {
        Text(label.uppercase(), color = LD.Dim, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        Text(
            value,
            color = LD.Text,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp,
            modifier = Modifier.padding(top = 3.dp),
        )
        Text(sub, color = LD.Dim, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


