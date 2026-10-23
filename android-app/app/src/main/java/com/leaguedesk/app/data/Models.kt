package com.leaguedesk.app.data

enum class GameId(
    val id: String,
    val label: String,
    val sport: String,
    val siteSport: String,
    val headshotSport: String,
) {
    FBA("fba", "Basketball", "Basketball", "basketball/nba", "nba"),
    FFL("ffl", "Football", "Football", "football/nfl", "nfl"),
    FLB("flb", "Baseball", "Baseball", "baseball/mlb", "mlb"),
    FHL("fhl", "Hockey", "Hockey", "hockey/nhl", "nhl");

    companion object {
        fun from(value: String?): GameId = entries.firstOrNull { it.id == value } ?: FBA
    }
}

data class Credentials(
    val label: String,
    val swid: String,
    val s2: String,
)

data class LeagueConfig(
    val leagueId: String = DEFAULT_LEAGUE,
    val game: GameId = GameId.FBA,
    val season: String = DEFAULT_SEASON,
) {
    companion object {
        const val DEFAULT_LEAGUE = "642667711"
        const val DEFAULT_SEASON = "2027"
    }
}

data class StoredSession(
    val credentials: Credentials,
    val config: LeagueConfig,
    val lm: Boolean,
)

data class SeasonInfo(
    val id: String,
    val name: String,
    val size: Int,
    val game: GameId,
    val sport: String,
    val season: String,
    val seasonLabel: String,
    val week: Int,
    val tradeDeadline: Long?,
    val revisionHours: Int?,
    val vetoVotes: Int?,
    val waiverType: String?,
    val waiverDays: Int?,
)

data class AccountInfo(
    val swid: String,
    val label: String,
    val isLM: Boolean,
    val teamId: Int?,
    val teamName: String?,
)

data class TeamRecord(
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val pct: Double?,
    val pointsFor: Double?,
    val pointsAgainst: Double?,
    val streak: String?,
    val streakLength: Int,
)

data class Accent(val a: String, val b: String)

data class PlayerOwnership(
    val owned: Double?,
    val started: Double?,
    val change: Double?,
    val adp: Double?,
    val auction: Double?,
)

data class StatLineItem(val label: String, val value: String)

data class Player(
    val id: Int,
    val name: String,
    val initials: String,
    val teamId: Int,
    val position: String,
    val positionId: Int?,
    val proTeamId: Int?,
    val proTeam: String,
    val proTeamName: String,
    val proTeamColor: String?,
    val slotId: Int?,
    val slot: String,
    val isStarter: Boolean,
    val injuryStatus: String,
    val injured: Boolean,
    val headshot: String,
    val ownership: PlayerOwnership,
    val fppg: Double?,
    val lastFppg: Double?,
    val lastSeasonLabel: String?,
    val projFppg: Double?,
    val gamesPlayed: Int?,
    val statsSeason: String?,
    val statLine: List<StatLineItem>,
    val outlook: String,
    val droppable: Boolean,
    val tradeLocked: Boolean,
    val lineupLocked: Boolean,
    val acquisitionType: String?,
    val acquisitionDate: Long?,
    val pendingTransactions: List<String>,
)

data class Team(
    val id: Int,
    val name: String,
    val abbrev: String,
    val logo: String?,
    val owners: List<String>,
    val isMine: Boolean,
    val accent: Accent,
    val record: TeamRecord,
    val rank: Int,
    val waiverRank: Int?,
    val roster: List<Player>,
    val totalFppg: Double?,
    val totalPlayers: Int,
)

data class TradePlayer(
    val id: Int?,
    val name: String,
    val position: String,
    val proTeam: String?,
    val headshot: String?,
    val fppg: Double?,
    val isPick: Boolean = false,
    val fromTeamId: Int? = null,
    val toTeamId: Int? = null,
)

data class TradeSide(
    val teamId: Int,
    val teamName: String,
    val teamLogo: String?,
    val accent: Accent,
    val players: List<TradePlayer>,
    val value: Double?,
    val accepted: Boolean,
    val isMine: Boolean,
    val isProposer: Boolean,
)

data class AcceptOption(
    val teamId: Int,
    val teamName: String,
    val requiresLM: Boolean,
)

data class PendingTrade(
    val id: String,
    val proposerTeamId: Int,
    val proposerTeam: String,
    val direction: String,
    val status: String,
    val isPending: Boolean,
    val sides: List<TradeSide>,
    val acceptOptions: List<AcceptOption>,
    val canCancel: Boolean,
    val proposedDate: Long?,
    val lastEventDate: Long?,
    val expirationDate: Long?,
    val needsAcceptFrom: List<Int>,
)

data class ActivitySide(
    val teamId: Int,
    val teamName: String,
    val teamLogo: String?,
    val players: List<TradePlayer>,
)

data class ActivityItem(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String,
    val date: Long?,
    val status: String,
    val sides: List<ActivitySide>,
)

data class LeagueState(
    val league: SeasonInfo,
    val account: AccountInfo,
    val teams: List<Team>,
    val myTeamId: Int?,
    val pendingTrades: List<PendingTrade>,
    val activity: List<ActivityItem>,
    val fetchedAt: Long,
)

data class TradeDraft(
    val partnerId: Int?,
    val give: Set<Int> = emptySet(),
    val receive: Set<Int> = emptySet(),
    val comment: String = "",
)

data class ActionOutcome(
    val message: String,
    val tradeId: String? = null,
    val restoreWarning: String? = null,
)

enum class NoticeTone { INFO, SUCCESS, ERROR }

data class Notice(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val tone: NoticeTone = NoticeTone.INFO,
)
