export interface Environment {
  production: boolean;
  sessionId: string;
  losWsUrl: string;
  skaleupWsUrl: string;
  thinkingAfterMs: number;
}

export const environment: Environment = {
  production: false,
  sessionId: 'session-chandan',
  losWsUrl: 'ws://127.0.0.1:8080/ws',
  skaleupWsUrl: 'ws://127.0.0.1:8000/ws',
  thinkingAfterMs: 4000
};