package g11.apigateway

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ApiGatewayApplicationTests {

    @MockBean
    private lateinit var clientRegistrationRepository: ClientRegistrationRepository

    @Test
    fun contextLoads() {
    }

}
