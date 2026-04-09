package com.vectencia.klarinet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioFileTagsTest {

    @Test
    fun defaultTagsAreAllNull() {
        val tags = AudioFileTags()
        assertNull(tags.title)
        assertNull(tags.artist)
        assertNull(tags.album)
        assertNull(tags.trackNumber)
        assertNull(tags.genre)
        assertNull(tags.year)
        assertNull(tags.albumArt)
    }

    @Test
    fun tagsWithAllFields() {
        val art = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val tags = AudioFileTags(
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            trackNumber = 3,
            genre = "Rock",
            year = 2024,
            albumArt = art,
        )
        assertEquals("Test Song", tags.title)
        assertEquals("Test Artist", tags.artist)
        assertEquals("Test Album", tags.album)
        assertEquals(3, tags.trackNumber)
        assertEquals("Rock", tags.genre)
        assertEquals(2024, tags.year)
        assertTrue(art.contentEquals(tags.albumArt!!))
    }

    @Test
    fun copyModifiesSingleField() {
        val original = AudioFileTags(title = "Original", artist = "Artist")
        val copy = original.copy(title = "Modified")
        assertEquals("Modified", copy.title)
        assertEquals("Artist", copy.artist)
    }

    @Test
    fun equalityWithMatchingByteArrayContent() {
        val art1 = byteArrayOf(1, 2, 3)
        val art2 = byteArrayOf(1, 2, 3)
        val tags1 = AudioFileTags(title = "Song", albumArt = art1)
        val tags2 = AudioFileTags(title = "Song", albumArt = art2)
        assertEquals(tags1, tags2)
    }

    @Test
    fun hashCodeConsistency() {
        val art = byteArrayOf(10, 20, 30)
        val tags1 = AudioFileTags(title = "Song", albumArt = art.copyOf())
        val tags2 = AudioFileTags(title = "Song", albumArt = art.copyOf())
        assertEquals(tags1.hashCode(), tags2.hashCode())
    }

    @Test
    fun nullAlbumArtEquality() {
        val tags1 = AudioFileTags(title = "Song", albumArt = null)
        val tags2 = AudioFileTags(title = "Song", albumArt = null)
        assertEquals(tags1, tags2)
    }

    @Test
    fun inequalityWithDifferentByteArrayContent() {
        val tags1 = AudioFileTags(title = "Song", albumArt = byteArrayOf(1, 2, 3))
        val tags2 = AudioFileTags(title = "Song", albumArt = byteArrayOf(4, 5, 6))
        assertNotEquals(tags1, tags2)
    }

    @Test
    fun inequalityNullVsNonNullAlbumArt() {
        val tags1 = AudioFileTags(title = "Song", albumArt = null)
        val tags2 = AudioFileTags(title = "Song", albumArt = byteArrayOf(1))
        assertNotEquals(tags1, tags2)
    }
}
