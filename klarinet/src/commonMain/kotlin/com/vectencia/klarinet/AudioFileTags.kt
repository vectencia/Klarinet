package com.vectencia.klarinet

/**
 * Metadata tags associated with an audio file.
 *
 * All properties are optional; a file may contain none, some, or all of
 * these tags. The [albumArt] field, when present, contains the raw bytes
 * of an embedded image (typically JPEG or PNG).
 *
 * Because [albumArt] is a [ByteArray], this class provides custom
 * [equals] and [hashCode] implementations that compare array contents
 * rather than references.
 */
data class AudioFileTags(
    /** The track title. */
    val title: String? = null,

    /** The performing artist. */
    val artist: String? = null,

    /** The album name. */
    val album: String? = null,

    /** The track number within the album. */
    val trackNumber: Int? = null,

    /** The genre of the track. */
    val genre: String? = null,

    /** The release year. */
    val year: Int? = null,

    /** Raw bytes of the embedded album artwork image, or null if absent. */
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
