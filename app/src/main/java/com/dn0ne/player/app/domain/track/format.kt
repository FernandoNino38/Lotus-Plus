package com.dn0ne.player.app.domain.track

val Track.format: String
    get() = data.substringAfterLast(".", "")

private val losslessFormats = setOf(
    "flac", "wav", "wave", "alac", "aiff", "aif", "ape", "wv", "dsf", "dff"
)

val Track.isLossless: Boolean
    get() {
        val ext = format.lowercase()
        val kbps = bitrate?.toIntOrNull() ?: 0
        return ext in losslessFormats || (ext == "m4a" && kbps >= 500)
    }

val Track.isHiRes: Boolean
    get() {
        val kbps = bitrate?.toIntOrNull() ?: 0
        return isLossless && kbps > 1411
    }

val Track.audioQualityBadgeText: String
    get() {
        val ext = format.uppercase().takeIf { it.isNotBlank() }
        val kbps = bitrate?.toIntOrNull()
        val codec = when {
            ext == "M4A" && (kbps ?: 0) >= 500 -> "ALAC"
            else -> ext
        }
        val tier = when {
            isHiRes -> "HI-RES"
            isLossless -> "LOSSLESS"
            (kbps ?: 0) >= 256 -> "HQ"
            else -> null
        }
        return listOfNotNull(
            tier,
            codec,
            kbps?.let { "$it kbps" }
        ).joinToString(" • ")
    }

val Track.audioQualityInfoText: String
    get() {
        val ext = format.uppercase().takeIf { it.isNotBlank() }
        val kbps = bitrate?.toIntOrNull()
        val codec = if (ext == "M4A" && (kbps ?: 0) >= 500) "ALAC" else ext
        val tier = when {
            isHiRes -> "Hi-Res Lossless"
            isLossless -> "Lossless"
            (kbps ?: 0) >= 256 -> "High Quality"
            else -> "Standard"
        }
        val details = listOfNotNull(codec, kbps?.let { "$it kbps" }).joinToString(" • ")
        return if (details.isNotBlank()) "$tier ($details)" else tier
    }
