package g11.apigateway.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Micrometer metrics for the gateway's auth surface, exported to the LGTM Prometheus via OTLP.
 *
 * Per-route latency/status is already covered by `http.server.requests`; this adds the one business
 * signal the gateway owns — how many `/auth/userinfo` checks resolve to an authenticated session vs
 * an anonymous caller (the SPA polls this endpoint on load). Best-effort, never affects the request.
 */
@Component
class GatewayMetrics(registry: MeterRegistry) {

    private val authenticated: Counter = Counter.builder("gateway.auth.userinfo")
        .description("Calls to /auth/userinfo by authentication outcome")
        .tag("outcome", "authenticated")
        .register(registry)

    private val anonymous: Counter = Counter.builder("gateway.auth.userinfo")
        .description("Calls to /auth/userinfo by authentication outcome")
        .tag("outcome", "anonymous")
        .register(registry)

    /** Counts a userinfo check that resolved to an authenticated user. */
    fun countAuthenticated() = authenticated.increment()

    /** Counts a userinfo check from an anonymous (not-logged-in) caller. */
    fun countAnonymous() = anonymous.increment()
}
