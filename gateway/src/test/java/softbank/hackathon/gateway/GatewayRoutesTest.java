package softbank.hackathon.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class GatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void shouldLoadAllConfiguredRoutes() {
        StepVerifier.create(routeLocator.getRoutes())
                .expectNextCount(4) // user-service, deploy-service, fe-service, default-route
                .verifyComplete();
    }

    @Test
    void shouldHaveUserServiceRoute() {
        StepVerifier.create(routeLocator.getRoutes())
                .recordWith(() -> List.<Route>of())
                .thenConsumeWhile(route -> true)
                .consumeRecordedWith(routes -> {
                    Route userServiceRoute = routes.stream()
                            .filter(route -> "user-service".equals(route.getId()))
                            .findFirst()
                            .orElse(null);
                    
                    assertThat(userServiceRoute).isNotNull();
                    assertThat(userServiceRoute.getUri()).isEqualTo(URI.create("lb://user"));
                })
                .verifyComplete();
    }

    @Test
    void shouldHaveDeployServiceRoute() {
        StepVerifier.create(routeLocator.getRoutes())
                .recordWith(() -> List.<Route>of())
                .thenConsumeWhile(route -> true)
                .consumeRecordedWith(routes -> {
                    Route deployServiceRoute = routes.stream()
                            .filter(route -> "deploy-service".equals(route.getId()))
                            .findFirst()
                            .orElse(null);
                    
                    assertThat(deployServiceRoute).isNotNull();
                    assertThat(deployServiceRoute.getUri()).isEqualTo(URI.create("lb://deploy"));
                })
                .verifyComplete();
    }

    @Test
    void shouldHaveFrontendServiceRoute() {
        StepVerifier.create(routeLocator.getRoutes())
                .recordWith(() -> List.<Route>of())
                .thenConsumeWhile(route -> true)
                .consumeRecordedWith(routes -> {
                    Route feServiceRoute = routes.stream()
                            .filter(route -> "fe-service".equals(route.getId()))
                            .findFirst()
                            .orElse(null);
                    
                    assertThat(feServiceRoute).isNotNull();
                    assertThat(feServiceRoute.getUri()).isEqualTo(URI.create("lb://fe"));
                })
                .verifyComplete();
    }

    @Test
    void shouldHaveDefaultRoute() {
        StepVerifier.create(routeLocator.getRoutes())
                .recordWith(() -> List.<Route>of())
                .thenConsumeWhile(route -> true)
                .consumeRecordedWith(routes -> {
                    Route defaultRoute = routes.stream()
                            .filter(route -> "default-route".equals(route.getId()))
                            .findFirst()
                            .orElse(null);
                    
                    assertThat(defaultRoute).isNotNull();
                    assertThat(defaultRoute.getUri()).isEqualTo(URI.create("lb://fe"));
                    assertThat(defaultRoute.getOrder()).isEqualTo(1000); // Should be lowest priority
                })
                .verifyComplete();
    }

    @Test
    void shouldHaveCorrectRouteOrdering() {
        StepVerifier.create(routeLocator.getRoutes())
                .recordWith(() -> List.<Route>of())
                .thenConsumeWhile(route -> true)
                .consumeRecordedWith(routes -> {
                    Route userRoute = routes.stream()
                            .filter(route -> "user-service".equals(route.getId()))
                            .findFirst()
                            .orElse(null);
                    
                    Route defaultRoute = routes.stream()
                            .filter(route -> "default-route".equals(route.getId()))
                            .findFirst()
                            .orElse(null);
                    
                    assertThat(userRoute).isNotNull();
                    assertThat(defaultRoute).isNotNull();
                    
                    // Default route should have higher order value (lower priority)
                    assertThat(defaultRoute.getOrder()).isGreaterThan(userRoute.getOrder());
                })
                .verifyComplete();
    }
}