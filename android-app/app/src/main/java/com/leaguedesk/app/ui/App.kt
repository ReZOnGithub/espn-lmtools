package com.leaguedesk.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaguedesk.app.MainViewModel
import com.leaguedesk.app.data.PendingTrade
import com.leaguedesk.app.data.Player
import com.leaguedesk.app.data.TradeDraft
import com.leaguedesk.app.ui.sheets.AcceptSheet
import com.leaguedesk.app.ui.sheets.PlayerSheet
import com.leaguedesk.app.ui.sheets.TradeSheet
import com.leaguedesk.app.ui.screens.ActivityScreen
import com.leaguedesk.app.ui.screens.ConnectScreen
import com.leaguedesk.app.ui.screens.EspnLoginScreen
import com.leaguedesk.app.ui.screens.SettingsScreen
import com.leaguedesk.app.ui.screens.TeamsScreen
import com.leaguedesk.app.ui.screens.TradesScreen
import com.leaguedesk.app.ui.theme.LD
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: ImageVector) {
    TEAMS("Teams", Icons.Rounded.Groups),
    TRADES("Trades", Icons.Rounded.SwapHoriz),
    ACTIVITY("Activity", Icons.Rounded.Timeline),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

@Composable
fun LeagueDeskApp(vm: MainViewModel) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.TEAMS) }
    var tradeSheetOpen by rememberSaveable { mutableStateOf(false) }
    var playerId by rememberSaveable { mutableStateOf<Int?>(null) }
    var acceptTradeId by rememberSaveable { mutableStateOf<String?>(null) }
    var acceptTeamId by rememberSaveable { mutableStateOf<Int?>(null) }
    var dropTarget by remember { mutableStateOf<Player?>(null) }
    var cancelTarget by remember { mutableStateOf<PendingTrade?>(null) }
    var signOutConfirm by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var loginConfig by remember { mutableStateOf<com.leaguedesk.app.data.LeagueConfig?>(null) }

    val session = vm.session
    val league = vm.league

    LaunchedEffect(vm.notice?.id) {
        val current = vm.notice ?: return@LaunchedEffect
        delay(if (current.tone == com.leaguedesk.app.data.NoticeTone.ERROR) 6500 else 4000)
        vm.dismissNotice(current.id)
    }

    if (showLogin && loginConfig != null) {
        EspnLoginScreen(
            onCookies = { credentials ->
                val config = loginConfig
                showLogin = false
                if (config != null) {
                    vm.connect(credentials, config) { ok ->
                        if (ok) editing = false
                        loginConfig = null
                    }
                }
            },
            onCancel = {
                showLogin = false
                loginConfig = null
            },
        )
        return
    }

    if (session == null || editing) {
        ConnectScreen(
            initialConfig = if (editing) session?.config ?: com.leaguedesk.app.data.LeagueConfig() else com.leaguedesk.app.data.LeagueConfig(),
            connecting = vm.connecting,
            error = vm.connectError,
            onEspnLogin = { config ->
                loginConfig = config
                showLogin = true
            },
            onCancel = if (session != null) {
                {
                    vm.clearConnectError()
                    editing = false
                }
            } else null,
        )
        return
    }

    val allPlayers = remember(league) { league?.teams?.flatMap { it.roster } ?: emptyList() }
    val player = playerId?.let { id -> allPlayers.firstOrNull { it.id == id } }
    val playerTeam = player?.let { p -> league?.teams?.firstOrNull { it.id == p.teamId } }
    val draft: TradeDraft? = vm.draft
    val selectedIds = draft?.let { it.give + it.receive } ?: emptySet()
    val acceptTrade = acceptTradeId?.let { id -> league?.pendingTrades?.firstOrNull { it.id == id } }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Header(
                leagueName = league?.league?.name ?: "League Desk",
                kicker = league?.let { "${it.league.sport} · ${it.league.size} teams" } ?: "League Desk",
                meta = league?.let { "${it.league.seasonLabel} · Week ${it.league.week} · ${it.account.label}" },
                lm = vm.lm,
                refreshing = vm.refreshing,
                onToggleLM = { vm.setLM(!vm.lm) },
                onRefresh = vm::refresh,
            )

            AnimatedVisibility(
                visible = vm.lm,
                enter = fadeIn() + slideInVertically { -it / 3 },
                exit = fadeOut() + slideOutVertically { -it / 3 },
            ) {
                LmBanner(onDisable = { vm.setLM(false) })
            }

            AnimatedVisibility(visible = vm.notice != null) {
                vm.notice?.let { notice ->
                    NoticeBar(notice) { vm.dismissNotice(notice.id) }
                }
            }

            if (league != null && league.myTeamId == null) {
                NoTeamBanner(
                    leagueName = league.league.name,
                    onSwitchAccount = {
                        vm.clearConnectError()
                        editing = true
                    },
                )
            }

            if (vm.error != null && league != null) {
                ErrorBanner(vm.error!!) { vm.dismissError() }
            }

            Box(Modifier.weight(1f)) {
                when {
                    league == null && vm.loading -> LoadingPane()
                    league == null -> ErrorPane(
                        message = vm.error ?: "Could not load the league.",
                        onRetry = vm::refresh,
                        onEdit = {
                            vm.clearConnectError()
                            editing = true
                        },
                        onSignOut = { signOutConfirm = true },
                    )
                    tab == Tab.TEAMS -> TeamsScreen(
                        state = league,
                        teamId = vm.teamId,
                        selectedIds = selectedIds,
                        onSelectTeam = vm::selectTeam,
                        onPlayerPress = { playerId = it.id },
                        onTogglePlayer = vm::togglePlayer,
                        onOpenTrade = { partner ->
                            vm.startTrade(partner)
                            tradeSheetOpen = true
                        },
                    )
                    tab == Tab.TRADES -> TradesScreen(
                        state = league,
                        lm = vm.lm,
                        accountIsLM = league.account.isLM,
                        busy = vm.busy,
                        onNewTrade = {
                            vm.startTrade(null)
                            tradeSheetOpen = true
                        },
                        onAccept = { trade, option ->
                            acceptTradeId = trade.id
                            acceptTeamId = option.teamId
                        },
                        onCancel = { cancelTarget = it },
                    )
                    tab == Tab.ACTIVITY -> ActivityScreen(state = league)
                    tab == Tab.SETTINGS -> SettingsScreen(
                        state = league,
                        config = session.config,
                        credentials = session.credentials,
                        lm = vm.lm,
                        accountIsLM = league.account.isLM,
                        refreshing = vm.refreshing,
                        onToggleLM = vm::setLM,
                        onRefresh = vm::refresh,
                        onChangeConnection = {
                            vm.clearConnectError()
                            editing = true
                        },
                        onSignOut = { signOutConfirm = true },
                    )
                }
            }

            if (draft != null && !tradeSheetOpen && (draft.give.isNotEmpty() || draft.receive.isNotEmpty()) && league != null) {
                TradeDock(
                    draft = draft,
                    partnerName = league.teams.firstOrNull { it.id == draft.partnerId }?.name,
                    onOpen = { tradeSheetOpen = true },
                    onClear = vm::clearDraft,
                )
            }

            BottomNav(
                tab = tab,
                pendingCount = league?.pendingTrades?.size ?: 0,
                onSelect = { tab = it },
            )
        }
    }

    if (league != null && draft != null && tradeSheetOpen) {
        TradeSheet(
            draft = draft,
            state = league,
            lm = vm.lm,
            busy = vm.busy,
            onDismiss = { tradeSheetOpen = false },
            onChange = { vm.updateDraft(it) },
            onPropose = { current ->
                vm.proposeTrade { ok ->
                    if (ok) {
                        tradeSheetOpen = false
                        tab = Tab.TRADES
                    }
                }
            },
        )
    }

    if (player != null) {
        PlayerSheet(
            player = player,
            team = playerTeam,
            selected = player.id in selectedIds,
            lm = vm.lm,
            accountIsLM = league?.account?.isLM == true,
            onDismiss = { playerId = null },
            onToggleTrade = {
                vm.togglePlayer(player)
                playerId = null
            },
            onDrop = { dropTarget = player },
            onNeedLM = { vm.showNotice("Arm LM mode to act on another roster.", com.leaguedesk.app.data.NoticeTone.ERROR) },
        )
    }

    if (acceptTrade != null) {
        AcceptSheet(
            trade = acceptTrade,
            teams = league?.teams ?: emptyList(),
            accountIsLM = league?.account?.isLM == true,
            lm = vm.lm,
            busy = vm.busy,
            initialTeamId = acceptTeamId,
            onDismiss = {
                acceptTradeId = null
                acceptTeamId = null
            },
            onAccept = { option ->
                vm.acceptTrade(acceptTrade.id, option.teamId, option.requiresLM) { ok ->
                    if (ok) {
                        acceptTradeId = null
                        acceptTeamId = null
                    }
                }
            },
        )
    }

    dropTarget?.let { target ->
        val team = league?.teams?.firstOrNull { it.id == target.teamId }
        val useLM = team?.isMine == false
        AlertDialog(
            onDismissRequest = { if (!vm.busy) dropTarget = null },
            containerColor = LD.Panel,
            titleContentColor = LD.Text,
            textContentColor = LD.Muted,
            title = { Text("Drop ${target.name}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${team?.name ?: "This team"} loses the player immediately and they return to the free-agent pool." +
                        if (useLM) "\n\nLM override: the account is promoted, then restored." else "",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.dropPlayer(target, useLM) { ok ->
                            if (ok) {
                                dropTarget = null
                                playerId = null
                            }
                        }
                    },
                    enabled = !vm.busy,
                ) {
                    Text(if (vm.busy) "Dropping…" else "Drop player", color = LD.Bad, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { dropTarget = null }, enabled = !vm.busy) {
                    Text("Keep player", color = LD.Muted)
                }
            },
        )
    }

    cancelTarget?.let { trade ->
        AlertDialog(
            onDismissRequest = { if (!vm.busy) cancelTarget = null },
            containerColor = LD.Panel,
            titleContentColor = LD.Text,
            textContentColor = LD.Muted,
            title = { Text("Cancel this proposal?", fontWeight = FontWeight.Bold) },
            text = { Text("The other team will no longer be able to accept it.", fontSize = 12.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.cancelTrade(trade.id) { ok ->
                            if (ok) cancelTarget = null
                        }
                    },
                    enabled = !vm.busy,
                ) {
                    Text(if (vm.busy) "Cancelling…" else "Cancel trade", color = LD.Bad, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelTarget = null }, enabled = !vm.busy) {
                    Text("Keep offer", color = LD.Muted)
                }
            },
        )
    }

    if (signOutConfirm) {
        AlertDialog(
            onDismissRequest = { signOutConfirm = false },
            containerColor = LD.Panel,
            titleContentColor = LD.Text,
            textContentColor = LD.Muted,
            title = { Text("Remove ESPN connection?", fontWeight = FontWeight.Bold) },
            text = { Text("The SWID and espn_s2 cookies will be deleted from this device.", fontSize = 12.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        signOutConfirm = false
                        editing = false
                        tradeSheetOpen = false
                        playerId = null
                        acceptTradeId = null
                        vm.signOut()
                    },
                ) {
                    Text("Remove", color = LD.Bad, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { signOutConfirm = false }) {
                    Text("Cancel", color = LD.Muted)
                }
            },
        )
    }
}

@Composable
private fun Header(
    leagueName: String,
    kicker: String,
    meta: String?,
    lm: Boolean,
    refreshing: Boolean,
    onToggleLM: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                kicker,
                color = LD.Dim,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                leagueName,
                color = LD.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.7).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (meta != null) {
                Text(
                    meta,
                    color = LD.Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Surface(
            onClick = onToggleLM,
            shape = RoundedCornerShape(10.dp),
            color = if (lm) LD.Warn.copy(alpha = 0.10f) else LD.Glass,
            border = BorderStroke(1.dp, if (lm) LD.Warn.copy(alpha = 0.42f) else LD.Line),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = if (lm) LD.Warn else LD.Muted,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "LM",
                    color = if (lm) LD.Warn else LD.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp,
                )
            }
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(LD.Glass)
                .border(1.dp, LD.Line, RoundedCornerShape(10.dp))
                .clickable(enabled = !refreshing) { onRefresh() },
            contentAlignment = Alignment.Center,
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = LD.Accent2,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = LD.Muted, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun LmBanner(onDisable: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LD.Warn.copy(alpha = 0.07f))
            .border(1.dp, LD.Warn.copy(alpha = 0.34f), RoundedCornerShape(12.dp))
            .padding(start = 12.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Shield, contentDescription = null, tint = LD.Warn, modifier = Modifier.size(15.dp))
        Text(
            "LM mode armed. Actions on other teams raise the flag, run, then restore it.",
            color = LD.Muted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f),
        )
        IconAction(
            Icons.Rounded.Close,
            onDisable,
            "Turn off LM mode",
            size = 28.dp,
            container = Color.Transparent,
            border = Color.Transparent,
            tint = LD.Dim,
        )
    }
}

@Composable
private fun NoTeamBanner(leagueName: String, onSwitchAccount: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LD.Warn.copy(alpha = 0.07f))
            .border(1.dp, LD.Warn.copy(alpha = 0.34f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Warning, null, tint = LD.Warn, modifier = Modifier.size(15.dp))
        Text(
            "This ESPN account isn't a member of $leagueName. Sign in with the account that owns your team.",
            color = LD.Muted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Switch account",
            color = LD.Warn,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .clickable { onSwitchAccount() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(LD.Bad.copy(alpha = 0.07f))
            .border(1.dp, LD.Bad.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(Icons.Rounded.Warning, null, tint = LD.Bad, modifier = Modifier.size(15.dp))
        Text(
            message,
            color = LD.Bad,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconAction(
            Icons.Rounded.Close,
            onDismiss,
            "Dismiss",
            size = 26.dp,
            container = Color.Transparent,
            border = Color.Transparent,
            tint = LD.Dim,
        )
    }
}

@Composable
private fun BottomNav(tab: Tab, pendingCount: Int, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LD.Panel)
            .border(1.dp, LD.Line, RoundedCornerShape(0.dp))
            .navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Tab.entries.forEach { item ->
            val active = item == tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(item) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (active) LD.Accent2 else LD.Dim,
                        modifier = Modifier.size(21.dp),
                    )
                    if (item == Tab.TRADES && pendingCount > 0) {
                        Box(
                            modifier = Modifier
                                .padding(start = 13.dp)
                                .size(15.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(LD.Accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (pendingCount > 9) "9+" else "$pendingCount",
                                color = LD.White,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
                Text(
                    item.label,
                    color = if (active) LD.Accent2 else LD.Dim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TradeDock(
    draft: TradeDraft,
    partnerName: String?,
    onOpen: () -> Unit,
    onClear: () -> Unit,
) {
    val count = draft.give.size + draft.receive.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LD.Panel2)
            .border(1.dp, LD.Accent2.copy(alpha = 0.28f), RoundedCornerShape(14.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onOpen() }
                .padding(start = 12.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(LD.Accent2.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.SwapHoriz, null, tint = LD.Accent2, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Trade draft · $count player${if (count == 1) "" else "s"}",
                    color = LD.Text,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${draft.give.size} send · ${draft.receive.size} receive" +
                        (partnerName?.let { " · $it" } ?: " · pick a partner"),
                    color = LD.Dim,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("Review", color = LD.Accent2, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        }
        IconAction(
            Icons.Rounded.Close,
            onClear,
            "Clear trade draft",
            size = 38.dp,
            container = Color.Transparent,
            border = Color.Transparent,
            tint = LD.Dim,
        )
    }
}

@Composable
private fun LoadingPane() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = LD.Accent2, strokeWidth = 3.dp)
        Text("Loading league…", color = LD.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Text("Reading rosters, trades, and activity from ESPN.", color = LD.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ErrorPane(
    message: String,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(LD.Bad.copy(alpha = 0.08f))
                .border(1.dp, LD.Bad.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Warning, null, tint = LD.Bad, modifier = Modifier.size(28.dp))
        }
        Text("Couldn't load the league", color = LD.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(message, color = LD.Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            LDButton("Try again", onRetry, icon = Icons.Rounded.Refresh, compact = true)
            LDButton("Edit connection", onEdit, variant = ButtonVariant.GHOST, compact = true)
        }
        Text(
            "Remove cookies and start over",
            color = LD.Dim,
            fontSize = 10.5.sp,
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable { onSignOut() },
        )
    }
}




