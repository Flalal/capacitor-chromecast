import type { PluginListenerHandle } from '@capacitor/core';

export interface ChromecastPlugin {
  /**
   * Initialize the Chromecast SDK with the given app ID.
   * Must be called before any other method.
   */
  initialize(options: InitializeOptions): Promise<void>;

  /**
   * Show the native route chooser dialog to select a Cast device.
   * Resolves with session info when a device is selected.
   */
  requestSession(): Promise<SessionResult>;

  /**
   * Select a specific route by its ID (programmatic connection).
   */
  selectRoute(options: { routeId: string }): Promise<SessionResult>;

  /**
   * Load media on the connected Chromecast.
   */
  loadMedia(options: LoadMediaOptions): Promise<void>;

  /** Play the current media. */
  mediaPlay(): Promise<void>;

  /** Pause the current media. */
  mediaPause(): Promise<void>;

  /** Seek to a position (in seconds). */
  mediaSeek(options: MediaSeekOptions): Promise<void>;

  /** Stop the current media. */
  mediaStop(): Promise<void>;

  /** Set the receiver volume level (0.0 to 1.0). */
  setReceiverVolumeLevel(options: { level: number }): Promise<void>;

  /** Set the receiver muted state. */
  setReceiverMuted(options: { muted: boolean }): Promise<void>;

  /** Set the media stream volume and/or mute state. */
  setMediaVolume(options: { level?: number; muted?: boolean }): Promise<void>;

  /** Send a custom message to the receiver on a namespace. */
  sendMessage(options: { namespace: string; message: string }): Promise<{ success: boolean; error?: string }>;

  /** Register a message listener for a namespace. */
  addMessageListener(options: { namespace: string }): Promise<void>;

  /** Stop the current Cast session. */
  sessionStop(): Promise<void>;

  /** Leave the current Cast session (keep casting, disconnect sender). */
  sessionLeave(): Promise<void>;

  /** Start scanning for available Cast routes. Returns routes via callback. */
  startRouteScan(callback: RouteScanCallback): Promise<string>;

  /** Stop the active route scan. */
  stopRouteScan(): Promise<void>;

  // Event listeners
  addListener(eventName: 'SESSION_STARTED', listenerFunc: (data: { isConnected: boolean; sessionId: string }) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'SESSION_ENDED', listenerFunc: (data: { isConnected: boolean; error: number }) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'SESSION_RESUMED', listenerFunc: (data: { isConnected: boolean; wasSuspended: boolean }) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'SESSION_START_FAILED', listenerFunc: (data: { isConnected: boolean; error: number }) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'SESSION_UPDATE', listenerFunc: (data: any) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'SESSION_LISTENER', listenerFunc: (data: any) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'RECEIVER_LISTENER', listenerFunc: (data: { isAvailable: boolean }) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'RECEIVER_MESSAGE', listenerFunc: (data: any) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'MEDIA_LOAD', listenerFunc: (data: any) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'MEDIA_UPDATE', listenerFunc: (data: any) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'SETUP', listenerFunc: () => void): Promise<PluginListenerHandle>;
  addListener(eventName: string, listenerFunc: (...args: any[]) => void): Promise<PluginListenerHandle>;
}

export interface InitializeOptions {
  /** Cast application ID. If omitted, uses the default media receiver. */
  appId?: string;
}

export interface SessionResult {
  isConnected?: boolean;
  sessionId?: string;
  [key: string]: any;
}

export interface LoadMediaOptions {
  /** The URL of the media to cast. */
  contentId: string;
  /** MIME type (e.g. "audio/mpeg", "video/mp4"). */
  contentType?: string;
  /** Duration in seconds. */
  duration?: number;
  /** Stream type: "buffered", "live", or "none". */
  streamType?: string;
  /** Whether to auto-play on load. Defaults to true. */
  autoPlay?: boolean;
  /** Start position in seconds. */
  currentTime?: number;
  /** Media metadata (title, artist, images, etc.). */
  metadata?: Record<string, any>;
  /** Custom data to send with the load request. */
  customData?: Record<string, any>;
  /** Text track style configuration. */
  textTrackStyle?: Record<string, any>;
}

export interface MediaSeekOptions {
  /** Position in seconds. */
  position: number;
  /** Resume state after seek: "PLAYBACK_START", "PLAYBACK_PAUSE", or "PLAYBACK_UNCHANGED". */
  resumeState?: string;
}

export interface RouteInfo {
  id: string;
  name: string;
  description: string;
  isSelected: boolean;
}

export type RouteScanCallback = (routes: RouteInfo[]) => void;
