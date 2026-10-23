package com.leaguedesk.app.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaguedesk.app.data.AcceptOption
import com.leaguedesk.app.data.PendingTrade
import com.leaguedesk.app.data.Team
import com.leaguedesk.app.ui.Avatar
import com.leaguedesk.app.ui.ButtonVariant
import com.leaguedesk.app.ui.IconAction
import com.leaguedesk.app.ui.LDButton
import com.leaguedesk.app.ui.fmt1
import com.leaguedesk.app.ui.theme.LD
import com.leaguedesk.app.ui.theme.hexColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptSheet(
    trade: PendingTrade?,
    teams: List<Team>,
    accountIsLM: Boolean,
    lm: Boolean,
    busy: Boolean,
    initialTeamId: Int? = null,
    onDismiss: () -> Unit,
    onAccept: (AcceptOption) -> Unit,
) {
    if (trade == null) return
    val options = trade.acceptOptions.filter { !it.requiresLM || lm || accountIsLM }
    var selectedTeamId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(trade.id, lm, accountIsLM, initialTeamId) {
        val preferred = options.firstOrNull { it.teamId == initialTeamId }
            ?: options.firstOrNull { !it.requiresLM }
            ?: options.firstOrNull()
        selectedTeamId = preferred?.teamId
    }
    val selected = options.firstOrNull { it.teamId == selectedTeamId } ?: options.firstOrNull()
    val needsLM = selected?.requiresLM == true && !accountIsLM
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "CONFIRM",
                        color = LD.Dim,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        "Accept trade",
                        color = LD.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                    )
                }
                IconAction(Icons.Rounded.Close, onDismiss, "Close", size = 34.dp)
            }

            Text(
                "ACCEPT AS",
                color = LD.Dim,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            options.forEach { option ->
                val team = teams.firstOrNull { it.id == option.teamId }
                val active = option.teamId == selected?.teamId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (active) LD.Accent.copy(alpha = 0.10f) else LD.Glass)
                        .border(1.dp, if (active) LD.Accent else LD.Line, RoundedCornerShape(13.dp))
                        .clickable { selectedTeamId = option.teamId }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatar(
                        team?.logo,
                        team?.abbrev ?: "T",
                        34.dp,
                        9.dp,
                        hexColor(team?.accent?.a),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            option.teamName,
                            color = LD.Text,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (option.requiresLM && !accountIsLM) "LM override required" else "You own this team",
                            color = LD.Dim,
                            fontSize = 9.5.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (active) LD.Accent else LD.GlassStrong)
                            .border(1.dp, if (active) LD.Accent else LD.LineStrong, RoundedCornerShape(99.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(LD.Bg),
                            )
                        }
                    }
                }
            }

            if (options.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LD.Warn.copy(alpha = 0.06f))
                        .border(1.dp, LD.Warn.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = LD.Warn, modifier = Modifier.size(16.dp))
                    Text(
                        "Arm LM mode to accept this trade for another team.",
                        color = LD.Muted,
                        fontSize = 11.sp,
                    )
                }
            }

            Text(
                "TRADE",
                color = LD.Dim,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 2.dp),
            )

            trade.sides.forEach { side ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LD.Glass)
                        .border(1.dp, LD.Line, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        side.teamName,
                        color = LD.Text,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.8f),
                    )
                    Text(
                        side.players.joinToString(", ") { it.name },
                        color = LD.Muted,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.2f),
                    )
                    Text(
                        fmt1(side.value),
                        color = LD.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            if (needsLM) {
                Callout(
                    icon = Icons.Rounded.Shield,
                    text = "LM override. The account is promoted to League Manager, the accept is sent as this team's owner, then the original flag is restored.",
                    tone = LD.Warn,
                )
            }
            if (trade.direction == "outgoing") {
                Callout(
                    icon = Icons.Rounded.Warning,
                    text = "This is your own proposal. Accepting it as the other team completes the loop without them acting.",
                    tone = LD.Warn,
                )
            }

            LDButton(
                "Accept trade",
                { selected?.let(onAccept) },
                icon = Icons.Rounded.Check,
                enabled = selected != null && !busy,
                loading = busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Callout(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tone: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tone.copy(alpha = 0.05f))
            .border(1.dp, tone.copy(alpha = 0.26f), RoundedCornerShape(12.dp))
            .padding(11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = tone, modifier = Modifier.size(15.dp))
        Text(text, color = LD.Muted, fontSize = 10.5.sp, lineHeight = 15.sp)
    }
}

