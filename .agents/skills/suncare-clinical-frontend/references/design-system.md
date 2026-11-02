# Sunflower Clinical: design tokens and UI vocabulary

## Identity

A warm and approachable healthcare product, without sacrificing clinical trust, readability and data density. Favor 80% neutral surfaces, 15% structured ink/leaf typography, and 5% golden emphasis as a directional guideline, not a pixel-count rule.

## Palette (all values are proposed, not existing brand assets)

| Token | Hex | Intended role |
|---|---|---|
| `suncare-50` | `#FFFDF8` | Ivory canvas |
| `suncare-100` | `#FFF3D6` | Warm tinted surface |
| `suncare-200` | `#FBE7B5` | Gold tint/borders |
| `suncare-300` | `#FAD578` | Decor and non-text highlights |
| `suncare-400` | `#F4B942` | Sunflower brand accent/bright CTA background, **dark text required** |
| `suncare-500` | `#E7A82E` | Darker sunflower accent, **dark text required** |
| `suncare-600` | `#936100` | Accessible amber links/text on white, or dark CTA with white text |
| `suncare-700` | `#765000` | Hover/active amber text |
| `suncare-800` | `#5C420F` | Dark amber headings |
| `suncare-900` | `#45350F` | Deep sunflower ink |
| `clinical-canvas` | `#FFFDF8` | Page body |
| `clinical-surface` | `#FFFFFF` | Table/card/dialog surfaces |
| `clinical-ink` | `#26342C` | Body/header text |
| `clinical-muted` | `#6B756C` | Secondary text |
| `clinical-border` | `#E8E3D9` | Decorative separators |
| `clinical-border-strong` | `#7B887E` | Form control boundary |
| `clinical-leaf` | `#59744B` | Quiet positive/secondary accent |
| `clinical-success` | `#28634F` | Success text with explicit label |
| `clinical-info` | `#146782` | Informational state |
| `clinical-warning` | `#765000` | Pending/attention state with text |
| `clinical-danger` | `#AE2D2A` | Validation and clinical warnings, not brand styling |
| `suncare-teal` | `#0D9488` | Preserved legacy utility for compatibility; avoid using it as body text on white |

## Tested contrast pairs (calculated with WCAG relative luminance)

- `#26342C` on `#F4B942`: **7.37:1**; appropriate for text on bright sunflower buttons.
- White on `#F4B942`: **1.77:1**; **never** use for text.
- White on `#936100`: **5.32:1**; appropriate for a dark amber primary button.
- `#936100` on white: **5.32:1**; appropriate for links and regular text.
- `#59744B` on white: **5.22:1**; appropriate for leaf accents with text.
- `#26342C` on `#FFFDF8`: **12.83:1**; appropriate for medical details.

These checks cover particular foreground/background pairs, not every interaction state or entire page. Re-evaluate actual combinations during implementation.

## Tailwind v4 usage

The bundled `assets/index.sunflower.css` is a **proposed replacement** for `frontend/src/index.css`, not a file that should overwrite the repository automatically. Before applying it, inspect existing `suncare-*` utility usages. Tailwind v4 discovers `--color-*` variables in `@theme` and generates utility names such as `bg-suncare-400`, `text-clinical-ink`, `bg-clinical-surface` and `border-clinical-border-strong`.

Recommended JSX patterns:

```jsx
<button className='rounded-lg bg-suncare-400 px-4 py-2.5 font-semibold text-clinical-ink hover:bg-suncare-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-suncare-700'>
  Đặt lịch khám
</button>

<section className='rounded-xl border border-clinical-border bg-clinical-surface p-4 shadow-sm'>
  <h2 className='text-lg font-semibold text-clinical-ink'>Lịch khám hôm nay</h2>
  <p className='text-sm text-clinical-muted'>Các lượt khám đã được phân công</p>
</section>
```

Use `border-clinical-border-strong` for form input borders. Apply clear focus states and errors. Status colors are semantic, not substitutes for text labels.

## Visual composition

- Public pages: friendly brand storytelling, clear services and specialties, named CTAs, useful trust details, spacious but not empty.
- Patient portal: one key next action, appointment summary, medical record links, clear timeline and payment statuses; no mixed operational controls.
- Doctor workspace: accessible table / worklist, patient/context summary, tabs or ordered sections for notes, vitals, diagnosis, orders, results and prescription. Avoid full-height illustrative panels that hide clinical data.
- Lab: compact queue with meaningful readable statuses, service/room/priority context, confirmation step for publish/retest where required by API.
- Cashier: exact monetary values, payment allocation, refund workflows, explicit confirmations and immutable record states; never infer payment succeeded from navigation alone.
- Admin: compact list and form views with filters, active/inactive states and clear permission restrictions.

## Typography, spacing and icons

- Start with system font (`Segoe UI`, Arial). `Be Vietnam Pro` can be evaluated separately only if deliberately installed with a compliant loading strategy; do not silently add remote fonts.
- Use consistent Tailwind spacing and normal font sizes. Dense clinical tables can use 13–14px text with adequate line-height, but prioritize legibility on mobile.
- Default component shapes: 8–12px controls, 12–16px cards, subtle border, little shadow, consistent 40–44px interactive targets when practical.
- Retain the project's existing Font Awesome integration. Do not mix Font Awesome, Lucide, Heroicons and Themify within one screen.
