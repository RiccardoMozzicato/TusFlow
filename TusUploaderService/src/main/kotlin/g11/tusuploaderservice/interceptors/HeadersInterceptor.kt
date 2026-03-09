package g11.tusuploaderservice.interceptors

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpMethod
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class HeadersInterceptor(
    @Value("\${tus.version}") private val supportedVersion: String
): HandlerInterceptor {

    private val TUS_UPLOAD_PATH = "/uploads" // The path you want to check

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {

        //Check if the request is for the TUS upload path
        val path = request.requestURI.removePrefix(request.contextPath)
        if (!path.startsWith(TUS_UPLOAD_PATH)) {
            return true
        }

        // Bypass the check for OPTIONS requests
        if (request.method == HttpMethod.OPTIONS.name()) {
            return true
        }

        val tusResumable = request.getHeader("Tus-Resumable")

        response.setHeader("Tus-Version", supportedVersion)

        if (tusResumable == null) {
            val errorMessage = "Missing Tus-Resumable header. All TUS requests must include this header."

            response.status = HttpStatus.BAD_REQUEST.value()
            response.contentType = "text/plain"
            response.writer.write(errorMessage)
            return false
        }

        if (tusResumable != supportedVersion) {
            val errorMessage = "Unsupported Tus-Resumable version. Server supports $supportedVersion, but received $tusResumable."

            response.status = HttpStatus.PRECONDITION_FAILED.value()
            response.contentType = "text/plain"
            response.writer.write(errorMessage)
            return false
        }
        return true
    }

}