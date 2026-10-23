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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaguedesk.app.data.Credentials
import com.leaguedesk.app.data.LeagueConfig
import com.leaguedesk.app.data.LeagueState
import com.leaguedesk.app.ui.ButtonVariant
import com.leaguedesk.app.ui.LDButton
import com.leaguedesk.app.ui.Tag
import com.leaguedesk.app.ui.TagTone
import com.leaguedesk.app.ui.theme.LD

@Composable
fun SettingsScreen(
    state: LeagueState,
    config: LeagueConfig,
    credentials: Credentials,
    lm: Boolean,
    accountIsLM: Boolean,
    refreshing: Boolean,
    onToggleLM: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onChangeConnection: () -> Unit,
    onSignOut: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("Settings", color = LD.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
                Text("Connection, privileges, and local data.", color = LD.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }

        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconBox(Icons.Rounded.Refresh, LD.Accent2)
                    Column(Modifier.weight(1f)) {
                        Text("ESPN connection", color = LD.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            state.league.name,
                            color = LD.Dim,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Tag(state.league.seasonLabel, TagTone.ACCENT)
                }
                SettingsRow("League ID", config.leagueId)
                SettingsRow("Sport", state.league.sport)
                SettingsRow("Season", config.season)
                SettingsRow("Week", state.league.week.toString())
                SettingsRow("Account", state.account.label + if (accountIsLM) " · LM" else "")
                SettingsRow("SWID", "…" + credentials.swid.replace("{", "").replace("}", "").takeLast(8))
                SettingsRow("Teams", state.league.size.toString())
                SettingsRow("Pending trades", state.pendingTrades.size.toString())
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    LDButton(
                        text = if (refreshing) "Refreshing…" else "Refresh",
                        onClick = onRefresh,
                        icon = Icons.Rounded.Refresh,
                        variant = ButtonVariant.SOFT,
                        loading = refreshing,
                        modifier = Modifier.weight(1f),
                    )
                    LDButton(
                        text = "Connection",
                        onClick = onChangeConnection,
                        icon = Icons.Rounded.Create,
                        variant = ButtonVariant.GHOST,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconBox(Icons.Rounded.Shield, LD.Warn)
                    Column(Modifier.weight(1f)) {
                        Text("League Manager mode", color = LD.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Allows actions on teams you don't own. Each action promotes the account, then restores the original flag.",
                            color = LD.Dim,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(LD.Glass)
                        .border(1.dp, LD.Line, RoundedCornerShape(13.dp))
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Arm LM actions", color = LD.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (lm) "Armed — LM options are visible on trades and player actions."
                            else "Off — actions are limited to your own team.",
                            color = LD.Dim,
                            fontSize = 9.5.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = lm,
                        onCheckedChange = onToggleLM,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = LD.Warn,
                            checkedTrackColor = LD.Warn.copy(alpha = 0.35f),
                            uncheckedThumbColor = LD.Muted,
                            uncheckedTrackColor = LD.GlassStrong,
                            uncheckedBorderColor = LD.LineStrong,
                        ),
                    )
                }
                if (accountIsLM) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Info, null, tint = LD.Accent2, modifier = Modifier.size(14.dp))
                        Text("This account is already a League Manager in ESPN.", color = LD.Dim, fontSize = 9.5.sp)
                    }
                }
            }
        }

        item {
            SettingsCard {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconBox(Icons.Rounded.Lock, LD.Good)
                    Column(Modifier.weight(1f)) {
                        Text("Local security", color = LD.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Cookies live in the Android Keystore-backed encrypted store.",
                            color = LD.Dim,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Text(
                    "No backend, no telemetry. The app sends requests directly to ESPN with your cookies. " +
                        "Removing the connection wipes the cookies from this device.",
                    color = LD.Muted,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
                LDButton(
                    text = "Sign out & remove cookies",
                    onClick = onSignOut,
                    icon = Icons.Rounded.Logout,
                    variant = ButtonVariant.DANGER,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "LEAGUE DESK ANDROID",
                    color = LD.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    "Version 1.0.0 · Native ESPN Fantasy client\nReads mRoster, mTeam, mSettings, mMembers, mTransactions2.",
                    color = LD.Dim,
                    fontSize = 9.5.sp,
                    lineHeight = 15.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LD.Glass)
            .border(1.dp, LD.Line, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}

@Composable
private fun IconBox(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LD.Dim, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
        Text(
            value,
            color = LD.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
