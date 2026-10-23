package com.leaguedesk.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaguedesk.app.data.GameId
import com.leaguedesk.app.data.LeagueConfig
import com.leaguedesk.app.ui.AppBackground
import com.leaguedesk.app.ui.ButtonVariant
import com.leaguedesk.app.ui.LDButton
import com.leaguedesk.app.ui.theme.LD

@Composable
fun ConnectScreen(
    initialConfig: LeagueConfig = LeagueConfig(),
    connecting: Boolean,
    error: String?,
    onEspnLogin: (LeagueConfig) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var leagueId by rememberSaveable { mutableStateOf(initialConfig.leagueId) }
    var season by rememberSaveable { mutableStateOf(initialConfig.season) }
    var gameId by rememberSaveable { mutableStateOf(initialConfig.game.id) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    fun signInWithEspn() {
        localError = null
        if (!leagueId.trim().matches(Regex("\\d{4,}"))) {
            localError = "League ID should be numeric."
            return
        }
        if (!season.trim().matches(Regex("\\d{4}"))) {
            localError = "Season should be a four-digit year, like 2027."
            return
        }
        onEspnLogin(LeagueConfig(leagueId.trim(), GameId.from(gameId), season.trim()))
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hero()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(LD.Panel)
                    .border(1.dp, LD.Line, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Connect ESPN",
                    color = LD.Text,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
                Text(
                    "Sign in with the ESPN account that owns your team in this league. " +
                        "League Desk imports the session cookies, encrypts them on this device, " +
                        "and clears the WebView afterward.",
                    color = LD.Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("League ID")
                        LDTextField(
                            value = leagueId,
                            onValueChange = { leagueId = it.filter { ch -> ch.isDigit() } },
                            placeholder = "642667711",
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Season")
                        LDTextField(
                            value = season,
                            onValueChange = { season = it.filter { ch -> ch.isDigit() }.take(4) },
                            placeholder = "2027",
                            keyboardType = KeyboardType.Number,
                        )
                    }
                }

                FieldLabel("Sport")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    GameId.entries.forEach { game ->
                        val active = game.id == gameId
                        Surface(
                            onClick = { gameId = game.id },
                            shape = RoundedCornerShape(9.dp),
                            color = if (active) LD.Accent.copy(alpha = 0.14f) else LD.Glass,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (active) LD.Accent else LD.Line,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    game.label,
                                    color = if (active) LD.Text else LD.Dim,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                val shownError = localError ?: error
                if (!shownError.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(LD.Bad.copy(alpha = 0.07f))
                            .border(1.dp, LD.Bad.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = LD.Bad, modifier = Modifier.size(16.dp))
                        Text(shownError, color = LD.Bad, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }

                LDButton(
                    text = if (connecting) "Connecting…" else "Sign in with ESPN",
                    onClick = ::signInWithEspn,
                    icon = Icons.Rounded.ArrowForward,
                    loading = connecting,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (onCancel != null) {
                    LDButton(
                        text = "Cancel",
                        onClick = onCancel,
                        variant = ButtonVariant.GHOST,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        "HOW THE SIGN-IN WORKS",
                        color = LD.Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        "ESPN opens inside the app. Once you're signed in, League Desk reads the " +
                            "SWID and espn_s2 cookies, stores them in the Android Keystore, and wipes " +
                            "the WebView session. No middle server and no analytics.",
                        color = LD.Dim,
                        fontSize = 10.5.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            Text(
                "Private by design. No accounts, no cloud. Cookies are encrypted in the Android Keystore.",
                color = LD.Dim,
                fontSize = 9.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Hero() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1B1238), Color(0xFF0C121B), LD.Bg),
                ),
            )
            .border(1.dp, LD.Accent.copy(alpha = 0.24f), RoundedCornerShape(26.dp))
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LD.Accent.copy(alpha = 0.14f))
                    .border(1.dp, LD.Accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 9.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                HeroBar(1f)
                Spacer(Modifier.height(3.dp))
                HeroBar(0.62f)
                Spacer(Modifier.height(3.dp))
                HeroBar(0.82f)
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text("League Desk", color = LD.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "FANTASY COMMAND CENTER",
                    color = LD.Dim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }

        Text(
            "Every roster.\nEvery trade.\nOne desk.",
            color = LD.Text,
            fontSize = 33.sp,
            lineHeight = 37.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.3).sp,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "A standalone client for ESPN Fantasy. Sign in once; the app keeps the session " +
                "encrypted on this device and talks only to ESPN.",
            color = LD.Muted,
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun HeroBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(3.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Brush.horizontalGradient(listOf(LD.Accent, LD.Accent2))),
    )
}

@Composable
private fun FieldLabel(text: String, hint: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            color = LD.Dim,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.9.sp,
        )
        if (hint != null) {
            Text(hint, color = LD.Dim, fontSize = 9.sp)
        }
    }
}

@Composable
private fun LDTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                placeholder,
                color = LD.Dim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(13.dp),
        textStyle = androidx.compose.ui.text.TextStyle(color = LD.Text, fontSize = 13.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LD.Accent,
            unfocusedBorderColor = LD.Line,
            focusedContainerColor = LD.Glass,
            unfocusedContainerColor = LD.Glass,
            cursorColor = LD.Accent2,
        ),
    )
}
