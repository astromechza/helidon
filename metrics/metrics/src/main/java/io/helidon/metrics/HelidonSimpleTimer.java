/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import jakarta.json.JsonObjectBuilder;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.SimpleTimer;

/**
 * Implementation of {@link SimpleTimer}.
 */
final class HelidonSimpleTimer extends MetricImpl implements SimpleTimer {
    private final SimpleTimer delegate;

    private HelidonSimpleTimer(String type, Metadata metadata, SimpleTimer delegate) {
        super(type, metadata);
        this.delegate = delegate;
    }

    static HelidonSimpleTimer create(String repoType, Metadata metadata) {
        return create(repoType, metadata, Clock.system());
    }

    static HelidonSimpleTimer create(String repoType, Metadata metadata, Clock clock) {
        return create(repoType, metadata, new SimpleTimerImpl(repoType, metadata.getName(), clock));
    }

    static HelidonSimpleTimer create(String repoType, Metadata metadata, SimpleTimer metric) {
        return new HelidonSimpleTimer(repoType, metadata, metric);
    }

    @Override
    public Duration getMaxTimeDuration() {
        return delegate.getMaxTimeDuration();
    }

    @Override
    public Duration getMinTimeDuration() {
        return delegate.getMinTimeDuration();
    }

    @Override
    public void update(Duration duration) {
        delegate.update(duration);
    }

    @Override
    public <T> T time(Callable<T> event) throws Exception {
        return delegate.time(event);
    }

    @Override
    public void time(Runnable event) {
        delegate.time(event);
    }

    @Override
    public Context time() {
        return delegate.time();
    }

    @Override
    public long getCount() {
        return delegate.getCount();
    }

    @Override
    public Duration getElapsedTime() {
        return delegate.getElapsedTime();
    }

    @Override
    public void prometheusData(StringBuilder sb, MetricID metricID, boolean withHelpType) {
        String promName;
        String name = metricID.getName();
        String tags = prometheusTags(metricID.getTags());
        promName = prometheusName(name) + "_total";
        if (withHelpType) {
            prometheusType(sb, promName, "counter");
            prometheusHelp(sb, promName);
        }
        sb.append(promName)
                .append(tags)
                .append(" ")
                .append(getCount());

        SimpleTimerImpl simpleTimerImpl = (delegate instanceof SimpleTimerImpl) ? ((SimpleTimerImpl) delegate) : null;
        Sample.Labeled sample = simpleTimerImpl != null ? simpleTimerImpl.sample : null;
        if (sample != null) {
            sb.append(prometheusExemplar(elapsedTimeInSeconds(sample.value()), simpleTimerImpl.sample));
        }
        sb.append("\n");

        promName = prometheusNameWithUnits(name + "_elapsedTime", Optional.of(MetricUnits.SECONDS));
        if (withHelpType) {
            prometheusType(sb, promName, "gauge");
            // By spec, no help for the elapsedTime part of SimpleTimer.
        }
        sb.append(promName)
                .append(tags)
                .append(" ")
                .append(elapsedTimeInSeconds())
                .append("\n");

        promName = prometheusNameWithUnits(name + "_maxTimeDuration", Optional.of(MetricUnits.SECONDS));
        if (withHelpType) {
            prometheusType(sb, promName, "gauge");
        }
        sb.append(promName)
                .append(tags)
                .append(" ")
                .append(durationPrometheusOutput(getMaxTimeDuration()))
                .append("\n");

        promName = prometheusNameWithUnits(name + "_minTimeDuration", Optional.of(MetricUnits.SECONDS));
        if (withHelpType) {
            prometheusType(sb, promName, "gauge");
        }
        sb.append(promName)
                .append(tags)
                .append(" ")
                .append(durationPrometheusOutput(getMinTimeDuration()))
                .append("\n");


        if (sample != null) {
            sb.append(prometheusExemplar(elapsedTimeInSeconds(sample.value()), sample));
        }
        sb.append("\n");
    }

    @Override
    public String prometheusValue() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void jsonData(JsonObjectBuilder builder, MetricID metricID) {
        JsonObjectBuilder myBuilder = JSON.createObjectBuilder()
                .add(jsonFullKey("count", metricID), getCount())
                .add(jsonFullKey("elapsedTime", metricID), jsonDuration(getElapsedTime()))
                .add(jsonFullKey("maxTimeDuration", metricID), jsonDuration(getMaxTimeDuration()))
                .add(jsonFullKey("minTimeDuration", metricID), jsonDuration(getMinTimeDuration()));
        builder.add(metricID.getName(), myBuilder);
    }

    private static String durationPrometheusOutput(Duration duration) {
        return duration == null ? "NaN" : Long.toString(duration.toSeconds());
    }

    private double elapsedTimeInSeconds() {
        return elapsedTimeInSeconds(getElapsedTime().toNanos());
    }

    private double elapsedTimeInSeconds(long nanos) {
        return nanos / (1000.0 * 1000.0 * 1000.0);
    }

    private static final class ContextImpl implements Context {
        private final SimpleTimerImpl theSimpleTimer;
        private final long startTime;
        private final Clock clock;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private Duration elapsed;

        private ContextImpl(SimpleTimerImpl theSimpleTimer, Clock clock) {
            this.theSimpleTimer = theSimpleTimer;
            this.startTime = clock.nanoTick();
            this.clock = clock;
        }

        @Override
        public long stop() {
            if (running.compareAndSet(true, false)) {
                elapsed = Duration.ofNanos(clock.nanoTick() - startTime);
                theSimpleTimer.update(elapsed);
            }
            return elapsed.toNanos();
        }

        @Override
        public void close() {
            stop();
        }

    }

    private static class SimpleTimerImpl implements SimpleTimer {

        private final HelidonCounter counter;
        private final Clock clock;
        private Duration elapsed = Duration.ofNanos(0);
        private Duration currentMin = null;
        private Duration currentMax = null;
        private Duration lastMin = null;
        private Duration lastMax = null;
        private long lastMinute;

        private Sample.Labeled sample = null;

        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        SimpleTimerImpl(String repoType, String name, Clock clock) {
            counter =  HelidonCounter.create(repoType, Metadata.builder()
                    .withName(name)
                    .withType(MetricType.COUNTER)
                    .build());
            this.clock = clock;
            lastMinute = currentTimeMinute();
        }

        @Override
        public Duration getMaxTimeDuration() {
            updateState();
            return readAccess(() -> lastMax == null ? null : Duration.from(lastMax));
        }

        @Override
        public Duration getMinTimeDuration() {
            updateState();
            return readAccess(() -> lastMin == null ? null : Duration.from(lastMin));
        }

        @Override
        public void update(Duration duration) {
            update(duration.toNanos());
        }

        @Override
        public <T> T time(Callable<T> event) throws Exception {
            long t = clock.nanoTick();

            try {
                return event.call();
            } finally {
                update(clock.nanoTick() - t);
            }
        }

        @Override
        public void time(Runnable event) {
            long t = clock.nanoTick();

            try {
                event.run();
            } finally {
                update(clock.nanoTick() - t);
            }
        }

        @Override
        public Context time() {
            return new ContextImpl(this, clock);
        }

        @Override
        public Duration getElapsedTime() {
            return readAccess(() -> Duration.from(elapsed));
        }

        @Override
        public long getCount() {
            return counter.getCount();
        }

        private long currentTimeMinute() {
            return clock.milliTime() / 1000 / 60;
        }

        private void update(long nanos) {
            writeAccess(() -> {
                if (nanos >= 0) {
                    counter.inc();
                    elapsed = elapsed.plusNanos(nanos);
                    sample = Sample.labeled(nanos);
                    updateStateLocked();
                    if (currentMin == null || currentMin.toNanos() > nanos) {
                        currentMin = Duration.ofNanos(nanos);
                    }
                    if (currentMax == null || currentMax.toNanos() < nanos) {
                        currentMax = Duration.ofNanos(nanos);
                    }
                }
                return null;
            });
        }

        private void updateState() {
            writeAccess(() -> {
                updateStateLocked();
                return null;
            });
        }

        private void updateStateLocked() {
            long currentMinute = currentTimeMinute();
            long diff = currentMinute - lastMinute;
            if (diff >= 1L) {
                lastMax = currentMax;
                lastMin = currentMin;
                currentMax = null;
                currentMin = null;
                lastMinute = currentMinute;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SimpleTimerImpl that = (SimpleTimerImpl) o;
            return counter.equals(that.counter)
                    && elapsed.equals(that.elapsed)
                    && Objects.equals(lastMin, that.lastMin)
                    && Objects.equals(lastMax, that.lastMax)
                    && Objects.equals(sample, that.sample);
        }

        @Override
        public int hashCode() {
            return Objects.hash(counter, elapsed, lastMin, lastMax, sample);
        }

        private <T> T writeAccess(Callable<T> action) {
            return access(lock.writeLock(), action);
        }

        private <T> T readAccess(Callable<T> action) {
            return access(lock.readLock(), action);
        }

        private <T> T access(Lock lock, Callable<T> action) {
            lock.lock();
            try {
                return action.call();
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass() || !super.equals(o)) {
            return false;
        }
        HelidonSimpleTimer that = (HelidonSimpleTimer) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), delegate);
    }

    @Override
    protected String toStringDetails() {
        return ", count='" + getCount() + '\''
                + ", elapsedTime='" + getElapsedTime() + '\''
                + ", minTimeDuration='" + getMinTimeDuration() + '\''
                + ", maxTimeDuration='" + getMaxTimeDuration() + '\'';
    }
}
