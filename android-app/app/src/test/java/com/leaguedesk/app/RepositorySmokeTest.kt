package com.leaguedesk.app

import com.leaguedesk.app.data.Credentials
import com.leaguedesk.app.data.GameId
import com.leaguedesk.app.data.LeagueConfig
import com.leaguedesk.app.data.LeagueRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RepositorySmokeTest {

    @Test
    fun loadsDefaultLeagueWithBrunsonCookies() = runBlocking {
        val cookieFile = File("../../cookies/brunson.txt")
        assertTrue("cookie fixture missing: ${cookieFile.absolutePath}", cookieFile.isFile)
        val jar = mutableMapOf<String, String>()
        for (line in cookieFile.readLines()) {
            val trimmed = line.removePrefix("#HttpOnly_")
            if (trimmed.startsWith("#") || trimmed.isBlank()) continue
            val parts = trimmed.split("\t")
            if (parts.size >= 7) jar[parts[5]] = parts[6]
        }
        val credentials = Credentials(
            label = "brunson",
            swid = requireNotNull(jar["SWID"]),
            s2 = requireNotNull(jar["espn_s2"]),
        )
        val config = LeagueConfig("642667711", GameId.FBA, "2027")

        val state = LeagueRepository().loadState(config, credentials)

        println("league: ${state.league.name} ${state.league.seasonLabel} week=${state.league.week}")
        println("teams: ${state.teams.size}  myTeam=${state.myTeamId} ${state.account.teamName}  LM=${state.account.isLM}")
        println("pending trades: ${state.pendingTrades.size}  activity: ${state.activity.size}")
        val me = state.teams.first { it.id == state.myTeamId }
        me.roster.take(4).forEach { player ->
            println("  ${player.name} ${player.position} ${player.proTeam} ${player.slot} fppg=${player.fppg} " +
                "proj=${player.projFppg} owned=${player.ownership.owned} stats=${player.statLine}")
        }
        state.pendingTrades.take(3).forEach { trade ->
            println("  trade ${trade.id.take(8)} ${trade.direction} ${trade.status} from=${trade.proposerTeam} " +
                "sides=${trade.sides.map { it.teamName to it.players.map { p -> p.name } }}")
        }

        assertEquals(14, state.teams.size)
        assertNotNull(state.myTeamId)
        assertTrue(me.roster.isNotEmpty())
        assertTrue(me.roster.any { it.fppg != null })
    }
}
