package com.leaguedesk.app.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaguedesk.app.data.LeagueState
import com.leaguedesk.app.data.Player
import com.leaguedesk.app.data.Team
import com.leaguedesk.app.data.TradeDraft
import com.leaguedesk.app.ui.Avatar
import com.leaguedesk.app.ui.ButtonVariant
import com.leaguedesk.app.ui.IconAction
import com.leaguedesk.app.ui.LDButton
import com.leaguedesk.app.ui.ValueBar
import com.leaguedesk.app.ui.fmt1
import com.leaguedesk.app.ui.theme.LD
import com.leaguedesk.app.ui.theme.hexColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeSheet(
    draft: TradeDraft,
    state: LeagueState,
    lm: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onChange: (TradeDraft) -> Unit,
    onPropose: (TradeDraft) -> Unit,
) {
    var pickerSide by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val myTeam = state.teams.firstOrNull { it.id == state.myTeamId }
    val partner = state.teams.firstOrNull { it.id == draft.partnerId }
    val playersById = remember(state.teams) { state.teams.flatMap { it.roster }.associateBy { it.id } }
    val give = draft.give.mapNotNull { playersById[it] }
    val receive = draft.receive.mapNotNull { playersById[it] }
    val giveValue = give.sumOf { it.fppg ?: 0.0 }
    val receiveValue = receive.sumOf { it.fppg ?: 0.0 }
    val canPropose = partner != null && (give.isNotEmpty() || receive.isNotEmpty()) && !busy
    val pickerTeam = if (pickerSide == "give") myTeam else partner
    val pickerSelection = if (pickerSide == "give") draft.give else draft.receive
    val pickerPlayers = (pickerTeam?.roster ?: emptyList()).filter {
        query.isBlank() || "${it.name} ${it.position} ${it.proTeam}".contains(query, ignoreCase = true)
    }

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
                .fillMaxHeight(0.94f)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (pickerSide != null) {
                    IconAction(
                        Icons.Rounded.ArrowBack,
                        { pickerSide = null; query = "" },
                        "Back to trade",
                        size = 34.dp,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (pickerSide == null) "TRADE BUILDER" else "PLAYER PICKER",
                        color = LD.Dim,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        when (pickerSide) {
                            "give" -> "Send from ${myTeam?.name ?: "your team"}"
                            "receive" -> "Receive from ${partner?.name ?: "partner"}"
                            else -> "Propose a trade"
                        },
                        color = LD.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.35).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (pickerSide != null) {
                    LDButton(
                        "Done",
                        { pickerSide = null; query = "" },
                        icon = Icons.Rounded.Check,
                        variant = ButtonVariant.SOFT,
                        compact = true,
                    )
                } else {
                    IconAction(Icons.Rounded.Close, onDismiss, "Close trade builder", size = 34.dp)
                }
            }

            if (pickerSide != null) {
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search this roster…", color = LD.Dim, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = LD.Dim, modifier = Modifier.size(17.dp)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(13.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = LD.Text, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LD.Accent,
                            unfocusedBorderColor = LD.Line,
                            focusedContainerColor = LD.Glass,
                            unfocusedContainerColor = LD.Glass,
                            cursorColor = LD.Accent2,
                        ),
                    )
                    Text(
                        "${pickerSelection.size} selected · tap a player to add or remove",
                        color = LD.Dim,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                    if (pickerTeam == null) {
                        Text(
                            "Choose a trade partner first.",
                            color = LD.Warn,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(pickerPlayers, key = { it.id }) { player ->
                                PickerRow(
                                    player = player,
                                    accent = hexColor(pickerTeam.accent.a),
                                    selected = player.id in pickerSelection,
                                    onClick = {
                                        val nextSet = if (player.id in pickerSelection) {
                                            pickerSelection - player.id
                                        } else {
                                            pickerSelection + player.id
                                        }
                                        onChange(
                                            if (pickerSide == "give") draft.copy(give = nextSet)
                                            else draft.copy(receive = nextSet),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "TRADE WITH",
                        color = LD.Dim,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        state.teams.filter { it.id != state.myTeamId }.forEach { team ->
                            PartnerChip(
                                team = team,
                                active = team.id == draft.partnerId,
                                onClick = {
                                    if (team.id != draft.partnerId) {
                                        onChange(draft.copy(partnerId = team.id, receive = emptySet()))
                                    }
                                },
                            )
                        }
                    }

                    SideBlock(
                        kicker = "You send",
                        team = myTeam,
                        value = giveValue,
                        players = give,
                        onPick = { pickerSide = "give" },
                        onRemove = { id -> onChange(draft.copy(give = draft.give - id)) },
                        accent = hexColor(myTeam?.accent?.a),
                    )

                    SideBlock(
                        kicker = "You receive",
                        team = partner,
                        value = receiveValue,
                        players = receive,
                        onPick = { if (partner != null) pickerSide = "receive" },
                        onRemove = { id -> onChange(draft.copy(receive = draft.receive - id)) },
                        accent = hexColor(partner?.accent?.a),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(LD.Glass)
                            .border(1.dp, LD.Line, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            ValueLabel("You send", giveValue)
                            ValueLabel("You receive", receiveValue, alignEnd = true)
                        }
                        ValueBar(giveValue, receiveValue)
                        Text(
                            verdict(giveValue, receiveValue),
                            color = LD.Dim,
                            fontSize = 9.5.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }

                    Text(
                        "MESSAGE · OPTIONAL",
                        color = LD.Dim,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    )
                    OutlinedTextField(
                        value = draft.comment,
                        onValueChange = { value -> onChange(draft.copy(comment = value.take(200))) },
                        placeholder = { Text("Add a note to your offer…", color = LD.Dim, fontSize = 13.sp) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = LD.Text, fontSize = 13.sp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LD.Accent,
                            unfocusedBorderColor = LD.Line,
                            focusedContainerColor = LD.Glass,
                            unfocusedContainerColor = LD.Glass,
                            cursorColor = LD.Accent2,
                        ),
                    )

                    if (lm) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Rounded.Shield, null, tint = LD.Warn, modifier = Modifier.size(14.dp))
                            Text(
                                "LM mode is armed. This proposal still goes out as your own team, so no escalation is required.",
                                color = LD.Dim,
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LD.Panel)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LDButton(
                        "Propose trade",
                        { onPropose(draft) },
                        icon = Icons.Rounded.SwapHoriz,
                        enabled = canPropose,
                        loading = busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "ESPN may reject locked, recently dropped, or undroppable players.",
                        color = LD.Dim,
                        fontSize = 9.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PartnerChip(team: Team, active: Boolean, onClick: () -> Unit) {
    val accent = hexColor(team.accent.a)
    Row(
        modifier = Modifier
            .width(174.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) accent.copy(alpha = 0.16f) else LD.Glass)
            .border(1.dp, if (active) accent else LD.Line, RoundedCornerShape(11.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Avatar(team.logo, team.abbrev, 24.dp, 7.dp, accent)
        Text(
            team.name,
            color = if (active) LD.Text else LD.Muted,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SideBlock(
    kicker: String,
    team: Team?,
    value: Double,
    players: List<Player>,
    onPick: () -> Unit,
    onRemove: (Int) -> Unit,
    accent: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LD.Glass)
            .border(1.dp, LD.Line, RoundedCornerShape(14.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$kicker  ${fmt1(value)}",
                    color = LD.Dim,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                )
                Text(
                    team?.name ?: "Choose a partner",
                    color = LD.Text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LDButton(
                "Pick",
                onPick,
                icon = Icons.Rounded.Add,
                variant = ButtonVariant.SOFT,
                compact = true,
                enabled = team != null,
            )
        }

        if (players.isEmpty()) {
            Text(
                if (kicker == "You send") "Add players from your roster."
                else "Add players from the partner roster.",
                color = LD.Dim,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else {
            players.forEach { player ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(LD.Glass)
                        .padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Avatar(player.headshot, player.initials, 34.dp, 9.dp, accent)
                    Column(Modifier.weight(1f)) {
                        Text(
                            player.name,
                            color = LD.Text,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${player.position} · ${player.proTeam} · ${fmt1(player.fppg)} FPPG",
                            color = LD.Dim,
                            fontSize = 9.sp,
                            maxLines = 1,
                        )
                    }
                    IconAction(
                        Icons.Rounded.Close,
                        { onRemove(player.id) },
                        "Remove ${player.name}",
                        size = 26.dp,
                        container = Color.Transparent,
                        border = Color.Transparent,
                        tint = LD.Dim,
                    )
                }
            }
        }
    }
}

@Composable
private fun ValueLabel(label: String, value: Double, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            label.uppercase(),
            color = LD.Dim,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                fmt1(value),
                color = LD.Text,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
            )
            Text(" fppg", color = LD.Dim, fontSize = 9.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun PickerRow(
    player: Player,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = if (selected) LD.Accent2.copy(alpha = 0.06f) else LD.Glass,
        border = BorderStroke(1.dp, if (selected) LD.Accent2 else LD.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(player.headshot, player.initials, 40.dp, 10.dp, accent)
            Column(Modifier.weight(1f)) {
                Text(
                    player.name,
                    color = LD.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${player.position} · ${player.proTeam} · ${fmt1(player.fppg)} FPPG",
                    color = LD.Dim,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) LD.Accent2 else LD.GlassStrong)
                    .border(1.dp, if (selected) Color.Transparent else LD.LineStrong, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Rounded.Check, null, tint = LD.Bg, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

private fun verdict(give: Double, receive: Double): String {
    if (give <= 0 && receive <= 0) return "Add players to see the balance."
    val max = maxOf(give, receive, 1.0)
    return if (kotlin.math.abs(give - receive) / max < 0.08) {
        "Balanced by fantasy PPG."
    } else if (give > receive) {
        "You're sending the stronger side by fantasy PPG."
    } else {
        "You're receiving the stronger side by fantasy PPG."
    }
}

