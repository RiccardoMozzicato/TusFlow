package g11.aiprocessingservice.unit.services

import g11.aiprocessingservice.conf.SupportedContentTypeProperties
import g11.aiprocessingservice.services.SupportedContentTypes
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupportedContentTypesTest {

    // Mirrors the allow-list configured in application.yml.
    private val service = SupportedContentTypes(
        SupportedContentTypeProperties(
            prefixes = listOf("text/", "image/"),
            types = listOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/json",
                "application/xml",
            ),
        ),
    )

    @Test
    fun `isAnalyzable accepts documents, text, text-like and images`() {
        assertTrue(service.isAnalyzable("application/pdf"))
        assertTrue(
            service.isAnalyzable(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ),
        )
        assertTrue(service.isAnalyzable("text/plain"))
        assertTrue(service.isAnalyzable("application/json"))
        assertTrue(service.isAnalyzable("image/png"))
    }

    @Test
    fun `isAnalyzable ignores the charset suffix and is case-insensitive`() {
        assertTrue(service.isAnalyzable("application/pdf;charset=UTF-8"))
        assertTrue(service.isAnalyzable("TEXT/Plain"))
        assertTrue(service.isAnalyzable("Application/PDF"))
    }

    @Test
    fun `isAnalyzable rejects binaries, archives, video and blank`() {
        assertFalse(service.isAnalyzable("application/x-apple-diskimage")) // .dmg
        assertFalse(service.isAnalyzable("application/octet-stream"))
        assertFalse(service.isAnalyzable("application/zip"))
        assertFalse(service.isAnalyzable("video/mp4"))
        assertFalse(service.isAnalyzable(""))
        assertFalse(service.isAnalyzable(null))
    }

    @Test
    fun `isTextExtractable keeps documents and text but excludes images`() {
        assertTrue(service.isTextExtractable("application/pdf"))
        assertTrue(service.isTextExtractable("text/plain"))
        assertTrue(service.isTextExtractable("application/json"))
        assertFalse(service.isTextExtractable("image/png"))
        assertFalse(service.isTextExtractable("application/x-apple-diskimage"))
    }
}
