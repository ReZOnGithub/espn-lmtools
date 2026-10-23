package com.leaguedesk.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leaguedesk.app.data.Credentials
import com.leaguedesk.app.data.LeagueConfig
import com.leaguedesk.app.data.LeagueRepository
import com.leaguedesk.app.data.LeagueState
import com.leaguedesk.app.data.Notice
import com.leaguedesk.app.data.NoticeTone
import com.leaguedesk.app.data.Player
import com.leaguedesk.app.data.SessionStore
import com.leaguedesk.app.data.StoredSession
import com.leaguedesk.app.data.TradeDraft
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SessionStore(application)
    private val repo = LeagueRepository()

    var session by mutableStateOf(store.loadSession())
        private set

    var league by mutableStateOf<LeagueState?>(null)
        private set

    var teamId by mutableStateOf<Int?>(null)
        private set

    var draft by mutableStateOf<TradeDraft?>(null)
        private set

    var lm by mutableStateOf(session?.lm ?: store.loadLm())
        private set

    var loading by mutableStateOf(false)
        private set

    var refreshing by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var connecting by mutableStateOf(false)
        private set

    var connectError by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    var notice by mutableStateOf<Notice?>(null)
        private set

    init {
        if (session != null) refresh()
    }

    fun refresh() {
        val active = session ?: return
        viewModelScope.launch {
            refreshing = true
            if (league == null) loading = true
            error = null
            try {
                applyState(repo.loadState(active.config, active.credentials))
            } catch (caught: Throwable) {
                error = caught.message ?: "Could not load the league."
            } finally {
                refreshing = false
                loading = false
            }
        }
    }

    fun connect(credentials: Credentials, config: LeagueConfig, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            connecting = true
            connectError = null
            try {
                val next = repo.loadState(config, credentials)
                store.saveSession(credentials, config)
                store.saveLm(lm)
                session = StoredSession(credentials, config, lm)
                league = next
                teamId = next.myTeamId ?: next.teams.firstOrNull()?.id
                draft = null
                error = null
                showNotice("Connected to ${next.league.name}.", NoticeTone.SUCCESS)
                onComplete(true)
            } catch (caught: Throwable) {
                connectError = caught.message ?: "Could not connect to ESPN."
                onComplete(false)
            } finally {
                connecting = false
            }
        }
    }

    fun selectTeam(id: Int) {
        teamId = id
    }

    fun setLM(value: Boolean) {
        lm = value
        store.saveLm(value)
        showNotice(
            if (value) "LM mode armed. Actions on other teams escalate and restore the flag." else "LM mode off.",
            NoticeTone.INFO,
        )
    }

    fun startTrade(partnerId: Int? = null) {
        val active = league ?: return
        val myId = active.myTeamId
        if (myId == null) {
            showNotice("This account does not own a team in the league.", NoticeTone.ERROR)
            return
        }
        val fallback = active.teams.firstOrNull { it.id != myId && it.roster.isNotEmpty() }?.id
        draft = TradeDraft(
            partnerId = partnerId?.takeIf { it != myId } ?: fallback,
        )
    }

    fun togglePlayer(player: Player) {
        val active = league ?: return
        val myId = active.myTeamId
        if (myId == null) {
            showNotice("This account does not own a team.", NoticeTone.ERROR)
            return
        }
        val fallback = active.teams.firstOrNull { it.id != myId && it.roster.isNotEmpty() }?.id
        val base = draft ?: TradeDraft(partnerId = fallback)
        draft = if (player.teamId == myId) {
            base.copy(
                give = if (player.id in base.give) base.give - player.id else base.give + player.id,
            )
        } else {
            val receive = if (base.partnerId != player.teamId) emptySet() else base.receive
            base.copy(
                partnerId = player.teamId,
                receive = if (player.id in receive) receive - player.id else receive + player.id,
            )
        }
        showNotice("Trade draft updated. Review it from the draft bar.", NoticeTone.INFO)
    }

    fun clearDraft() {
        draft = null
    }

    fun updateDraft(value: TradeDraft) {
        draft = value
    }

    fun dismissError() {
        error = null
    }

    fun clearConnectError() {
        connectError = null
    }

    fun proposeTrade(onResult: (Boolean) -> Unit) {
        val activeSession = session ?: return
        val activeLeague = league ?: return
        val current = draft ?: return
        val team = activeLeague.myTeamId ?: return
        if (current.partnerId == null) {
            showNotice("Pick a trade partner first.", NoticeTone.ERROR)
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                val outcome = repo.proposeTrade(
                    config = activeSession.config,
                    credentials = activeSession.credentials,
                    teamId = team,
                    toTeamId = current.partnerId,
                    give = current.give.toList(),
                    receive = current.receive.toList(),
                    comment = current.comment,
                    lm = false,
                )
                draft = null
                showNotice(
                    listOfNotNull(outcome.message, outcome.restoreWarning).joinToString(" "),
                    if (outcome.restoreWarning != null) NoticeTone.ERROR else NoticeTone.SUCCESS,
                )
                onResult(true)
            } catch (caught: Throwable) {
                showNotice(caught.message ?: "Could not propose the trade.", NoticeTone.ERROR)
                onResult(false)
            } finally {
                busy = false
            }
            reloadQuietly()
        }
    }

    fun acceptTrade(tradeId: String, teamId: Int, requiresLM: Boolean, onResult: (Boolean) -> Unit) {
        val active = session ?: return
        viewModelScope.launch {
            busy = true
            try {
                val outcome = repo.acceptTrade(
                    active.config,
                    active.credentials,
                    tradeId,
                    teamId,
                    requiresLM,
                )
                showNotice(
                    listOfNotNull(outcome.message, outcome.restoreWarning).joinToString(" "),
                    if (outcome.restoreWarning != null) NoticeTone.ERROR else NoticeTone.SUCCESS,
                )
                onResult(true)
            } catch (caught: Throwable) {
                showNotice(caught.message ?: "Could not accept the trade.", NoticeTone.ERROR)
                onResult(false)
            } finally {
                busy = false
            }
            reloadQuietly()
        }
    }

    fun cancelTrade(tradeId: String, onResult: (Boolean) -> Unit) {
        val active = session ?: return
        viewModelScope.launch {
            busy = true
            try {
                val outcome = repo.cancelTrade(active.config, active.credentials, tradeId, false)
                showNotice(outcome.message, NoticeTone.SUCCESS)
                onResult(true)
            } catch (caught: Throwable) {
                showNotice(caught.message ?: "Could not cancel the trade.", NoticeTone.ERROR)
                onResult(false)
            } finally {
                busy = false
            }
            reloadQuietly()
        }
    }

    fun dropPlayer(player: Player, useLM: Boolean, onResult: (Boolean) -> Unit) {
        val active = session ?: return
        viewModelScope.launch {
            busy = true
            try {
                val outcome = repo.dropPlayer(
                    active.config,
                    active.credentials,
                    player.teamId,
                    player.id,
                    useLM,
                )
                draft = draft?.copy(
                    give = draft?.give?.minus(player.id) ?: emptySet(),
                    receive = draft?.receive?.minus(player.id) ?: emptySet(),
                )
                showNotice(
                    listOfNotNull(outcome.message, outcome.restoreWarning).joinToString(" "),
                    if (outcome.restoreWarning != null) NoticeTone.ERROR else NoticeTone.SUCCESS,
                )
                onResult(true)
            } catch (caught: Throwable) {
                showNotice(caught.message ?: "Could not drop the player.", NoticeTone.ERROR)
                onResult(false)
            } finally {
                busy = false
            }
            reloadQuietly()
        }
    }

    fun signOut() {
        store.clearSession()
        store.saveLm(false)
        session = null
        league = null
        teamId = null
        draft = null
        error = null
        connectError = null
        lm = false
        notice = null
    }

    fun showNotice(text: String, tone: NoticeTone = NoticeTone.INFO) {
        notice = Notice(text = text, tone = tone)
    }

    fun dismissNotice(id: Long? = notice?.id) {
        if (id == null || notice?.id == id) notice = null
    }

    private suspend fun reloadQuietly() {
        val active = session ?: return
        try {
            applyState(repo.loadState(active.config, active.credentials))
        } catch (caught: Throwable) {
            error = caught.message ?: "Could not refresh the league."
        }
    }

    private fun applyState(next: LeagueState) {
        league = next
        error = null
        if (teamId == null || next.teams.none { it.id == teamId }) {
            teamId = next.myTeamId ?: next.teams.firstOrNull()?.id
        }
        val current = draft ?: return
        val validPlayers = next.teams.flatMap { it.roster }.map { it.id }.toSet()
        val partnerValid = next.teams.any { it.id == current.partnerId }
        draft = current.copy(
            partnerId = if (partnerValid) current.partnerId else null,
            give = current.give.filter { it in validPlayers }.toSet(),
            receive = if (partnerValid) current.receive.filter { it in validPlayers }.toSet() else emptySet(),
        )
    }
}


