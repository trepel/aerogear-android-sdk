package org.aerogear.mobile.security;

import android.content.Context;

import org.aerogear.mobile.core.MobileCore;
import org.aerogear.mobile.core.metrics.MetricsService;
import org.aerogear.mobile.security.utils.MockSecurityCheck;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class SecurityServiceTest {
    @Mock
    MobileCore mobileCore;

    @Mock
    Context context;

    @Mock
    MetricsService metricsService;

    SecurityService securityService;
    SecurityCheck securityCheck;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mobileCore.getContext()).thenReturn(RuntimeEnvironment.application);

        securityCheck = new MockSecurityCheck();

        securityService = new SecurityService();
        securityService.configure(mobileCore, null);
    }

    @Test
    public void testCheckAndSendMetric() {
        when(metricsService.publish()).thenReturn(null);

        securityService.checkAndSendMetric(securityCheck, metricsService);
        verify(metricsService, times(1)).publish(any());
    }
}
