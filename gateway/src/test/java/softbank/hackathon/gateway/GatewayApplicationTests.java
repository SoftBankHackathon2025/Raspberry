package softbank.hackathon.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// @SpringBootTest
@ActiveProfiles("test")
class GatewayApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private RouteLocator routeLocator;

	@Test
	void contextLoads() {
		assertThat(applicationContext).isNotNull();
	}

	@Test
	void gatewayBeansAreLoaded() {
		assertThat(routeLocator).isNotNull();
	}

	@Test
	void discoveryClientIsConfigured() {
		assertThat(applicationContext.containsBean("discoveryClient")).isTrue();
	}

	@Test
	void actuatorEndpointsAreConfigured() {
		assertThat(applicationContext.containsBean("webEndpointServletHandlerMapping")).isTrue();
	}
}
