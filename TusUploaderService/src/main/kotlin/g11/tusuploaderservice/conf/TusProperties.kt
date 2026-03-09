package g11.tusuploaderservice.conf

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Configuration
@ConfigurationProperties(prefix = "tus")
open class TusProperties {
    open var version: String? = null
    open var extensions: List<String>? = null
    open var maxSize:  String? = null
    open var checksumAlgorithm: String? = null
    open var vary: String? = null
    open var allowMethods: List<String>? = null
    open var allowHeaders: List<String>? = null
    open var exposeHeaders: List<String>? = null
    open var allowOrigin: List<String>? = null
}