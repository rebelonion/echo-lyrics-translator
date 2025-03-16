package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.LyricsExtension
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.providers.LyricsExtensionsProvider
import dev.brahmkshatriya.echo.common.providers.MusicExtensionsProvider
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingList
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.helpers.updateMetadata
import dev.brahmkshatriya.echo.extension.helpers.getIdFromLyric
import dev.brahmkshatriya.echo.extension.helpers.translate
import me.bush.translator.Language


class LyricsTranslator : ExtensionClient, LyricsClient, LyricsExtensionsProvider,
    MusicExtensionsProvider {

    ////--------------------------------------------------------------------------------------------
    // ExtensionClient
    override val settingItems: List<Setting> by lazy {
        listOf(
            SettingSwitch(
                title = "Translate Lyrics",
                key = "translateLyrics",
                defaultValue = true
            ),
            SettingList(
                title = "Translation Language",
                key = "translationLanguage",
                entryTitles = Language.entries.filter { it.code != "auto" }.sortedBy { it.name }
                    .map { it.name },
                entryValues = Language.entries.filter { it.code != "auto" }.sortedBy { it.name }
                    .map { it.code },
                defaultEntryIndex = Language.entries.find { it.code == "en" }
                    ?.let { Language.entries.indexOf(it) - 1 } // -1 to account for auto
                    ?: 0
            )
        )
    }

    private lateinit var settings: Settings
    override fun setSettings(settings: Settings) {
        this.settings = settings
    }

    private fun selectedLanguage(): Language? {
        if (settings.getBoolean("translateLyrics") == false) {
            return null
        }
        val language = settings.getString("translationLanguage") ?: return Language.ENGLISH
        return Language.entries.find { it.code == language } ?: Language.ENGLISH
    }

    ////--------------------------------------------------------------------------------------------
    // LyricsClient

    override suspend fun loadLyrics(lyrics: Lyrics): Lyrics {
        if (lyrics.lyrics != null) {
            return lyrics.translate(selectedLanguage())
        } else {
            val id = lyrics.getIdFromLyric()!!
            val extension: LyricsClient? =
                lyricsExtensions.find { it.id == id }?.instance?.value()?.getOrNull()
                    ?: (musicExtensions.find { it.id == id }?.instance?.value()
                        ?.getOrNull() as? LyricsClient)
            return extension?.loadLyrics(lyrics)
                ?.translate(selectedLanguage()) ?: lyrics
        }
    }


    private suspend fun getAllLyrics(clientId: String, track: Track): List<Lyrics> {
        val potentialLyrics: MutableList<Lyrics> = mutableListOf()
        val lyricsExtensionsLyrics = lyricsExtensions.mapNotNull { lyricsExtension ->
            try {
                if (!lyricsExtension.metadata.enabled) {
                    return@mapNotNull null
                }
                val extension = lyricsExtension.instance.value().getOrThrow()
                //make sure we don't call ourself
                if (extension is LyricsTranslator) {
                    return@mapNotNull null
                }
                extension.searchTrackLyrics(clientId, track).loadFirst()
                    .map { it.updateMetadata(lyricsExtension) }
            } catch (e: Exception) {
                null
            }
        }.flatten()
        potentialLyrics.addAll(lyricsExtensionsLyrics)
        val musicExtensionsLyrics = musicExtensions.mapNotNull { musicExtension ->
            try {
                if (!musicExtension.metadata.enabled) {
                    return@mapNotNull null
                }
                (musicExtension.instance.value().getOrThrow() as? LyricsClient)?.searchTrackLyrics(
                    clientId,
                    track
                )?.loadFirst()?.map { it.updateMetadata(musicExtension) }
            } catch (e: Exception) {
                null
            }
        }.flatten()
        potentialLyrics.addAll(musicExtensionsLyrics)
        return potentialLyrics
    }

    override fun searchTrackLyrics(clientId: String, track: Track): PagedData<Lyrics> =
        PagedData.Single {
            getAllLyrics(clientId, track)
        }

    ////--------------------------------------------------------------------------------------------
    // LyricsExtensionsProvider
    override val requiredLyricsExtensions: List<String> = emptyList()

    private var lyricsExtensions: List<LyricsExtension> = emptyList()
    override fun setLyricsExtensions(extensions: List<LyricsExtension>) {
        lyricsExtensions = extensions
    }

    ////--------------------------------------------------------------------------------------------
    // MusicExtensionsProvider
    override val requiredMusicExtensions: List<String> = emptyList()

    private var musicExtensions: List<MusicExtension> = emptyList()
    override fun setMusicExtensions(extensions: List<MusicExtension>) {
        musicExtensions = extensions
    }


}