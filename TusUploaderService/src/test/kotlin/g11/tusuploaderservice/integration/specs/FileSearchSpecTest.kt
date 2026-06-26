package g11.tusuploaderservice.integration.specs

import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.entities.File
import g11.tusuploaderservice.entities.FileStatus
import g11.tusuploaderservice.entities.FileVersion
import g11.tusuploaderservice.integration.IntegrationTest
import g11.tusuploaderservice.repositories.FileRepository
import g11.tusuploaderservice.specs.FileSpecifications
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import java.util.UUID

/**
 * Verifies the `search` filter of [FileSpecifications.buildFilesByScopes]: a free-text query is
 * matched case-insensitively (substring) against the current file name (lastFilename), so the
 * server-side search lines up with the title shown in the UI. A blank/null query applies no
 * filter. LIKE wildcards in the query are treated literally. Each test uses a fresh organization
 * so assertions are isolated from other seeded data.
 */
@Transactional
class FileSearchSpecTest @Autowired constructor(
    private val fileRepository: FileRepository,
) : IntegrationTest() {

    private val org = "org-${UUID.randomUUID()}"
    private val me = UUID.randomUUID()

    private fun persistFile(filename: String): UUID {
        val fileId = UUID.randomUUID()
        val now = Date()
        val file = File(
            uuid = fileId,
            ownerId = me,
            createdAt = now,
            lastUpdateAt = now,
            lastFilename = filename,
            currentVersion = 1L,
            organization = org,
            department = null,
            sharingScope = SharingScope.PERSONAL,
        )
        file.addNewVersion(
            FileVersion(
                version = 1L,
                status = FileStatus.AVAILABLE,
                filename = filename,
                size = 10L,
                metadata = null,
                checksum = null,
                contentType = "text/plain",
                createdAt = now,
                lastUpdateAt = now,
                objectKey = "key/$fileId",
            ),
        )
        return fileRepository.save(file).uuid
    }

    private fun matching(search: String?): Set<UUID> {
        val spec = FileSpecifications.buildFilesByScopes(
            contentType = null,
            dateFrom = null,
            dateTo = null,
            sharingScopes = setOf(SharingScope.PERSONAL),
            userId = me,
            organization = org,
            department = null,
            isAdmin = false,
            search = search,
        )
        return fileRepository.findAll(spec).map { it.uuid }.toSet()
    }

    @Test
    fun `search matches file name as a case-insensitive substring`() {
        val annualReport = persistFile("Annual Report 2025.pdf")
        val budgetReport = persistFile("budget-report.docx")
        persistFile("holiday-photo.png")

        assertEquals(setOf(annualReport, budgetReport), matching("report"))
    }

    @Test
    fun `search is case-insensitive`() {
        val report = persistFile("Quarterly REPORT.pdf")

        assertEquals(setOf(report), matching("report"))
    }

    @Test
    fun `blank search applies no filter`() {
        val a = persistFile("alpha.txt")
        val b = persistFile("beta.txt")

        assertEquals(setOf(a, b), matching("   "))
    }

    @Test
    fun `null search applies no filter`() {
        val a = persistFile("alpha.txt")
        val b = persistFile("beta.txt")

        assertEquals(setOf(a, b), matching(null))
    }

    @Test
    fun `LIKE wildcards in the query are matched literally`() {
        val literal = persistFile("50%-off-flyer.pdf")
        // Without escaping, "50%" would match any name starting with "50"; the underscore
        // would match any single char. Persist decoys that must NOT be returned.
        persistFile("500-invoices.csv")
        persistFile("50x-off-flyer.pdf")

        assertEquals(setOf(literal), matching("50%"))
    }
}
