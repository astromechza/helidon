/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.metrics.jaeger;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import io.helidon.common.LazyValue;
import io.helidon.metrics.api.RegistryFactory;

import io.jaegertracing.internal.metrics.Counter;
import io.jaegertracing.internal.metrics.Gauge;
import io.jaegertracing.internal.metrics.Timer;
import io.jaegertracing.spi.MetricsFactory;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Tag;

/**
 * Exposes Jaeger tracing metrics as Helidon vendor metrics.
 */
public class HelidonJaegerMetricsFactory implements MetricsFactory {

    private final LazyValue<MetricRegistry> vendorRegistry = LazyValue.create(() -> RegistryFactory.getInstance()
            .getRegistry(MetricRegistry.Type.VENDOR));

    @Override
    public Counter createCounter(String name, Map<String, String> jaegerTags) {
        return new Counter() {

            private final org.eclipse.microprofile.metrics.Counter counter = createMetric(
                    name,
                    jaegerTags,
                    MetricType.COUNTER,
                    MetricUnits.NONE,
                    HelidonJaegerMetricsFactory.this::counter);

            @Override
            public void inc(long delta) {
                counter.inc(delta);
            }
        };
    }

    @Override
    public Timer createTimer(String name, Map<String, String> jaegerTags) {
        return new Timer() {

            private final org.eclipse.microprofile.metrics.Timer timer = createMetric(
                    name,
                    jaegerTags,
                    MetricType.TIMER,
                    MetricUnits.MICROSECONDS,
                    HelidonJaegerMetricsFactory.this::timer);

            @Override
            public void durationMicros(long time) {
                timer.update(Duration.ofNanos(TimeUnit.MICROSECONDS.toNanos(time)));
            }
        };
    }

    @Override
    public Gauge createGauge(String name, Map<String, String> jaegerTags) {
        return new Gauge() {

            private final JaegerGauge gauge = new JaegerGauge();

            {
                Metadata metadata = metadata(name, MetricType.GAUGE, MetricUnits.NONE);
                vendorRegistry.get().register(metadata,
                        gauge,
                        convertTags(jaegerTags));
            }

            @Override
            public void update(long amount) {
                gauge.update(amount);
            }
        };
    }

    private org.eclipse.microprofile.metrics.Counter counter(Metadata metadata, Tag[] tags) {
        return vendorRegistry.get().counter(metadata, tags);
    }

    private org.eclipse.microprofile.metrics.Timer timer(Metadata metadata, Tag[] tags) {
        return vendorRegistry.get().timer(metadata, tags);
    }

    private static class JaegerGauge implements org.eclipse.microprofile.metrics.Gauge<Long> {

        private static final Long DEFAULT_VALUE = 0L;

        private Long value = DEFAULT_VALUE;

        void update(Long value) {
            this.value = value;
        }

        @Override
        public Long getValue() {
            return value;
        }
    }

    private static <T extends Metric> T createMetric(
            String name,
            Map<String, String> jaegerTags,
            MetricType metricType,
            String metricUnits,
            BiFunction<Metadata, Tag[], T> metricFactoryFn) {

        Metadata metadata = metadata(name, metricType, metricUnits);
        return metricFactoryFn.apply(metadata, convertTags(jaegerTags));
    }

    private static Metadata metadata(String name, MetricType metricType, String metricUnits) {
        return Metadata.builder()
                .withName(name)
                .withDisplayName("Jaeger tracing " + name)
                .withDescription("Jaeger tracing " + metricType.toString() + " for " + name)
                .withType(metricType)
                .withUnit(metricUnits)
                .build();
    }

    static Tag[] convertTags(Map<String, String> jaegerTags) {
        if (jaegerTags == null) {
            return new Tag[0];
        }
        return jaegerTags
                .entrySet()
                .stream()
                .map(e -> new Tag(e.getKey(), e.getValue()))
                .toArray(Tag[]::new);
    }
}
