import { WebPlugin } from '@capacitor/core';

import type { ChromecastPlugin } from './definitions';

export class ChromecastWeb extends WebPlugin implements ChromecastPlugin {
  async initialize(): Promise<void> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async requestSession(): Promise<any> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async selectRoute(): Promise<any> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async loadMedia(): Promise<void> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async mediaPlay(): Promise<void> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async mediaPause(): Promise<void> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async mediaSeek(): Promise<void> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async mediaStop(): Promise<void> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async setReceiverVolumeLevel(): Promise<void> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async setReceiverMuted(): Promise<void> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async setMediaVolume(): Promise<void> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async sendMessage(): Promise<any> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async addMessageListener(): Promise<void> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async sessionStop(): Promise<void> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async sessionLeave(): Promise<void> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async startRouteScan(): Promise<string> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
  async stopRouteScan(): Promise<void> {
    throw this.unimplemented('Chromecast is not supported on web.');
  }
}
