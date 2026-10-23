package com.leaguedesk.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class EspnException(
    override val message: String,
    val status: Int? = null,
    val detail: String? = null,
) : Exception(message)

data class ProTeamInfo(
    val abbrev: String,
    val name: String,
    val color: String?,
)

data class RawResponse(val status: Int, val body: String) {
    val ok: Boolean get() = status in 200..299
}

class EspnClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val proTeamsCache = mutableMapOf<GameId, Pair<Long, Map<String, ProTeamInfo>>>()
    private val nameCache = mutableMapOf<String, String>()

    suspend fun get(config: LeagueConfig, credentials: Credentials, views: List<String>): JSONObject {
        val query = views.joinToString("&") { "view=${java.net.URLEncoder.encode(it, "UTF-8")}" }
        val request = Request.Builder()
            .url("${readBase(config)}?$query")
            .apply { headers(config, credentials) }
            .get()
            .build()

        val (code, text) = withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                response.code to response.body?.string().orEmpty()
            }
        }
        if (code !in 200..299) {
            val hint = if (code == 401 || code == 403) {
                " Cookies may be expired, or this ESPN account cannot see the league."
            } else ""
            throw EspnException(
                "ESPN read failed ($code).$hint",
                code,
                text.take(400),
            )
        }
        return runCatching { JSONObject(text) }.getOrElse { JSONObject() }
    }

    suspend fun send(
        config: LeagueConfig,
        credentials: Credentials,
        method: String,
        path: String,
        body: JSONObject,
    ): RawResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${writeBase(config)}$path")
            .apply { headers(config, credentials) }
            .method(method, body.toString().toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { response ->
            RawResponse(response.code, response.body?.string().orEmpty())
        }
    }

    suspend fun proTeams(game: GameId): Map<String, ProTeamInfo> {
        val cached = proTeamsCache[game]
        if (cached != null && System.currentTimeMillis() - cached.first < 12 * 3600 * 1000L) {
            return cached.second
        }

        val map = mutableMapOf<String, ProTeamInfo>()
        runCatching {
            val request = Request.Builder()
                .url("https://site.api.espn.com/apis/site/v2/sports/${game.siteSport}/teams")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            val text = withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response -> response.body?.string().orEmpty() }
            }
            val root = JSONObject(text)
            val teams = root.optJSONArray("sports")
                ?.optJSONObject(0)
                ?.optJSONArray("leagues")
                ?.optJSONObject(0)
                ?.optJSONArray("teams")
            if (teams != null) {
                for (i in 0 until teams.length()) {
                    val team = teams.optJSONObject(i)?.optJSONObject("team") ?: continue
                    val id = team.optString("id")
                    if (id.isBlank()) continue
                    map[id] = ProTeamInfo(
                        abbrev = team.optString("abbreviation"),
                        name = team.optString("displayName").ifBlank { team.optString("name") },
                        color = team.optString("color").takeIf { it.isNotBlank() }?.let { "#$it" },
                    )
                }
            }
        }
        proTeamsCache[game] = System.currentTimeMillis() to map
        return map
    }

    suspend fun resolveNames(game: GameId, ids: Collection<Int>): Map<Int, String> {
        val unique = ids.filter { it > 0 }.distinct().take(60)
        if (unique.isEmpty()) return emptyMap()
        return coroutineScope {
            unique.map { id ->
                async {
                    id to resolveName(game, id)
                }
            }.awaitAll().toMap()
        }
    }

    private suspend fun resolveName(game: GameId, playerId: Int): String {
        val key = "${game.id}:$playerId"
        nameCache[key]?.let { return it }

        var name = "Player $playerId"
        runCatching {
            val request = Request.Builder()
                .url("https://site.web.api.espn.com/apis/common/v3/sports/${game.siteSport}/athletes/$playerId")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()
            val text = withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response -> response.body?.string().orEmpty() }
            }
            val athlete = JSONObject(text).optJSONObject("athlete")
            val resolved = athlete?.optString("displayName").orEmpty()
                .ifBlank { athlete?.optString("fullName").orEmpty() }
            if (resolved.isNotBlank()) name = resolved
        }
        nameCache[key] = name
        return name
    }

    private fun readBase(config: LeagueConfig) =
        "https://lm-api-reads.fantasy.espn.com/apis/v3/games/${config.game.id}/seasons/${config.season}/segments/0/leagues/${config.leagueId}"

    private fun writeBase(config: LeagueConfig) =
        "https://lm-api-writes.fantasy.espn.com/apis/v3/games/${config.game.id}/seasons/${config.season}/segments/0/leagues/${config.leagueId}"

    private fun Request.Builder.headers(config: LeagueConfig, credentials: Credentials) =
        this
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Cookie", "espn_s2=${credentials.s2}; SWID=${credentials.swid}")
            .header("Origin", "https://fantasy.espn.com")
            .header(
                "Referer",
                "https://fantasy.espn.com/${config.game.sport.lowercase()}/league?leagueId=${config.leagueId}&seasonId=${config.season}",
            )
            .header("User-Agent", "Mozilla/5.0")
            .header("X-Fantasy-Source", "fantasy")
            .header("X-Fantasy-Platform", "kona-PROD-1")
}

