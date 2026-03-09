package g11.tusuploaderservice.specs

import g11.tusuploaderservice.controllers.TusController
import g11.tusuploaderservice.entities.Upload
import g11.tusuploaderservice.entities.UploadStatus
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.util.Date

object UploadSpecifications {
    private val log = LoggerFactory.getLogger(UploadSpecifications::class.java)
    fun hasStatus(status: UploadStatus): Specification<Upload> {
        log.debug("hasStatus({})", status)
        return Specification { root, _, cb -> cb.equal(root.get<UploadStatus>("status"), status) }

    }

    fun hasContentType(contentType: String): Specification<Upload> =
        Specification { root, _, cb -> cb.equal(root.get<String>("contentType"), contentType) }

    fun createdAfter(dateFromIso: String): Specification<Upload> {
        val from = Instant.parse(dateFromIso)
        val fromDate = Date.from(from)
        return Specification { root, _, cb -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate) }
    }

    fun createdBefore(dateToIso: String): Specification<Upload> {
        val to = Instant.parse(dateToIso)
        val toDate = Date.from(to)
        return Specification { root, _, cb -> cb.lessThanOrEqualTo(root.get("createdAt"), toDate) }
    }

    fun build(
        status: UploadStatus,
        contentType: String?,
        dateFrom: String?,
        dateTo: String?
    ): Specification<Upload> {
        var spec = hasStatus(status)
        if (contentType != null) spec = spec.and(hasContentType(contentType))
        if (dateFrom != null) spec = spec.and(createdAfter(dateFrom))
        if (dateTo != null) spec = spec.and(createdBefore(dateTo))
        return spec
    }
}