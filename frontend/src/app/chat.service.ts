import { Injectable } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { environment } from './environment';
import { MessageType, WsMessage, BotSource } from './chat.models';

interface PendingRequest {
  resolve: (data: any) => void;
  reject: (err: any) => void;
  timer: any;
}

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private losWs: WebSocket | null = null;
  private skaleupWs: WebSocket | null = null;

  private pendingRequests: Map<string, PendingRequest> = new Map();

  private losRetryCount = 0;
  private skaleupRetryCount = 0;

  constructor() {
    this.connectLos();
    this.connectSkaleup();
  }

  private connectLos(): void {
    try {
      this.losWs = new WebSocket(environment.losWsUrl);

      this.losWs.onopen = () => {
        console.log('LOS WebSocket connected');
        this.losRetryCount = 0;
      };

      this.losWs.onmessage = (event) => {
        this.handleIncomingMessage('los', event.data);
      };

      this.losWs.onerror = (err) => {
        console.warn('LOS WebSocket error:', err);
      };

      this.losWs.onclose = () => {
        console.log('LOS WebSocket closed, scheduling reconnect...');
        this.scheduleReconnect('los');
      };
    } catch (e) {
      this.scheduleReconnect('los');
    }
  }

  private connectSkaleup(): void {
    try {
      this.skaleupWs = new WebSocket(environment.skaleupWsUrl);

      this.skaleupWs.onopen = () => {
        console.log('SkaleUp WebSocket connected');
        this.skaleupRetryCount = 0;
      };

      this.skaleupWs.onmessage = (event) => {
        this.handleIncomingMessage('rag', event.data);
      };

      this.skaleupWs.onerror = (err) => {
        console.warn('SkaleUp WebSocket error:', err);
      };

      this.skaleupWs.onclose = () => {
        console.log('SkaleUp WebSocket closed, scheduling reconnect...');
        this.scheduleReconnect('rag');
      };
    } catch (e) {
      this.scheduleReconnect('rag');
    }
  }

  private scheduleReconnect(source: BotSource): void {
    if (source === 'los') {
      const delay = Math.min(1000 * Math.pow(2, this.losRetryCount), 10000);
      this.losRetryCount++;
      setTimeout(() => this.connectLos(), delay);
    } else {
      const delay = Math.min(1000 * Math.pow(2, this.skaleupRetryCount), 10000);
      this.skaleupRetryCount++;
      setTimeout(() => this.connectSkaleup(), delay);
    }
  }

  private handleIncomingMessage(source: BotSource, rawData: string): void {
    try {
      const msg: WsMessage = JSON.parse(rawData);
      const key = `${source}:${msg.type}`;
      const pending = this.pendingRequests.get(key);
      if (pending) {
        clearTimeout(pending.timer);
        this.pendingRequests.delete(key);
        if (msg.payload && msg.payload.error) {
          pending.reject(new Error(typeof msg.payload.error === 'string' ? msg.payload.error : JSON.stringify(msg.payload.error)));
        } else {
          pending.resolve(msg.payload);
        }
      }
    } catch (e) {
      console.error('Failed to parse incoming WebSocket message:', e);
    }
  }

  private sendRequest(source: BotSource, type: MessageType, payload: any, timeoutMs = 35000): Promise<any> {
    return new Promise((resolve, reject) => {
      const ws = source === 'los' ? this.losWs : this.skaleupWs;
      if (!ws || ws.readyState !== WebSocket.OPEN) {
        return reject(new Error(`WebSocket for ${source.toUpperCase()} is not connected. Reconnecting...`));
      }

      const key = `${source}:${type}`;

      const timer = setTimeout(() => {
        if (this.pendingRequests.has(key)) {
          this.pendingRequests.delete(key);
          reject(new Error('Request timed out waiting for server response.'));
        }
      }, timeoutMs);

      this.pendingRequests.set(key, { resolve, reject, timer });

      const msg: WsMessage = { type, payload };
      ws.send(JSON.stringify(msg));
    });
  }

  // --- Public API Methods ---

  public greet(sessionId: string = environment.sessionId): Promise<any> {
    return this.sendRequest('los', 'greet', { session_id: sessionId });
  }

  public sendLosChat(message: string, sessionId: string = environment.sessionId): Promise<any> {
    return this.sendRequest('los', 'chat', { message, session_id: sessionId });
  }

  public sendLosChatDirect(field: string, value: string, offset: number = 0, sessionId: string = environment.sessionId): Promise<any> {
    return this.sendRequest('los', 'chat.direct', { session_id: sessionId, field, value, offset });
  }

  public sendSkaleUpChat(message: string, sessionId: string = environment.sessionId): Promise<any> {
    return this.sendRequest('rag', 'chat', { message, session_id: sessionId });
  }

  public sendFeedback(queryId: number, positive: boolean, source: BotSource): Promise<any> {
    return this.sendRequest(source, 'feedback', { query_id: queryId, positive }).catch(() => {
      // Fire-and-forget: ignore errors
    });
  }

  public sendFeedbackDetail(
    queryId: number | null,
    queryText: string | null,
    answerText: string | null,
    positive: boolean,
    tags: string[],
    comment: string | null,
    source: BotSource
  ): Promise<boolean> {
    return this.sendRequest(source, 'feedback.detail', {
      query_id: queryId,
      query_text: queryText,
      answer_text: answerText,
      positive,
      tags,
      comment
    })
      .then((res) => res && res.success)
      .catch(() => false);
  }
}