package com.example.prismlock

import com.example.prismlock.data.MediaImporter
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaImporterTest {
    @Test
    fun resolvesVideoExtensionsFromMime() {
        assertEquals("mp4", MediaImporter.resolveExtension("video/mp4"))
        assertEquals("mov", MediaImporter.resolveExtension("video/quicktime"))
        assertEquals("mkv", MediaImporter.resolveExtension("video/x-matroska"))
        assertEquals("webm", MediaImporter.resolveExtension("video/webm"))
        assertEquals("3gp", MediaImporter.resolveExtension("video/3gpp"))
        assertEquals("mp4", MediaImporter.resolveExtension("video/unknown-codec"))
    }

    @Test
    fun resolvesImageExtensionsFromMime() {
        assertEquals("png", MediaImporter.resolveExtension("image/png"))
        assertEquals("webp", MediaImporter.resolveExtension("image/webp"))
        assertEquals("heic", MediaImporter.resolveExtension("image/heic"))
        assertEquals("heif", MediaImporter.resolveExtension("image/heif"))
        assertEquals("jpg", MediaImporter.resolveExtension("image/jpeg"))
        assertEquals("jpg", MediaImporter.resolveExtension("image/unknown"))
    }

    @Test
    fun resolvesFallbackExtensionFromNameWhenMimeBlank() {
        assertEquals("mov", MediaImporter.resolveExtension(null, "clip.MOV"))
        assertEquals("webp", MediaImporter.resolveExtension("", "photo/banner.webp?v=1"))
        assertEquals("jpg", MediaImporter.resolveExtension(null, "no_extension_file"))
    }
}
