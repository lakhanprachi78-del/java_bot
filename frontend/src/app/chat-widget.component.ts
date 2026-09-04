import {
  Component,
  OnInit,
  OnDestroy,
  ElementRef,
  ViewChild,
  ChangeDetectorRef,
  SecurityContext
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

import { ChatService } from './chat.service';
import { environment } from './environment';
import {
  FlowState,
  ChatMessage,
  ChatAction,
  QUERY_TYPES,
  STATUS_CATEGORIES,
  SKALEUP_SUGGESTED_QUESTIONS,
  FEEDBACK_TAGS,
  BotSource
} from './chat.models';

declare global {
  interface Window {
    SpeechRecognition: any;
    webkitSpeechRecognition: any;
  }
}

@Component({
  selector: 'app-chat-widget',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-widget.component.html',
  styleUrls: ['./chat-widget.component.css']
})
export class ChatWidgetComponent implements OnInit, OnDestroy {
  @ViewChild('messagesContainer') messagesContainer!: ElementRef<HTMLDivElement>;
  @ViewChild('chatInput') chatInput!: ElementRef<HTMLInputElement>;

  public isOpen = false;
  public widgetState: 'closed' | 'open' | 'collapsed' = 'closed';
  public preCollapseState: 'closed' | 'open' | 'collapsed' = 'closed';
  public flow: FlowState = FlowState.INITIAL;
  public selectedQueryType: string | null = null;
  public isLoading = false;
  public hasGreeted = false;

  public activeStatusFilter: string | null = null;
  public statusExtraFilter: string | null = null;
  public statusOffset = 0;

  public speechSupported = false;
  public isListening = false;
  private recognition: any = null;

  public inputText = '';
  public inputPlaceholder = 'Type a message…';
  public isInputDisabled = true;

  public isTypingVisible = false;
  public typingMode: 'typing' | 'thinking' = 'typing';
  public typingLabel = 'Assistant is typing…';
  private thinkingTimer: any = null;

  public messages: ChatMessage[] = [];

  // Feedback detail modal/panel tracking per message
  public feedbackPanels: Record<string, {
    queryId: number | null;
    queryText: string | null;
    answerText: string | null;
    positive: boolean;
    source: BotSource;
    selectedTags: Set<string>;
    comment: string;
    isSending: boolean;
    isSent: boolean;
    sentSuccess: boolean;
  }> = {};

  constructor(
    private chatService: ChatService,
    private cdr: ChangeDetectorRef,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.setupVoiceInput();
  }

  ngOnDestroy(): void {
    if (this.thinkingTimer) clearTimeout(this.thinkingTimer);
  }

  // --- Voice Input (Web Speech API) ---
  private setupVoiceInput(): void {
    const SpeechRecognitionAPI = window.SpeechRecognition || window.webkitSpeechRecognition;

    if (!SpeechRecognitionAPI) {
      this.speechSupported = false;
      return;
    }

    this.speechSupported = true;
    this.recognition = new SpeechRecognitionAPI();
    this.recognition.lang = 'en-IN';
    this.recognition.continuous = false;
    this.recognition.interimResults = true;
    this.recognition.maxAlternatives = 1;

    let finalTranscript = '';

    this.recognition.addEventListener('start', () => {
      this.isListening = true;
      finalTranscript = '';
      this.inputPlaceholder = 'Listening…';
      this.cdr.detectChanges();
    });

    this.recognition.addEventListener('result', (event: any) => {
      let interimTranscript = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          finalTranscript += transcript;
        } else {
          interimTranscript += transcript;
        }
      }
      this.inputText = (finalTranscript + interimTranscript).trim();
      this.cdr.detectChanges();
    });

    this.recognition.addEventListener('error', (event: any) => {
      console.warn('Speech recognition error:', event.error);
      this.stopListening();
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        this.appendBotMessage(
          "I couldn't access your microphone. Please allow microphone permissions in your browser and try again."
        );
      }
    });

    this.recognition.addEventListener('end', () => {
      this.stopListening();
      if (this.inputText.trim() && !this.isInputDisabled) {
        this.onSubmitForm();
      }
    });
  }

  public toggleMic(): void {
    if (!this.speechSupported) return;
    if (this.isListening) {
      this.recognition.stop();
    } else {
      this.inputText = '';
      try {
        this.recognition.start();
      } catch (err) {
        console.warn('Speech recognition start failed:', err);
      }
    }
  }

  private stopListening(): void {
    this.isListening = false;
    this.inputPlaceholder = 'Type a message…';
    this.cdr.detectChanges();
  }

  // --- Chat Window Toggle & Collapse ---
  public openChat(): void {
    this.widgetState = 'open';
    this.isOpen = true;
    if (!this.hasGreeted) {
      this.showWelcome();
    }
    this.scrollToBottom();
    this.cdr.detectChanges();
  }

  public closeChat(): void {
    this.widgetState = 'closed';
    this.isOpen = false;
    this.cdr.detectChanges();
  }

  public toggleChat(): void {
    this.widgetState === 'open' ? this.closeChat() : this.openChat();
  }

  public collapseWidget(event?: Event): void {
    if (event) event.stopPropagation();
    this.preCollapseState = this.widgetState === 'collapsed' ? this.preCollapseState : this.widgetState;
    this.widgetState = 'collapsed';
    this.isOpen = false;
    this.cdr.detectChanges();
  }

  public expandFromEdge(): void {
    const targetState = this.preCollapseState === 'collapsed' ? 'closed' : this.preCollapseState;
    if (targetState === 'open') {
      this.openChat();
    } else {
      this.closeChat();
    }
  }

  // --- Helper & Utility Methods ---
  private formatTime(date: Date = new Date()): string {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      if (this.messagesContainer) {
        this.messagesContainer.nativeElement.scrollTop = this.messagesContainer.nativeElement.scrollHeight;
      }
    }, 50);
  }

  private setInputEnabled(enabled: boolean, placeholder = 'Type a message…'): void {
    this.isInputDisabled = !enabled;
    this.inputPlaceholder = placeholder;
    if (enabled) {
      setTimeout(() => this.chatInput?.nativeElement?.focus(), 100);
    }
    this.cdr.detectChanges();
  }

  private setLoading(loading: boolean): void {
    this.isLoading = loading;
    this.cdr.detectChanges();
  }

  private showTyping(visible: boolean): void {
    if (this.thinkingTimer) {
      clearTimeout(this.thinkingTimer);
      this.thinkingTimer = null;
    }

    if (visible) {
      this.typingMode = 'typing';
      this.typingLabel = 'Skai is typing…';
      this.isTypingVisible = true;
      this.thinkingTimer = setTimeout(() => {
        this.typingMode = 'thinking';
        this.typingLabel = 'Skai is thinking…';
        this.cdr.detectChanges();
      }, environment.thinkingAfterMs);
    } else {
      this.isTypingVisible = false;
    }
    this.scrollToBottom();
    this.cdr.detectChanges();
  }

  // --- Formatting logic ---
  public renderInlineMarkdown(rawText: string): string {
    const escaped = String(rawText)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
    const bolded = escaped.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
    const EMAIL_PATTERN = /([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})/g;
    return bolded.replace(
      EMAIL_PATTERN,
      (email) => `<a href="mailto:${email}" class="bubble-link">${email}</a>`
    );
  }

  public renderBotBubbleBlocks(text: string): { heading: string | null; pairs: [string, string][]; text?: string }[] {
    if (!text) return [];
    const blocks = String(text).split(/\n\s*\n/);
    const result: { heading: string | null; pairs: [string, string][]; text?: string }[] = [];

    blocks.forEach((rawBlock) => {
      const lines = rawBlock
        .split('\n')
        .map((l) => l.trim())
        .filter((l) => l.length > 0);

      if (lines.length === 0) return;

      let heading: string | null = null;
      const pairs: [string, string][] = [];
      let isKeyValueBlock = lines.length >= 2;

      lines.forEach((line, i) => {
        let working = line;
        const numberedMatch = i === 0 ? working.match(/^(\d+)\.\s*(.*)$/) : null;
        if (numberedMatch) {
          heading = `Application ${numberedMatch[1]}`;
          working = numberedMatch[2];
        }

        const kvMatch = working.match(/^([A-Za-z][A-Za-z0-9 /_'-]{1,40}):\s*(.+)$/);
        if (kvMatch) {
          pairs.push([kvMatch[1].trim(), kvMatch[2].trim()]);
        } else {
          isKeyValueBlock = false;
        }
      });

      if (isKeyValueBlock && pairs.length >= 2) {
        result.push({ heading, pairs });
      } else {
        result.push({ heading: null, pairs: [], text: rawBlock.trim() });
      }
    });

    return result;
  }

  private extractBotReply(responseData: any): string {
    if (!responseData) return 'No response received from the server.';
    if (typeof responseData === 'string') return responseData;
    if (responseData.reply) return responseData.reply;
    if (responseData.message) return responseData.message;
    if (responseData.response) return responseData.response;
    return 'I received a response but couldn\'t display it. Please try rephrasing your question.';
  }

  // --- Message Append Utilities ---
  private appendMessage(
    type: 'bot' | 'user',
    text: string,
    options: {
      actions?: ChatAction[];
      isError?: boolean;
      queryId?: number | null;
      queryText?: string | null;
      answerText?: string | null;
      source?: BotSource;
    } = {}
  ): ChatMessage {
    const msg: ChatMessage = {
      id: 'msg_' + Date.now() + '_' + Math.random().toString(36).substring(2, 6),
      type,
      text,
      timestamp: this.formatTime(),
      actions: options.actions || [],
      isError: !!options.isError,
      queryId: options.queryId ?? null,
      queryText: options.queryText ?? null,
      answerText: options.answerText ?? null,
      source: options.source || 'rag'
    };
    this.messages.push(msg);
    this.scrollToBottom();
    this.cdr.detectChanges();
    return msg;
  }

  private appendUserMessage(text: string): ChatMessage {
    return this.appendMessage('user', text);
  }

  private appendBotMessage(text: string, options: any = {}): ChatMessage {
    return this.appendMessage('bot', text, options);
  }

  // --- Feedback Logic ---
  public onVote(msg: ChatMessage, positive: boolean): void {
    if (msg.feedbackGiven || msg.queryId === null || msg.queryId === undefined) return;
    msg.feedbackGiven = true;

    this.chatService.sendFeedback(msg.queryId, positive, msg.source || 'rag');

    const source = msg.source || 'rag';
    this.feedbackPanels[msg.id] = {
      queryId: msg.queryId,
      queryText: msg.queryText ?? null,
      answerText: msg.answerText ?? null,
      positive,
      source,
      selectedTags: new Set<string>(),
      comment: '',
      isSending: false,
      isSent: false,
      sentSuccess: false
    };
    this.cdr.detectChanges();
  }

  public getTagOptions(source: BotSource, positive: boolean): string[] {
    const tagSet = FEEDBACK_TAGS[source] || FEEDBACK_TAGS.rag;
    return positive ? tagSet.positive : tagSet.negative;
  }

  public toggleTag(msgId: string, tag: string): void {
    const panel = this.feedbackPanels[msgId];
    if (!panel) return;
    if (panel.selectedTags.has(tag)) {
      panel.selectedTags.delete(tag);
    } else {
      panel.selectedTags.add(tag);
    }
    this.cdr.detectChanges();
  }

  public dismissFeedbackPanel(msgId: string): void {
    delete this.feedbackPanels[msgId];
    this.cdr.detectChanges();
  }

  public async sendFeedbackDetail(msgId: string): Promise<void> {
    const panel = this.feedbackPanels[msgId];
    if (!panel || panel.isSending) return;

    panel.isSending = true;
    this.cdr.detectChanges();

    const ok = await this.chatService.sendFeedbackDetail(
      panel.queryId,
      panel.queryText,
      panel.answerText,
      panel.positive,
      Array.from(panel.selectedTags),
      panel.comment.trim() || null,
      panel.source
    );

    panel.isSending = false;
    panel.isSent = true;
    panel.sentSuccess = ok;
    this.cdr.detectChanges();
  }

  // --- Flow Actions & Handlers ---
  private async showWelcome(): Promise<void> {
    if (this.hasGreeted) return;
    this.hasGreeted = true;
    this.flow = FlowState.MAIN_MENU;

    let greetingText = "Hi 👋 I'm Skai — Find. Track. Know. How can I help you today?";
    try {
      const data = await this.chatService.greet();
      if (data && data.greeting) {
        greetingText = `${data.greeting} I'm Skai — Find. Track. Know. How can I help you today?`;
      }
    } catch (e) {
      // Backend unreachable or WS error — fall back to generic greeting
    }

    this.appendBotMessage(greetingText, {
      actions: [
        { label: 'Know About SkaleUp', primary: true, onClick: () => this.startSkaleUpChat() },
        { label: 'Know About Your Application', primary: true, onClick: () => this.showQueryTypeMenu() }
      ]
    });
    this.setInputEnabled(false);
  }

  public showWelcomeMenuAgain(): void {
    this.resetStatusContext();
    this.flow = FlowState.MAIN_MENU;
    this.setInputEnabled(false);
    this.appendBotMessage('What would you like to do?', {
      actions: [
        { label: 'Know About SkaleUp', primary: true, onClick: () => this.startSkaleUpChat() },
        { label: 'Know About Your Application', primary: true, onClick: () => this.showQueryTypeMenu() }
      ]
    });
  }

  public startSkaleUpChat(): void {
    this.flow = FlowState.SKALEUP_CHAT;
    this.setInputEnabled(true, 'Ask SkaleUp anything…');

    const actions: ChatAction[] = [
      ...SKALEUP_SUGGESTED_QUESTIONS.map((q) => ({
        label: q,
        onClick: () => this.handleSkaleUpQuery(q)
      })),
      { label: '⬅ Back to main menu', onClick: () => this.showWelcomeMenuAgain() }
    ];

    this.appendBotMessage(
      'Ask me anything about SkaleUp — or try one of these popular questions:',
      { actions }
    );
  }

  public async handleSkaleUpQuery(rawInput: string): Promise<void> {
    const trimmed = rawInput.trim();
    if (!trimmed || this.isLoading) return;

    this.appendUserMessage(trimmed);
    this.inputText = '';

    this.setLoading(true);
    this.setInputEnabled(false);
    this.showTyping(true);

    try {
      const data = await this.chatService.sendSkaleUpChat(trimmed);
      this.showTyping(false);
      const reply = data?.answer || "I couldn't find an answer to that.";
      this.appendMessage('bot', reply, {
        queryId: data?.query_id ?? null,
        queryText: trimmed,
        answerText: reply,
        source: 'rag',
        actions: [
          {
            label: '⬅ Back to main menu',
            onClick: () => this.showWelcomeMenuAgain()
          }
        ]
      });
    } catch (err: any) {
      this.showTyping(false);
      this.appendMessage(
        'bot',
        `Hmm, I ran into a snag connecting to SkaleUp. ${err.message}\n\nMake sure the RAG server is running and try again.`,
        { isError: true, actions: [{ label: 'Try again', onClick: () => this.startSkaleUpChat() }] }
      );
    } finally {
      this.setLoading(false);
      this.setInputEnabled(true, 'Ask SkaleUp anything…');
    }
  }

  public showQueryTypeMenu(): void {
    this.resetStatusContext();
    this.flow = FlowState.QUERY_TYPE;
    this.selectedQueryType = null;
    this.setInputEnabled(true, 'Describe your search');

    const actions: ChatAction[] = [
      ...Object.entries(QUERY_TYPES).map(([key, def]) => ({
        label: def.label,
        onClick: () => this.selectQueryType(key)
      })),
      { label: '⬅ Back to main menu', onClick: () => this.showWelcomeMenuAgain() }
    ];

    this.appendBotMessage(
      "Certainly — to help me pull up the right details, could you let me know which of the following you'd like to check?",
      { actions }
    );
  }

  public async selectQueryType(typeKey: string): Promise<void> {
    if (typeKey === 'status') {
      this.showStatusOptions();
      return;
    }

    this.flow = FlowState.AWAITING_INPUT;
    this.selectedQueryType = typeKey;

    const queryDef = QUERY_TYPES[typeKey];
    this.appendBotMessage(queryDef.prompt);
    this.setInputEnabled(true, queryDef.placeholder);
  }

  // Top-level "Find by Status" menu: Sales, Credit, Commercial, Operations, Discrepant
  private showStatusOptions(): void {
    this.flow = FlowState.AWAITING_INPUT;
    this.selectedQueryType = 'status';
    this.setInputEnabled(false);

    const actions: ChatAction[] = [
      ...STATUS_CATEGORIES.map((category) => ({
        label: category.label,
        onClick: () => this.selectStatusCategory(category)
      })),
      { label: '⬅ Back to main menu', onClick: () => this.showWelcomeMenuAgain() }
    ];

    this.appendBotMessage('Which application stage would you like to check?', { actions });
  }

  // A category was tapped: if it has sub-options (Sales, Discrepant) open the
  // second-level menu; otherwise it's a leaf status (Credit, Commercial,
  // Operations) and we run the query directly.
  private selectStatusCategory(category: { label: string; value?: string; subOptions?: string[] }): void {
    if (category.subOptions && category.subOptions.length > 0) {
      this.showStatusSubOptions(category.label, category.subOptions);
      return;
    }
    this.selectStatus(category.value ?? category.label);
  }

  private showStatusSubOptions(categoryLabel: string, subOptions: string[]): void {
    this.setInputEnabled(false);

    const actions: ChatAction[] = [
      ...subOptions.map((label) => ({
        label,
        onClick: () => this.selectStatus(label)
      })),
      { label: '⬅ Back to status list', onClick: () => this.showStatusOptions() },
      { label: '⬅ Back to main menu', onClick: () => this.showWelcomeMenuAgain() }
    ];

    this.appendBotMessage(`Which ${categoryLabel} stage?`, { actions });
  }

  private async selectStatus(label: string): Promise<void> {
    this.activeStatusFilter = label.toLowerCase();
    this.statusExtraFilter = null;
    this.statusOffset = 0;
    await this.runStatusQuery(label);
  }

  private async giveFiveMoreStatusResults(): Promise<void> {
    this.statusOffset += 5;
    await this.runStatusQuery('Give 5 more applications');
  }

  private resetStatusContext(): void {
    this.activeStatusFilter = null;
    this.statusExtraFilter = null;
    this.statusOffset = 0;
  }

  private buildStatusApiMessage(): string {
    let msg = `Show applications with status: ${this.activeStatusFilter}`;
    if (this.statusExtraFilter) {
      msg += ` and ${this.statusExtraFilter}`;
    }
    if (this.statusOffset > 0) {
      msg += `. Skip the first ${this.statusOffset} results already shown and return the next 5 most recent matches.`;
    } else {
      msg += `. Return the 5 most recent matches.`;
    }
    return msg;
  }

  private buildStatusResultActions(hasMore: boolean | undefined): ChatAction[] {
    const actions: ChatAction[] = [];

    if (hasMore === true) {
      actions.push({ label: 'Show more applications', onClick: () => this.giveFiveMoreStatusResults() });
    }

    actions.push(
      {
        label: 'Ask another question',
        onClick: () => {
          this.resetStatusContext();
          this.showQueryTypeMenu();
        }
      },
      {
        label: '⬅ Back to main menu',
        onClick: () => {
          this.resetStatusContext();
          this.showWelcomeMenuAgain();
        }
      }
    );

    return actions;
  }

  private async runStatusQuery(displayText: string): Promise<void> {
    if (this.isLoading) return;

    this.appendUserMessage(displayText);
    this.setLoading(true);
    this.setInputEnabled(false);
    this.showTyping(true);

    try {
      const data = this.statusExtraFilter
        ? await this.chatService.sendLosChat(this.buildStatusApiMessage())
        : await this.chatService.sendLosChatDirect('status', this.activeStatusFilter!, this.statusOffset);

      this.showTyping(false);
      const reply = this.extractBotReply(data);
      this.appendMessage('bot', reply, {
        queryId: data?.query_id ?? null,
        queryText: displayText,
        answerText: reply,
        source: 'los',
        actions: this.buildStatusResultActions(data?.has_more)
      });
      this.flow = FlowState.STATUS_RESULTS;
      this.setInputEnabled(true, 'Add another detail (e.g. "created yesterday") or pick an option below…');
    } catch (err: any) {
      this.showTyping(false);
      this.appendMessage(
        'bot',
        `Hmm, I ran into a snag connecting to the server. ${err.message}\n\nMake sure the backend is running and try again.`,
        { isError: true, actions: [{ label: 'Try again', onClick: () => this.runStatusQuery(displayText) }] }
      );
    } finally {
      this.setLoading(false);
    }
  }

  public async handleUserQuery(rawInput: string): Promise<void> {
    const trimmed = rawInput.trim();
    if (!trimmed || this.isLoading) return;

    if (this.flow === FlowState.STATUS_RESULTS && this.activeStatusFilter) {
      this.inputText = '';
      this.statusExtraFilter = trimmed;
      this.statusOffset = 0;
      await this.runStatusQuery(trimmed);
      return;
    }

    let apiMessage = trimmed;
    const DIRECT_FIELDS = new Set(['application_id', 'applicant_name', 'date_time']);
    let directField: string | null = null;

    if (this.selectedQueryType && this.flow === FlowState.AWAITING_INPUT) {
      const queryDef = QUERY_TYPES[this.selectedQueryType];

      if (typeof queryDef.validate === 'function') {
        const validationError = queryDef.validate(trimmed);
        if (validationError) {
          this.appendUserMessage(trimmed);
          this.inputText = '';
          this.appendBotMessage(validationError);
          return;
        }
      }

      if (DIRECT_FIELDS.has(this.selectedQueryType)) {
        directField = this.selectedQueryType;
      } else {
        apiMessage = queryDef.buildMessage(trimmed);
      }
      this.flow = FlowState.FREE_CHAT;
      this.selectedQueryType = null;
    }

    this.appendUserMessage(trimmed);
    this.inputText = '';

    this.setLoading(true);
    this.setInputEnabled(false);
    this.showTyping(true);

    try {
      const data = directField
        ? await this.chatService.sendLosChatDirect(directField, trimmed)
        : await this.chatService.sendLosChat(apiMessage);

      this.showTyping(false);
      const reply = this.extractBotReply(data);
      this.appendMessage('bot', reply, {
        queryId: data?.query_id ?? null,
        queryText: trimmed,
        answerText: reply,
        source: 'los',
        actions: [
          {
            label: 'Ask another question',
            onClick: () => this.showQueryTypeMenu()
          },
          {
            label: '⬅ Back to main menu',
            onClick: () => this.showWelcomeMenuAgain()
          }
        ]
      });
    } catch (err: any) {
      this.showTyping(false);
      this.appendMessage(
        'bot',
        `Hmm, I ran into a snag connecting to the server. ${err.message}\n\nMake sure the backend is running and try again.`,
        { isError: true, actions: [{ label: 'Try again', onClick: () => this.showQueryTypeMenu() }] }
      );
    } finally {
      this.setLoading(false);
      this.setInputEnabled(true);
    }
  }

  public onSubmitForm(): void {
    if (this.flow === FlowState.SKALEUP_CHAT) {
      this.handleSkaleUpQuery(this.inputText);
    } else {
      this.handleUserQuery(this.inputText);
    }
  }
}