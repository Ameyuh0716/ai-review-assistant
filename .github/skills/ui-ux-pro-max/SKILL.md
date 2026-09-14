---
name: ui-ux-pro-max
user-invocable: true
description: '**UI/UX DESIGN SKILL** — Search the ui-ux-pro-max design database for informed design decisions. USE FOR: color palettes, typography, UI styles (glassmorphism, minimalism), layout patterns, UX best practices, icon recommendations, chart types, animation presets, responsive design guidelines. INVOKES: Python script `.skills/ui-ux-pro-max/scripts/search.py` with domain-specific queries. DO NOT USE FOR: general coding, backend logic, database queries, API design.'
---

# UI/UX Pro Max Design Skill

## How to Use

Search the design database using the Python script:

```bash
python3 .skills/ui-ux-pro-max/scripts/search.py "<query>" --domain <domain>
```

## Available Domains

| Domain | Description | Example Query |
|--------|-------------|---------------|
| `product` | Product type recommendations | "SaaS dashboard", "e-commerce checkout" |
| `style` | UI styles + CSS keywords | "glassmorphism", "minimalism", "brutalism" |
| `typography` | Font pairings with Google Fonts | "modern clean readable" |
| `color` | Color palettes by product type | "education app", "fintech dashboard" |
| `landing` | Page structure and CTA strategies | "hero section", "pricing page" |
| `chart` | Chart types and libraries | "analytics dashboard", "data visualization" |
| `ux` | Best practices (119 guidelines) | "form validation", "error handling" |
| `icons` | Icon recommendations | "navigation", "menu", "social" |
| `web` | App interface guidelines | "iOS", "Android", "React Native" |
| `google-fonts` | Individual Google Fonts lookup | "Inter", "Poppins", "Roboto" |
| `gsap` | GSAP animation presets | "scroll animation", "hover effects" |

## Stack-Specific Search

```bash
python3 .skills/ui-ux-pro-max/scripts/search.py "<query>" --stack html-tailwind
```

Available stacks: `html-tailwind`, `react`, `nextjs`, `vue`, `svelte`, `angular`, `flutter`, `swiftui`, `react-native`

## Full Design System

For new projects or complete redesigns:

```bash
python3 .skills/ui-ux-pro-max/scripts/search.py "<description>" --design-system -p "Project Name"
```

## Design Priority Order

1. Accessibility (contrast, keyboard nav, aria-labels)
2. Touch & Interaction (44x44px min, loading feedback)
3. Performance (WebP, lazy loading, CLS < 0.1)
4. Style Selection (match product type, consistency)
5. Layout & Responsive (mobile-first, no horizontal scroll)
6. Typography & Color (16px base, line-height 1.5, semantic tokens)
7. Animation (context-aware timing, reduced-motion support)
8. Forms & Feedback (visible labels, error near field)
9. Navigation Patterns (predictable back, deep linking)
10. Charts & Data (legends, tooltips, accessible colors)

## Examples

### Get Color Palette
```bash
python3 .skills/ui-ux-pro-max/scripts/search.py "education study app modern clean" --domain color
```

### Get Typography
```bash
python3 .skills/ui-ux-pro-max/scripts/search.py "clean readable modern interface" --domain typography
```

### Get UI Style
```bash
python3 .skills/ui-ux-pro-max/scripts/search.py "chat messaging interface streaming" --domain style
```

### Get UX Guidelines
```bash
python3 .skills/ui-ux-pro-max/scripts/search.py "form validation error feedback" --domain ux
```

### Get Icon Recommendations
```bash
python3 .skills/ui-ux-pro-max/scripts/search.py "menu navigation hamburger" --domain icons
```

### Get Full Design System
```bash
python3 .skills/ui-ux-pro-max/scripts/search.py "modern glassmorphism dashboard" --design-system -p "AI Review Assistant"
```

## Usage Rules

1. **Always search before making design decisions** - Don't guess colors, fonts, or spacing
2. **Use `--design-system` for new pages/projects** to get a coherent visual direction
3. **Use specific domains** for targeted concerns (e.g., `--domain ux` for accessibility)
4. **Match the query to the task** - 2-5 meaningful terms per query
5. **Verify results** before applying - check that the style fits the product type
