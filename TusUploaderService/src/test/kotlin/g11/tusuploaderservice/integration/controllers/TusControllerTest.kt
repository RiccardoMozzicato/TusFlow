package g11.tusuploaderservice.integration.controllers


import g11.tusuploaderservice.controllers.TusController
import g11.tusuploaderservice.integration.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test

class TusControllerTest : IntegrationTest() {
    @Autowired private lateinit var tusController: TusController
    // Test cases for TusController would go here
    @Test
    fun `sample test`() {
        assertEquals(1, 1)
    }
}



