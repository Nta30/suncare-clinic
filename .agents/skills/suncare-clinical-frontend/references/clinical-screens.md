# Suncare role-specific UI workflow contract

These are interpretation reminders from the supplied Suncare repository AGENTS documentation. The actual requirement docs and live API contract take precedence when more specific.

## Public website and accounts

- Guest pages can present real departments, specialties, doctors and services when exposed by public APIs. Do not fabricate public endpoints.
- Patients may register and log in, request password recovery only where real APIs exist, and view their own information after authentication.
- Do not expose staff creation or role selection on public forms.

## Onsite appointments

- Patient selects specialty, day and shift. **Do not add a doctor picker** for onsite booking, because assignment is governed by business rules.
- Do not expose onsite reschedule/cancel flows when absent from the approved scope.
- Show queue/order only once sourced by the API. Never derive a waiting number from patient ID, appointment ID or invoice code.

## Online appointments

- Published available times are 20-minute slots. A hold is time-limited to 5 minutes, per project rules.
- Do not show a booking as confirmed solely because a user reached a MoMo redirect page; show the verified backend payment/appointment state.
- Cancellation, if implemented and authorized, is constrained by the project's 24-hour rule; do not create an arbitrary reschedule action.
- Disable or clearly explain actions while payment is pending; avoid replacing server conflict protection with frontend double-click prevention.

## Doctor encounter

- Design for actual clinical work: appointment context, history/allergies, current vital signs, notes, diagnosis, clinical orders, results review, prescription and completion.
- Make unfinished steps visible; do not imply a clinical encounter is completed when required final diagnosis, result review or payment conditions are unresolved.
- A released result has version history and should not be silently edited in the UI.

## Lab worklist

- Present order state and room context, queue/check-in, accepted/in-progress/deferred and final results as supplied by the API.
- Highlight patient/order identifiers clearly while limiting access to authorized records.
- Do not treat a service as ready just because the page has a call-to-action; actual payments and validity rules are server-owned.

## Cashier and billing

- Show currency in VND, line-item amounts and outstanding balance explicitly. Do not show rounding ambiguity in prices or totals.
- Separate payment request, actual confirmed payment, credit notes, refund request and completed refund states.
- Never change a frozen invoice snapshot by recomputing current service prices in the browser.

## Medical record, privacy and admin

- Patient EMR should render only data actually released to that patient; unauthorized or draft lab results must not be leaked through the frontend.
- Backend authorization is mandatory even if UI hides links.
- Admin screens should not imply that admin may edit doctor-authored medical records by default.

## Common screen state matrix

| State | Required treatment |
|---|---|
| Loading | Contextual skeleton/spinner + sensible request lifecycle |
| Empty | Explain absence and available next action |
| Validation error | Show field-level message and preserve safe input |
| Auth expired | Explain session expiration and re-authentication path |
| Forbidden | Say the user lacks access without exposing resource contents |
| Conflict | Explain stale/occupied slot or update conflict; request refresh |
| Network/server issue | Retry where safe; do not duplicate money/booking commands blindly |
| Success | Confirm exact completed action, not an inferred outcome |

For every task inspect API response DTOs before rendering fields or mapping enum states.
