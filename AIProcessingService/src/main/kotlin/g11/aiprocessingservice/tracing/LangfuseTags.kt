package g11.aiprocessingservice.tracing

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span

/**
 * Langfuse reads trace tags from the `langfuse.trace.tags` OpenTelemetry attribute, which must be a
 * `string[]`. Micrometer's `Span.tag(key, value)` only sets single string values, so the array has
 * to be set through the OTel API on the current span.
 *
 * No-op when there is no active span (`Span.current()` returns a non-recording span), so this is
 * safe to call unconditionally — tracing must never affect request/pipeline processing.
 *
 * Reference: https://langfuse.com/integrations/native/opentelemetry (OTel → Langfuse attribute map).
 */
private val LANGFUSE_TRACE_TAGS: AttributeKey<List<String>> =
    AttributeKey.stringArrayKey("langfuse.trace.tags")

fun setLangfuseTraceTags(vararg tags: String) {
    Span.current().setAttribute(LANGFUSE_TRACE_TAGS, tags.toList())
}
