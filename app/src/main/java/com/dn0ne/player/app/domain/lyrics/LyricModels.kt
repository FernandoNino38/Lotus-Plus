package com.dn0ne.player.app.domain.lyrics

import kotlinx.serialization.Serializable

@Serializable
data class LyricLine(
    val startTimeMs: Long,
    val endTimeMs: Long = 0L,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val isInstrumental: Boolean = text.isBlank()
)

@Serializable
data class LyricWord(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val trailingSpace: Boolean = false
)

enum class LyricWordState {
    INACTIVE,
    UPCOMING,
    CURRENT,
    PASSED
}

data class ClusteredWord(
    val words: List<LyricWord>,
    val trailingSpace: Boolean = true
)

/**
 * Clusterização Gramatical: agrupa sílabas e palavras para evitar quebras
 * inadequadas de linha no meio de palavras dentro de um FlowRow, preservando
 * o espaçamento natural.
 */
fun clusterSyncedWords(words: List<LyricWord>): List<ClusteredWord> {
    if (words.isEmpty()) return emptyList()

    val clusters = mutableListOf<ClusteredWord>()
    var currentClusterWords = mutableListOf<LyricWord>()

    for (i in words.indices) {
        val word = words[i]
        currentClusterWords.add(word)

        val hasTrailingSpace = word.trailingSpace ||
                word.text.endsWith(" ") ||
                word.text.endsWith("\t")

        val isLast = i == words.size - 1

        if (hasTrailingSpace || isLast) {
            clusters.add(
                ClusteredWord(
                    words = currentClusterWords.toList(),
                    trailingSpace = hasTrailingSpace
                )
            )
            currentClusterWords = mutableListOf()
        }
    }

    if (currentClusterWords.isNotEmpty()) {
        clusters.add(
            ClusteredWord(
                words = currentClusterWords.toList(),
                trailingSpace = false
            )
        )
    }

    return clusters
}

/**
 * Detecção de Fim de Linha Flexível:
 * O tempo final é calculado com base nas palavras internas (line.words),
 * no início da próxima linha (nextLineStartMs) e com limites dinâmicos para evitar cortes bruscos.
 */
fun resolveLineEndTimeMs(
    line: LyricLine,
    nextLineStartMs: Long?,
    trackDurationMs: Long = Long.MAX_VALUE
): Long {
    // 1. Se tem palavras internas, a última palavra define o término com margem suave
    val lastWordEnd = line.words.lastOrNull()?.endTimeMs
    if (lastWordEnd != null && lastWordEnd > line.startTimeMs) {
        return if (nextLineStartMs != null) {
            minOf(lastWordEnd + 400L, nextLineStartMs)
        } else {
            lastWordEnd + 800L
        }
    }

    // 2. Se não tem palavras internas, estima com base na próxima linha e limites dinâmicos
    return if (nextLineStartMs != null) {
        val durationUntilNext = nextLineStartMs - line.startTimeMs
        if (durationUntilNext > 7000L) {
            // Próxima linha demorada (>7s): estima duração confortável pela quantidade de caracteres
            val estimatedTextDuration = (line.text.length * 85L).coerceIn(3000L, 6500L)
            minOf(line.startTimeMs + estimatedTextDuration, nextLineStartMs - 500L)
        } else {
            // Próxima linha próxima: transição natural antes do início da próxima
            maxOf(line.startTimeMs + 800L, nextLineStartMs - 150L)
        }
    } else {
        // Última linha
        val estimatedTextDuration = (line.text.length * 85L).coerceIn(3000L, 6000L)
        minOf(line.startTimeMs + estimatedTextDuration, trackDurationMs)
    }
}

/**
 * Parser para linhas com tags de palavras:
 * Suporta formatos estendidos LRC (<mm:ss.xx>palavra), Kugou (<start,len,0>palavra) e TTML.
 */
fun parseLineWords(
    lineStartTimeMs: Long,
    rawLine: String,
    nextLineStartMs: Long?
): Pair<String, List<LyricWord>> {
    // Formato 1: Extended LRC com timestamps inline <mm:ss.xx> ou (mm:ss.xx) ou [mm:ss.xx]
    val inlineTagRegex = Regex("""[<(\[](\d+):(\d+)\.(\d+)[>)\]]""")
    val kugouTagRegex = Regex("""<(\d+),(\d+)(?:,\d+)?>""")

    if (inlineTagRegex.containsMatchIn(rawLine)) {
        val words = mutableListOf<LyricWord>()
        val matches = inlineTagRegex.findAll(rawLine).toList()
        val cleanTextBuilder = StringBuilder()

        for (i in matches.indices) {
            val match = matches[i]
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val centis = match.groupValues[3].toLongOrNull() ?: 0L
            val wordStart = minutes * 60000L + seconds * 1000L + centis * 10L

            val textStartIndex = match.range.last + 1
            val textEndIndex = if (i < matches.size - 1) {
                matches[i + 1].range.first
            } else {
                rawLine.length
            }

            val textChunk = if (textStartIndex < textEndIndex) {
                rawLine.substring(textStartIndex, textEndIndex)
            } else ""

            val nextWordStart = if (i < matches.size - 1) {
                val nextMatch = matches[i + 1]
                val nm = nextMatch.groupValues[1].toLongOrNull() ?: 0L
                val ns = nextMatch.groupValues[2].toLongOrNull() ?: 0L
                val nc = nextMatch.groupValues[3].toLongOrNull() ?: 0L
                nm * 60000L + ns * 1000L + nc * 10L
            } else {
                nextLineStartMs ?: (wordStart + 800L)
            }

            val wordEnd = maxOf(wordStart + 100L, minOf(wordStart + 2000L, nextWordStart))
            val hasTrailingSpace = textChunk.endsWith(" ")

            val cleanWordText = textChunk.trim()
            if (cleanWordText.isNotEmpty()) {
                cleanTextBuilder.append(cleanWordText).append(if (hasTrailingSpace) " " else "")
                words.add(
                    LyricWord(
                        startTimeMs = wordStart,
                        endTimeMs = wordEnd,
                        text = cleanWordText,
                        trailingSpace = hasTrailingSpace
                    )
                )
            }
        }

        val fullCleanText = cleanTextBuilder.toString().trim()
        if (words.isNotEmpty()) {
            return fullCleanText to words
        }
    } else if (kugouTagRegex.containsMatchIn(rawLine)) {
        val words = mutableListOf<LyricWord>()
        val matches = kugouTagRegex.findAll(rawLine).toList()
        val cleanTextBuilder = StringBuilder()

        for (i in matches.indices) {
            val match = matches[i]
            val offsetMs = match.groupValues[1].toLongOrNull() ?: 0L
            val durationMs = match.groupValues[2].toLongOrNull() ?: 300L
            val wordStart = lineStartTimeMs + offsetMs
            val wordEnd = wordStart + durationMs

            val textStartIndex = match.range.last + 1
            val textEndIndex = if (i < matches.size - 1) {
                matches[i + 1].range.first
            } else {
                rawLine.length
            }

            val textChunk = if (textStartIndex < textEndIndex) {
                rawLine.substring(textStartIndex, textEndIndex)
            } else ""

            val hasTrailingSpace = textChunk.endsWith(" ")
            val cleanWordText = textChunk.trim()
            if (cleanWordText.isNotEmpty()) {
                cleanTextBuilder.append(cleanWordText).append(if (hasTrailingSpace) " " else "")
                words.add(
                    LyricWord(
                        startTimeMs = wordStart,
                        endTimeMs = wordEnd,
                        text = cleanWordText,
                        trailingSpace = hasTrailingSpace
                    )
                )
            }
        }

        val fullCleanText = cleanTextBuilder.toString().trim()
        if (words.isNotEmpty()) {
            return fullCleanText to words
        }
    }

    // Linha simples sem tags de palavra
    val cleanLine = rawLine.replace(Regex("""<[^>]+>"""), "").trim()
    return cleanLine to emptyList()
}

/**
 * Converte pares (timestamp, texto) para [LyricLine]s enriquecidas com palavras,
 * detecção de fim flexível e interlúdios instrumentais automáticos.
 */
fun List<Pair<Int, String>>.toLyricLines(trackDurationMs: Long = Long.MAX_VALUE): List<LyricLine> {
    if (isEmpty()) return emptyList()

    val result = mutableListOf<LyricLine>()
    val sorted = this.sortedBy { it.first }

    // Interlúdio instrumental inicial se a música demora a começar os vocais
    val firstTime = sorted.first().first.toLong()
    if (firstTime > 4000L) {
        result.add(
            LyricLine(
                startTimeMs = 0L,
                endTimeMs = firstTime - 400L,
                text = "",
                words = emptyList(),
                isInstrumental = true
            )
        )
    }

    for (i in sorted.indices) {
        val (timeInt, rawText) = sorted[i]
        val startTime = timeInt.toLong()
        val nextTime = sorted.getOrNull(i + 1)?.first?.toLong()

        val (cleanText, words) = parseLineWords(startTime, rawText, nextTime)
        val isInstrumental = cleanText.isBlank()

        val tempLine = LyricLine(
            startTimeMs = startTime,
            endTimeMs = 0L,
            text = cleanText,
            words = words,
            isInstrumental = isInstrumental
        )
        val resolvedEndTime = resolveLineEndTimeMs(tempLine, nextTime, trackDurationMs)
        val finalLine = tempLine.copy(endTimeMs = resolvedEndTime)
        result.add(finalLine)

        // Interlúdio instrumental automático entre estrofes se a pausa for longa (> 7.5s)
        if (nextTime != null && (nextTime - resolvedEndTime) > 7000L && !isInstrumental) {
            result.add(
                LyricLine(
                    startTimeMs = resolvedEndTime + 400L,
                    endTimeMs = nextTime - 400L,
                    text = "",
                    words = emptyList(),
                    isInstrumental = true
                )
            )
        }
    }

    return result
}