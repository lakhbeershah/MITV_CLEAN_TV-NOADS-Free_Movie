# Ultra UI/UX: Master Engineering & Design Intelligence

> **Skill ID:** `ultra_uiux`  
> **Location:** `~/.gemini/config/skills/ultra_uiux/SKILL.md`  
> **Source:** Unified synthesis of Snyk's *Top 8 Claude Skills for UI/UX Engineers*

---

## Overview

The **Ultra UI/UX** skill synthesizes the top 8 UI/UX engineering methodologies into a unified, production-grade intelligence system:
1. **Anthropic Frontend Design** — Anti-AI-slop mandates, distinct aesthetic character, spatial tension, memorable craft.
2. **Vercel Web Design Guidelines** — 100+ quality gate lint rules for semantic HTML, touch targets, and focus states.
3. **Vercel React Best Practices** — 57 performance rules, zero CLS, streaming SSR, and rendering optimization.
4. **Vercel Composition Patterns** — Eradication of boolean prop hell, compound components, and headless slots.
5. **UI/UX Pro Max** — Comprehensive design system brain: 60-30-10 color rules, font archetypes, and token hierarchies.
6. **Bencium UX Designer** — Cognitive load heuristics (Fitts, Hick, Miller, Jakob) and complete 7-state component lifecycles.
7. **AccessLint WCAG Engine** — Strict WCAG 2.1/2.2 AA & AAA compliance, ARIA precision, and keyboard navigation.
8. **Multi-Platform & 10-Foot Spatial Ergonomics** — Touch, desktop mouse, and TV/Remote D-Pad navigation systems.

---

## 1. The Anti-Slop Directive (Anthropic Frontend Design)

When generating UI, **reject default AI aesthetics**:
- ❌ **Banned Font Defaults:** Never default to raw unstyled system sans, generic Inter, Roboto, or Arial without intentional pairing.
- ❌ **Banned Color Defaults:** No generic dark-gray backgrounds (`#121212`) paired with random electric purple/violet gradients (`#8A2BE2`) unless explicitly justified by branding.
- ❌ **Banned Card Walls:** Never generate repetitive grids of identical rounded rectangles with centered icons and 3 bullet points.
- ❌ **Banned Flat Whitespace:** Avoid vacant, purposeless whitespace masquerading as "minimalism".

### The 4 Pillars of Aesthetic Character
1. **Explicit Aesthetic Stance:** Choose an archetype before writing code (e.g., *Kinetic Editorial*, *High-Craft Utilitarian*, *Swiss International*, *Tactile Skeuomorphic Warmth*, or *Obsidian Studio*).
2. **Typographic Soul & Scale:** Pair a font of strong character (display serif, geometric grotesque, or engineered monospace) with an ultra-legible body font. Use tight negative letter-spacing (`-0.02em` to `-0.04em`) on large display headers and open letter-spacing (`+0.04em` to `+0.08em`) on small uppercase metadata.
3. **Intentional Asymmetry & Spatial Tension:** Anchor the eye with a dominant hero element, offset layouts, and intentional rhythm rather than centering everything.
4. **Cohesive Restraint:** Every decorative flourish, border gradient, or micro-interaction must serve the overarching product function.

---

## 2. Cognitive Ergonomics & Usability Heuristics (Bencium UX)

### The 5 Cognitive Laws
- **Hick’s Law (Minimizing Decision Fatigue):** Reduce choices per view. Group actions into primary, secondary, and overflow. Use progressive disclosure for complex configuration.
- **Fitts’s Law (Target Accessibility):** Make primary action targets large and easily clickable. Place destructive or irreversible actions away from high-frequency navigation paths.
  - Web: $\ge 44 \times 44$ px minimum interactive area.
  - Mobile Touch: $\ge 48 \times 48$ px with $\ge 8$ px surrounding buffer.
  - TV / Remote: $\ge 64 \times 64$ dp with clear $1.08 \times$ focus zoom.
- **Miller’s Law (Chunking):** Group complex data sets (forms, dashboards, settings) into clusters of 5 to 7 related items.
- **Jakob’s Law (Platform Mental Models):** Do not reinvent standard mechanics (e.g., search triggers, back buttons, modal dismissal, or tab navigation). Keep mental friction near zero.
- **The Doherty Threshold (< 100ms Feedback):**
  - **Immediate (< 100ms):** Visual acknowledgment on tap/click (active press state, ripple, scale).
  - **Short (< 1000ms):** Optimistic UI updates with rollback on network failure.
  - **Longer (> 1000ms):** Skeleton screens that reserve layout dimensions (never raw spinners that cause layout shifts).

### The Complete 7-State Component Lifecycle
Every interactive component (buttons, inputs, cards, list items) **must explicitly account for all 7 states**:
1. **Default / Idle:** Balanced contrast, clear interactive affordance.
2. **Hover:** Elevation lift, subtle border illumination, or surface tint (pointer devices only; `@media (hover: hover)`).
3. **Active / Pressed:** Physical depression (scale `0.97x`–`0.98x`), darker surface fill, instant haptic or visual feedback.
4. **Focused / Focus-Visible:** 2px or 3px high-contrast outline with 2px offset (`ring-2 ring-offset-2`). Never hide `:focus-visible`.
5. **Loading / Processing:** Inline skeleton or spinner, disabled interaction, preserved layout dimensions (no width collapse).
6. **Disabled:** Reduced opacity (`0.4`–`0.5`), `cursor-not-allowed`, `pointer-events-none`, `aria-disabled="true"`.
7. **Empty & Error:** Helpful contextual illustrations/icons, clear recovery instructions, and inline error messages tied via `aria-describedby`.

---

## 3. Design System & Visual Architecture (UI/UX Pro Max)

### The 60-30-10 Color Rule
- **60% Dominant (Canvas & Background):** Deep obsidian (`#0A0C10`, `#0E1116`) for dark mode; clean off-white / architectural paper (`#F8F9FA`, `#F4F5F7`) for light mode.
- **30% Structural (Surface, Cards, Typography, Borders):** Elevated cards, dividers, secondary text hierarchy (`#A6FFFFFF`, `#80FFFFFF`).
- **10% Accent (Interactive & Focal Energy):** High-contrast accent color reserved strictly for primary actions, active indicators, and critical badges.

### Semantic Design Tokens (CSS Variables)
```css
:root {
  /* Surface Tokens */
  --surface-canvas: #0E1116;
  --surface-raised: #161B22;
  --surface-elevated: #21262D;
  --surface-glass: rgba(22, 27, 34, 0.75);

  /* Border Tokens */
  --border-subtle: rgba(255, 255, 255, 0.08);
  --border-prominent: rgba(255, 255, 255, 0.16);
  --border-focus: #388BFD;

  /* Typography Tokens */
  --text-primary: #FFFFFF;
  --text-secondary: rgba(255, 255, 255, 0.70);
  --text-tertiary: rgba(255, 255, 255, 0.45);

  /* Functional Tokens */
  --interactive-accent: #238636;
  --status-danger: #F85149;
  --status-warning: #D29922;
  --status-success: #3FB950;
}
```

### Font Pairing Archetypes
| Archetype | Display / Heading Font | Body / UI Font | Character / Best For |
|---|---|---|---|
| **Modern Kinetic Tech** | Syne, Plus Jakarta Sans, Outfit | Inter Variable, DM Sans | High-growth SaaS, AI, Fintech |
| **Editorial Prestige** | Newsreader, Instrument Serif, Playfair | Source Sans 3, Archivo | Media, Publishing, Luxury, Portfolios |
| **Utilitarian Engineering** | JetBrains Mono, Space Grotesk | Geist Sans, IBM Plex Sans | Developer Tools, Dashboards, Hardware |
| **Humanist Warmth** | Recoleta, Fraunces | Work Sans, Plus Jakarta Sans | Consumer, Wellness, Creative Tools |

---

## 4. Vercel Web Design Guidelines (Quality Gate)

### Semantic HTML & Structure
- **No `<div>`-Soup:** Use semantic structural landmarks: `<header>`, `<nav>`, `<main>`, `<section>`, `<article>`, `<aside>`, `<footer>`.
- **Strict Heading Hierarchy:** Only one `<h1>` per page. Ensure logical descending progression (`<h1>` $\rightarrow$ `<h2>` $\rightarrow$ `<h3>`). Never skip heading levels for styling purposes (use CSS utility classes instead).
- **Interactive Elements:**
  - If it triggers a route change: use `<a>` or `<Link>`.
  - If it triggers an in-page action: use `<button type="button">`.
  - Never place `onClick` on a generic `<div>` or `<span>` without keyboard handlers (`onKeyDown`), `role="button"`, and `tabIndex={0}`.

### Form Architecture
- Every `<input>` must have a dedicated associated `<label>` (linked via `htmlFor="id"`).
- Placeholder text is **never** a replacement for a label.
- Inputs must specify explicit autocomplete types (`autocomplete="email"`, `autocomplete="tel"`, etc.).
- Errors must be rendered in close proximity to the affected input, using `aria-invalid="true"` and linked via `aria-describedby="error-id"`.

---

## 5. Component Architecture & Vercel Composition Patterns

### Eradicating Boolean Prop Explosion
❌ **Anti-Pattern (Boolean Prop Hell):**
```tsx
// Fragile, unscalable, causes combinatorial bugs
<Card 
  isElevated 
  hasHeader 
  hasFooter 
  isInteractive 
  isLoading={loading} 
  badgeText="New" 
  badgeColor="green" 
  onActionClick={handleAction} 
/>
```

✅ **Ultra Composition Pattern (Compound Components & Slots):**
```tsx
// Composable, maintainable, single-responsibility
<Card variant="elevated" interactive>
  <Card.Header>
    <Card.Title>Analytics Engine</Card.Title>
    <Badge variant="success">New</Badge>
  </Card.Header>
  <Card.Body>
    {loading ? <Card.Skeleton /> : <MetricChart data={data} />}
  </Card.Body>
  <Card.Footer>
    <Button variant="primary" onClick={handleAction}>Export Report</Button>
  </Card.Footer>
</Card>
```

### Clean Polymorphism & Delegation
- Use headless primitives (Radix UI, Headless UI, Ark UI) with `asChild` composition to forward DOM semantics cleanly without wrapper bloat.
- Isolate `"use client"` directives strictly to the interactive leaves of the React component tree; keep parent layouts and data wrappers as Server Components.

---

## 6. Performance & Fluid Motion Engine (Vercel React Best Practices)

### Zero Cumulative Layout Shift (CLS)
- **Reserved Aspect Ratios:** Always provide explicit `width` and `height` (or CSS `aspect-ratio: 16 / 9`) on images, videos, and dynamic embeds before they load.
- **Skeleton Synchronization:** Skeletons must mirror the exact dimensions and padding of the incoming content to prevent layout jumps upon load completion.
- **Font Display:** Use `font-display: swap` paired with fallback metrics overrides (`size-adjust`, `ascent-override`) to avoid Flash of Invisible Text (FOIT) and layout shifts.

### Physics-Based Fluid Motion
- **No Linear Tweening:** Banish `transition: all 0.3s linear`. Use natural spring physics or calibrated bezier curves:
  - Standard ease-out: `cubic-bezier(0.16, 1, 0.3, 1)` (snappy, responsive).
  - Duration budget:
    - Micro-feedback (toggle, active press): **100ms–150ms**
    - Structural transitions (drawers, modals, accordions): **250ms–350ms**
- **Reduced Motion Respect:**
```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

---

## 7. AccessLint & WCAG 2.2 AA/AAA Master Compliance

### Contrast Formula
- **Normal Text (< 18pt / 24px regular, < 14pt / 18.5px bold):** Minimum **4.5:1** contrast against background.
- **Large Text ($\ge$ 18pt or $\ge$ 14pt bold):** Minimum **3.0:1** contrast.
- **Interactive UI Components & Visual Boundaries:** Minimum **3.0:1** contrast against adjacent surfaces.
- **Color Independence:** Never use color as the sole indicator of state (e.g. error, success, selection). Always combine color with an icon, descriptive text label, or pattern.

### Screen Readers & Keyboard Trapping
- **Icon Buttons:** Any button containing only an SVG/icon must have an explicit `aria-label="Close dialog"` or visually hidden text.
- **Dynamic Content:** Alert banners and real-time updates must declare `aria-live="polite"` so screen readers announce changes without interrupting speech.
- **Modal & Drawer Focus Trapping:** When a modal opens, focus must be trapped within the modal container. Pressing `Escape` must dismiss the modal and return focus to the trigger element.

---

## 8. Multi-Platform, Mobile & 10-Foot Spatial Ergonomics

| Dimension | Desktop Web | Mobile Touch (iOS / Android) | 10-Foot TV / Remote (Android TV / Apple TV) |
|---|---|---|---|
| **Input Model** | Mouse & Keyboard (Fine pointer) | Finger Tap & Gestures (Coarse) | 5-Way D-Pad (Directional navigation) |
| **Target Size** | $\ge 32 \times 32$ px (44px comfortable) | $\ge 48 \times 48$ px with spacing | $\ge 64 \times 64$ dp |
| **Focus Indicator** | Subtle `:focus-visible` offset ring | Not applicable (Direct touch) | High-contrast glow, elevation lift, 1.05x–1.1x scale |
| **Safe Areas** | Browser viewport margin | `env(safe-area-inset-*)` notches | 4%–5% overscan boundary padding |
| **Interaction Latency** | Mouse hover pre-fetching | Tap down/up instantaneous | Debounced remote key repeating |

---

## 9. Pre-Shipment UI/UX Verification Checklist

Before shipping any UI code, verify against this 10-point gate:
1. [ ] **Aesthetic Character:** Does the interface have a distinct, non-generic personality?
2. [ ] **Color Contrast:** Do all text elements meet $\ge 4.5:1$ contrast (WCAG AA)?
3. [ ] **Focus Rings:** Can every interactive element be navigated using only the keyboard (`Tab` / Arrow keys) with clear focus indicators?
4. [ ] **Target Size:** Are touch targets $\ge 44 \times 44$ px (web) or $\ge 48 \times 48$ px (mobile)?
5. [ ] **Zero Layout Shift:** Are image, video, and dynamic card heights explicitly reserved?
6. [ ] **Component Lifecycle:** Are Hover, Active, Focus, Loading, Disabled, Empty, and Error states cleanly styled?
7. [ ] **No Boolean Prop Hell:** Are complex components decomposed into clean compound structures?
8. [ ] **Semantic Structure:** Is HTML landmarked with `<main>`, `<nav>`, `<header>`, and clean heading hierarchy?
9. [ ] **Reduced Motion:** Does the UI respect `prefers-reduced-motion`?
10. [ ] **Memory & Performance:** Are heavy blur layers and expensive layout repaints minimized?
