package org.aerogear.mobile.core.integration;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

import android.app.Application;
import android.support.test.filters.SmallTest;

import org.aerogear.mobile.core.AeroGearTestRunner;
import org.aerogear.mobile.core.MobileCore;
import org.aerogear.mobile.core.configuration.MobileCoreConfiguration;
import org.aerogear.mobile.core.configuration.MobileCoreJsonParser;
import org.aerogear.mobile.core.configuration.ServiceConfiguration;
import org.aerogear.mobile.core.executor.AppExecutors;
import org.aerogear.mobile.core.metrics.Metrics;
import org.aerogear.mobile.core.metrics.MetricsService;
import org.aerogear.mobile.core.reactive.Responder;
import org.aerogear.mobile.core.unit.metrics.MetricsServiceTest;

@RunWith(AeroGearTestRunner.class)
@SmallTest
public class MetricsServiceIntegrationTest {

    private MetricsService metricsService;
    private Throwable error;

    @Before
    public void setUp() {
        Application context = RuntimeEnvironment.application;
        MobileCore.init(context);

        // read the app metrics endpoint from mobile-services.json
        String metricsEndpoint = null;
        try (InputStream configStream = context.getAssets().open("mobile-services.json")) {
            MobileCoreConfiguration jsonConfig = new MobileCoreJsonParser(configStream).parse();
            Map<String, ServiceConfiguration> configsPerId = jsonConfig.getServicesConfigPerId();
            ServiceConfiguration metricsConfig = configsPerId.get("metrics-myapp-android");
            metricsEndpoint = metricsConfig.getUrl();
        } catch (JSONException | IOException exception) {
            System.err.println(exception);
            fail(exception.getMessage());
        }

        metricsService = new MetricsService(metricsEndpoint);

        error = null;
    }

    @Test
    public void testSendDefaultMetricsShouldReturnNoError() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        metricsService.sendAppAndDeviceMetrics().requestOn(new AppExecutors().mainThread())
                        .respondWith(new Responder<Boolean>() {
                            @Override
                            public void onResult(Boolean value) {
                                latch.countDown();
                            }

                            @Override
                            public void onException(Exception exception) {
                                error = exception;
                                latch.countDown();
                            }
                        });

        latch.await(10, TimeUnit.SECONDS);

        assertNull(error);
    }

    @Test
    public void testPublishMetricsShouldReturnNoError() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Metrics metrics = new MetricsServiceTest.DummyMetrics();

        metricsService.publish("init", metrics).requestOn(new AppExecutors().mainThread())
                        .respondWith(new Responder<Boolean>() {
                            @Override
                            public void onResult(Boolean value) {
                                latch.countDown();
                            }

                            @Override
                            public void onException(Exception exception) {
                                error = exception;
                                latch.countDown();
                            }
                        });

        latch.await(10, TimeUnit.SECONDS);

        assertNull(error);
    }

}
