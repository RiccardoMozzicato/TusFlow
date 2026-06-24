package g11.aiprocessingservice.langfuse

import g11.aiprocessingservice.rag.retrieval.RagProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Posts evaluation scores to the Langfuse REST API (`POST {base}/api/public/scores`). Scores are a
 * separate ingestion path from OTLP traces, so this is a plain REST call — not OpenTelemetry. It
 * reuses the same Basic auth header used for OTLP ingestion.
 *
 * A score links to a trace by [traceId]: with the OTLP integration the Langfuse trace id is the OTel
 * trace id (32-hex), which the caller reads from the active span.
 *
 * Best-effort: any failure is logged and swallowed so scoring never affects the user request.
 *
 * Connection handling: Langfuse answers with `Keep-Alive: timeout=5` (it drops idle connections after
 * 5s). Scoring is sporadic and async, so a pooled connection is almost always stale by the time the
 * next score posts; reusing that dead socket fails with "header parser received no bytes" (the JDK
 * HttpClient — the `RestClient.builder()` default) or a premature EOF. Two mitigations: we use
 * HttpURLConnection (`SimpleClientHttpRequestFactory`) with explicit timeouts so a wedged Langfuse
 * can't tie up the async scoring thread, and we [retry][MAX_ATTEMPTS] once — a stale connection is
 * discarded on the failed read, so the next attempt opens a fresh one.
 */
@Component
class LangfuseScoreClient(props: RagProperties) {
    private val log = LoggerFactory.getLogger(this.javaClass)
    private val authHeader = props.scoring.langfuseAuthHeader
    private val restClient = RestClient.builder()
        .baseUrl(props.scoring.langfuseBaseUrl.trimEnd('/'))
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(CONNECT_TIMEOUT_MS)
                setReadTimeout(READ_TIMEOUT_MS)
            }
        )
        .build()

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_000
        const val READ_TIMEOUT_MS = 10_000
        const val MAX_ATTEMPTS = 2
    }

    fun postNumericScore(traceId: String, name: String, value: Double, comment: String? = null) {
        if (authHeader.isBlank()) {
            log.debug("Skipping Langfuse score '{}': no auth header configured", name)
            return
        }
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                restClient.post()
                    .uri("/api/public/scores")
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        mapOf(
                            "traceId" to traceId,
                            "name" to name,
                            "value" to value,
                            "dataType" to "NUMERIC",
                            "comment" to comment,
                        )
                    )
                    .retrieve()
                    .toBodilessEntity()
                log.debug("Posted Langfuse score '{}'={} for trace {}", name, value, traceId)
                return
            } catch (e: Exception) {
                if (attempt < MAX_ATTEMPTS - 1) {
                    log.debug("Retrying Langfuse score '{}' after transient error: {}", name, e.message)
                } else {
                    log.warn("Failed to post Langfuse score '{}' for trace {}: {}", name, traceId, e.message)
                }
            }
        }
    }
}
