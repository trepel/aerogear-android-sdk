package org.aerogear.mobile.security;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.aerogear.mobile.core.metrics.MetricsService;

/**
 * Synchronously executes provided {@link DeviceCheck}s.
 */
public class SyncDeviceCheckExecutor extends AbstractDeviceCheckExecutor<SyncDeviceCheckExecutor> {

    /**
     * Builder class for constructing a SyncDeviceCheckExecutor object.
     */
    public static class Builder extends
                    DeviceCheckExecutor.Builder.AbstractBuilder<Builder, SyncDeviceCheckExecutor> {

        /**
         * Creates a Builder object.
         *
         * @param ctx {@link Context} to be used by security checks
         * @throws IllegalArgumentException if ctx is null
         */
        Builder(final Context ctx) {
            super(ctx);
        }

        /**
         * Creates a new SyncDeviceCheckExecutor object.
         *
         * @return {@link SyncDeviceCheckExecutor}
         */
        @Override
        public SyncDeviceCheckExecutor build() {
            return new SyncDeviceCheckExecutor(getCtx(), getChecks(), getMetricsService());
        }
    }

    /**
     * Constructor for SyncDeviceCheckExecutor.
     *
     * @param context the {@link Context} to be used by security checks
     * @param checks the {@link Collection} of security checks to be tested
     * @param metricsService {@link MetricsService}. Can be null
     */
    SyncDeviceCheckExecutor(@NonNull final Context context,
                    @NonNull final Collection<DeviceCheck> checks,
                    @Nullable final MetricsService metricsService) {
        super(context, checks, metricsService);
    }

    /**
     * Executes the provided checks and returns the results. Blocks until all checks are executed.
     *
     * Returns a {@link Map} containing the results of each executed test. The key of the map will
     * be the output of {@link DeviceCheck#getId()}, while the value will be the
     * {@link DeviceCheckResult} of the check.
     *
     * @return {@link Map}
     */
    public Map<String, DeviceCheckResult> execute() {
        final Map<String, DeviceCheckResult> results = new HashMap<>();

        final DeviceCheckExecutorListener metricServicePublisher = getMetricServicePublisher();

        for (DeviceCheck check : getChecks()) {
            DeviceCheckResult result = check.test(getContext());
            results.put(check.getId(), result);

            metricServicePublisher.onExecuted(result);
        }

        metricServicePublisher.onComplete();

        return results;
    }
}
