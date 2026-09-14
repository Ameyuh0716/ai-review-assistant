---
name: ui-ux
description: 'Search UI/UX design database for colors, typography, styles, UX guidelines. Usage: /ui-ux <query> --domain <domain>'
---

# UI/UX Design Search

Search the ui-ux-pro-max design database for informed design decisions.

## Usage

```bash
python3 .skills/ui-ux-pro-max/scripts/search.py "$ARGUMENTS"
```

## Available Domains

- `product` - Product type recommendations
- `style` - UI styles (glassmorphism, minimalism, brutalism)
- `typography` - Font pairings with Google Fonts
- `color` - Color palettes by product type
- `landing` - Page structure and CTA strategies
- `chart` - Chart types and libraries
- `ux` - Best practices (119 guidelines)
- `icons` - Icon recommendations
- `web` - App interface guidelines
- `google-fonts` - Individual Google Fonts lookup
- `gsap` - GSAP animation presets

## Examples

```bash
/ui-ux "education app" --domain color
/ui-ux "chat interface" --domain style
/ui-ux "form validation" --domain ux
/ui-ux "modern dashboard" --design-system -p "My App"
```

## Design Priority

1. Accessibility (contrast, keyboard nav)
2. Touch & Interaction (44x44px min)
3. Performance (WebP, lazy loading)
4. Style Selection (match product type)
5. Layout & Responsive (mobile-first)
6. Typography & Color (16px base, line-height 1.5)
7. Animation (context-aware timing)
8. Forms & Feedback (visible labels)
9. Navigation Patterns (predictable back)
10. Charts & Data (legends, tooltips)
