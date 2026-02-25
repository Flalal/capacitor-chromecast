package com.caprockapps.plugins.chromecast;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.mediarouter.media.MediaRouter;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.framework.Session;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

@CapacitorPlugin(name = "Chromecast")
public class Chromecast extends Plugin {
    private static final String TAG = "Chromecast";

    /** Object to control the connection to the chromecast. */
    private ChromecastConnection connection;
    /** Holds the reference to the current client initiated scan. */
    private ChromecastConnection.ScanCallback clientScan;
    /** Holds the reference to the current client initiated scan callback. */
    private PluginCall scanPluginCall;
    /** In the case that chromecast can't be used. */
    private String noChromecastError;

    /**
     * Initialize all of the MediaRouter stuff with the AppId.
     *
     * @param pluginCall called with .success or .error depending on the result
     */
    @PluginMethod
    public void initialize(final PluginCall pluginCall) {
        String appId = pluginCall.getString("appId");

        setup();

        try {
            this.connection = new ChromecastConnection(getActivity(), new ChromecastConnection.Listener() {
                @Override
                public void onSessionStarted(Session session, String sessionId) {
                    try {
                        JSONObject result = new JSONObject();
                        result.put("isConnected", session.isConnected());
                        result.put("sessionId", sessionId);
                        sendEvent("SESSION_STARTED", JSObject.fromJSONObject(result));
                    } catch (JSONException e) {
                    }
                }

                @Override
                public void onSessionEnded(Session session, int error) {
                    try {
                        JSONObject result = new JSONObject();
                        result.put("isConnected", session.isConnected());
                        result.put("error", error);
                        sendEvent("SESSION_ENDED", JSObject.fromJSONObject(result));
                    } catch (JSONException e) {
                    }
                }

                @Override
                public void onSessionEnding(Session session) {
                }

                @Override
                public void onSessionResumeFailed(Session session, int error) {
                }

                @Override
                public void onSessionResumed(Session session, boolean wasSuspended) {
                    try {
                        JSONObject result = new JSONObject();
                        result.put("isConnected", session.isConnected());
                        result.put("wasSuspended", wasSuspended);
                        sendEvent("SESSION_RESUMED", JSObject.fromJSONObject(result));
                    } catch (JSONException e) {
                    }
                }

                @Override
                public void onSessionResuming(Session session, String sessionId) {
                }

                @Override
                public void onSessionStartFailed(Session session, int error) {
                    try {
                        JSONObject result = new JSONObject();
                        result.put("isConnected", session.isConnected());
                        result.put("error", error);
                        sendEvent("SESSION_START_FAILED", JSObject.fromJSONObject(result));
                    } catch (JSONException e) {
                    }
                }

                @Override
                public void onSessionStarting(Session session) {
                }

                @Override
                public void onSessionSuspended(Session session, int reason) {
                }

                @Override
                public void onSessionRejoin(JSONObject jsonSession) {
                    try {
                        sendEvent("SESSION_LISTENER", JSObject.fromJSONObject(jsonSession));
                    } catch (JSONException e) {
                    }
                }

                @Override
                public void onSessionUpdate(JSONObject jsonSession) {
                    try {
                        sendEvent("SESSION_UPDATE", JSObject.fromJSONObject(jsonSession));
                    } catch (JSONException e) {
                    }
                }

                @Override
                public void onSessionEnd(JSONObject jsonSession) {
                    onSessionUpdate(jsonSession);
                }

                @Override
                public void onReceiverAvailableUpdate(boolean available) {
                    sendEvent("RECEIVER_LISTENER", new JSObject().put("isAvailable", available));
                }

                @Override
                public void onMediaLoaded(JSONObject jsonMedia) {
                    try {
                        sendEvent("MEDIA_LOAD", JSObject.fromJSONObject(jsonMedia));
                    } catch (JSONException e) {
                    }
                }

                @Override
                public void onMediaUpdate(JSONObject jsonMedia) {
                    try {
                        if (jsonMedia != null) {
                            sendEvent("MEDIA_UPDATE", JSObject.fromJSONObject(jsonMedia));
                        }
                    } catch (JSONException e) {
                    }
                }

                @Override
                public void onMessageReceived(CastDevice device, String namespace, String message) {
                    sendEvent("RECEIVER_MESSAGE", new JSObject()
                            .put(device.getDeviceId(), new JSObject()
                                    .put("namespace", namespace)
                                    .put("message", message)));
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Error initializing Chromecast connection: " + e.getMessage());
            noChromecastError = "Could not initialize chromecast: " + e.getMessage();
            e.printStackTrace();
        }

        connection.initialize(appId, pluginCall);
    }

    /**
     * Request the session for the previously sent appId.
     * This launches the Chromecast picker dialog.
     */
    @PluginMethod
    public void requestSession(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        connection.requestSession(new ChromecastConnection.RequestSessionCallback() {
            @Override
            public void onJoin(JSONObject jsonSession) {
                try {
                    pluginCall.resolve(JSObject.fromJSONObject(jsonSession));
                } catch (JSONException e) {
                    pluginCall.reject("json_parse_error", e);
                }
            }

            @Override
            public void onError(int errorCode) {
                pluginCall.reject("session_error");
            }

            @Override
            public void onCancel() {
                pluginCall.reject("cancel");
            }
        });
    }

    /**
     * Selects a route by its id.
     */
    @PluginMethod
    public void selectRoute(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        String routeId = pluginCall.getString("routeId");
        connection.selectRoute(routeId, new ChromecastConnection.SelectRouteCallback() {
            @Override
            public void onJoin(JSONObject jsonSession) {
                try {
                    pluginCall.resolve(JSObject.fromJSONObject(jsonSession));
                } catch (JSONException e) {
                    pluginCall.reject("json_parse_error", e);
                }
            }

            @Override
            public void onError(JSONObject message) {
                try {
                    pluginCall.resolve(JSObject.fromJSONObject(message));
                } catch (JSONException e) {
                    pluginCall.reject("json_parse_error", e);
                }
            }
        });
    }

    /**
     * Loads media on the Chromecast using the media APIs.
     */
    @PluginMethod
    public void loadMedia(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        String contentId = pluginCall.getString("contentId");
        JSObject customData = pluginCall.getObject("customData", new JSObject());
        String contentType = pluginCall.getString("contentType", "");
        Integer duration = pluginCall.getInt("duration", 0);
        String streamType = pluginCall.getString("streamType", "");
        Boolean autoPlay = pluginCall.getBoolean("autoPlay", true);
        Double currentTime = pluginCall.getDouble("currentTime", 0.0);
        JSObject metadata = pluginCall.getObject("metadata", new JSObject());
        JSObject textTrackStyle = pluginCall.getObject("textTrackStyle", new JSObject());

        this.connection.getChromecastSession().loadMedia(
                contentId, customData, contentType, duration, streamType,
                autoPlay, currentTime, metadata, textTrackStyle, pluginCall);
    }

    /**
     * Play the current media.
     */
    @PluginMethod
    public void mediaPlay(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        connection.getChromecastSession().mediaPlay(pluginCall);
    }

    /**
     * Pause the current media.
     */
    @PluginMethod
    public void mediaPause(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        connection.getChromecastSession().mediaPause(pluginCall);
    }

    /**
     * Seek the current media.
     */
    @PluginMethod
    public void mediaSeek(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        Integer seekTime = pluginCall.getInt("position", 0);
        String resumeState = pluginCall.getString("resumeState", "PLAYBACK_UNCHANGED");
        connection.getChromecastSession().mediaSeek(seekTime.longValue() * 1000, resumeState, pluginCall);
    }

    /**
     * Stop the current media.
     */
    @PluginMethod
    public void mediaStop(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        connection.getChromecastSession().mediaStop(pluginCall);
    }

    /**
     * Set the receiver volume level.
     */
    @PluginMethod
    public void setReceiverVolumeLevel(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        Double level = pluginCall.getDouble("level", 1.0);
        connection.getChromecastSession().setVolume(level, pluginCall);
    }

    /**
     * Set the receiver muted state.
     */
    @PluginMethod
    public void setReceiverMuted(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        Boolean muted = pluginCall.getBoolean("muted", false);
        connection.getChromecastSession().setMute(muted, pluginCall);
    }

    /**
     * Set the media volume level and/or mute state.
     */
    @PluginMethod
    public void setMediaVolume(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        Double level = pluginCall.getDouble("level");
        Boolean muted = pluginCall.getBoolean("muted");
        connection.getChromecastSession().mediaSetVolume(level, muted, pluginCall);
    }

    /**
     * Send a custom message to the receiver.
     */
    @PluginMethod
    public void sendMessage(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        String namespace = pluginCall.getString("namespace");
        String message = pluginCall.getString("message");

        ChromecastSession session = connection.getChromecastSession();
        if (session == null) {
            pluginCall.reject("session_error");
            return;
        }

        session.sendMessage(namespace, message, new ResultCallback<Status>() {
            @Override
            public void onResult(Status result) {
                JSObject returnObj = new JSObject();
                if (result.isSuccess()) {
                    returnObj.put("success", true);
                    pluginCall.resolve(returnObj);
                } else {
                    returnObj.put("success", false);
                    returnObj.put("error", result.getStatus().toString());
                    pluginCall.resolve(returnObj);
                }
            }
        });
    }

    /**
     * Add a message listener for a namespace.
     */
    @PluginMethod
    public void addMessageListener(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        String namespace = pluginCall.getString("namespace");
        connection.getChromecastSession().addMessageListener(namespace);
        pluginCall.resolve();
    }

    /**
     * Stop the session (and stop casting).
     */
    @PluginMethod
    public void sessionStop(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        connection.endSession(true, pluginCall);
    }

    /**
     * Leave the session (keep casting, just disconnect this sender).
     */
    @PluginMethod
    public void sessionLeave(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        connection.endSession(false, pluginCall);
    }

    /**
     * Start scanning for available cast routes.
     */
    @PluginMethod(returnType = PluginMethod.RETURN_CALLBACK)
    public void startRouteScan(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        pluginCall.setKeepAlive(true);

        if (scanPluginCall != null) {
            scanPluginCall.reject("Started a new route scan before stopping previous one.");
        }
        scanPluginCall = pluginCall;
        Runnable startScan = new Runnable() {
            @Override
            public void run() {
                clientScan = new ChromecastConnection.ScanCallback() {
                    @Override
                    void onRouteUpdate(List<MediaRouter.RouteInfo> routes) {
                        if (scanPluginCall != null) {
                            JSObject ret = new JSObject();
                            JSArray retArr = new JSArray();
                            for (int i = 0; i < routes.size(); i++) {
                                JSObject route = new JSObject();
                                route.put("id", routes.get(i).getId());
                                route.put("name", routes.get(i).getName());
                                route.put("description", routes.get(i).getDescription());
                                route.put("isSelected", routes.get(i).isSelected());
                                retArr.put(route);
                            }
                            ret.put("routes", retArr);
                            scanPluginCall.resolve(ret);
                        } else {
                            connection.stopRouteScan(clientScan, null);
                        }
                    }
                };
                connection.startRouteScan(null, clientScan, null);
            }
        };
        if (clientScan != null) {
            connection.stopRouteScan(clientScan, startScan);
        } else {
            startScan.run();
        }
    }

    /**
     * Stop the route scan.
     */
    @PluginMethod
    public void stopRouteScan(final PluginCall pluginCall) {
        if (checkNotInitialized(pluginCall)) return;
        connection.stopRouteScan(clientScan, new Runnable() {
            @Override
            public void run() {
                if (scanPluginCall != null) {
                    scanPluginCall.reject("Scan stopped.");
                    scanPluginCall = null;
                }
                pluginCall.resolve();
            }
        });
    }

    /**
     * Cleanup when the plugin is destroyed.
     */
    @Override
    protected void handleOnDestroy() {
        if (connection != null) {
            connection.stopRouteScan(clientScan, null);
        }
        scanPluginCall = null;
        clientScan = null;
    }

    /* ------------------------------------   HELPERS  ---------------------------------------------- */

    private boolean setup() {
        if (this.connection != null) {
            connection.stopRouteScan(clientScan, new Runnable() {
                @Override
                public void run() {
                    if (scanPluginCall != null) {
                        scanPluginCall.reject("Scan stopped because setup triggered.");
                        scanPluginCall = null;
                    }
                    sendEvent("SETUP", new JSObject());
                }
            });
        }
        return true;
    }

    private boolean checkNotInitialized(PluginCall pluginCall) {
        if (noChromecastError != null) {
            pluginCall.reject(noChromecastError);
            return true;
        }
        if (connection == null) {
            pluginCall.reject("Not initialized. Call initialize() first.");
            return true;
        }
        return false;
    }

    private void sendEvent(String eventName, JSObject args) {
        notifyListeners(eventName, args);
    }
}
