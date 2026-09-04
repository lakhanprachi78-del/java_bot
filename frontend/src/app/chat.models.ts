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

// A status category shown under "Find by Status".
// - If `subOptions` is present, clicking this category opens a second menu of leaf statuses.
// - If `subOptions` is absent, `value` IS the leaf status sent straight to the backend
//   (must match a group key in the backend's StatusGroups class).
export interface StatusCategory {
  label: string;
  value?: string;
  subOptions?: StatusCategory[];
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

// Object key order = menu button order shown under "Know About Your Application".
export const QUERY_TYPES: Record<string, QueryTypeDef> = {
  applicant_name: {
    label: 'Applicant Name',
    prompt: 'Enter Applicant Name',
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
  application_id: {
    label: 'Application ID / LAF ID',
    prompt: 'Please enter Application ID / LAF ID',
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
  date_time: {
    label: 'Find by Time/Date',
    prompt: 'Enter Date or Time',
    placeholder: 'yesterday, today, last week, last month…',
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
  status: {
    label: 'Find by Status',
    prompt: 'Which application stage would you like to check?',
    placeholder: 'e.g. Pre Login, Credit, Commercial…',
    buildMessage: (value) => `Show applications with status: ${value}`
  }
};

// Two-level "Find by Status" menu — the 4 main branches.
// Top-level entries with `subOptions` open a second button menu; entries with
// only `value` are leaf statuses sent straight to the backend as a group key
// (the backend's StatusGroups class maps each key to every raw statuscode
// spelling stored in the database for that stage).
export const STATUS_CATEGORIES: StatusCategory[] = [
  {
    label: 'Sales',
    subOptions: [
      { label: 'Pre-Login', value: 'sales_pre_login' },
      { label: 'Pre-Login Review', value: 'sales_pre_login_review' },
      { label: 'Pre-Login Discrepant', value: 'sales_pre_login_discrepant' },
      { label: 'Sales Discrepant', value: 'sales_discrepant' }
    ]
  },
  { label: 'Credit Assessment', value: 'credit_assessment' },
  { label: 'Pre-Disbursement', value: 'pre_disbursement' },
  {
    label: 'Disbursement',
    subOptions: [
      { label: 'Sent to LMS', value: 'disbursement_sent_to_lms' },
      { label: 'Sent to LMS Failed', value: 'disbursement_sent_to_lms_failed' }
    ]
  }
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