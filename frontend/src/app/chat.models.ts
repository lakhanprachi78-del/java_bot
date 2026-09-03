export type MessageType =
  | 'chat'
  | 'chat.direct'
  | 'greet'
  | 'chat.reset'
  | 'feedback'
  | 'feedback.detail'
  | 'health';

export interface WsMessage<T = any> {
  type: MessageType;
  payload: T;
}

export type BotSource = 'los' | 'rag';

export enum FlowState {
  INITIAL = 'initial',
  MAIN_MENU = 'main_menu',
  QUERY_TYPE = 'query_type',
  AWAITING_INPUT = 'awaiting_input',
  FREE_CHAT = 'free_chat',
  STATUS_RESULTS = 'status_results',
  SKALEUP_CHAT = 'skaleup_chat'
}

export interface QueryTypeDef {
  label: string;
  prompt: string;
  placeholder: string;
  buildMessage: (value: string) => string;
  validate?: (value: string) => string | null;
}

export interface ChatAction {
  label: string;
  onClick: () => void;
  primary?: boolean;
  disabled?: boolean;
}

export interface ChatMessage {
  id: string;
  type: 'bot' | 'user';
  text: string;
  timestamp: string;
  isError?: boolean;
  actions?: ChatAction[];
  queryId?: number | null;
  queryText?: string | null;
  answerText?: string | null;
  source?: BotSource;
  feedbackGiven?: boolean;
}

export const RELATIVE_DATE_PATTERN =
  /^(today|yesterday|tomorrow|this week|last week|this month|last month|this year|last year)$/i;

export const DATE_LOOKING_PATTERN =
  /\d{1,4}[/-]\d{1,2}[/-]\d{1,4}|\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\b/i;

export const QUERY_TYPES: Record<string, QueryTypeDef> = {
  applicant_name: {
    label: 'Applicant Name',
    prompt: 'Enter applicant name',
    placeholder: 'e.g. Rahul Sharma',
    buildMessage: (value) => `Show applications for applicant name: ${value}`,
    validate: (value) => {
      const v = value.trim();
      if (RELATIVE_DATE_PATTERN.test(v) || DATE_LOOKING_PATTERN.test(v)) {
        return `"${value}" looks like a date, not an applicant name. Please enter the applicant's name — or tap "⬅ Back to main menu" to search by date instead.`;
      }
      if (!/^[A-Za-z][A-Za-z .'-]{1,80}$/.test(v)) {
        return "That doesn't look like a valid name. Please enter the applicant's name using letters only.";
      }
      return null;
    }
  },
  applicant_email: {
    label: 'Applicant Email',
    prompt: 'Enter applicant email',
    placeholder: 'e.g. rahul.sharma@example.com',
    buildMessage: (value) => `Show applications for applicant email: ${value}`,
    validate: (value) => {
      const v = value.trim();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)) {
        return "That doesn't look like a valid email address. Please enter the applicant's email, e.g. rahul.sharma@example.com.";
      }
      return null;
    }
  },
  status: {
    label: 'Status of Application',
    prompt: 'Please enter the application status',
    placeholder: 'e.g. credit assessment, pre-login',
    buildMessage: (value) => `Show applications with status: ${value}`
  },
  date_time: {
    label: 'Time / Date',
    prompt: 'Please enter a date or time period',
    placeholder: 'e.g. today, 2024-08-04, last week',
    buildMessage: (value) => `Find applications created on or around: ${value}`,
    validate: (value) => {
      const v = value.trim();
      const looksLikeDate =
        RELATIVE_DATE_PATTERN.test(v) || DATE_LOOKING_PATTERN.test(v) || /\d/.test(v);
      if (!looksLikeDate) {
        return `"${value}" doesn't look like a date or time period. Try something like "today", "2024-08-04", or "last week".`;
      }
      return null;
    }
  },
  application_id: {
    label: 'Application ID / LAF ID',
    prompt: 'Please enter Application ID or LAF ID',
    placeholder: 'e.g. APP-2024-00123',
    buildMessage: (value) => `application_id:${value}`,
    validate: (value) => {
      const v = value.trim();
      if (!/^[A-Za-z0-9-]{5,30}$/.test(v)) {
        return "That doesn't look like a valid Application ID / LAF ID. It should be a short alphanumeric code, e.g. LUGL637520260810103918.";
      }
      return null;
    }
  },
  other: {
    label: 'Other',
    prompt: "Please describe what you'd like to know about your application",
    placeholder: 'Type your question…',
    buildMessage: (value) => value
  }
};

export const STATUS_OPTIONS: string[] = [
  'Pre-login',
  'Pre-login Review',
  'Pre Login Discrepant',
  'Sales Discrepant',
  'Credit Assessment',
  'Pre-Disbursement',
  'Approved',
  'Rejected',
  'Sent to LMS'
];

export const SKALEUP_SUGGESTED_QUESTIONS: string[] = [
  'What is GRO Score?',
  'What documents are required for SkaleUp?',
  'Explain Sales Journey'
];

export const FEEDBACK_TAGS: Record<BotSource, { positive: string[]; negative: string[] }> = {
  rag: {
    negative: ['Answer not valid', 'Incorrect', 'Incomplete', 'Outdated', 'Not relevant', 'Other'],
    positive: ['Accurate', 'Well explained', 'Fast & helpful', 'Other']
  },
  los: {
    negative: ['Wrong application', 'Missing/incomplete info', 'Outdated status', 'Access issue', 'Other'],
    positive: ['Accurate', 'Fast & helpful', 'Easy to find', 'Other']
  }
};