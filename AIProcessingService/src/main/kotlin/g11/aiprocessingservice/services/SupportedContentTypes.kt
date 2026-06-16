package g11.aiprocessingservice.services

import g11.aiprocessingservice.conf.SupportedContentTypeProperties
import org.springframework.stereotype.Service

/**
 * Central policy for which uploaded content types the AI pipeline handles, driven by
 * [SupportedContentTypeProperties] (configurable in application.yml). Keeping the decision in
 * one place lets both the metadata gate and the embedding gate stay consistent.
 */
@Service
class SupportedContentTypes(private val properties: SupportedContentTypeProperties) {

    /**
     * True if the content type may be analyzed at all (metadata analysis), covering documents,
     * text, and images. Charset suffixes are ignored and matching is case-insensitive.
     */
    fun isAnalyzable(contentType: String?): Boolean {
        val base = baseType(contentType) ?: return false
        return properties.types.any { base == it.trim().lowercase() } ||
            properties.prefixes.any { base.startsWith(it.trim().lowercase()) }
    }

    /**
     * True if the content type yields extractable text (PDF/DOCX/text-like). Images are
     * analyzable via the vision model but have no text to extract or embed, so they are excluded
     * here — this gate guards the text extractor and the embedding pipeline.
     */
    fun isTextExtractable(contentType: String?): Boolean {
        val base = baseType(contentType) ?: return false
        return isAnalyzable(base) && !base.startsWith("image/")
    }

    // Strips charset/parameters and normalizes case, e.g. "application/PDF;charset=UTF-8" -> "application/pdf".
    private fun baseType(contentType: String?): String? =
        contentType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
}
