# Skai Frontend (Angular)

This is the Angular port of the Skai chat widget — same UI, same behavior, same flow as the original vanilla-JS/Python version. Only the framework changed.

## Setup

```bash
npm install
npm start        # runs on http://localhost:4200
```

## Backend connection

`src/app/environment.ts` currently points at the old Python WebSocket endpoints:

```ts
losWsUrl: 'ws://127.0.0.1:5000/ws',
skaleupWsUrl: 'ws://127.0.0.1:8000/ws',
```

Once the Java backend is ready, update these two URLs to wherever your Java WebSocket endpoints live. Nothing else in the frontend needs to change — the widget talks to the backend purely through the `WsMessage { type, payload }` JSON contract defined in `chat.models.ts` / `chat.service.ts`.

## Build

```bash
npm run build     # outputs to dist/frontend
```

---

# Frontend

This project was generated with [Angular CLI](https://github.com/angular/angular-cli) version 15.2.11.

## Development server

Run `ng serve` for a dev server. Navigate to `http://localhost:4200/`. The application will automatically reload if you change any of the source files.

## Code scaffolding

Run `ng generate component component-name` to generate a new component. You can also use `ng generate directive|pipe|service|class|guard|interface|enum|module`.

## Build

Run `ng build` to build the project. The build artifacts will be stored in the `dist/` directory.

## Running unit tests

Run `ng test` to execute the unit tests via [Karma](https://karma-runner.github.io).

## Running end-to-end tests

Run `ng e2e` to execute the end-to-end tests via a platform of your choice. To use this command, you need to first add a package that implements end-to-end testing capabilities.

## Further help

To get more help on the Angular CLI use `ng help` or go check out the [Angular CLI Overview and Command Reference](https://angular.io/cli) page.