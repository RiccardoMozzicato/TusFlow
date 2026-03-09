package g11.aiprocessingservice.services

import g11.aiprocessingservice.exceptions.UnsupportedContentTypeException
import org.springframework.stereotype.Service
import java.io.InputStream
import java.time.Instant

@Service
class TextExtractionService {

    fun extract(inputStream: InputStream, contentType: String): ExtractedText {
        return when {
            contentType.startsWith("application/pdf") -> extractFromPdf(inputStream)
            contentType.startsWith("text/") -> extractFromText(inputStream)
            //contentType.contains("word") -> extractFromWord(inputStream)
            // ...
            else -> throw UnsupportedContentTypeException(contentType)
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
}
