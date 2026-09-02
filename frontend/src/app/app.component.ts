import { Component } from '@angular/core';
import { ChatWidgetComponent } from './chat-widget.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [ChatWidgetComponent],
  template: `<app-chat-widget></app-chat-widget>`
})
export class AppComponent {}