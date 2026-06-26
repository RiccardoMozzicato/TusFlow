package g11.aiprocessingservice.unit.services

import g11.aiprocessingservice.services.TextExtractionService
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextExtractionServiceTest {

    private val service = TextExtractionService()

    // Builds a minimal .docx in memory so the test needs no binary fixture.
    private fun docxBytes(vararg paragraphs: String): ByteArray {
        val document = XWPFDocument()
        paragraphs.forEach { text ->
            document.createParagraph().createRun().setText(text)
        }
        return ByteArrayOutputStream().use { out ->
            document.write(out)
            document.close()
            out.toByteArray()
        }
    }

    @Test
    fun `extracts text from a docx, tolerating a charset-suffixed content type`() {
        val bytes = docxBytes("Hello from the contract.", "Second paragraph.")
        // Mirrors the real Kafka event, which carries the charset suffix.
        val contentType =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document;charset=UTF-8"

        val result = service.extract(ByteArrayInputStream(bytes), contentType)

        assertTrue(result.content.contains("Hello from the contract."))
        assertTrue(result.content.contains("Second paragraph."))
        assertEquals("docx", result.metadata["type"])
        assertEquals(2, result.metadata["paragraphs"])
    }

    @Test
    fun `reads a text-like content type as plain text`() {
        // Policy lives in SupportedContentTypes; the extractor just reads non-PDF/DOCX as text.
        val json = """{"hello":"world"}"""

        val result = service.extract(ByteArrayInputStream(json.toByteArray()), "application/json")

        assertEquals(json, result.content)
        assertEquals("text", result.metadata["type"])
    }
}
