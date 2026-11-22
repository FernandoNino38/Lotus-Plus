package com.dn0ne.player.app.domain.track

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.serialization.Serializable


@Serializable(with = TrackSerializer::class)
data class Track(
    val uri: Uri,
    val mediaItem: MediaItem,
    val coverArtUri: Uri,
    val duration: Int,
    val size: Long,
    val dateModified: Long,
    val data: String,

    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val trackNumber: String? = null,
    val bitrate: String? = null
)

fun buildTrackMediaItem(
    uri: Uri,
    coverArtUri: Uri,
    title: String?,
    artist: String?,
    album: String?,
    albumArtist: String?,
    genre: String?
): MediaItem = MediaItem.Builder()
    .setUri(uri)
    .setMediaId(uri.toString())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setAlbumArtist(albumArtist)
            .setGenre(genre)
            .setArtworkUri(coverArtUri)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
    )
    .build()