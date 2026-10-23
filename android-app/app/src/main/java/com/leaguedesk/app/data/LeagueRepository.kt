package com.leaguedesk.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

class LeagueRepository(private val client: EspnClient = EspnClient()) {

    suspend fun loadState(config: LeagueConfig, credentials: Credentials): LeagueState = withContext(Dispatchers.Default) {
        val data = client.get(
            config,
            credentials,
            listOf("mRoster", "mTeam", "mSettings", "mMembers", "mTransactions2"),
        )
        val season = config.season.toIntOrNull() ?: 2027
        val game = config.game
        val proMap = client.proTeams(game)

        val members = data.arr("members").objects()
        val membersBySwid = members
            .mapNotNull { member -> member.text("id")?.let { normalizeSwid(it) to member } }
            .filter { it.first.isNotBlank() && it.first != ZERO_SWID }
            .toMap()

        fun ownerLabel(swid: String?): String {
            val member = membersBySwid[normalizeSwid(swid)] ?: return "Unclaimed"
            return member.text("displayName")
                ?: listOfNotNull(member.text("firstName"), member.text("lastName"))
                    .joinToString(" ")
                    .ifBlank { "Member" }
        }

        val me = membersBySwid[normalizeSwid(credentials.swid)]
        val rawTeams = data.arr("teams").objects().sortedBy { it.integer("id") ?: 0 }
        val myTeamId = rawTeams.firstOrNull { team ->
            team.arr("owners").strings().any { normalizeSwid(it) == normalizeSwid(credentials.swid) }
        }?.integer("id")

        val teams = rawTeams.mapIndexed { index, team ->
            val owners = team.arr("owners").strings().map { ownerLabel(it) }
            buildTeam(team, index, season, game, proMap, owners, myTeamId)
        }

        val playersById = teams.flatMap { it.roster }.associateBy { it.id }

        val unknownIds = tradeRecords(data)
            .flatMap { family -> family.arr("items").objects() }
            .mapNotNull { it.integer("playerId") }
            .filter { it > 0 && !playersById.containsKey(it) }
            .distinct()

        val names = if (unknownIds.isEmpty()) emptyMap() else client.resolveNames(game, unknownIds)

        val settings = data.optJSONObject("settings") ?: JSONObject()
        val status = data.optJSONObject("status") ?: JSONObject()
        val tradeSettings = settings.optJSONObject("tradeSettings") ?: JSONObject()
        val acquisition = settings.optJSONObject("acquisitionSettings") ?: JSONObject()
        val myTeam = teams.firstOrNull { it.id == myTeamId }
        val accountLabel = me?.text("displayName")
            ?: listOfNotNull(me?.text("firstName"), me?.text("lastName"))
                .joinToString(" ")
                .ifBlank { credentials.label.ifBlank { "Account" } }

        LeagueState(
            league = SeasonInfo(
                id = config.leagueId,
                name = settings.text("name") ?: "League ${config.leagueId}",
                size = settings.integer("size") ?: teams.size,
                game = game,
                sport = game.sport,
                season = config.season,
                seasonLabel = seasonLabel(season),
                week = status.integer("latestScoringPeriod") ?: 1,
                tradeDeadline = tradeSettings.longOrNull("deadlineDate"),
                revisionHours = tradeSettings.integer("revisionHours"),
                vetoVotes = tradeSettings.integer("vetoVotesRequired"),
                waiverType = acquisition.text("acquisitionType"),
                waiverDays = acquisition.integer("waiverProcessDays"),
            ),
            account = AccountInfo(
                swid = credentials.swid,
                label = accountLabel,
                isLM = me?.optBoolean("isLeagueManager") == true,
                teamId = myTeamId,
                teamName = myTeam?.name,
            ),
            teams = teams,
            myTeamId = myTeamId,
            pendingTrades = buildTrades(data, playersById, teams, myTeamId, game, names)
                .filter { it.status == "pending" || it.status == "processing" },
            activity = buildActivity(data, playersById, teams, game, names),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    suspend fun proposeTrade(
        config: LeagueConfig,
        credentials: Credentials,
        teamId: Int,
        toTeamId: Int,
        give: List<Int>,
        receive: List<Int>,
        comment: String,
        lm: Boolean,
    ): ActionOutcome {
        if (give.isEmpty() && receive.isEmpty()) {
            throw EspnException("Select at least one player for the trade.")
        }
        if (teamId == 0 || toTeamId == 0 || teamId == toTeamId) {
            throw EspnException("Pick a valid trade partner.")
        }

        val data = client.get(config, credentials, listOf("mTeam", "mRoster", "mSettings"))
        val myTeam = teamOf(data, teamId) ?: throw EspnException("That team is not in this league.")
        val partner = teamOf(data, toTeamId) ?: throw EspnException("That team is not in this league.")
        if (!hasOwner(myTeam, credentials.swid) && !lm) {
            throw EspnException("You do not own that team. Arm LM mode to act for it.")
        }

        val myRoster = rosterIds(data, teamId)
        val partnerRoster = rosterIds(data, toTeamId)
        if (give.any { it !in myRoster }) {
            throw EspnException("One of the players you are sending is not on that roster.")
        }
        if (receive.any { it !in partnerRoster }) {
            throw EspnException("One of the players you are requesting is not on that roster.")
        }

        val items = JSONArray()
        give.distinct().forEach { id ->
            items.put(JSONObject()
                .put("playerId", id)
                .put("fromTeamId", teamId)
                .put("toTeamId", toTeamId)
                .put("type", "TRADE"))
        }
        receive.distinct().forEach { id ->
            items.put(JSONObject()
                .put("playerId", id)
                .put("fromTeamId", toTeamId)
                .put("toTeamId", teamId)
                .put("type", "TRADE"))
        }
        if (items.length() == 0) throw EspnException("The trade is empty.")

        val body = transactionBody(
            type = "TRADE_PROPOSAL",
            teamId = teamId,
            memberId = ownerOf(myTeam, credentials.swid),
            period = periodOf(data),
            items = items,
            comment = comment.take(200).ifBlank { "Proposed from League Desk" },
            lm = lm,
        )

        val run = withLM(config, credentials, lm) {
            postTransaction(config, credentials, body)
        }
        return ActionOutcome(
            message = "Trade proposal sent.",
            tradeId = run.response?.text("id"),
            restoreWarning = run.warning,
        )
    }

    suspend fun acceptTrade(
        config: LeagueConfig,
        credentials: Credentials,
        tradeId: String,
        teamId: Int,
        lm: Boolean,
    ): ActionOutcome {
        val data = client.get(config, credentials, listOf("mTeam", "mRoster", "mSettings"))
        val family = tradeFamilies(data)[tradeId] ?: throw EspnException("That trade is no longer pending.")
        if (!family.pending) throw EspnException("That trade is not pending.")

        val acceptingTeam = teamOf(data, teamId) ?: throw EspnException("That team is not in this league.")
        val involved = family.items
            .filter { it.text("type") == "TRADE" }
            .flatMap { listOfNotNull(it.integer("fromTeamId"), it.integer("toTeamId")) }
            .filter { it != 0 }
            .toSet()
        if (teamId !in involved) throw EspnException("That team is not part of this trade.")
        if (!hasOwner(acceptingTeam, credentials.swid) && !lm) {
            throw EspnException("You do not own that team. Arm LM mode to accept on its behalf.")
        }

        val body = transactionBody(
            type = "TRADE_ACCEPT",
            teamId = teamId,
            memberId = ownerOf(acceptingTeam, credentials.swid),
            period = periodOf(data),
            related = tradeId,
            lm = lm,
        )

        val run = withLM(config, credentials, lm) {
            postTransaction(config, credentials, body)
        }
        val completed = run.response?.optBoolean("isPending") == false
        return ActionOutcome(
            message = if (completed) "Trade accepted." else "Trade accepted — awaiting league review/processing.",
            tradeId = tradeId,
            restoreWarning = run.warning,
        )
    }

    suspend fun cancelTrade(
        config: LeagueConfig,
        credentials: Credentials,
        tradeId: String,
        lm: Boolean,
    ): ActionOutcome {
        val data = client.get(config, credentials, listOf("mTeam", "mRoster", "mSettings"))
        val family = tradeFamilies(data)[tradeId] ?: throw EspnException("That trade is no longer pending.")
        val proposerId = family.proposer ?: throw EspnException("Could not identify the proposing team.")
        val team = teamOf(data, proposerId) ?: throw EspnException("The proposing team is no longer in the league.")
        if (!hasOwner(team, credentials.swid) && !lm) {
            throw EspnException("Only the proposing team can cancel that trade.")
        }

        val body = transactionBody(
            type = "TRADE_PROPOSAL",
            teamId = proposerId,
            memberId = ownerOf(team, credentials.swid),
            period = periodOf(data),
            related = tradeId,
            execution = "CANCEL",
            comment = "Cancelled from League Desk",
            lm = lm,
        )
        val run = withLM(config, credentials, lm) {
            postTransaction(config, credentials, body)
        }
        return ActionOutcome("Trade cancelled.", tradeId, run.warning)
    }

    suspend fun dropPlayer(
        config: LeagueConfig,
        credentials: Credentials,
        teamId: Int,
        playerId: Int,
        lm: Boolean,
    ): ActionOutcome {
        val data = client.get(config, credentials, listOf("mTeam", "mRoster", "mSettings"))
        val team = teamOf(data, teamId) ?: throw EspnException("That team is not in this league.")
        val entry = team.optJSONObject("roster")?.arr("entries")?.objects()
            ?.firstOrNull { it.integer("playerId") == playerId }
        if (entry == null) throw EspnException("That player is not on that roster.")

        val player = entry.optJSONObject("playerPoolEntry")?.optJSONObject("player") ?: JSONObject()
        if (player.has("droppable") && !player.isNull("droppable") && !player.optBoolean("droppable")) {
            throw EspnException("ESPN marks that player as undroppable.")
        }
        if (!hasOwner(team, credentials.swid) && !lm) {
            throw EspnException("You do not own that team. Arm LM mode to drop from another roster.")
        }

        val items = JSONArray().put(
            JSONObject()
                .put("playerId", playerId)
                .put("fromTeamId", teamId)
                .put("toTeamId", 0)
                .put("type", "DROP"),
        )
        val body = transactionBody(
            type = "ROSTER",
            teamId = teamId,
            memberId = ownerOf(team, credentials.swid),
            period = periodOf(data),
            items = items,
            comment = "Dropped from League Desk",
            lm = lm,
        )
        val run = withLM(config, credentials, lm) {
            postTransaction(config, credentials, body)
        }
        return ActionOutcome(
            message = "${player.text("fullName") ?: "Player $playerId"} dropped.",
            restoreWarning = run.warning,
        )
    }

    // ------------------------------------------------------------------
    // model builders
    // ------------------------------------------------------------------

    private fun buildPlayer(
        entry: JSONObject,
        teamId: Int,
        season: Int,
        game: GameId,
        proMap: Map<String, ProTeamInfo>,
    ): Player {
        val pool = entry.optJSONObject("playerPoolEntry") ?: JSONObject()
        val player = pool.optJSONObject("player") ?: JSONObject()
        val id = player.integer("id") ?: entry.integer("playerId") ?: 0
        val name = player.text("fullName") ?: "Player $id"
        val pro = proMap[player.integer("proTeamId")?.toString()]
        val slotId = entry.integer("lineupSlotId")
        val slot = slotId?.let { slots(game)[it] } ?: "—"
        val injury = (player.text("injuryStatus") ?: entry.text("injuryStatus") ?: "").uppercase()
        val ownership = player.optJSONObject("ownership") ?: JSONObject()
        val summary = statSummary(player, season, game)

        return Player(
            id = id,
            name = name,
            initials = initials(name),
            teamId = teamId,
            position = positions(game)[player.integer("defaultPositionId") ?: -1] ?: "—",
            positionId = player.integer("defaultPositionId"),
            proTeamId = player.integer("proTeamId"),
            proTeam = pro?.abbrev ?: "",
            proTeamName = pro?.name ?: "",
            proTeamColor = pro?.color,
            slotId = slotId,
            slot = slot,
            isStarter = slotId != null && slotId < 11,
            injuryStatus = injury,
            injured = player.optBoolean("injured") || injury !in ACTIVE_INJURIES,
            headshot = headshotUrl(game, id),
            ownership = PlayerOwnership(
                owned = round(ownership.number("percentOwned")),
                started = round(ownership.number("percentStarted")),
                change = round(ownership.number("percentChange"), 2),
                adp = round(ownership.number("averageDraftPosition")),
                auction = round(ownership.number("auctionValueAverage")),
            ),
            fppg = summary.fppg,
            lastFppg = summary.lastFppg,
            lastSeasonLabel = summary.lastSeasonLabel,
            projFppg = summary.projFppg,
            gamesPlayed = summary.gamesPlayed,
            statsSeason = summary.statsSeason,
            statLine = summary.statLine,
            outlook = player.text("seasonOutlook") ?: "",
            droppable = !player.has("droppable") || player.isNull("droppable") || player.optBoolean("droppable"),
            tradeLocked = pool.optBoolean("tradeLocked"),
            lineupLocked = pool.optBoolean("lineupLocked"),
            acquisitionType = entry.text("acquisitionType"),
            acquisitionDate = entry.longOrNull("acquisitionDate"),
            pendingTransactions = entry.arr("pendingTransactionIds").strings(),
        )
    }

    private fun buildTeam(
        team: JSONObject,
        index: Int,
        season: Int,
        game: GameId,
        proMap: Map<String, ProTeamInfo>,
        owners: List<String>,
        myTeamId: Int?,
    ): Team {
        val roster = team.optJSONObject("roster")?.arr("entries")?.objects()
            ?.filter { (it.integer("playerId") ?: 0) > 0 }
            ?.map { buildPlayer(it, team.integer("id") ?: 0, season, game, proMap) }
            ?.sortedWith(
                compareBy<Player> { if (it.isStarter) 0 else 1 }
                    .thenBy { it.slotId ?: 99 }
                    .thenByDescending { it.fppg ?: 0.0 },
            )
            ?: emptyList()

        val record = team.optJSONObject("record")?.optJSONObject("overall") ?: JSONObject()
        val id = team.integer("id") ?: 0
        return Team(
            id = id,
            name = team.text("name") ?: "Team $id",
            abbrev = team.text("abbrev") ?: "T$id",
            logo = team.text("logo"),
            owners = owners.ifEmpty { listOf("Unclaimed") },
            isMine = id == myTeamId,
            accent = ACCENTS[index % ACCENTS.size],
            record = TeamRecord(
                wins = record.integer("wins") ?: 0,
                losses = record.integer("losses") ?: 0,
                ties = record.integer("ties") ?: 0,
                pct = round(record.number("percentage"), 3),
                pointsFor = round(record.number("pointsFor")),
                pointsAgainst = round(record.number("pointsAgainst")),
                streak = record.text("streakType"),
                streakLength = record.integer("streakLength") ?: 0,
            ),
            rank = team.integer("playoffSeed") ?: team.integer("currentProjectedRank") ?: index + 1,
            waiverRank = team.integer("waiverRank"),
            roster = roster,
            totalFppg = round(roster.sumOf { it.fppg ?: 0.0 }),
            totalPlayers = roster.size,
        )
    }

    private fun statSummary(player: JSONObject, season: Int, game: GameId): StatSummary {
        val stats = player.arr("stats").objects()
        val currentActual = statEntry(stats, season, 0)
        val previousActual = statEntry(stats, season - 1, 0)
        val currentProjection = statEntry(stats, season, 1)
        val previousProjection = statEntry(stats, season - 1, 1)

        fun average(entry: JSONObject?): Double? {
            if (entry == null) return null
            val value = entry.number("appliedAverage") ?: return null
            return if (value > 0) value else null
        }

        val fppg = average(currentActual) ?: average(previousActual)
        val lastFppg = average(previousActual)
        val projFppg = average(currentProjection) ?: average(previousProjection)

        var actual = currentActual?.takeIf { average(it) != null }?.let { it to season }
            ?: previousActual?.takeIf { average(it) != null }?.let { it to (season - 1) }

        val gamesPlayed = actual?.let { (entry, _) ->
            val total = entry.number("appliedTotal")
            val avg = entry.number("appliedAverage")
            if (total != null && avg != null && avg > 0) (total / avg).roundToInt() else null
        }

        val statLine = mutableListOf<StatLineItem>()
        if (game == GameId.FBA) {
            val averages = actual?.first?.optJSONObject("averageStats")
            if (averages != null) {
                for ((label, statId, scale, isPct) in BASKETBALL_STATS) {
                    val raw = averages.number(statId.toString()) ?: continue
                    val value = raw * scale
                    statLine += StatLineItem(
                        label,
                        if (isPct) String.format("%.1f%%", value) else String.format("%.1f", value),
                    )
                }
            }
        }

        return StatSummary(
            fppg = round(fppg),
            lastFppg = round(lastFppg),
            lastSeasonLabel = seasonLabel(season - 1),
            projFppg = round(projFppg),
            gamesPlayed = gamesPlayed,
            statsSeason = actual?.second?.let { seasonLabel(it) },
            statLine = statLine,
        )
    }

    private fun buildTrades(
        data: JSONObject,
        playersById: Map<Int, Player>,
        teams: List<Team>,
        myTeamId: Int?,
        game: GameId,
        names: Map<Int, String>,
    ): List<PendingTrade> {
        val meta = teams.associateBy { it.id }
        val trades = mutableListOf<PendingTrade>()

        for ((familyId, family) in tradeFamilies(data)) {
            val items = family.items.filter { it.text("type") == "TRADE" }
            if (items.isEmpty()) continue

            val sides = mutableMapOf<Int, MutableList<TradePlayer>>()
            for (item in items) {
                val from = item.integer("fromTeamId") ?: 0
                val to = item.integer("toTeamId") ?: 0
                val playerId = item.integer("playerId") ?: 0
                if (playerId > 0) {
                    val brief = briefPlayer(playerId, playersById, names, game).copy(
                        fromTeamId = from,
                        toTeamId = to,
                    )
                    sides.getOrPut(from) { mutableListOf() } += brief
                } else if ((item.integer("overallPickNumber") ?: 0) > 0) {
                    sides.getOrPut(from) { mutableListOf() } += TradePlayer(
                        id = null,
                        name = "Draft pick",
                        position = "PICK",
                        proTeam = null,
                        headshot = null,
                        fppg = null,
                        isPick = true,
                        fromTeamId = from,
                        toTeamId = to,
                    )
                }
            }
            val involved = sides.keys.filter { it != 0 }.sorted()
            if (involved.size < 2) continue

            val proposer = family.proposer
                ?: family.records.firstNotNullOfOrNull { it.integer("teamId") }
                ?: involved.first()
            val accepted = (family.accepted + proposer).toMutableSet()
            val needs = involved.filter { it !in accepted }
            val pending = needs.isNotEmpty() && family.pending && !family.executed
            val processing = needs.isNotEmpty() && family.pending && family.executed
            val status = when {
                !family.pending && needs.isEmpty() -> "completed"
                pending -> "pending"
                processing -> "processing"
                needs.isNotEmpty() -> "pending"
                family.pending -> "processing"
                else -> "completed"
            }

            val sidePayload = involved.map { teamId ->
                val players = sides[teamId] ?: emptyList()
                val team = meta[teamId]
                TradeSide(
                    teamId = teamId,
                    teamName = team?.name ?: "Team $teamId",
                    teamLogo = team?.logo,
                    accent = team?.accent ?: ACCENTS[teamId % ACCENTS.size],
                    players = players,
                    value = round(players.sumOf { it.fppg ?: 0.0 }),
                    accepted = teamId in accepted,
                    isMine = teamId == myTeamId,
                    isProposer = teamId == proposer,
                )
            }

            val direction = when {
                myTeamId == proposer -> "outgoing"
                myTeamId != null && involved.contains(myTeamId) -> "incoming"
                else -> "neutral"
            }

            trades += PendingTrade(
                id = familyId,
                proposerTeamId = proposer,
                proposerTeam = meta[proposer]?.name ?: "Team $proposer",
                direction = direction,
                status = status,
                isPending = pending || processing,
                sides = sidePayload,
                acceptOptions = needs.map { teamId ->
                    AcceptOption(
                        teamId = teamId,
                        teamName = meta[teamId]?.name ?: "Team $teamId",
                        requiresLM = teamId != myTeamId,
                    )
                },
                canCancel = direction == "outgoing" && pending,
                proposedDate = family.dates.minOrNull(),
                lastEventDate = family.dates.maxOrNull(),
                expirationDate = family.expirations.maxOrNull(),
                needsAcceptFrom = needs,
            )
        }

        return trades.sortedWith(
            compareBy<PendingTrade> { if (it.status == "pending") 0 else 1 }
                .thenByDescending { it.lastEventDate ?: 0L },
        )
    }

    private fun buildActivity(
        data: JSONObject,
        playersById: Map<Int, Player>,
        teams: List<Team>,
        game: GameId,
        names: Map<Int, String>,
    ): List<ActivityItem> {
        val meta = teams.associateBy { it.id }
        val activity = mutableListOf<ActivityItem>()

        for ((familyId, family) in tradeFamilies(data)) {
            if (family.pending && !family.executed) continue
            val completed = family.records.any { record ->
                record.text("type") == "TRADE_ACCEPT" && (
                    record.text("status")?.uppercase() == "EXECUTED"
                        || (record.text("executionType")?.uppercase() == "EXECUTE" && !record.optBoolean("isPending"))
                    )
            }
            if (!completed) continue
            val items = family.items.filter { it.text("type") == "TRADE" }
            if (items.isEmpty()) continue

            val grouped = mutableMapOf<Int, MutableList<TradePlayer>>()
            for (item in items) {
                val playerId = item.integer("playerId") ?: 0
                if (playerId <= 0) continue
                val brief = briefPlayer(playerId, playersById, names, game).copy(
                    fromTeamId = item.integer("fromTeamId"),
                    toTeamId = item.integer("toTeamId"),
                )
                grouped.getOrPut(item.integer("fromTeamId") ?: 0) { mutableListOf() } += brief
            }

            val sides = grouped.map { (teamId, players) ->
                ActivitySide(
                    teamId = teamId,
                    teamName = meta[teamId]?.name ?: "Team $teamId",
                    teamLogo = meta[teamId]?.logo,
                    players = players,
                )
            }
            activity += ActivityItem(
                id = familyId,
                kind = "trade",
                title = "Trade completed",
                subtitle = "${sides.size} teams · ${sides.sumOf { it.players.size }} players",
                date = family.dates.maxOrNull(),
                status = "completed",
                sides = sides,
            )
        }

        for (txn in data.arr("transactions").objects()) {
            val kind = txn.text("type") ?: ""
            if (kind.startsWith("TRADE")) continue
            val items = txn.arr("items").objects().filter { (it.integer("playerId") ?: -1) > 0 }
            val players = items.map { item ->
                briefPlayer(item.integer("playerId") ?: 0, playersById, names, game).copy(
                    fromTeamId = item.integer("fromTeamId"),
                    toTeamId = item.integer("toTeamId"),
                )
            }
            val teamId = txn.integer("teamId")
            val title = when {
                items.any { it.text("type") == "DROP" } -> "Player dropped"
                items.any { it.text("type") in listOf("ADD", "FREEAGENT", "WAIVER") } -> "Player added"
                else -> "Roster move"
            }
            activity += ActivityItem(
                id = txn.text("id") ?: "$teamId-${txn.longOrNull("proposedDate")}",
                kind = "roster",
                title = title,
                subtitle = meta[teamId]?.name ?: "Team $teamId",
                date = txn.longOrNull("proposedDate") ?: txn.longOrNull("acceptedDate"),
                status = "completed",
                sides = listOf(
                    ActivitySide(
                        teamId = teamId ?: 0,
                        teamName = meta[teamId]?.name ?: "Team $teamId",
                        teamLogo = meta[teamId]?.logo,
                        players = players,
                    ),
                ),
            )
        }

        return activity.sortedByDescending { it.date ?: 0L }.take(40)
    }

    private fun briefPlayer(
        playerId: Int,
        playersById: Map<Int, Player>,
        names: Map<Int, String>,
        game: GameId,
    ): TradePlayer {
        val known = playersById[playerId]
        return if (known != null) {
            TradePlayer(
                id = known.id,
                name = known.name,
                position = known.position,
                proTeam = known.proTeam,
                headshot = known.headshot,
                fppg = known.fppg,
            )
        } else {
            TradePlayer(
                id = playerId,
                name = names[playerId] ?: "Player $playerId",
                position = "",
                proTeam = "",
                headshot = headshotUrl(game, playerId),
                fppg = null,
            )
        }
    }

    // ------------------------------------------------------------------
    // trade families
    // ------------------------------------------------------------------

    private class RawFamily(
        val id: String,
    ) {
        val records = mutableListOf<JSONObject>()
        var items: List<JSONObject> = emptyList()
        var proposer: Int? = null
        val accepted = mutableSetOf<Int>()
        val dates = mutableListOf<Long>()
        val expirations = mutableListOf<Long>()
        var pending = false
        var executed = false
    }

    private fun tradeRecords(data: JSONObject): List<JSONObject> {
        val byId = linkedMapOf<String, JSONObject>()
        data.arr("transactions").objects().forEach { txn ->
            txn.text("id")?.let { byId[it] = txn }
        }
        data.arr("teams").objects().forEach { team ->
            team.arr("pendingTransactions").objects().forEach { txn ->
                txn.text("id")?.let { byId[it] = txn }
            }
        }
        return byId.values.toList()
    }

    private fun tradeFamilies(data: JSONObject): Map<String, RawFamily> {
        val families = linkedMapOf<String, RawFamily>()
        for (txn in tradeRecords(data)) {
            val kind = txn.text("type") ?: ""
            if (!kind.startsWith("TRADE")) continue
            val related = txn.text("relatedTransactionId")
            val familyId = if (kind == "TRADE_ACCEPT" && !related.isNullOrBlank()) related else txn.text("id") ?: continue
            val family = families.getOrPut(familyId) { RawFamily(familyId) }

            family.records += txn
            val items = txn.arr("items").objects()
            if (items.isNotEmpty() && (kind == "TRADE_PROPOSAL" || family.items.isEmpty())) {
                family.items = items
            }
            if (kind == "TRADE_PROPOSAL") family.proposer = txn.integer("teamId")
            txn.optJSONObject("teamActions")?.let { actions ->
                for (key in actions.keys()) {
                    if (actions.optString(key).uppercase() == "ACCEPTED") {
                        key.toIntOrNull()?.let { family.accepted += it }
                    }
                }
            }
            listOf("proposedDate", "acceptedDate").forEach { key ->
                txn.longOrNull(key)?.let { family.dates += it }
            }
            txn.longOrNull("expirationDate")?.let { family.expirations += it }
            family.pending = family.pending || txn.optBoolean("isPending") ||
                txn.text("status")?.uppercase() == "PENDING"
            family.executed = family.executed || txn.text("status")?.uppercase() == "EXECUTED" ||
                (txn.text("executionType")?.uppercase() == "EXECUTE" && !txn.optBoolean("isPending"))
        }
        return families
    }

    // ------------------------------------------------------------------
    // writes + LM handling
    // ------------------------------------------------------------------

    private data class LmRun(val response: JSONObject?, val warning: String?)

    private suspend fun readMe(config: LeagueConfig, credentials: Credentials): JSONObject? {
        val data = client.get(config, credentials, listOf("mMembers", "mTeam"))
        return data.arr("members").objects().firstOrNull {
            normalizeSwid(it.text("id")) == normalizeSwid(credentials.swid)
        }
    }

    private suspend fun isLM(config: LeagueConfig, credentials: Credentials): Boolean =
        readMe(config, credentials)?.optBoolean("isLeagueManager") == true

    private suspend fun putMember(
        config: LeagueConfig,
        credentials: Credentials,
        member: JSONObject,
        value: Boolean,
    ): RawResponse {
        val body = JSONObject(member.toString()).put("isLeagueManager", value)
        return client.send(config, credentials, "PUT", "/members/${credentials.swid}", body)
    }

    private suspend fun withLM(
        config: LeagueConfig,
        credentials: Credentials,
        enabled: Boolean,
        action: suspend () -> JSONObject,
    ): LmRun {
        val original = readMe(config, credentials) ?: throw EspnException(
            "This cookie does not belong to a member of the league.",
        )
        val start = original.optBoolean("isLeagueManager")
        var raised = false
        var result: JSONObject? = null
        var failure: Throwable? = null
        var warning: String? = null

        try {
            if (enabled && !start) {
                val response = putMember(config, credentials, original, true)
                if (!response.ok) {
                    throw EspnException(
                        "LM escalation refused (${response.status}).",
                        response.status,
                        response.body.take(240),
                    )
                }
                if (!isLM(config, credentials)) {
                    throw EspnException("LM escalation did not persist; action aborted.")
                }
                raised = true
            }
            result = action()
        } catch (caught: Throwable) {
            failure = caught
        } finally {
            if (raised) {
                try {
                    val response = putMember(config, credentials, original, start)
                    if (isLM(config, credentials) != start) {
                        warning = "LM flag restore FAILED. Fix manually: PUT members/${credentials.swid} " +
                            "isLeagueManager=$start. HTTP ${response.status}."
                    }
                } catch (caught: Throwable) {
                    warning = "LM flag restore FAILED: ${caught.message}"
                }
            }
        }

        failure?.let { throw it }
        return LmRun(result, warning)
    }

    private fun transactionBody(
        type: String,
        teamId: Int,
        memberId: String,
        period: Int,
        items: JSONArray? = null,
        related: String? = null,
        execution: String? = null,
        comment: String = "",
        lm: Boolean = false,
    ): JSONObject {
        return JSONObject()
            .put("type", type)
            .put("teamId", teamId)
            .put("memberId", memberId)
            .put("scoringPeriodId", period)
            .put("isActingAsTeamOwner", true)
            .put("isLeagueManager", lm)
            .put("skipTransactionCounters", false)
            .put("bidAmount", 0)
            .put("comment", comment)
            .put("executionType", execution ?: JSONObject.NULL)
            .put("relatedTransactionId", related ?: JSONObject.NULL)
            .apply { if (items != null) put("items", items) }
    }

    private suspend fun postTransaction(
        config: LeagueConfig,
        credentials: Credentials,
        body: JSONObject,
    ): JSONObject {
        val response = client.send(config, credentials, "POST", "/transactions/", body)
        if (!response.ok) {
            throw EspnException(
                "ESPN rejected the transaction (${response.status}): ${response.body.take(300)}",
                response.status,
                response.body.take(400),
            )
        }
        return runCatching { JSONObject(response.body) }.getOrElse { JSONObject() }
    }

    private fun teamOf(data: JSONObject, teamId: Int): JSONObject? =
        data.arr("teams").objects().firstOrNull { it.integer("id") == teamId }

    private fun ownerIds(team: JSONObject): List<String> =
        team.arr("owners").strings().filter { normalizeSwid(it) != ZERO_SWID }

    private fun hasOwner(team: JSONObject, swid: String): Boolean =
        ownerIds(team).any { normalizeSwid(it) == normalizeSwid(swid) }

    private fun ownerOf(team: JSONObject, fallback: String): String =
        ownerIds(team).firstOrNull() ?: fallback

    private fun rosterIds(data: JSONObject, teamId: Int): Set<Int> =
        teamOf(data, teamId)
            ?.optJSONObject("roster")
            ?.arr("entries")
            ?.objects()
            ?.mapNotNull { it.integer("playerId") }
            ?.filter { it > 0 }
            ?.toSet()
            ?: emptySet()

    private fun periodOf(data: JSONObject): Int =
        data.optJSONObject("status")?.integer("latestScoringPeriod") ?: 1

    // ------------------------------------------------------------------
    // constants + tiny helpers
    // ------------------------------------------------------------------

    private data class StatSummary(
        val fppg: Double?,
        val lastFppg: Double?,
        val lastSeasonLabel: String?,
        val projFppg: Double?,
        val gamesPlayed: Int?,
        val statsSeason: String?,
        val statLine: List<StatLineItem>,
    )

    companion object {
        private const val ZERO_SWID = "00000000000000000000000000000000"
        private val ACTIVE_INJURIES = setOf("ACTIVE", "NORMAL", "")

        private val ACCENTS = listOf(
            Accent("#7c5cff", "#22d3ee"),
            Accent("#f59e0b", "#f43f5e"),
            Accent("#10b981", "#84cc16"),
            Accent("#0ea5e9", "#a855f7"),
            Accent("#f43f5e", "#fb923c"),
            Accent("#14b8a6", "#6366f1"),
        )

        private val BASKETBALL_STATS = listOf(
            StatDef("PTS", 0, 1.0, false),
            StatDef("REB", 6, 1.0, false),
            StatDef("AST", 3, 1.0, false),
            StatDef("STL", 2, 1.0, false),
            StatDef("BLK", 1, 1.0, false),
            StatDef("3PM", 17, 1.0, false),
            StatDef("FG%", 19, 100.0, true),
            StatDef("FT%", 20, 100.0, true),
            StatDef("TO", 11, 1.0, false),
            StatDef("MIN", 28, 1.0, false),
        )

        private data class StatDef(
            val label: String,
            val statId: Int,
            val scale: Double,
            val percent: Boolean,
        )

        private fun positions(game: GameId): Map<Int, String> = when (game) {
            GameId.FBA -> mapOf(1 to "PG", 2 to "SG", 3 to "SF", 4 to "PF", 5 to "C")
            GameId.FFL -> mapOf(1 to "QB", 2 to "RB", 3 to "WR", 4 to "TE", 5 to "K", 16 to "D/ST")
            GameId.FLB -> mapOf(
                1 to "SP", 2 to "RP", 3 to "C", 4 to "1B", 5 to "2B", 6 to "3B",
                7 to "SS", 8 to "OF", 9 to "DH", 10 to "UTIL", 11 to "P",
            )
            GameId.FHL -> mapOf(1 to "C", 2 to "LW", 3 to "RW", 4 to "D", 5 to "G", 6 to "F", 7 to "UTIL")
        }

        private fun slots(game: GameId): Map<Int, String> = when (game) {
            GameId.FBA -> mapOf(
                0 to "PG", 1 to "SG", 2 to "SF", 3 to "PF", 4 to "C", 5 to "G", 6 to "F",
                7 to "G/F", 8 to "F/C", 9 to "UTIL", 10 to "UTIL", 11 to "BE", 12 to "BN",
                13 to "IR", 14 to "IR+",
            )
            else -> mapOf(11 to "BE", 12 to "BE", 13 to "IR", 14 to "IR")
        }

        private fun normalizeSwid(value: String?): String =
            (value ?: "").replace("{", "").replace("}", "").replace("-", "").trim().uppercase()

        private fun initials(name: String): String {
            val parts = name.replace(".", " ").split(Regex("\\s+")).filter { it.isNotBlank() }
            return ((parts.firstOrNull()?.firstOrNull()?.toString() ?: "?") +
                (parts.getOrNull(1)?.firstOrNull()?.toString() ?: "")).uppercase()
        }

        private fun headshotUrl(game: GameId, playerId: Int): String =
            "https://a.espncdn.com/combiner/i?img=/i/headshots/${game.headshotSport}/players/full/$playerId.png" +
                "&w=350&h=254&scale=crop&cquality=40"

        private fun seasonLabel(season: Int): String = "${season - 1}-${season.toString().takeLast(2)}"

        private fun round(value: Double?, digits: Int = 1): Double? {
            if (value == null || !value.isFinite()) return null
            val factor = 10.0.pow(digits)
            return kotlin.math.round(value * factor) / factor
        }

        private fun statEntry(stats: List<JSONObject>, season: Int, source: Int): JSONObject? =
            stats.firstOrNull {
                it.integer("seasonId") == season &&
                    it.integer("statSourceId") == source &&
                    (it.integer("statSplitTypeId") ?: -1) == 0 &&
                    (it.integer("scoringPeriodId") ?: 0) == 0
            }

        private fun JSONObject.arr(key: String): JSONArray = optJSONArray(key) ?: JSONArray()

        private fun JSONArray.objects(): List<JSONObject> =
            (0 until length()).mapNotNull { optJSONObject(it) }

        private fun JSONArray.strings(): List<String> =
            (0 until length()).mapNotNull { if (isNull(it)) null else optString(it) }

        private fun JSONObject.text(key: String): String? =
            if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

        private fun JSONObject.integer(key: String): Int? =
            if (!has(key) || isNull(key)) null else optInt(key)

        private fun JSONObject.longOrNull(key: String): Long? =
            if (!has(key) || isNull(key)) null else optLong(key)

        private fun JSONObject.number(key: String): Double? {
            if (!has(key) || isNull(key)) return null
            val value = optDouble(key)
            return if (value.isFinite()) value else null
        }
    }
}

