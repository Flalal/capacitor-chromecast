package com.caprockapps.plugins.chromecast;

import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaSeekOptions;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.cast.framework.media.RemoteMediaClient.MediaChannelResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import android.app.Activity;

import androidx.annotation.NonNull;

/*
 * All of the Chromecast session specific functions should start here.
 */
public class ChromecastSession {
    /** The current context. */
    private Activity activity;
    /** A registered callback that we will un-register and re-register each time the session changes. */
    private Listener clientListener;
    /** The current session. */
    private CastSession session;
    /** The current session's client for controlling playback. */
    private RemoteMediaClient client;
    /** Indicates whether we are requesting media or not. **/
    private boolean requestingMedia = false;
    /** Handles and used to trigger queue updates. **/
    private MediaQueueController mediaQueueCallback;
    /** Stores a callback that should be called when the queue is loaded. **/
    private Runnable queueReloadCallback;
    /** Stores a callback that should be called when the queue status is updated. **/
    private Runnable queueStatusUpdatedCallback;

    /**
     * ChromecastSession constructor.
     * @param act the current activity
     * @param listener callback that will notify of certain events
     */
    public ChromecastSession(Activity act, @NonNull Listener listener) {
        this.activity = act;
        this.clientListener = listener;
    }

    /**
     * Sets the session object the will be used for other commands in this class.
     * @param castSession the session to use
     */
    public void setSession(final CastSession castSession) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                if (castSession == null) {
                    client = null;
                    return;
                }
                if (castSession.equals(session)) {
                    return;
                }
                session = castSession;
                client = session.getRemoteMediaClient();
                if (client == null) {
                    return;
                }
                setupQueue();
                // Periodic progress updates (every 1s) for UI progress bar
                client.addProgressListener(new RemoteMediaClient.ProgressListener() {
                    @Override
                    public void onProgressUpdated(long progressMs, long durationMs) {
                        clientListener.onMediaUpdate(createMediaObject());
                    }
                }, 1000);

                client.registerCallback(new RemoteMediaClient.Callback() {
                    private Integer prevItemId;
                    @Override
                    public void onStatusUpdated() {
                        final MediaStatus status = client.getMediaStatus();
                        if (requestingMedia
                                || queueStatusUpdatedCallback != null
                                || queueReloadCallback != null) {
                            return;
                        }

                        if (status != null) {
                            if (prevItemId == null) {
                                prevItemId = status.getCurrentItemId();
                            }
                            boolean shouldSkipUpdate = false;
                            if (status.getPlayerState() == MediaStatus.PLAYER_STATE_LOADING) {
                                clientListener.onMediaUpdate(createMediaObject(MediaStatus.IDLE_REASON_FINISHED));
                                shouldSkipUpdate = true;
                            }
                            if (prevItemId != null && prevItemId != status.getCurrentItemId() && mediaQueueCallback.getCurrentItemIndex() != -1) {
                                setQueueReloadCallback(new Runnable() {
                                    @Override
                                    public void run() {
                                        prevItemId = status.getCurrentItemId();
                                    }
                                });
                                mediaQueueCallback.refreshQueueItems();
                                shouldSkipUpdate = true;
                            }
                            if (shouldSkipUpdate) {
                                return;
                            }
                        }
                        clientListener.onMediaUpdate(createMediaObject());
                    }
                    @Override
                    public void onQueueStatusUpdated() {
                        if (queueStatusUpdatedCallback != null) {
                            queueStatusUpdatedCallback.run();
                            setQueueStatusUpdatedCallback(null);
                        }
                    }
                });
                session.addCastListener(new Cast.Listener() {
                    @Override
                    public void onApplicationStatusChanged() {
                        clientListener.onSessionUpdate(createSessionObject());
                    }
                    @Override
                    public void onApplicationMetadataChanged(ApplicationMetadata appMetadata) {
                        clientListener.onSessionUpdate(createSessionObject());
                    }
                    @Override
                    public void onApplicationDisconnected(int i) {
                        clientListener.onSessionEnd(
                                ChromecastUtilities.createSessionObject(session, "stopped"));
                    }
                    @Override
                    public void onActiveInputStateChanged(int i) {
                        clientListener.onSessionUpdate(createSessionObject());
                    }
                    @Override
                    public void onStandbyStateChanged(int i) {
                        clientListener.onSessionUpdate(createSessionObject());
                    }
                    @Override
                    public void onVolumeChanged() {
                        clientListener.onSessionUpdate(createSessionObject());
                    }
                });
            }
        });
    }

    /**
     * Adds a message listener if one does not already exist.
     * @param namespace namespace
     */
    public void addMessageListener(final String namespace) {
        if (client == null || session == null) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    session.setMessageReceivedCallbacks(namespace, clientListener);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Sends a message to a specified namespace.
     * @param namespace namespace
     * @param message the message to send
     * @param callback called with success or error
     */
    public void sendMessage(final String namespace, final String message, final ResultCallback<Status> callback) {
        if (client == null || session == null) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                session.sendMessage(namespace, message).setResultCallback(callback);
            }
        });
    }

/* ------------------------------------   MEDIA FNs   ------------------------------------------- */

    /**
     * Loads media over the media API.
     * @param contentId      - The URL of the content
     * @param customData     - CustomData
     * @param contentType    - The MIME type of the content
     * @param duration       - The length of the video (if known)
     * @param streamType     - The stream type
     * @param autoPlay       - Whether or not to start the video playing or not
     * @param currentTime    - Where in the video to begin playing from
     * @param metadata       - Metadata
     * @param textTrackStyle - The text track style
     * @param callback called with success or error
     */
    public void loadMedia(final String contentId, final JSONObject customData, final String contentType, final long duration, final String streamType, final boolean autoPlay, final double currentTime, final JSONObject metadata, final JSONObject textTrackStyle, final PluginCall callback) {
        if (client == null || session == null) {
            callback.reject("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                MediaInfo mediaInfo = ChromecastUtilities.createMediaInfo(contentId, customData, contentType, duration, streamType, metadata, textTrackStyle);
                MediaLoadRequestData loadRequest = new MediaLoadRequestData.Builder()
                        .setMediaInfo(mediaInfo)
                        .setAutoplay(autoPlay)
                        .setCurrentTime((long) currentTime * 1000)
                        .build();

                requestingMedia = true;
                setQueueReloadCallback(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            callback.resolve(JSObject.fromJSONObject(createMediaObject()));
                        } catch (JSONException e) {
                            callback.reject(e.getMessage(), e);
                        }
                    }
                });
                client.load(loadRequest).setResultCallback(new ResultCallback<MediaChannelResult>() {
                    @Override
                    public void onResult(@NonNull MediaChannelResult result) {
                        requestingMedia = false;
                        if (!result.getStatus().isSuccess()) {
                            callback.reject("session_error");
                            setQueueReloadCallback(null);
                        }
                    }
                });
            }
        });
    }

    /**
     * Media API - Calls play on the current media.
     * @param callback called with success or error
     */
    public void mediaPlay(final PluginCall callback) {
        if (client == null || session == null) {
            callback.reject("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                client.play()
                        .setResultCallback(getResultCallback(callback, "Failed to play."));
            }
        });
    }

    /**
     * Media API - Calls pause on the current media.
     * @param callback called with success or error
     */
    public void mediaPause(final PluginCall callback) {
        if (client == null || session == null) {
            callback.reject("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                client.pause()
                        .setResultCallback(getResultCallback(callback, "Failed to pause."));
            }
        });
    }

    /**
     * Media API - Seeks the current playing media.
     * @param seekPosition - Milliseconds to seek to
     * @param resumeState  - Resume state once seeking is complete: PLAYBACK_PAUSE or PLAYBACK_START
     * @param callback called with success or error
     */
    public void mediaSeek(final long seekPosition, final String resumeState, final PluginCall callback) {
        if (client == null || session == null) {
            callback.reject("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                int resState;
                switch (resumeState) {
                    case "PLAYBACK_START":
                        resState = MediaSeekOptions.RESUME_STATE_PLAY;
                        break;
                    case "PLAYBACK_PAUSE":
                        resState = MediaSeekOptions.RESUME_STATE_PAUSE;
                        break;
                    default:
                        resState = MediaSeekOptions.RESUME_STATE_UNCHANGED;
                }

                client.seek(new MediaSeekOptions.Builder()
                        .setPosition(seekPosition)
                        .setResumeState(resState)
                        .build()
                ).setResultCallback(getResultCallback(callback, "Failed to seek."));
            }
        });
    }

    /**
     * Media API - Sets the volume on the current playing media object, NOT ON THE CHROMECAST DIRECTLY.
     * @param level the level to set the volume to
     * @param muted if true set the media to muted, else, unmute
     * @param callback called with success or error
     */
    public void mediaSetVolume(final Double level, final Boolean muted, final PluginCall callback) {
        if (client == null || session == null) {
            callback.reject("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                int calls = 0;
                if (level != null) {
                    calls++;
                }
                if (muted != null) {
                    calls++;
                }
                if (calls == 0) {
                    callback.resolve();
                    return;
                }

                final int expectedCalls = calls;
                ResultCallback<MediaChannelResult> cb = new ResultCallback<MediaChannelResult>() {
                    private int callsCompleted = 0;
                    private String finalErr = null;
                    private void completionCall() {
                        callsCompleted++;
                        if (callsCompleted >= expectedCalls) {
                            if (finalErr != null) {
                                callback.reject(finalErr);
                            } else {
                                callback.resolve();
                            }
                        }
                    }
                    @Override
                    public void onResult(@NonNull MediaChannelResult result) {
                        if (!result.getStatus().isSuccess()) {
                            if (finalErr == null) {
                                finalErr = "Failed to set media volume/mute state:\n";
                            }
                            JSONObject errorResult = result.getCustomData();
                            if (errorResult != null) {
                                finalErr += "\n" + errorResult;
                            }
                        }
                        completionCall();
                    }
                };

                if (level != null) {
                    client.setStreamVolume(level)
                            .setResultCallback(cb);
                }
                if (muted != null) {
                    client.setStreamMute(muted)
                            .setResultCallback(cb);
                }
            }
        });
    }

    /**
     * Media API - Stops and unloads the current playing media.
     * @param callback called with success or error
     */
    public void mediaStop(final PluginCall callback) {
        if (client == null || session == null) {
            callback.reject("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                client.stop()
                        .setResultCallback(getResultCallback(callback, "Failed to stop."));
            }
        });
    }

    /**
     * Handle track changed.
     * @param activeTracksIds active track ids
     * @param textTrackStyle track style
     * @param callback called with success or error
     */
    public void mediaEditTracksInfo(final long[] activeTracksIds, final JSONObject textTrackStyle, final PluginCall callback) {
        if (client == null || session == null) {
            callback.reject("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                client.setActiveMediaTracks(activeTracksIds)
                        .setResultCallback(getResultCallback(callback, "Failed to set active media tracks."));
                client.setTextTrackStyle(ChromecastUtilities.parseTextTrackStyle(textTrackStyle))
                        .setResultCallback(getResultCallback(callback, "Failed to set text track style."));
            }
        });
    }

/* ------------------------------------   QUEUE FNs   ------------------------------------------- */

    private void setQueueReloadCallback(Runnable callback) {
        this.queueReloadCallback = callback;
    }

    private void setQueueStatusUpdatedCallback(Runnable callback) {
        this.queueStatusUpdatedCallback = callback;
    }

    /**
     * Sets up the objects and listeners required for queue functionality.
     */
    private void setupQueue() {
        MediaQueue queue = client.getMediaQueue();
        setQueueReloadCallback(null);
        mediaQueueCallback = new MediaQueueController(queue);
        queue.registerCallback(mediaQueueCallback);
    }

    private class MediaQueueController extends MediaQueue.Callback {
        private MediaQueue queue;
        private ArrayList<Integer> lookingForIndexes = new ArrayList<Integer>();
        private JSONArray queueItems;

        MediaQueueController(MediaQueue q) {
            this.queue = q;
        }

        void refreshQueueItems() {
            int len = queue.getItemIds().length;
            int index = getCurrentItemIndex();
            lookingForIndexes = new ArrayList<>();
            if (index != -1) {
                for (int i = index - 1; i <= index + 1; i++) {
                    if (i >= 0 && i < len) {
                        lookingForIndexes.add(i);
                    }
                }
            }
            checkLookingForIndexes();
        }
        private int getCurrentItemIndex() {
            return queue.indexOfItemWithId(client.getMediaStatus().getCurrentItemId());
        }
        private void checkLookingForIndexes() {
            queueItems = new JSONArray();
            MediaQueueItem item;
            boolean foundAllIndexes = true;
            for (int index : lookingForIndexes) {
                item = queue.getItemAtIndex(index, true);
                if (item != null) {
                    queueItems.put(ChromecastUtilities.createQueueItem(item, index));
                } else {
                    foundAllIndexes = false;
                }
            }
            if (foundAllIndexes) {
                lookingForIndexes.clear();
                updateFinished();
            }
        }
        private void updateFinished() {
            ChromecastUtilities.setQueueItems(queueItems);
            if (queueReloadCallback != null && queue.getItemCount() > 0) {
                queueReloadCallback.run();
                setQueueReloadCallback(null);
            }
            clientListener.onMediaUpdate(createMediaObject());
        }

        @Override
        public void itemsReloaded() {
            synchronized (queue) {
                int itemCount = queue.getItemCount();
                if (itemCount == 0) {
                    return;
                }
                if (queueReloadCallback == null) {
                    setQueueReloadCallback(new Runnable() {
                        @Override
                        public void run() {
                            clientListener.onMediaLoaded(createMediaObject());
                        }
                    });
                }
                refreshQueueItems();
            }
        }
        @Override
        public void itemsUpdatedAtIndexes(int[] ints) {
            synchronized (queue) {
                for (int i = 0; i < ints.length; i++) {
                    if (lookingForIndexes.indexOf(ints[i]) == -1) {
                        refreshQueueItems();
                        return;
                    }
                }
                checkLookingForIndexes();
            }
        }
        @Override
        public void itemsInsertedInRange(int startIndex, int insertCount) {
            synchronized (queue) {
                refreshQueueItems();
            }
        }
        @Override
        public void itemsRemovedAtIndexes(int[] ints) {
            synchronized (queue) {
                refreshQueueItems();
            }
        }
    };

    /**
     * Loads a queue of media to the Chromecast.
     * @param queueLoadRequest chrome.cast.media.QueueLoadRequest
     * @param callback called with success or error
     */
    public void queueLoad(final JSONObject queueLoadRequest, final PluginCall callback) {
        if (client == null || session == null) {
            callback.reject("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    JSONArray qItems = queueLoadRequest.getJSONArray("items");
                    MediaQueueItem[] items = new MediaQueueItem[qItems.length()];
                    for (int i = 0; i < qItems.length(); i++) {
                        items[i] = ChromecastUtilities.createMediaQueueItem(qItems.getJSONObject(i));
                    }

                    int startIndex = queueLoadRequest.getInt("startIndex");
                    int repeatMode = ChromecastUtilities.getAndroidRepeatMode(queueLoadRequest.getString("repeatMode"));
                    long playPosition = Double.valueOf(items[startIndex].getStartTime() * 1000).longValue();
                    JSONObject customData = null;
                    try {
                        customData = queueLoadRequest.getJSONObject("customData");
                    } catch (JSONException e) {
                    }

                    setQueueReloadCallback(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                callback.resolve(JSObject.fromJSONObject(createMediaObject()));
                            } catch (JSONException e) {
                                callback.reject(e.getMessage(), e);
                            }
                        }
                    });
                    client.queueLoad(items, startIndex, repeatMode, playPosition, customData).setResultCallback(new ResultCallback<MediaChannelResult>() {
                        @Override
                        public void onResult(@NonNull MediaChannelResult result) {
                            if (!result.getStatus().isSuccess()) {
                                callback.reject("session_error");
                                setQueueReloadCallback(null);
                            }
                        }
                    });
                } catch (JSONException e) {
                    callback.reject("invalid_parameter: " + e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Plays the item with itemId in the queue.
     * @param itemId The ID of the item to jump to.
     * @param callback called with .success or .error depending on the result
     */
    public void queueJumpToItem(final Integer itemId, final PluginCall callback) {
        if (client == null || session == null) {
            callback.reject("session_error");
            return;
        }

        activity.runOnUiThread(new Runnable() {
            public void run() {
                setQueueStatusUpdatedCallback(new Runnable() {
                    @Override
                    public void run() {
                        clientListener.onMediaUpdate(createMediaObject(MediaStatus.IDLE_REASON_INTERRUPTED));
                    }
                });
                client.queueJumpToItem(itemId, null)
                        .setResultCallback(new ResultCallback<MediaChannelResult>() {
                    @Override
                    public void onResult(@NonNull MediaChannelResult result) {
                        if (result.getStatus().isSuccess()) {
                            callback.resolve();
                        } else {
                            setQueueStatusUpdatedCallback(null);
                            JSONObject errorResult = result.getCustomData();
                            String error = "Failed to jump to queue item with ID: " + itemId;
                            if (errorResult != null) {
                                error += "\nError details: " + errorResult;
                            }
                            callback.reject(error);
                        }
                    }
                });
            }
        });
    }

/* ------------------------------------   SESSION FNs ------------------------------------------- */

    /**
     * Sets the receiver volume level.
     * @param volume volume to set the receiver to
     * @param callback called with success or error
     */
    public void setVolume(final double volume, final PluginCall callback) {
        if (client == null || session == null) {
            callback.reject("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    session.setVolume(volume);
                    callback.resolve();
                } catch (IOException e) {
                    callback.reject("CHANNEL_ERROR");
                }
            }
        });
    }

    /**
     * Mutes the receiver.
     * @param muted if true mute, else, unmute
     * @param callback called with success or error
     */
    public void setMute(final boolean muted, final PluginCall callback) {
        if (client == null || session == null) {
            callback.reject("session_error");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    session.setMute(muted);
                    callback.resolve();
                } catch (IOException e) {
                    callback.reject("CHANNEL_ERROR");
                }
            }
        });
    }

/* ------------------------------------   HELPERS  ---------------------------------------------- */

    /**
     * Returns a resultCallback that wraps the callback and calls the onMediaUpdate listener.
     * @param callback client callback
     * @param errorMsg error message if failure
     * @return a callback for use in PendingResult.setResultCallback()
     */
    private ResultCallback<MediaChannelResult> getResultCallback(final PluginCall callback, final String errorMsg) {
        return new ResultCallback<MediaChannelResult>() {
            @Override
            public void onResult(@NonNull MediaChannelResult result) {
                if (result.getStatus().isSuccess()) {
                    callback.resolve();
                } else {
                    JSONObject errorResult = result.getCustomData();
                    String error = errorMsg;
                    if (errorResult != null) {
                        error += "\nError details: " + errorResult;
                    }
                    callback.reject(error);
                }
            }
        };
    }

    private JSONObject createSessionObject() {
        return ChromecastUtilities.createSessionObject(session);
    }

    /** Last sent media object. **/
    private JSONObject lastMediaObject;
    private JSONObject createMediaObject() {
        return createMediaObject(null);
    }

    private JSONObject createMediaObject(Integer idleReason) {
        if (idleReason != null && lastMediaObject != null) {
            try {
                lastMediaObject.put("playerState", ChromecastUtilities.getMediaPlayerState(MediaStatus.PLAYER_STATE_IDLE));
                lastMediaObject.put("idleReason", ChromecastUtilities.getMediaIdleReason(idleReason));
                return lastMediaObject;
            } catch (JSONException e) {
            }
        }
        JSONObject out = ChromecastUtilities.createMediaObject(session);
        lastMediaObject = out;
        return out;
    }

    interface Listener extends Cast.MessageReceivedCallback {
        void onMediaLoaded(JSONObject jsonMedia);
        void onMediaUpdate(JSONObject jsonMedia);
        void onSessionUpdate(JSONObject jsonSession);
        void onSessionEnd(JSONObject jsonSession);
    }
}
