package softbank.hackathon.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EurekaDiscoveryTest {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Test
    void shouldHaveDiscoveryClientConfigured() {
        assertThat(discoveryClient).isNotNull();
        assertThat(discoveryClient.description()).contains("Eureka");
    }

    @Test
    void shouldReturnServicesListWhenQueried() {
        List<String> services = discoveryClient.getServices();
        assertThat(services).isNotNull();
        // In test environment, this might be empty or contain mock services
        // This test mainly verifies the DiscoveryClient is properly configured
    }

    @Test
    void shouldHandleServiceInstanceRetrieval() {
        // Test with a mock service that might exist in test context
        List<ServiceInstance> instances = discoveryClient.getInstances("non-existent-service");
        assertThat(instances).isNotNull();
        assertThat(instances).isEmpty(); // Should return empty list, not null
    }

    @Test
    void shouldConfigureServiceIdCorrectly() {
        // Verify that the gateway itself can be discovered if registered
        String serviceId = "gateway";
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
        assertThat(instances).isNotNull();
        
        // In a full integration test environment, we might have the gateway registered
        // For unit tests, this just verifies the client is working
    }

    @Test 
    void shouldHandleMultipleServiceQueries() {
        String[] serviceIds = {"user", "deploy", "fe"};
        
        for (String serviceId : serviceIds) {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
            assertThat(instances).isNotNull();
            // Services might not be available in test context, but client should not throw
        }
    }

    @Test
    void shouldProvideCorrectOrderingForServiceRetrieval() {
        List<String> services = discoveryClient.getServices();
        assertThat(services).isNotNull();
        
        // Test that we can call getServices multiple times without issues
        List<String> servicesSecondCall = discoveryClient.getServices();
        assertThat(servicesSecondCall).isNotNull();
        assertThat(servicesSecondCall).isEqualTo(services);
    }
}