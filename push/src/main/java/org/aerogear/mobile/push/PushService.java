package org.aerogear.mobile.push;

import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.aerogear.mobile.core.utils.SanityCheck.nonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.Gson;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Base64;

import org.aerogear.mobile.core.MobileCore;
import org.aerogear.mobile.core.configuration.ServiceConfiguration;
import org.aerogear.mobile.core.exception.ConfigurationNotFoundException;
import org.aerogear.mobile.core.exception.HttpException;
import org.aerogear.mobile.core.executor.AppExecutors;
import org.aerogear.mobile.core.http.HttpRequest;
import org.aerogear.mobile.core.reactive.Request;
import org.aerogear.mobile.core.reactive.Requester;
import org.aerogear.mobile.core.reactive.Responder;

/**
 * The entry point for communication with Unified Push Server
 */
public class PushService {

    public static final String TYPE = "push";

    private static final String TAG = PushService.class.getName();

    private static final String DEFAULT_MESSAGE_HANDLER_KEY = "DEFAULT_MESSAGE_HANDLER_KEY";

    private static final String SHARED_PREFERENCE_PUSH_NAME = "AEROGEAR_UNIFIED_PUSH";
    private static final String SHARED_PREFERENCE_PUSH_KEY_CONFIG = "AEROGEAR_UNIFIED_PUSH_CONFIG";
    private static final String SHARED_PREFERENCE_PUSH_KEY_CREDENTIALS =
                    "AEROGEAR_UNIFIED_PUSH_CREDENTIALS";

    private static final String registryDeviceEndpoint = "rest/registry/device";
    private static final String JSON_ANDROID_CONFIG_KEY = "android";
    private static final String JSON_VARIANT_ID_KEY = "variantId";
    private static final String JSON_VARIANT_SECRET_KEY = "variantSecret";
    private static final String JSON_SENDER_ID_KEY = "senderId";

    private static final List<MessageHandler> MAIN_THREAD_HANDLERS =
                    Collections.synchronizedList(new ArrayList<>());
    private static final List<MessageHandler> BACKGROUND_THREAD_HANDLERS =
                    Collections.synchronizedList(new ArrayList<>());

    private final static String ERROR_MESSAGE =
                    "An error occurred while trying to load the push config";

    private static SharedPreferences sharedPreferences;
    private static MessageHandler defaultHandler;

    private final String deviceType = "ANDROID";
    private final String operatingSystem = "android";
    private final String osVersion = android.os.Build.VERSION.RELEASE;

    private final UnifiedPushCredentials unifiedPushCredentials;
    private final Gson gson = new Gson();

    public PushService(Context context, UnifiedPushCredentials unifiedPushCredentials) {
        nonNull(context, "context");
        nonNull(unifiedPushCredentials, "unifiedPushCredentials");

        this.unifiedPushCredentials = unifiedPushCredentials;

        initDefaultHandler(context);
        initSharedPreference(context);

    }

    public static final class Builder {

        private Context context;
        private UnifiedPushCredentials unifiedPushCredentials = new UnifiedPushCredentials();

        public Builder() {}

        public Builder openshift() {

            MobileCore mobileCore = MobileCore.getInstance();

            context = mobileCore.getContext();

            ServiceConfiguration serviceConfiguration =
                            mobileCore.getServiceConfigurationByType(TYPE);

            try {
                JSONObject android = new JSONObject(
                                serviceConfiguration.getProperties().get(JSON_ANDROID_CONFIG_KEY));

                unifiedPushCredentials = parseCredentialsFromJSON(android);
                unifiedPushCredentials.setUrl(serviceConfiguration.getUrl());
            } catch (JSONException e) {
                MobileCore.getLogger().error(e.getMessage(), e);
                throw new ConfigurationNotFoundException(ERROR_MESSAGE);
            }

            return this;
        }

        private UnifiedPushCredentials parseCredentialsFromJSON(JSONObject jsonObject) {
            try {
                UnifiedPushCredentials unifiedPushCredentials = new UnifiedPushCredentials();
                unifiedPushCredentials.setVariant(jsonObject.getString(JSON_VARIANT_ID_KEY));
                unifiedPushCredentials.setSecret(jsonObject.getString(JSON_VARIANT_SECRET_KEY));
                unifiedPushCredentials.setSenderId(jsonObject.getString(JSON_SENDER_ID_KEY));

                return unifiedPushCredentials;
            } catch (JSONException e) {
                MobileCore.getLogger().error(e.getMessage(), e);
                throw new ConfigurationNotFoundException(ERROR_MESSAGE);
            }
        }

        public PushService build() {
            return new PushService(context, unifiedPushCredentials);
        }

    }

    /**
     * Register the device on Unified Push Server
     *
     * @return a request for a device registration
     */
    public Request<Boolean> registerDevice() {
        return registerDevice(new UnifiedPushConfig());
    }

    /**
     * Register the device on Unified Push Server
     *
     * @param unifiedPushConfig Unified Push configuration to be send to the Unified Push Server
     * @return a request for a device registration
     */
    public Request<Boolean> registerDevice(final UnifiedPushConfig unifiedPushConfig) {
        nonNull(unifiedPushConfig, "unifiedPushConfig");

        return registerDevice(asJson(unifiedPushConfig));
    }

    private Request<Boolean> registerDevice(final JSONObject data) {

        return Requester.call(() -> {
            data.put("deviceToken", FirebaseInstanceId.getInstance().getToken());

            String authHash = getHashedAuth(unifiedPushCredentials.getVariant(),
                            unifiedPushCredentials.getSecret().toCharArray());

            final HttpRequest httpRequest = MobileCore.getInstance().getHttpLayer().newRequest();
            httpRequest.addHeader("Authorization", authHash);

            // Invalidate old on Unified Push Server
            String oldDeviceToken = retrieveOldDeviceToken();
            if (oldDeviceToken != null) {
                httpRequest.addHeader("x-ag-old-token", oldDeviceToken);
            }
            return httpRequest;
        }).requestMap(httpRequest -> httpRequest.post(
                        unifiedPushCredentials.getUrl() + registryDeviceEndpoint,
                        data.toString().getBytes()))
                        .requestMap(httpResponse -> Requester.call(() -> {
                            switch (httpResponse.getStatus()) {
                                case HTTP_OK:

                                    FirebaseMessaging firebaseMessaging =
                                                    FirebaseMessaging.getInstance();

                                    try {
                                        JSONArray categories = data.getJSONArray("categories");
                                        for (int i = 0; i < categories.length(); i++) {
                                            String category =
                                                            categories.getJSONObject(i).toString();
                                            firebaseMessaging.subscribeToTopic(category);
                                        }
                                    } catch (JSONException e) {
                                        // ignore
                                    }

                                    firebaseMessaging.subscribeToTopic(
                                                    unifiedPushCredentials.getVariant());

                                    saveCache(data);

                                    return Boolean.TRUE;
                                default:
                                    throw (new HttpException(httpResponse.getStatus()));
                            }
                        }))

                        .requestOn(new AppExecutors().networkThread());

    }

    /**
     * Unregister the device on Unified Push Server
     *
     * @return a response to monitor the unregistration process.
     */
    public Request<Boolean> unregisterDevice() {

        String authHash = getHashedAuth(unifiedPushCredentials.getVariant(),
                        unifiedPushCredentials.getSecret().toCharArray());

        final HttpRequest httpRequest = MobileCore.getInstance().getHttpLayer().newRequest();
        httpRequest.addHeader("Authorization", authHash);
        return httpRequest
                        .delete(unifiedPushCredentials.getUrl() + registryDeviceEndpoint + "/"
                                        + FirebaseInstanceId.getInstance().getToken())
                        .requestMap(httpResponse -> Requester.call(() -> {
                            switch (httpResponse.getStatus()) {
                                case HTTP_NO_CONTENT:

                                    FirebaseMessaging firebaseMessaging =
                                                    FirebaseMessaging.getInstance();

                                    try {
                                        JSONObject data = retrieveCachedConfig();
                                        if (data != null) {
                                            JSONArray categories = data.getJSONArray("categories");
                                            for (int i = 0; i < categories.length(); i++) {
                                                String category = categories.getString(i);
                                                firebaseMessaging.unsubscribeFromTopic(category);
                                            }
                                        }
                                    } catch (JSONException e) {
                                        // ignore
                                    }

                                    firebaseMessaging.unsubscribeFromTopic(
                                                    unifiedPushCredentials.getVariant());

                                    clearCache();

                                    return true;

                                default:
                                    throw new HttpException(httpResponse.getStatus());


                            }
                        }));
    }

    /**
     * Provide an auth hash to be used to authenticate in Unified Push Service
     *
     * @param variant Unified Push variant id
     * @param secret Unified Push variant secret
     * @return Auth hash
     */
    private String getHashedAuth(final String variant, final char[] secret) {
        StringBuilder headerValueBuilder = new StringBuilder("Basic").append(" ");
        String unhashedCredentials = variant + ":" + String.valueOf(secret);
        String hashedCrentials =
                        Base64.encodeToString(unhashedCredentials.getBytes(), Base64.NO_WRAP);
        return headerValueBuilder.append(hashedCrentials).toString();
    }

    /**
     * Update the device token on Unified Push Server
     *
     * @param context Application context
     */
    public static void refreshToken(Context context) {
        nonNull(context, "context");

        if (sharedPreferences == null) {
            initSharedPreference(context);
        }

        JSONObject jsonObject = retrieveCachedConfig();
        UnifiedPushCredentials unifiedPushCredentials = retrieveCachedCredentials();

        if (jsonObject != null) {
            new PushService(context, unifiedPushCredentials).registerDevice()
                            .respondOn(new AppExecutors().mainThread())
                            .respondWith(new Responder<Boolean>() {
                                @Override
                                public void onResult(Boolean value) {
                                    MobileCore.getLogger().info("Device token refresh");
                                }

                                @Override
                                public void onException(Exception error) {
                                    MobileCore.getLogger().error(error.getMessage(), error);
                                }
                            });
        }
    }

    /**
     * Save info sent to the Unified Push Server
     *
     * @param config JSONObject sent to Unified Push Server
     */
    private void saveCache(JSONObject config) {
        sharedPreferences.edit().putString(SHARED_PREFERENCE_PUSH_KEY_CONFIG, config.toString())
                        .putString(SHARED_PREFERENCE_PUSH_KEY_CREDENTIALS,
                                        gson.toJson(unifiedPushCredentials))
                        .apply();
    }

    /**
     * Retrieve info sent to the Unified Push Server
     *
     * @return JSONObject sent to Unified Push Server
     */
    private static Map<String, Object> retrieveCache() {
        Map<String, Object> data = new HashMap<>();
        data.put(SHARED_PREFERENCE_PUSH_KEY_CONFIG,
                        sharedPreferences.getString(SHARED_PREFERENCE_PUSH_KEY_CONFIG, ""));
        data.put(SHARED_PREFERENCE_PUSH_KEY_CREDENTIALS,
                        sharedPreferences.getString(SHARED_PREFERENCE_PUSH_KEY_CREDENTIALS, ""));
        return data;
    }

    private static JSONObject retrieveCachedConfig() {
        try {
            String jsonString = (String) retrieveCache().get(SHARED_PREFERENCE_PUSH_KEY_CONFIG);
            return (!jsonString.isEmpty()) ? new JSONObject(jsonString) : null;
        } catch (JSONException e) {
            MobileCore.getLogger().error(e.getMessage(), e);
            return null;
        }
    }

    private static UnifiedPushCredentials retrieveCachedCredentials() {
        String cachedCredentials =
                        (String) retrieveCache().get(SHARED_PREFERENCE_PUSH_KEY_CREDENTIALS);
        return new Gson().fromJson(cachedCredentials, UnifiedPushCredentials.class);
    }

    /**
     * Retrive the last device token sent to the Unified Push Server
     *
     * @return Device Token
     */
    private String retrieveOldDeviceToken() {

        try {
            JSONObject config = retrieveCachedConfig();
            return (config == null) ? null : config.getString("deviceToken");
        } catch (JSONException e) {
            MobileCore.getLogger().error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * Clear cache of info sent to the Unified Push Server
     */
    private void clearCache() {
        sharedPreferences.edit().remove(SHARED_PREFERENCE_PUSH_KEY_CONFIG)
                        .remove(SHARED_PREFERENCE_PUSH_KEY_CREDENTIALS).apply();
    }

    /**
     * When a push message is received, all main thread handlers will be notified on the main(UI)
     * thread. This is very convenient for Activities and Fragments.
     *
     * @param handler a handler to added to the list of handlers to be notified.
     */
    public static void registerMainThreadHandler(final MessageHandler handler) {
        MAIN_THREAD_HANDLERS.add(nonNull(handler, "handle"));
    }

    /**
     * When a push message is received, all background thread handlers will be notified on a non UI
     * thread. This should be used by classes which need to update internal state or preform some
     * action which doesn't change the UI.
     *
     * @param handler a handler to added to the list of handlers to be notified.
     */
    public static void registerBackgroundThreadHandler(final MessageHandler handler) {
        BACKGROUND_THREAD_HANDLERS.add(nonNull(handler, "handle"));
    }

    /**
     * This will remove the given handler from the collection of main thread handlers. This MUST be
     * called when a Fragment or activity is backgrounded via onPause.
     *
     * @param handler a handler to be removed to the list of handlers
     */
    public static void unregisterMainThreadHandler(final MessageHandler handler) {
        MAIN_THREAD_HANDLERS.remove(nonNull(handler, "handle"));
    }

    /**
     * This will remove the given handler from the collection of background thread handlers.
     *
     * @param handler a handler to be removed to the list of handlers
     */
    public static void unregisterBackgroundThreadHandler(final MessageHandler handler) {
        BACKGROUND_THREAD_HANDLERS.remove(nonNull(handler, "handle"));
    }

    /**
     * Notify all registered handlers.
     *
     * @param context the Android context
     * @param message the push message
     */
    public static void notifyHandlers(final Context context, final Map<String, String> message) {
        nonNull(context, "context");

        if (defaultHandler == null) {
            initDefaultHandler(context);
        }

        if (BACKGROUND_THREAD_HANDLERS.isEmpty() && MAIN_THREAD_HANDLERS.isEmpty()
                        && defaultHandler != null) {
            new AppExecutors().singleThreadService().execute(() -> defaultHandler.onMessage(context,
                            Collections.unmodifiableMap(message)));
        } else {

            for (final MessageHandler handler : BACKGROUND_THREAD_HANDLERS) {
                new AppExecutors().singleThreadService().execute(() -> handler.onMessage(context,
                                Collections.unmodifiableMap(message)));
            }

            for (final MessageHandler handler : MAIN_THREAD_HANDLERS) {
                new AppExecutors().mainThread().execute(() -> handler.onMessage(context,
                                Collections.unmodifiableMap(message)));
            }

        }

    }

    /**
     * Get a default handler from AndroidManifest.xml if exists
     * <p>
     * <code>
     * <meta-data
     * android:name="DEFAULT_MESSAGE_HANDLER_KEY" android:value="my.package.HandlerClassName" />
     * </code>
     */
    @SuppressWarnings("unchecked")
    private static void initDefaultHandler(final Context context) {
        nonNull(context, "context");

        try {

            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(
                            context.getPackageName(), PackageManager.GET_META_DATA);

            Bundle metaData = applicationInfo.metaData;

            if (metaData != null) {

                String defaultHandlerClassName = metaData.getString(DEFAULT_MESSAGE_HANDLER_KEY);
                if (defaultHandlerClassName != null) {
                    try {
                        Class<? extends MessageHandler> defaultHandlerClass =
                                        (Class<? extends MessageHandler>) Class
                                                        .forName(defaultHandlerClassName);
                        defaultHandler = defaultHandlerClass.newInstance();
                    } catch (Exception ex) {
                        MobileCore.getLogger().error(ex.getMessage(), ex);
                    }
                }

            }

        } catch (PackageManager.NameNotFoundException e) {
            MobileCore.getLogger().warning(e.getMessage(), e);
        }

    }

    /**
     * Configure the shared preference to store/retrive UPS credentials and config
     *
     * @param context Application context
     */
    private static void initSharedPreference(Context context) {
        sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCE_PUSH_NAME,
                        Context.MODE_PRIVATE);
    }

    private JSONObject asJson(UnifiedPushConfig unifiedPushConfig) {
        final JSONObject data = new JSONObject();
        try {
            data.put("deviceType", deviceType);

            data.put("operatingSystem", operatingSystem);
            data.put("osVersion", osVersion);
            data.put("alias", unifiedPushConfig.getAlias());

            final List<String> categories = unifiedPushConfig.getCategories();
            if (!categories.isEmpty()) {
                JSONArray jsonCategories = new JSONArray();
                for (String category : categories) {
                    jsonCategories.put(category);
                }
                data.put("categories", jsonCategories);
            }
            return data;
        } catch (JSONException e) {
            MobileCore.getLogger().error(TAG, e);
            throw new IllegalArgumentException("UnifiedPushConfig could not be expressed as json");
        }
    }

}
