package g11.aiprocessingservice.conf

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Allow-list of content types the AI pipeline will analyze, bound from
 * `ai.analysis.supported-content-types` in application.yml. Anything not matched
 * is treated as UNSUPPORTED (the file is still stored, just not analyzed).
 *
 * @property prefixes MIME family prefixes accepted as analyzable (e.g. "text/", "image/").
 * @property types Exact MIME types accepted (base type; charset is ignored when matching).
 */
@ConfigurationProperties(prefix = "ai.analysis.supported-content-types")
data class SupportedContentTypeProperties(
    val prefixes: List<String> = emptyList(),
    val types: List<String> = emptyList(),
)
