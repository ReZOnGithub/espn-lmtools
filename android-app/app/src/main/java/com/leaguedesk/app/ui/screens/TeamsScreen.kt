package com.leaguedesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaguedesk.app.data.LeagueState
import com.leaguedesk.app.data.Player
import com.leaguedesk.app.data.Team
import com.leaguedesk.app.ui.Avatar
import com.leaguedesk.app.ui.EmptyState
import com.leaguedesk.app.ui.GroupLabel
import com.leaguedesk.app.ui.LDButton
import com.leaguedesk.app.ui.MetricTile
import com.leaguedesk.app.ui.PlayerRow
import com.leaguedesk.app.ui.Tag
import com.leaguedesk.app.ui.TagTone
import com.leaguedesk.app.ui.fmt1
import com.leaguedesk.app.ui.theme.LD
import com.leaguedesk.app.ui.theme.hexColor

private enum class SortMode { DEPTH, PPG, NAME }

@Composable
fun TeamsScreen(
    state: LeagueState,
    teamId: Int?,
    selectedIds: Set<Int>,
    onSelectTeam: (Int) -> Unit,
    onPlayerPress: (Player) -> Unit,
    onTogglePlayer: (Player) -> Unit,
    onOpenTrade: (Int?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("ALL") }
    var sort by remember { mutableStateOf(SortMode.DEPTH) }

    val team = state.teams.firstOrNull { it.id == teamId } ?: state.teams.firstOrNull()
    if (team == null) {
        EmptyState(
            icon = Icons.Rounded.Groups,
            title = "No teams",
            body = "This account cannot see any teams in the league. Check the SWID and league id.",
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    val positions = remember(team) {
        listOf("ALL") + team.roster.map { it.position }.filter { it.isNotBlank() && it != "—" }.distinct()
    }
    val filtered = remember(team, query, position, sort) {
        var list = team.roster
        if (query.isNotBlank()) {
            val needle = query.trim().lowercase()
            list = list.filter {
                "${it.name} ${it.position} ${it.proTeam} ${it.slot}".lowercase().contains(needle)
            }
        }
        if (position != "ALL") list = list.filter { it.position == position }
        when (sort) {
            SortMode.PPG -> list.sortedByDescending { it.fppg ?: 0.0 }
            SortMode.NAME -> list.sortedBy { it.name }
            SortMode.DEPTH -> list
        }
    }
    val starters = filtered.filter { it.isStarter }
    val bench = filtered.filter { !it.isStarter }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TeamRail(state, team.id, onSelectTeam)
        }
        item {
            TeamHero(
                team = team,
                myTeamId = state.myTeamId,
                onOpenTrade = onOpenTrade,
                onSelectTeam = onSelectTeam,
            )
        }
        item {
            SearchField(query, { query = it })
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    positions.forEach { pos ->
                        FilterPill(pos, position == pos) { position = pos }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterPill("Depth", sort == SortMode.DEPTH) { sort = SortMode.DEPTH }
                    FilterPill("PPG", sort == SortMode.PPG) { sort = SortMode.PPG }
                    FilterPill("A–Z", sort == SortMode.NAME) { sort = SortMode.NAME }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${filtered.size} of ${team.totalPlayers} players", color = LD.Muted, fontSize = 10.sp)
                Text("tap for details", color = LD.Dim, fontSize = 9.5.sp)
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Search,
                    title = "No players match",
                    body = "Try another position, clear the search, or switch teams.",
                    action = {
                        LDButton(
                            text = "Clear filters",
                            onClick = {
                                query = ""
                                position = "ALL"
                            },
                            variant = com.leaguedesk.app.ui.ButtonVariant.SOFT,
                            compact = true,
                        )
                    },
                )
            }
        } else if (sort == SortMode.DEPTH) {
            if (starters.isNotEmpty()) {
                item { GroupLabel("Starters", starters.size) }
                items(starters, key = { "s-${it.id}" }) { player ->
                    PlayerCardRow(player, player.id in selectedIds, onPlayerPress, onTogglePlayer)
                }
            }
            if (bench.isNotEmpty()) {
                item { GroupLabel("Bench / IR", bench.size) }
                items(bench, key = { "b-${it.id}" }) { player ->
                    PlayerCardRow(player, player.id in selectedIds, onPlayerPress, onTogglePlayer)
                }
            }
        } else {
            items(filtered, key = { "f-${it.id}" }) { player ->
                PlayerCardRow(player, player.id in selectedIds, onPlayerPress, onTogglePlayer)
            }
        }
    }
}

@Composable
private fun PlayerCardRow(
    player: Player,
    selected: Boolean,
    onPress: (Player) -> Unit,
    onToggle: (Player) -> Unit,
) {
    PlayerRow(
        player = player,
        selected = selected,
        onPress = { onPress(player) },
        onToggle = { onToggle(player) },
    )
}

@Composable
private fun TeamRail(state: LeagueState, selectedId: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(state.teams, key = { it.id }) { team ->
            TeamChip(team, team.id == selectedId) { onSelect(team.id) }
        }
    }
}

@Composable
private fun TeamChip(team: Team, active: Boolean, onClick: () -> Unit) {
    val accent = hexColor(team.accent.a)
    Row(
        modifier = Modifier
            .width(174.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) accent.copy(alpha = 0.13f) else LD.Glass)
            .border(1.dp, if (active) accent else LD.Line, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(team.logo, team.abbrev, 30.dp, 9.dp, accent)
        Column(Modifier.weight(1f)) {
            Text(
                team.name,
                color = if (active) LD.Text else LD.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${team.record.wins}-${team.record.losses} · ${team.totalPlayers}p",
                color = LD.Dim,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
        if (team.isMine) {
            Box(Modifier.size(5.dp).clip(RoundedCornerShape(99.dp)).background(LD.Accent2))
        }
    }
}

@Composable
private fun TeamHero(
    team: Team,
    myTeamId: Int?,
    onOpenTrade: (Int?) -> Unit,
    onSelectTeam: (Int) -> Unit,
) {
    val accent = hexColor(team.accent.a)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.28f), LD.Panel, LD.Panel),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.36f), RoundedCornerShape(26.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Avatar(team.logo, team.abbrev, 62.dp, 18.dp, accent)
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (team.isMine) Tag("Your team", TagTone.ACCENT)
                    Tag("Rank #${team.rank}")
                    team.waiverRank?.let { Tag("Waiver $it") }
                }
                Text(
                    team.name,
                    color = LD.Text,
                    fontSize = 24.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.9).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    team.owners.joinToString(", "),
                    color = LD.Muted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricTile(
                "Record",
                "${team.record.wins}-${team.record.losses}",
                modifier = Modifier.weight(1f),
            )
            MetricTile("Players", "${team.totalPlayers}", modifier = Modifier.weight(1f))
            MetricTile("Team FPPG", fmt1(team.totalFppg), modifier = Modifier.weight(1f))
            MetricTile("Points", fmt1(team.record.pointsFor), modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            LDButton(
                text = if (team.isMine) "Propose a trade" else "Trade with ${team.abbrev}",
                onClick = { onOpenTrade(if (team.isMine) null else team.id) },
                icon = Icons.Rounded.SwapHoriz,
                modifier = Modifier.weight(1f),
            )
            if (!team.isMine && myTeamId != null) {
                LDButton(
                    text = "My team",
                    onClick = { onSelectTeam(myTeamId) },
                    icon = Icons.Rounded.Groups,
                    variant = com.leaguedesk.app.ui.ButtonVariant.SOFT,
                )
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = LD.Glass,
        border = androidx.compose.foundation.BorderStroke(1.dp, LD.Line),
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = LD.Dim, modifier = Modifier.size(17.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text("Search this roster…", color = LD.Dim, fontSize = 13.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = LD.Text, fontSize = 13.sp),
                    cursorBrush = SolidColor(LD.Accent2),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Clear search",
                    tint = LD.Dim,
                    modifier = Modifier
                        .size(17.dp)
                        .clickable { onValueChange("") },
                )
            }
        }
    }
}

@Composable
private fun FilterPill(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) LD.Panel2 else LD.Glass)
            .border(1.dp, if (active) LD.LineStrong else LD.Line, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            if (text == "ALL") "All" else text,
            color = if (active) LD.Text else LD.Dim,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
