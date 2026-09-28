package com.wayline.app.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class TracingConfiguration {
    @Bean(destroyMethod = "close")
    public SdkTracerProvider tracerProvider(
        @Value("${wayline.tracing.endpoint:http://localhost:4318/v1/traces}") String endpoint,
        @Value("${management.tracing.sampling.probability:1.0}") double samplingProbability
    ) {
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
            .setEndpoint(endpoint)
            .setTimeout(Duration.ofSeconds(5))
            .build();
        Resource resource = Resource.getDefault().merge(Resource.create(
            io.opentelemetry.api.common.Attributes.of(
                AttributeKey.stringKey("service.name"), "wayline-payment-platform"
            )
        ));
        return SdkTracerProvider.builder()
            .setSampler(Sampler.traceIdRatioBased(samplingProbability))
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .build();
    }

    @Bean
    public OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider) {
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();
    }
}
