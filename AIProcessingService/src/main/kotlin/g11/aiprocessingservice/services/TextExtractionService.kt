package g11.aiprocessingservice.services

import org.springframework.stereotype.Service
import java.io.InputStream
import java.time.Instant

/**
 * Mechanism-only text extraction: PDF via PDFBox, DOCX via POI, everything else read as plain
 * UTF-8 text. The decision of *which* content types are allowed lives in [SupportedContentTypes];
 * callers gate first, so this service only ever receives text-extractable types.
 */
@Service
class TextExtractionService {

    fun extract(inputStream: InputStream, contentType: String): ExtractedText {
        return when {
            contentType.startsWith("application/pdf") -> extractFromPdf(inputStream)
            contentType.startsWith(DOCX_CONTENT_TYPE) -> extractFromDocx(inputStream)
            else -> extractFromText(inputStream)
        }
    }

    private fun extractFromPdf(inputStream: InputStream): ExtractedText {
        val document = org.apache.pdfbox.pdmodel.PDDocument.load(inputStream)
        return document.use {
            val stripper = org.apache.pdfbox.text.PDFTextStripper()
            val text = stripper.getText(document)

            ExtractedText(
                content = text,
                metadata = mapOf(
                    "pages" to document.numberOfPages,
                    "type" to "pdf"
                )
            )
        }
    }

    private fun extractFromDocx(inputStream: InputStream): ExtractedText {
        return org.apache.poi.xwpf.usermodel.XWPFDocument(inputStream).use { document ->
            org.apache.poi.xwpf.extractor.XWPFWordExtractor(document).use { extractor ->
                ExtractedText(
                    content = extractor.text,
                    metadata = mapOf(
                        "paragraphs" to document.paragraphs.size,
                        "type" to "docx"
                    )
                )
            }
        }
    }

    private fun extractFromText(inputStream: InputStream): ExtractedText {
        val text = inputStream.bufferedReader().use { it.readText() }
        return ExtractedText(
            content = text,
            metadata = mapOf("type" to "text")
        )
    }

    data class ExtractedText(
        val content: String,
        val metadata: Map<String, Any> = emptyMap(), // pages, language, etc.
        val extractedAt: Instant = Instant.now()
    )

    private companion object {
        const val DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
}
