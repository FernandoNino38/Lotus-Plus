package com.dn0ne.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.ui.util.fastForEach
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.dn0ne.player.app.data.LyricsReader
import com.dn0ne.player.app.data.SavedPlayerState
import com.dn0ne.player.app.data.remote.lyrics.LyricsProvider
import com.dn0ne.player.app.data.repository.LyricsRepository
import com.dn0ne.player.app.data.repository.PlaylistRepository
import com.dn0ne.player.app.data.repository.TrackRepository
import com.dn0ne.player.app.domain.lyrics.LyricLine
import com.dn0ne.player.app.domain.lyrics.Lyrics
import com.dn0ne.player.app.domain.playback.PlaybackMode
import com.dn0ne.player.app.domain.result.Result
import com.dn0ne.player.app.domain.sort.sortedBy
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.domain.track.filterTracks
import com.dn0ne.player.core.data.Settings
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.get

private class EqualizerSettings(context: Context) {
    private val sharedPreferences =
        context.getSharedPreferences("equalizer-settings", Context.MODE_PRIVATE)

    private val isEnabledKey = "is-equalizer-enabled"
    private val bandFrequenciesKey = "band-frequencies"
    private val lowerLevelLimitKey = "lower-level-limit"
    private val upperLevelLimitKey = "upper-level-limit"
    private val bandLevelsKey = "band-levels"

    var isEnabled: Boolean
        get() = sharedPreferences.getBoolean(isEnabledKey, false)
        set(value) {
            with(sharedPreferences.edit()) {
                putBoolean(isEnabledKey, value)
                apply()
            }
        }

    var bandFrequencies: List<String>?
        get() = sharedPreferences.getString(bandFrequenciesKey, null)
            ?.let { Json.decodeFromString(it) }
        set(value) {
            with(sharedPreferences.edit()) {
                putString(bandFrequenciesKey, value?.let { Json.encodeToString(it) })
                apply()
            }
        }

    var lowerLevelLimit: Int
        get() = sharedPreferences.getInt(lowerLevelLimitKey, 0)
        set(value) {
            with(sharedPreferences.edit()) {
                putInt(lowerLevelLimitKey, value)
                apply()
            }
        }

    var upperLevelLimit: Int
        get() = sharedPreferences.getInt(upperLevelLimitKey, 0)
        set(value) {
            with(sharedPreferences.edit()) {
                putInt(upperLevelLimitKey, value)
                apply()
            }
        }

    var bandLevels: List<Short>?
        get() = sharedPreferences.getString(bandLevelsKey, null)
            ?.let { Json.decodeFromString(it) }
        set(value) {
            with(sharedPreferences.edit()) {
                putString(bandLevelsKey, value?.let { Json.encodeToString(it) })
                apply()
            }
        }
}

class EqualizerController(context: Context) {
    private val _settings = EqualizerSettings(context)
    private var _audioSessionId: Int? = null
    var equalizer: Equalizer? = null
        private set

    init {
        firstLaunchInit()
    }

    private val _isEqEnabled = MutableStateFlow(_settings.isEnabled)
    val isEqEnabled = _isEqEnabled.asStateFlow()
    fun updateIsEqEnabled(value: Boolean) {
        _isEqEnabled.update { value }
        _settings.isEnabled = value

        _audioSessionId?.let {
            updateEqualizer(it)
        }
    }

    private val _bandFrequencies = MutableStateFlow<List<String>?>(_settings.bandFrequencies)
    val bandFrequencies = _bandFrequencies.asStateFlow()

    private val _lowerLevelLimit = MutableStateFlow<Short>(_settings.lowerLevelLimit.toShort())
    val lowerLevelLimit = _lowerLevelLimit.asStateFlow()

    private val _upperLevelLimit = MutableStateFlow<Short>(_settings.upperLevelLimit.toShort())
    val upperLevelLimit = _upperLevelLimit.asStateFlow()

    private val _bandLevels = MutableStateFlow(_settings.bandLevels)
    val bandLevels = _bandLevels.asStateFlow()
    fun updateBandLevels(levels: List<Short>) {
        _bandLevels.update { levels }
        _settings.bandLevels = levels

        _audioSessionId?.let {
            updateEqualizer(it)
        }
    }

    fun updateEqualizer(audioSessionId: Int) {
        equalizer?.release()

        _audioSessionId = audioSessionId

        if (!_settings.isEnabled) return

        equalizer = Equalizer(Int.MAX_VALUE, audioSessionId).apply {
            enabled = true

            (0 until numberOfBands).forEach {
                println("BAND FREQ RANGE: ${getBandFreqRange(it.toShort()).joinToString("..")}")
            }
            _settings.bandLevels?.forEachIndexed { band, level ->
                setBandLevel(band.toShort(), level)
            }
        }
    }

    fun releaseEqualizer() {
        equalizer?.release()
        equalizer = null
    }

    fun resetBandLevels() {
        _settings.bandLevels?.let { bandLevels ->
            val defaultLevels = List<Short>(bandLevels.size) { 0 }
            _settings.bandLevels = defaultLevels
            _bandLevels.update {
                defaultLevels
            }
        }

        _audioSessionId?.let {
            updateEqualizer(it)
        }
    }

    private fun firstLaunchInit() {
        if (_settings.bandFrequencies == null || _settings.bandLevels == null) {
            try {
                val audioTrack = AudioTrack.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(44100)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(
                        AudioTrack.getMinBufferSize(
                            44100,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                    )
                    .build()

                Equalizer(0, audioTrack.audioSessionId).run {
                    enabled = false

                    val defaultLevels = List<Short>(numberOfBands.toInt()) { 0 }
                    _settings.bandLevels = defaultLevels

                    val bandFreqs = List(numberOfBands.toInt()) {
                        getBandFreqRange(it.toShort()).joinToString("-") {
                            (it / 1000).toString()
                        } + "Hz"
                    }
                    _settings.bandFrequencies = bandFreqs

                    val lowerLevelLimit = bandLevelRange[0]
                    _settings.lowerLevelLimit = lowerLevelLimit.toInt()

                    val upperLevelLimit = bandLevelRange[1]
                    _settings.upperLevelLimit = upperLevelLimit.toInt()

                    release()
                }

                audioTrack.release()
            } catch (e: Exception) {
                Log.e("FIRST LAUNCH EQUALIZER INITIALIZATION", e.message, e)
            }
        }
    }
}

@OptIn(UnstableApi::class)
private class LyricsForwardingPlayer(player: Player) : ForwardingPlayer(player) {
    private val listeners = CopyOnWriteArraySet<Player.Listener>()

    var activeLyricSubtitle: String? = null
        private set

    override fun getMediaMetadata(): MediaMetadata {
        val base = super.getMediaMetadata()
        val line = activeLyricSubtitle
        return if (!line.isNullOrBlank()) {
            base.buildUpon()
                .setArtist(line)
                .setSubtitle(line)
                .build()
        } else {
            base
        }
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        super.removeListener(listener)
    }

    fun updateLyricSubtitle(line: String?) {
        if (activeLyricSubtitle == line) return
        activeLyricSubtitle = line
        notifyMetadataChanged()
    }

    fun notifyMetadataChanged() {
        val currentMetadata = mediaMetadata
        val events = Player.Events(
            FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build()
        )
        for (listener in listeners) {
            listener.onMediaMetadataChanged(currentMetadata)
            listener.onEvents(this, events)
        }
    }
}

class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private var forwardingPlayer: LyricsForwardingPlayer? = null
    private val equalizerController = get<EqualizerController>()
    private val trackRepository = get<TrackRepository>()
    private val playlistRepository = get<PlaylistRepository>()
    private val lyricsRepository = get<LyricsRepository>()
    private val lyricsProvider = get<LyricsProvider>()
    private val lyricsReader = get<LyricsReader>()
    private val savedPlayerState = get<SavedPlayerState>()
    private val settings = get<Settings>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lyricsLoadJob: Job? = null
    private var lyricsSyncJob: Job? = null
    private var currentLyricLines: List<LyricLine> = emptyList()
    private var currentTrackArtist: String? = null

    companion object {
        private const val ROOT_ID = "root"
        private const val TRACKS_ROOT_ID = "tracks"
        private const val PLAYLISTS_ROOT_ID = "playlists"
        private const val ALBUMS_ROOT_ID = "albums"
        private const val ARTISTS_ROOT_ID = "artists"
        private const val GENRES_ROOT_ID = "genres"
        private const val FOLDERS_ROOT_ID = "folders"

        private const val RECENTLY_ADDED_PLAYLIST_ID = "__recently_added__"
        private const val MOST_PLAYED_PLAYLIST_ID = "__most_played__"
        private const val ACTION_TOGGLE_AUTO_LYRICS = "com.dn0ne.player.TOGGLE_AUTO_LYRICS"
    }

    override fun onCreate() {
        super.onCreate()

        val shouldHandleAudioFocus = settings.handleAudioFocus
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, shouldHandleAudioFocus)
            .setHandleAudioBecomingNoisy(true)
            .build()

        val player = LyricsForwardingPlayer(exoPlayer).also {
            forwardingPlayer = it
        }

        exoPlayer.addListener(object : Player.Listener {
            @OptIn(UnstableApi::class)
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                    val audioSessionId = exoPlayer.audioSessionId
                    if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                        equalizerController.updateEqualizer(audioSessionId)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (currentLyricLines.isEmpty() && exoPlayer.currentMediaItem != null && lyricsLoadJob?.isActive != true) {
                        loadLyricsForCurrentItem(exoPlayer.currentMediaItem)
                    }
                    startLyricsSyncLoop()
                } else {
                    lyricsSyncJob?.cancel()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                updateCurrentLyricOnPlayer()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                if (!player.activeLyricSubtitle.isNullOrBlank()) {
                    player.notifyMetadataChanged()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                loadLyricsForCurrentItem(mediaItem)
            }
        })

        // Restaura a última fila salva caso o serviço seja iniciado diretamente pelo Android Auto
        serviceScope.launch {
            val savedPlaylist = savedPlayerState.playlist
            val savedTrack = savedPlayerState.track
            val savedMode = savedPlayerState.playbackMode
            if (savedPlaylist != null && savedPlaylist.trackList.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    if (exoPlayer.mediaItemCount == 0) {
                        exoPlayer.addMediaItems(savedPlaylist.trackList.map { it.mediaItem })
                        val startIdx = savedPlaylist.trackList.indexOfFirst {
                            it.uri == savedTrack?.uri
                        }.coerceAtLeast(0)
                        exoPlayer.seekTo(startIdx, 0L)
                        applyPlaybackMode(exoPlayer, savedMode)
                        exoPlayer.prepare()
                        loadLyricsForCurrentItem(exoPlayer.currentMediaItem)
                    }
                }
            }
        }

        SleepTimer.addOnFinishCallback {
            exoPlayer.stop()
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .setCustomLayout(buildCustomLayout(settings.androidAutoSyncedLyrics.value))
            .build()

        serviceScope.launch {
            settings.androidAutoSyncedLyrics.collectLatest { enabled ->
                withContext(Dispatchers.Main) {
                    mediaSession?.setCustomLayout(buildCustomLayout(enabled))
                    updateCurrentLyricOnPlayer()
                    if (enabled && player.isPlaying) {
                        startLyricsSyncLoop()
                    } else if (!enabled) {
                        lyricsSyncJob?.cancel()
                    }
                }
            }
        }

        serviceScope.launch {
            settings.lyricsSyncOffsetFlow.collectLatest {
                withContext(Dispatchers.Main) {
                    updateCurrentLyricOnPlayer()
                }
            }
        }
    }

    private fun buildCustomLayout(isLyricsEnabled: Boolean): List<CommandButton> {
        val toggleCommand = SessionCommand(ACTION_TOGGLE_AUTO_LYRICS, Bundle.EMPTY)
        val button = CommandButton.Builder()
            .setDisplayName(
                getString(
                    if (isLyricsEnabled) R.string.android_auto_lyrics_on
                    else R.string.android_auto_lyrics_off
                )
            )
            .setSessionCommand(toggleCommand)
            .setIconResId(
                if (isLyricsEnabled) R.drawable.ic_lyrics_on
                else R.drawable.ic_lyrics_off
            )
            .build()
        return listOf(button)
    }

    private fun loadLyricsForCurrentItem(mediaItem: MediaItem?) {
        lyricsLoadJob?.cancel()
        currentLyricLines = emptyList()
        currentTrackArtist = mediaItem?.mediaMetadata?.artist?.toString()
        forwardingPlayer?.updateLyricSubtitle(null)

        val rawId = mediaItem?.localConfiguration?.uri?.toString() ?: mediaItem?.mediaId ?: return
        val uriStr = extractTrackUri(rawId)
        if (uriStr.isBlank()) return

        lyricsLoadJob = serviceScope.launch {
            val currentPlaylist = savedPlayerState.playlist
            val matchedTrack = currentPlaylist?.trackList?.firstOrNull {
                it.uri.toString() == uriStr || it.mediaItem.mediaId == uriStr
            } ?: trackRepository.getTracks().firstOrNull {
                it.uri.toString() == uriStr || it.mediaItem.mediaId == uriStr
            }

            if (matchedTrack != null) {
                savedPlayerState.track = matchedTrack
                if (!matchedTrack.artist.isNullOrBlank()) {
                    currentTrackArtist = matchedTrack.artist
                }
            }

            val lyrics = resolveTrackLyrics(uriStr, matchedTrack)
            val resolvedLines = lyrics?.resolvedLines.orEmpty()
            withContext(Dispatchers.Main) {
                currentLyricLines = resolvedLines
                updateCurrentLyricOnPlayer()
                if (forwardingPlayer?.isPlaying == true && resolvedLines.isNotEmpty() && settings.androidAutoSyncedLyrics.value) {
                    startLyricsSyncLoop()
                }
            }
        }
    }

    private suspend fun resolveTrackLyrics(uriStr: String, track: Track?): Lyrics? {
        var lyrics = runCatching { lyricsRepository.getLyricsByUri(uriStr) }.getOrNull()
        if (lyrics?.synced != null || lyrics?.lines != null) {
            return lyrics
        }

        if (track != null) {
            val tagResult = runCatching { lyricsReader.readFromTag(track) }.getOrNull()
            if (tagResult is Result.Success && tagResult.data != null) {
                val tagLyrics = tagResult.data
                runCatching { lyricsRepository.insertLyrics(tagLyrics) }
                if (tagLyrics.synced != null || tagLyrics.lines != null) {
                    return tagLyrics
                }
                lyrics = tagLyrics
            }

            val remoteResult = runCatching { lyricsProvider.getLyrics(track) }.getOrNull()
            if (remoteResult is Result.Success) {
                val remoteLyrics = remoteResult.data
                runCatching { lyricsRepository.insertLyrics(remoteLyrics) }
                return remoteLyrics
            }
        }

        return lyrics
    }

    private fun startLyricsSyncLoop() {
        lyricsSyncJob?.cancel()
        if (!settings.androidAutoSyncedLyrics.value) return

        lyricsSyncJob = serviceScope.launch(Dispatchers.Main) {
            var emptyCheckCounter = 0
            while (forwardingPlayer?.isPlaying == true && settings.androidAutoSyncedLyrics.value) {
                if (currentLyricLines.isEmpty()) {
                    emptyCheckCounter++
                    // Caso o usuário tenha acabado de baixar/importar a letra no celular
                    if (emptyCheckCounter >= 35) {
                        emptyCheckCounter = 0
                        val item = forwardingPlayer?.currentMediaItem
                        val rawId = item?.localConfiguration?.uri?.toString() ?: item?.mediaId
                        if (rawId != null) {
                            val uriStr = extractTrackUri(rawId)
                            val cached = withContext(Dispatchers.IO) {
                                runCatching { lyricsRepository.getLyricsByUri(uriStr) }.getOrNull()
                            }
                            val resolved = cached?.resolvedLines.orEmpty()
                            if (resolved.isNotEmpty()) {
                                currentLyricLines = resolved
                            }
                        }
                    }
                } else {
                    updateCurrentLyricOnPlayer()
                }
                delay(80L)
            }
        }
    }

    private fun updateCurrentLyricOnPlayer() {
        val player = forwardingPlayer ?: return
        if (!settings.androidAutoSyncedLyrics.value || currentLyricLines.isEmpty()) {
            player.updateLyricSubtitle(null)
            return
        }

        val effectivePos = player.currentPosition + settings.lyricsSyncOffset
        val lineIndex = currentLyricLines.indexOfLast { effectivePos >= it.startTimeMs }
        if (lineIndex < 0) {
            player.updateLyricSubtitle(null)
            return
        }

        val line = currentLyricLines[lineIndex]
        val subtitle = when {
            line.isInstrumental || line.text.isBlank() -> {
                val artist = currentTrackArtist?.takeIf { it.isNotBlank() }
                if (artist != null) "♪ • $artist" else "♪"
            }
            effectivePos > line.endTimeMs + 4000L -> {
                val artist = currentTrackArtist?.takeIf { it.isNotBlank() }
                if (artist != null) "♪ • $artist" else "♪"
            }
            else -> line.text
        }
        player.updateLyricSubtitle(subtitle)
    }

    private fun applyPlaybackMode(player: Player, mode: PlaybackMode) {
        when (mode) {
            PlaybackMode.Repeat -> {
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = false
            }
            PlaybackMode.RepeatOne -> {
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.shuffleModeEnabled = false
            }
            PlaybackMode.Shuffle -> {
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.shuffleModeEnabled = true
            }
        }
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_AUTO_LYRICS, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.accept(
                availableSessionCommands,
                connectionResult.availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_AUTO_LYRICS) {
                settings.updateAndroidAutoSyncedLyrics(!settings.androidAutoSyncedLyrics.value)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        @OptIn(UnstableApi::class)
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = Bundle().apply {
                putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true)
                putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, true)
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                )
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                )
            }
            val rootParams = LibraryParams.Builder()
                .setExtras(extras)
                .build()

            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()

            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, rootParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                try {
                    val children = buildChildrenForParent(parentId)
                    future.set(LibraryResult.ofItemList(ImmutableList.copyOf(children), params))
                } catch (e: Exception) {
                    Log.e("PlaybackService", "Error loading children for $parentId", e)
                    future.set(LibraryResult.ofItemList(ImmutableList.of(), params))
                }
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch {
                try {
                    val tracks = getAllSortedTracks()
                    val trackUriStr = extractTrackUri(mediaId)
                    val track = tracks.firstOrNull { it.uri.toString() == trackUriStr }
                    val item = track?.let { toBrowsableTrackItem(mediaId, it) }
                        ?: MediaItem.Builder().setMediaId(mediaId).build()
                    future.set(LibraryResult.ofItem(item, null))
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val future = SettableFuture.create<LibraryResult<Void>>()
            serviceScope.launch {
                val results = getAllSortedTracks().filterTracks(query)
                session.notifySearchResultChanged(browser, query, results.size, params)
                future.set(LibraryResult.ofVoid(params))
            }
            return future
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                val results = getAllSortedTracks()
                    .filterTracks(query)
                    .map { toBrowsableTrackItem("$TRACKS_ROOT_ID|${it.uri}", it) }
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(results), params))
            }
            return future
        }

        @OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // Se já vieram múltiplos itens com URI configurada (ex.: app no celular), mantém o comportamento direto
            if (mediaItems.size > 1 && mediaItems.all { it.localConfiguration?.uri != null }) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        mediaItems,
                        startIndex.coerceAtLeast(0),
                        startPositionMs
                    )
                )
            }

            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                try {
                    val requestedItem = mediaItems.getOrNull(startIndex.coerceAtLeast(0))
                        ?: mediaItems.firstOrNull()
                    val resolved = resolvePlaybackContext(requestedItem)
                    if (resolved != null) {
                        val (playlist, targetIndex, targetPos) = resolved
                        val targetTrack = playlist.trackList.getOrNull(targetIndex)
                        savedPlayerState.playlist = playlist
                        savedPlayerState.track = targetTrack
                        if (targetTrack != null) {
                            savedPlayerState.incrementPlayCount(targetTrack.uri.toString())
                        }
                        future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                playlist.trackList.map { it.mediaItem },
                                targetIndex,
                                if (startPositionMs != C.TIME_UNSET && startPositionMs > 0) startPositionMs else targetPos
                            )
                        )
                    } else {
                        future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                mediaItems,
                                startIndex.coerceAtLeast(0),
                                startPositionMs
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("PlaybackService", "Error in onSetMediaItems", e)
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(
                            mediaItems,
                            startIndex.coerceAtLeast(0),
                            startPositionMs
                        )
                    )
                }
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            if (mediaItems.all { it.localConfiguration?.uri != null }) {
                return Futures.immediateFuture(mediaItems)
            }
            val future = SettableFuture.create<MutableList<MediaItem>>()
            serviceScope.launch {
                val tracks = getAllSortedTracks()
                val resolved = mediaItems.mapNotNull { item ->
                    item.localConfiguration?.uri?.let { return@mapNotNull item }
                    val uriStr = extractTrackUri(item.mediaId)
                    tracks.firstOrNull { it.uri.toString() == uriStr }?.mediaItem
                }.toMutableList()
                future.set(resolved)
            }
            return future
        }
    }

    private fun getAllSortedTracks(): List<Track> {
        return trackRepository.getTracks().sortedBy(settings.trackSort, settings.trackSortOrder)
    }

    private suspend fun buildChildrenForParent(parentId: String): List<MediaItem> {
        val tracks = getAllSortedTracks()
        return when {
            parentId == ROOT_ID -> listOf(
                buildBrowsableFolderItem(
                    mediaId = TRACKS_ROOT_ID,
                    title = getString(R.string.tracks),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                ),
                buildBrowsableFolderItem(
                    mediaId = PLAYLISTS_ROOT_ID,
                    title = getString(R.string.playlists),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                ),
                buildBrowsableFolderItem(
                    mediaId = ALBUMS_ROOT_ID,
                    title = getString(R.string.albums),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                ),
                buildBrowsableFolderItem(
                    mediaId = ARTISTS_ROOT_ID,
                    title = getString(R.string.artists),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
                ),
                buildBrowsableFolderItem(
                    mediaId = GENRES_ROOT_ID,
                    title = getString(R.string.genres),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_GENRES
                ),
                buildBrowsableFolderItem(
                    mediaId = FOLDERS_ROOT_ID,
                    title = getString(R.string.folders),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

            parentId == TRACKS_ROOT_ID -> {
                tracks.map { track ->
                    toBrowsableTrackItem("$TRACKS_ROOT_ID|${track.uri}", track)
                }
            }

            parentId == PLAYLISTS_ROOT_ID -> {
                val userPlaylists = playlistRepository.getPlaylists().first()
                    .sortedBy(settings.playlistSort, settings.playlistSortOrder)
                val recentlyAdded = tracks.sortedByDescending { it.dateModified }.take(100)
                val playCounts = savedPlayerState.playCounts.value
                val mostPlayed = tracks
                    .filter { (playCounts[it.uri.toString()] ?: 0) > 0 }
                    .sortedWith(
                        compareByDescending<Track> { playCounts[it.uri.toString()] ?: 0 }
                            .thenByDescending { it.dateModified }
                    )
                    .take(100)

                buildList {
                    if (recentlyAdded.isNotEmpty()) {
                        add(
                            buildBrowsableFolderItem(
                                mediaId = "playlist|$RECENTLY_ADDED_PLAYLIST_ID",
                                title = getString(R.string.recently_added),
                                subtitle = "${recentlyAdded.size} ${getString(R.string.tracks).lowercase()}",
                                artworkUri = recentlyAdded.firstOrNull()?.coverArtUri,
                                mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                                isPlayable = true
                            )
                        )
                    }
                    if (mostPlayed.isNotEmpty()) {
                        add(
                            buildBrowsableFolderItem(
                                mediaId = "playlist|$MOST_PLAYED_PLAYLIST_ID",
                                title = getString(R.string.most_played),
                                subtitle = "${mostPlayed.size} ${getString(R.string.tracks).lowercase()}",
                                artworkUri = mostPlayed.firstOrNull()?.coverArtUri,
                                mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                                isPlayable = true
                            )
                        )
                    }
                    userPlaylists.forEach { playlist ->
                        val name = playlist.name ?: getString(R.string.unknown)
                        add(
                            buildBrowsableFolderItem(
                                mediaId = "playlist|$name",
                                title = name,
                                subtitle = "${playlist.trackList.size} ${getString(R.string.tracks).lowercase()}",
                                artworkUri = playlist.trackList.firstOrNull()?.coverArtUri,
                                mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                                isPlayable = true
                            )
                        )
                    }
                }
            }

            parentId == ALBUMS_ROOT_ID -> {
                tracks.groupBy { it.album }
                    .map { (album, albumTracks) ->
                        Playlist(name = album, trackList = albumTracks)
                    }
                    .sortedBy(settings.playlistSort, settings.playlistSortOrder)
                    .map { playlist ->
                        val name = playlist.name ?: getString(R.string.unknown_album)
                        val key = playlist.name ?: "__null__"
                        buildBrowsableFolderItem(
                            mediaId = "album|$key",
                            title = name,
                            subtitle = playlist.trackList.firstOrNull()?.artist
                                ?: getString(R.string.unknown_artist),
                            artworkUri = playlist.trackList.firstOrNull()?.coverArtUri,
                            mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
                            isPlayable = true
                        )
                    }
            }

            parentId == ARTISTS_ROOT_ID -> {
                tracks.groupBy { it.artist }
                    .map { (artist, artistTracks) ->
                        Playlist(name = artist, trackList = artistTracks)
                    }
                    .sortedBy(settings.playlistSort, settings.playlistSortOrder)
                    .map { playlist ->
                        val name = playlist.name ?: getString(R.string.unknown_artist)
                        val key = playlist.name ?: "__null__"
                        buildBrowsableFolderItem(
                            mediaId = "artist|$key",
                            title = name,
                            subtitle = "${playlist.trackList.size} ${getString(R.string.tracks).lowercase()}",
                            artworkUri = playlist.trackList.firstOrNull()?.coverArtUri,
                            mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
                            isPlayable = true
                        )
                    }
            }

            parentId == GENRES_ROOT_ID -> {
                tracks.groupBy { it.genre }
                    .map { (genre, genreTracks) ->
                        Playlist(name = genre, trackList = genreTracks)
                    }
                    .sortedBy(settings.playlistSort, settings.playlistSortOrder)
                    .map { playlist ->
                        val name = playlist.name ?: getString(R.string.unknown_genre)
                        val key = playlist.name ?: "__null__"
                        buildBrowsableFolderItem(
                            mediaId = "genre|$key",
                            title = name,
                            subtitle = "${playlist.trackList.size} ${getString(R.string.tracks).lowercase()}",
                            artworkUri = playlist.trackList.firstOrNull()?.coverArtUri,
                            mediaType = MediaMetadata.MEDIA_TYPE_GENRE,
                            isPlayable = true
                        )
                    }
            }

            parentId == FOLDERS_ROOT_ID -> {
                tracks.groupBy { it.data.substringBeforeLast('/') }
                    .map { (folderPath, folderTracks) ->
                        folderPath to Playlist(
                            name = folderPath.substringAfterLast('/'),
                            trackList = folderTracks
                        )
                    }
                    .sortedBy { it.second.name?.lowercase() ?: "" }
                    .map { (folderPath, playlist) ->
                        val name = playlist.name ?: getString(R.string.unknown_folder)
                        buildBrowsableFolderItem(
                            mediaId = "folder|$folderPath",
                            title = name,
                            subtitle = "${playlist.trackList.size} ${getString(R.string.tracks).lowercase()}",
                            artworkUri = playlist.trackList.firstOrNull()?.coverArtUri,
                            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                            isPlayable = true
                        )
                    }
            }

            parentId.startsWith("playlist|") ||
                parentId.startsWith("album|") ||
                parentId.startsWith("artist|") ||
                parentId.startsWith("genre|") ||
                parentId.startsWith("folder|") -> {
                val playlist = getPlaylistForCategoryId(parentId, tracks)
                playlist?.trackList?.map { track ->
                    toBrowsableTrackItem("$parentId|${track.uri}", track)
                }.orEmpty()
            }

            else -> emptyList()
        }
    }

    private suspend fun getPlaylistForCategoryId(
        categoryId: String,
        allTracks: List<Track>
    ): Playlist? {
        val parts = categoryId.split("|", limit = 3)
        if (parts.size < 2) return null
        val type = parts[0]
        val key = parts[1]
        val nullableKey = key.takeIf { it != "__null__" }

        return when (type) {
            "playlist" -> when (key) {
                RECENTLY_ADDED_PLAYLIST_ID -> Playlist(
                    name = getString(R.string.recently_added),
                    trackList = allTracks.sortedByDescending { it.dateModified }.take(100)
                )
                MOST_PLAYED_PLAYLIST_ID -> {
                    val counts = savedPlayerState.playCounts.value
                    Playlist(
                        name = getString(R.string.most_played),
                        trackList = allTracks
                            .filter { (counts[it.uri.toString()] ?: 0) > 0 }
                            .sortedWith(
                                compareByDescending<Track> { counts[it.uri.toString()] ?: 0 }
                                    .thenByDescending { it.dateModified }
                            )
                            .take(100)
                    )
                }
                else -> playlistRepository.getPlaylists().first()
                    .firstOrNull { it.name == key }
            }
            "album" -> {
                val filtered = allTracks.filter { it.album == nullableKey }
                if (filtered.isNotEmpty()) Playlist(name = nullableKey, trackList = filtered) else null
            }
            "artist" -> {
                val filtered = allTracks.filter { it.artist == nullableKey }
                if (filtered.isNotEmpty()) Playlist(name = nullableKey, trackList = filtered) else null
            }
            "genre" -> {
                val filtered = allTracks.filter { it.genre == nullableKey }
                if (filtered.isNotEmpty()) Playlist(name = nullableKey, trackList = filtered) else null
            }
            "folder" -> {
                val filtered = allTracks.filter { it.data.substringBeforeLast('/') == key }
                if (filtered.isNotEmpty()) {
                    Playlist(name = key.substringAfterLast('/'), trackList = filtered)
                } else null
            }
            else -> null
        }
    }

    private suspend fun resolvePlaybackContext(
        requestedItem: MediaItem?
    ): Triple<Playlist, Int, Long>? {
        val allTracks = getAllSortedTracks()
        if (allTracks.isEmpty()) return null

        val searchQuery = requestedItem?.requestMetadata?.searchQuery
        if (!searchQuery.isNullOrBlank()) {
            val matched = allTracks.filterTracks(searchQuery)
            val list = matched.ifEmpty { allTracks }
            return Triple(Playlist(name = null, trackList = list), 0, 0L)
        }

        val mediaId = requestedItem?.mediaId.orEmpty()
        if (mediaId.isBlank()) {
            val saved = savedPlayerState.playlist?.takeIf { it.trackList.isNotEmpty() }
                ?: Playlist(name = null, trackList = allTracks)
            val idx = saved.trackList.indexOfFirst { it.uri == savedPlayerState.track?.uri }
                .coerceAtLeast(0)
            return Triple(saved, idx, 0L)
        }

        // Caso 1: clique em uma faixa na aba Músicas ("tracks|<uri>")
        if (mediaId.startsWith("$TRACKS_ROOT_ID|")) {
            val trackUri = mediaId.removePrefix("$TRACKS_ROOT_ID|")
            val index = allTracks.indexOfFirst { it.uri.toString() == trackUri }.coerceAtLeast(0)
            return Triple(Playlist(name = null, trackList = allTracks), index, 0L)
        }

        // Caso 2: clique em uma categoria/playlist ou faixa dentro dela ("tipo|chave" ou "tipo|chave|uri")
        val parts = mediaId.split("|", limit = 3)
        if (parts.size >= 2) {
            val categoryId = "${parts[0]}|${parts[1]}"
            val playlist = getPlaylistForCategoryId(categoryId, allTracks)
            if (playlist != null && playlist.trackList.isNotEmpty()) {
                val targetUri = parts.getOrNull(2)
                val index = if (targetUri != null) {
                    playlist.trackList.indexOfFirst { it.uri.toString() == targetUri }.coerceAtLeast(0)
                } else 0
                return Triple(playlist, index, 0L)
            }
        }

        // Caso 3: mediaId é diretamente a URI da faixa
        val directIndex = allTracks.indexOfFirst { it.uri.toString() == mediaId }
        if (directIndex >= 0) {
            return Triple(Playlist(name = null, trackList = allTracks), directIndex, 0L)
        }

        return Triple(Playlist(name = null, trackList = allTracks), 0, 0L)
    }

    private fun extractTrackUri(mediaId: String): String {
        return mediaId.substringAfterLast('|')
    }

    private fun buildBrowsableFolderItem(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        artworkUri: Uri? = null,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        isPlayable: Boolean = false
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(artworkUri)
                    .setIsBrowsable(true)
                    .setIsPlayable(isPlayable)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()
    }

    private fun toBrowsableTrackItem(mediaId: String, track: Track): MediaItem {
        return track.mediaItem.buildUpon()
            .setMediaId(mediaId)
            .setMediaMetadata(
                track.mediaItem.mediaMetadata.buildUpon()
                    .setTitle(track.title ?: getString(R.string.unknown_title))
                    .setArtist(track.artist ?: getString(R.string.unknown_artist))
                    .setAlbumTitle(track.album ?: getString(R.string.unknown_album))
                    .setSubtitle(track.artist ?: getString(R.string.unknown_artist))
                    .build()
            )
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return

        if (!player.playWhenReady
            || player.mediaItemCount == 0
            || player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        lyricsLoadJob?.cancel()
        lyricsSyncJob?.cancel()
        serviceScope.cancel()
        equalizerController.releaseEqualizer()
        SleepTimer.stop()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        forwardingPlayer = null
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

}

object SleepTimer {
    var timer: CountDownTimer? = null
    private val finishCallbacks = mutableListOf<() -> Unit>()

    private val _minutesLeft = MutableStateFlow(1)
    val minutesLeft = _minutesLeft.asStateFlow()
    fun updateMinutesLeft(value: Int) {
        _minutesLeft.update { value }
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    fun start() {
        stop()
        timer = object : CountDownTimer(_minutesLeft.value.toLong() * 60000L, 60000L) {
            override fun onTick(millisUntilFinished: Long) {
                _minutesLeft.update { millisUntilFinished.toInt() / 60000 + 1 }
            }

            override fun onFinish() {
                finishCallbacks.fastForEach { it.invoke() }
                _isRunning.update { false }
            }

        }.start()

        _isRunning.update { true }
    }

    fun stop() {
        timer?.cancel()

        _isRunning.update { false }
    }

    fun addOnFinishCallback(callback: () -> Unit) {
        finishCallbacks += callback
    }
}