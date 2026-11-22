package com.dn0ne.player.app.data

import android.content.Context
import com.dn0ne.player.app.domain.playback.PlaybackMode
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SavedPlayerState(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("saved-player-state", 0)

    private val playlistKey = "playlist"
    private val playbackModeKey = "playback-mode"
    private val trackKey = "track"
    private val playCountsKey = "play-counts"

    private val _playCounts = MutableStateFlow(loadPlayCounts())
    val playCounts = _playCounts.asStateFlow()

    var playlist: Playlist?
        get() {
            return sharedPreferences.getString(playlistKey, null)?.let {
                Json.decodeFromString<Playlist?>(it)
            }
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putString(playlistKey, Json.encodeToString(value))
                apply()
            }
        }

    var playbackMode: PlaybackMode
        get() {
            return PlaybackMode.entries[sharedPreferences.getInt(playbackModeKey, 0)]
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putInt(playbackModeKey, value.ordinal)
                apply()
            }
        }

    var track: Track?
        get() {
            return sharedPreferences.getString(trackKey, null)?.let {
                Json.decodeFromString<Track?>(it)
            }
        }
        set(value) {
            with(sharedPreferences.edit()) {
                putString(trackKey, Json.encodeToString(value))
                apply()
            }
        }

    private fun loadPlayCounts(): Map<String, Int> {
        val raw = sharedPreferences.getString(playCountsKey, null) ?: return emptyMap()
        return try {
            Json.decodeFromString<Map<String, Int>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun incrementPlayCount(trackKey: String) {
        val updated = _playCounts.value.toMutableMap()
        updated[trackKey] = (updated[trackKey] ?: 0) + 1
        _playCounts.value = updated
        with(sharedPreferences.edit()) {
            putString(playCountsKey, Json.encodeToString(updated))
            apply()
        }
    }
}
