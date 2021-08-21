package com.dailycodebuffer.cloudgateway.config;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * https://github.com/spring-cloud/spring-cloud-circuitbreaker/issues/80
 * https://github.com/spring-cloud/spring-cloud-circuitbreaker/blob/1.0.x/spring-cloud-circuitbreaker-resilience4j/src/main/java/org/springframework/cloud/circuitbreaker/resilience4j/ReactiveResilience4JAutoConfiguration.java
 */
@Configuration
public class GatewayConfig {

  @Autowired private EurekaClient discoveryClient;

  @Bean
  public RouteLocator myRoutes(RouteLocatorBuilder routeLocatorBuilder) {
    InstanceInfo userInstance = discoveryClient.getNextServerFromEureka("USER-SERVICE", false);
    InstanceInfo departmentInstance =
        discoveryClient.getNextServerFromEureka("DEPARTMENT-SERVICE", false);

    return routeLocatorBuilder
        .routes()
        .route(
            p ->
                p.path("/users/**")
                    .filters(
                        f ->
                            f.circuitBreaker(
                                c ->
                                    c.setName("codedTribeCB")
                                        .setFallbackUri("/userServiceFallBack")))
                    .uri(userInstance.getHomePageUrl()))
        .route(
            p ->
                p.path("/departments/**")
                    .filters(
                        f ->
                            f.circuitBreaker(
                                c ->
                                    c.setName("codedTribeCB")
                                        .setFallbackUri("/departmentServiceFallBack")))
                    .uri(departmentInstance.getHomePageUrl()))
        .build();
  }

  @Bean
  public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
    return factory ->
        factory.configureDefault(
            id ->
                new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                    .timeLimiterConfig(
                        TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build())
                    .build());
  }

  @Autowired(required = false)
  private List<Customizer<ReactiveResilience4JCircuitBreakerFactory>> customizers =
      new ArrayList<>();

  @Bean
  @ConditionalOnMissingBean(ReactiveCircuitBreakerFactory.class)
  public ReactiveResilience4JCircuitBreakerFactory reactiveResilience4JCircuitBreakerFactory(
      CircuitBreakerRegistry circuitBreakerRegistry, TimeLimiterRegistry timeLimiterRegistry) {
    ReactiveResilience4JCircuitBreakerFactory factory =
        new ReactiveResilience4JCircuitBreakerFactory(circuitBreakerRegistry, timeLimiterRegistry);
    customizers.forEach(customizer -> customizer.customize(factory));
    return factory;
  }
}
