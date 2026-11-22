package com.dn0ne.player.core.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import com.dn0ne.player.R
import com.dn0ne.player.app.domain.track.Track

fun Context.shareTrack(track: Track) {
    val mimeType = contentResolver.getType(track.uri) ?: "audio/*"
    val title = track.title ?: resources.getString(R.string.share)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, track.uri)
        clipData = ClipData.newRawUri(title, track.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(shareIntent, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(chooser)
}

fun Context.shareTracks(tracks: List<Track>) {
    if (tracks.isEmpty()) return
    if (tracks.size == 1) {
        shareTrack(tracks.first())
        return
    }

    val uris = ArrayList(tracks.map { it.uri })
    val shareTitle = resources.getString(R.string.share)
    val clipData = ClipData.newRawUri(shareTitle, uris.first()).apply {
        for (i in 1 until uris.size) {
            addItem(ClipData.Item(uris[i]))
        }
    }

    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "audio/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        this.clipData = clipData
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(shareIntent, shareTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(chooser)
}
