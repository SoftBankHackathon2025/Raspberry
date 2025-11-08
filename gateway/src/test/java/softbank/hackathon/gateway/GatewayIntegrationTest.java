package softbank.hackathon.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private WireMockServer userService;
    private WireMockServer deployService;
    private WireMockServer feService;

    @BeforeEach
    void setUp() {
        userService = new WireMockServer(8083);
        deployService = new WireMockServer(8082);
        feService = new WireMockServer(8081);

        userService.start();
        deployService.start();
        feService.start();

        // Configure WireMock stubs
        configureUserService();
        configureDeployService();
        configureFrontendService();
    }

    @AfterEach
    void tearDown() {
        if (userService != null && userService.isRunning()) {
            userService.stop();
        }
        if (deployService != null && deployService.isRunning()) {
            deployService.stop();
        }
        if (feService != null && feService.isRunning()) {
            feService.stop();
        }
    }

    private void configureUserService() {
        userService.stubFor(get(urlPathMatching("/users/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"test-user\",\"service\":\"user\"}")));

        userService.stubFor(get("/actuator/health")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"UP\"}")));
    }

    private void configureDeployService() {
        deployService.stubFor(get(urlPathMatching("/deployments/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"test-deployment\",\"service\":\"deploy\"}")));

        deployService.stubFor(post("/deployments")
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":2,\"name\":\"new-deployment\",\"service\":\"deploy\"}")));
    }

    private void configureFrontendService() {
        feService.stubFor(get("/")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><h1>Frontend Service</h1></body></html>")));

        feService.stubFor(get(urlPathMatching("/frontend/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"service\":\"frontend\",\"status\":\"running\"}")));
    }

    @Test
    void shouldRouteUserServiceRequests() {
        webTestClient
                .get()
                .uri("/user/users/123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.service").isEqualTo("user")
                .jsonPath("$.name").isEqualTo("test-user");

        userService.verify(getRequestedFor(urlEqualTo("/users/123")));
    }

    @Test
    void shouldRouteDeployServiceRequests() {
        webTestClient
                .get()
                .uri("/deploy/deployments/456")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.service").isEqualTo("deploy")
                .jsonPath("$.name").isEqualTo("test-deployment");

        deployService.verify(getRequestedFor(urlEqualTo("/deployments/456")));
    }

    @Test
    void shouldCreateDeployment() {
        webTestClient
                .post()
                .uri("/deploy/deployments")
                .bodyValue("{\"name\":\"new-deployment\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.id").isEqualTo(2)
                .jsonPath("$.name").isEqualTo("new-deployment");

        deployService.verify(postRequestedFor(urlEqualTo("/deployments")));
    }

    @Test
    void shouldRouteFrontendServiceRequests() {
        webTestClient
                .get()
                .uri("/fe/frontend/status")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.service").isEqualTo("frontend");

        feService.verify(getRequestedFor(urlEqualTo("/frontend/status")));
    }

    @Test
    void shouldRouteDefaultRequestsToFrontend() {
        webTestClient
                .get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("text/html")
                .expectBody(String.class)
                .value(body -> body.contains("Frontend Service"));

        feService.verify(getRequestedFor(urlEqualTo("/")));
    }

    @Test
    void shouldAddGatewayHeaders() {
        webTestClient
                .get()
                .uri("/user/users/123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Gateway-Timestamp");

        userService.verify(getRequestedFor(urlEqualTo("/users/123"))
                .withHeader("X-Gateway-Source", equalTo("raspberry-gateway")));
    }

    @Test
    void shouldHandleActuatorEndpoints() {
        webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/vnd.spring-boot.actuator.v3+json")
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void shouldExposeGatewayActuatorEndpoints() {
        webTestClient
                .get()
                .uri("/actuator/gateway/routes")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(4);
    }

    @Test
    void shouldReturnNotFoundForUnknownRoute() {
        webTestClient
                .get()
                .uri("/unknown-service/test")
                .exchange()
                .expectStatus().isNotFound();
    }
}