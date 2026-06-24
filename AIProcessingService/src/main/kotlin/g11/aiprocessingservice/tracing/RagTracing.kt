package g11.aiprocessingservice.tracing

import io.micrometer.tracing.Span
import io.micrometer.tracing.Tracer
import java.util.concurrent.atomic.AtomicBoolean
import org.springframework.stereotype.Component

/**
 * Langfuse-oriented tracing for the RAG query flow.
 *
 * - [tagTrace] associates the active request span (the Langfuse trace root) with the user and the
 *   conversation session, plus the query input.
 * - [traceQuery] wraps the advisor call (retrieval + generation) in a "RAG Query" span and records
 *   the answer and retrieval stats on it. The per-generation `gen_ai.prompt`/`gen_ai.completion`
 *   attributes are added separately by [LangfuseObservationFilter] on Spring AI's ChatModel span.
 *
 * Kept free of RAG domain types so it stays a thin tracing utility.
 */
@Component
class RagTracing(private val tracer: Tracer) {

    private companion object {
        const val USER_ID_FIELD = "langfuse.user.id"
        const val SESSION_ID_FIELD = "langfuse.session.id"
    }

    /**
     * Runs [work] with the Langfuse user/session ids set as OpenTelemetry baggage, so every child
     * span created during the query (the "RAG Query" span and the ChatModel generation) carries
     * them — Langfuse increasingly filters per-observation, not just per-trace. The baggage field
     * names are declared under `management.tracing.baggage` (tag-fields + remote-fields), which is
     * what copies them onto each span. No-op effect on behaviour beyond tagging.
     */
    fun <T> withTraceBaggage(userId: String, sessionId: String, work: () -> T): T =
        tracer.createBaggageInScope(USER_ID_FIELD, userId).use {
            tracer.createBaggageInScope(SESSION_ID_FIELD, sessionId).use {
                work()
            }
        }

    fun tagTrace(userId: String, sessionId: String, input: String) {
        tracer.currentSpan()?.let { span ->
            // A descriptive trace name (else Langfuse names the trace after the HTTP route).
            span.tag("langfuse.trace.name", "rag-query")
            span.tag("langfuse.user.id", userId)
            span.tag("langfuse.session.id", sessionId)
            span.tag("langfuse.observation.input", input)
        }
        setLangfuseTraceTags("rag", "chat")
    }

    /**
     * The active OTel trace id (32-hex), which equals the Langfuse trace id under the OTLP
     * integration — used to attach evaluation scores to this query's trace. Null if no active span.
     */
    fun currentTraceId(): String? = tracer.currentSpan()?.context()?.traceId()

    /**
     * Streaming counterpart of [tagTrace] + [traceQuery]. The `@Async` streaming path has no ambient
     * request span (the SecurityContext/trace don't ride the async hop) and completes asynchronously,
     * so we open an explicit "rag-query" root span here, tag it for Langfuse (name + user + session +
     * input + tags), and keep it current while [subscribe] builds and subscribes the reactive chain.
     * That nests the Spring AI ChatModel generation span under this trace and — together with the
     * user/session baggage from [withTraceBaggage] and `spring.reactor.context-propagation=auto` —
     * stamps `langfuse.user.id` onto it so the whole stream groups under the user.
     *
     * The span outlives this method (the stream finishes later), so it is NOT ended here on success:
     * the returned [StreamTrace.end] MUST be called from the stream's terminal signal
     * (onComplete/onError). It is ended here only if [subscribe] fails synchronously.
     */
    fun traceStream(userId: String, sessionId: String, input: String, subscribe: (StreamTrace) -> Unit) {
        val span = tracer.nextSpan().name("rag-query").start()
        val trace = StreamTrace(span)
        try {
            tracer.withSpan(span).use {
                span.tag("langfuse.trace.name", "rag-query")
                span.tag("langfuse.user.id", userId)
                span.tag("langfuse.session.id", sessionId)
                span.tag("langfuse.observation.input", input)
                setLangfuseTraceTags("rag", "chat", "stream")
                subscribe(trace)
            }
        } catch (e: Exception) {
            trace.end()
            throw e
        }
    }

    /**
     * Handle for the streaming RAG trace span. [end] is idempotent so it is safe to wire to both the
     * onComplete and onError reactive signals; [recordOutput] stamps the assembled answer.
     */
    class StreamTrace(private val span: Span) {
        private val ended = AtomicBoolean(false)

        fun recordOutput(answer: String) {
            span.tag("langfuse.observation.output", answer)
        }

        fun end() {
            if (ended.compareAndSet(false, true)) {
                span.end()
            }
        }
    }

    /** Runs [work] inside a "RAG Query" span; [work] records its result via [RagQuerySpan]. */
    fun <T> traceQuery(input: String, topK: Int, work: (RagQuerySpan) -> T): T {
        val span = tracer.nextSpan().name("RAG Query").start()
        return tracer.withSpan(span).use {
            span.tag("langfuse.observation.input", input)
            span.tag("rag.topK", topK.toString())
            try {
                work(RagQuerySpan(span))
            } finally {
                span.end()
            }
        }
    }

    /** Handle for recording the outcome of a RAG query on its span. */
    class RagQuerySpan(private val span: Span) {
        fun recordResult(answer: String, similarities: List<Double>) {
            span.tag("langfuse.observation.output", answer)
            span.tag("rag.sourcesCount", similarities.size.toString())
            if (similarities.isNotEmpty()) {
                span.tag("rag.similarity.max", similarities.max().toString())
                span.tag("rag.similarity.min", similarities.min().toString())
            }
        }
    }
}
