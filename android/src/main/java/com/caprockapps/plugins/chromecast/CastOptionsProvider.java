package com.caprockapps.plugins.chromecast;

import android.content.Context;

import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.CastMediaOptions;

import java.util.List;

/**
 * CastOptionsProvider for the Chromecast plugin.
 * Uses a dynamic appId set at runtime via setAppId(), falling back to the
 * default media receiver if not set.
 */
public class CastOptionsProvider implements OptionsProvider {

    private static String appId;

    /**
     * Sets the app ID to use for Cast sessions.
     * Must be called before the Cast framework initializes.
     * @param applicationId the Cast application ID
     */
    public static void setAppId(String applicationId) {
        appId = applicationId;
    }

    @Override
    public CastOptions getCastOptions(Context context) {
        String id = (appId != null && !appId.isEmpty())
                ? appId
                : CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;
        CastMediaOptions mediaOptions = new CastMediaOptions.Builder()
                .setNotificationOptions(null)
                .build();
        return new CastOptions.Builder()
                .setReceiverApplicationId(id)
                .setCastMediaOptions(mediaOptions)
                .build();
    }

    @Override
    public List<SessionProvider> getAdditionalSessionProviders(Context context) {
        return null;
    }
}
