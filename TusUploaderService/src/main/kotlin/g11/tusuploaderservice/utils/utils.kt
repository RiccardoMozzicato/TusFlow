package g11.tusuploaderservice.utils

import g11.tusuploaderservice.exceptions.UploadMetadataException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID

fun getDateResponseFormat(date: ZonedDateTime ): String {
    return date.plusHours(1).format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH))
}

 fun RFC9110(instant: Instant): String {
    val formatter = DateTimeFormatter
        .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
        .withZone(ZoneOffset.UTC)
    return formatter.format(instant)
}

fun mergeByteArrays(byteArrays: List<ByteArray>): java.io.ByteArrayInputStream {
    val totalSize = byteArrays.sumOf { it.size }
    val mergedArray = ByteArray(totalSize)
    var offset = 0

    byteArrays.forEach { byteArray ->
        byteArray.copyInto(mergedArray, offset)
        offset += byteArray.size
    }

    return java.io.ByteArrayInputStream(mergedArray)
}

fun parseMetadataHeader(header: String?): Map<String, String>? {
    val result = header
        ?.split(",")
        ?.mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() } }?.associate { part ->
            val tokens = part.split(Regex("\\s+"), limit = 2)
            val key = tokens[0]
            val value = if (tokens.size > 1) {
                try {
                    String(Base64.getDecoder().decode(tokens[1]))
                } catch (e: IllegalArgumentException) {
                    throw UploadMetadataException(
                        "uploadMetadata values must be base64 encoded ASCII strings",
                        tokens[0],
                        e
                    )
                }
            } else ""
            key to value
        }
    return result
}

fun encodeStringToBase64(text: String): String {
    return Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
}

fun getUserIdFromJwt(): UUID {
    val principal = SecurityContextHolder.getContext().authentication.principal as? Jwt
        ?: throw IllegalStateException("No JWT authentication found in security context")
    return UUID.fromString(principal.subject)
}

/**
 * Ottiene il token JWT dell'utente corrente dal SecurityContext.
 * Questo token può essere propagato ad altri servizi per mantenere il contesto dell'utente.
 */
fun getUserTokenFromJwt(): String {
    val principal = SecurityContextHolder.getContext().authentication.principal as? Jwt
        ?: throw IllegalStateException("No JWT authentication found in security context")
    return principal.tokenValue
}
