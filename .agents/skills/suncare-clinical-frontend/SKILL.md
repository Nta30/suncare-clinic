---
name: suncare-clinical-frontend
description: Build, change, or visually review Suncare Clinic frontend screens with React JavaScript, Vite, Tailwind CSS v4 and the Sunflower Clinical visual system. Keep existing Spring Boot API contracts and Suncare business rules. Use web-design-guidelines for a formal interface-guidelines audit. Do not use for backend-only, SQL-only or database migration tasks.
---

# Suncare Clinical Frontend

Deliver calm, credible, efficient healthcare interfaces. Express the sunflower identity through warm ivory, restrained golden yellow and subtle botanical green, not a full yellow dashboard. Prioritize accurate clinical workflows and accessible information over decorative layouts.

## Load the project context before editing

1. Follow the repo-root `AGENTS.md` and `frontend/AGENTS.md`; read `docs/implementation/PROGRESS.md` and the relevant requirements under `docs/01-...` through `docs/06-...`. Consult `frontend/AGENTS_SKILL.md` only when the task needs help choosing an additional specialist skill; it routes skills and does not add another implementation checklist.
2. Inspect `git status --short`, the target screen, existing layout/components/routes, `frontend/src/index.css`, and the real backend API requests/responses/DTOs. Preserve unrelated user edits.
3. Identify the task code (`Pxx`), role, user journey, API readiness, states, and files in scope. If an API or business rule is missing, identify the blocker; do not invent a functioning interface or silently change the backend.
4. The current user request sets scope and preferences. Follow project instructions and requirements for business, security, data and evidence; if a requested change alters an unresolved business decision, explain the impact and ask before implementing that dependent behavior.
5. Explain the intended layout and acceptance checks briefly before implementing the scoped task.

## Constraints with priority over generic design skills

- Use **React + JavaScript JSX + Vite + Tailwind CSS v4**. Do not migrate to TypeScript, Next.js, another styling stack, a generic admin template, or an extra UI state framework.
- Retain the project's **Font Awesome** icon convention. Add dependencies only for a demonstrated need and explain why.
- Follow existing folders: `src/features/<module>/`, `src/components/ui/`, `src/components/layout/`, `src/services/`, and `src/routes/`. Keep business rules on the server; use API data rather than inventing fields or statuses.
- Respect local formatting: 2-space indentation, single-quoted JS strings, no semicolons, PascalCase components and camelCase functions.
- Only a patient self-registers; public requests must never submit `role`, `staffId` or `accountType`. Frontend visibility is not server authorization.
- Do not add fake working controls, sample real patient information, fabricated payment success, video sessions, or mock API responses represented as production behavior.
- For frontend-only requests, keep implementation limited to the requested Pxx or screen; do not edit SQL migrations or unrelated backend modules. If the user explicitly requests a full-stack change, follow that scope and apply the backend rules in the root `AGENTS.md`.

## Sunflower Clinical design rules

1. Read [references/design-system.md](references/design-system.md) before creating or restyling components. Reuse design tokens; avoid raw hex strings inside JSX.
2. Read [references/clinical-screens.md](references/clinical-screens.md) for the target role and information hierarchy.
3. Use white or warm ivory for main surfaces. Reserve sunflower gold for brand cues, primary actions and small highlights. Use leaf green sparingly; keep patient notes, results, medical measurements and financial values on neutral, high-contrast backgrounds.
4. Avoid loud gradients, rainbow status chips, giant empty dashboard cards, hero sections on operational screens, nested rounded cards, oversized whitespace and over-animated charts.
5. Distinguish visual hierarchy by role: welcoming public pages, fast-scanning patient pages, denser doctor/lab workflows, exact money/accounting-adjacent summaries for cashier, controlled tables for admin.
6. Preserve existing `suncare-*` class compatibility when adopting the new CSS palette, and inspect all uses of `bg-suncare-*`, `text-suncare-*`, and `border-suncare-*` before changing global tokens.
7. Forms, dialogs, menus and popups should use a short, restrained transition both when opening and closing. Keep the element mounted until its exit finishes, preserve keyboard focus behavior, and respect `prefers-reduced-motion: reduce`. Motion must not delay critical feedback or obscure clinical information.

## Usability and accessibility are acceptance criteria

- Meet WCAG 2.2 AA as a working target: text contrast at least 4.5:1 (3:1 for qualifying large text); keyboard access, visible focus, semantic landmarks, accessible input labels, correctly announced errors, reduced-motion support.
- Never use color alone to express a diagnosis, critical result, booking state, payment result, priority or form error; provide clear Vietnamese text and suitable icon/shape where helpful.
- Every API-driven view handles: loading, empty, success, validation error, unavailable/network error, unauthorized/expired session, and conflict when applicable. Display a next action, not just an HTTP code.
- Forms have persistent visible labels, inline field errors, required indicators, disabled/submitting states and prevention of accidental duplicate submission; server validation remains authoritative.
- Test 320px / 375px mobile, 768px tablet and 1280px desktop. Avoid page-wide horizontal overflow; give large clinical tables an intentionally scrollable region with readable sticky headers where appropriate.
- Display dates and currency in Vietnamese locale, respecting the server's UTC storage and the business timezone specified by project docs; never use floating point for financial calculations.

## Execution and verification

1. Map existing components and API contracts, list user states, and choose a restrained component composition: page header, relevant actions, filters, content area, empty/error fallback.
2. Update tokens centrally; implement reusable primitives only when actually needed by the scoped screen (button, input, select, status label, table, dialog, loading/empty/error state).
3. Implement scoped feature JSX and API integration without altering unrelated routes, endpoint shapes, permissions or business rules.
4. Run `npm run lint` and `npm run build` in `frontend/`. If browser/API access exists, verify valid, invalid, permission and network scenarios; otherwise report exactly which checks were not executed.
5. Report changed files, screenshots/manual review where possible, responsive checks, actual test results, unresolved blockers, and update `docs/implementation/PROGRESS.md` only according to the project's workflow.

## Reference hierarchy when combining skills

1. The current user request defines scope and preferences. Root and frontend `AGENTS.md`, business documents, actual API contracts and live code govern project workflow and correctness.
2. This skill and its references govern Sunflower Clinical visual language and frontend interaction guidance within that scope.
3. Other skills (e.g. `frontend-design`, `web-design-guidelines`, `vercel-react-best-practices`) add design, audit or performance advice only for the requested task and only where it fits the current stack and items 1–2.

## Suggested invocation

`Use $suncare-clinical-frontend to redesign only the patient appointments page in React JavaScript and Tailwind CSS v4. Inspect existing components, APIs, role rules and current token usages; follow the Sunflower Clinical system; implement real loading/error/empty/success states, then run lint/build. Do not change backend/database. Report the modified files and test results.`
