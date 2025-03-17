package dev.brahmkshatriya.echo.extension.lyricsTranslator.helpers

import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.models.Lyrics

fun String.findAll(char: Char) =
    this.mapIndexed { index, c -> if (c == char) index else null }.filterNotNull()

fun <T> T.listOf(): List<T> {
    return listOf(this)
}

fun Lyrics.updateMetadata(extension: Extension<*>): Lyrics {
    return this.copy(
        subtitle = "${extension.name}${if (this.subtitle != null) ": ${this.subtitle}" else ""}",
        extras = this.extras.plus(
            listOf(
                "lyricExtensionId" to extension.id,
                "lyricExtensionName" to extension.name
            )
        )
    )
}

fun Lyrics.getIdFromLyric(): String? {
    return this.extras["lyricExtensionId"]
}