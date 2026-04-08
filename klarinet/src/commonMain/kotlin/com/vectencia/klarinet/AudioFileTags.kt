package com.vectencia.klarinet

/**
 * Metadata tags associated with an audio file.
 *
 * All properties are optional and default to `null`; a file may contain
 * none, some, or all of these tags. The [albumArt] field, when present,
 * contains the raw bytes of an embedded image (typically JPEG or PNG).
 *
 * Tags can be read from existing files via [AudioFileReader] and
 * [AudioFileInfo.tags], or written into new files by passing an instance
 * to the [AudioFileWriter] constructor.
 *
 * Because [albumArt] is a [ByteArray], this class provides custom
 * [equals] and [hashCode] implementations that compare array contents
 * rather than references.
 *
 * ## Usage
 *
 * ```kotlin
 * // Reading tags from a file
 * val reader = AudioFileReader("/path/to/song.mp3")
 * val tags = reader.info.tags
 * println("Now playing: ${tags.title} by ${tags.artist}")
 * reader.close()
 *
 * // Creating tags for a new file
 * val tags = AudioFileTags(
 *     title = "My Song",
 *     artist = "My Artist",
 *     album = "My Album",
 *     year = 2025,
 * )
 * ```
 *
 * @see AudioFileInfo.tags
 * @see AudioFileWriter
 */
data class AudioFileTags(
    /** The track title, or `null` if not present in the file. */
    val title: String? = null,

    /** The performing artist name, or `null` if not present. */
    val artist: String? = null,

    /** The album name, or `null` if not present. */
    val album: String? = null,

    /**
     * The track number within the album, or `null` if not present.
     *
     * Typically a 1-based index (e.g., track 3 of 12).
     */
    val trackNumber: Int? = null,

    /**
     * The genre of the track as a free-form string, or `null` if not present.
     *
     * Examples: `"Rock"`, `"Electronic"`, `"Classical"`.
     */
    val genre: String? = null,

    /**
     * The release year, or `null` if not present.
     *
     * Stored as a four-digit integer (e.g., `2025`).
     */
    val year: Int? = null,

    /**
     * Raw bytes of the embedded album artwork image, or `null` if absent.
     *
     * The image data is typically encoded as JPEG or PNG. To display the
     * artwork, decode these bytes using your platform's image APIs.
     */
    val albumArt: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFileTags) return false
        return title == other.title && artist == other.artist && album == other.album &&
            trackNumber == other.trackNumber && genre == other.genre && year == other.year &&
            albumArt.contentEqualsNullable(other.albumArt)
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + trackNumber.hashCode()
        result = 31 * result + genre.hashCode()
        result = 31 * result + year.hashCode()
        result = 31 * result + (albumArt?.contentHashCode() ?: 0)
        return result
    }

    private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean {
        if (this == null && other == null) return true
        if (this == null || other == null) return false
        return this.contentEquals(other)
    }
}
