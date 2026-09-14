# AI Review Assistant - Copilot Instructions

## Project Overview
This is a Spring Boot 3.2 + Thymeleaf web application for AI-powered exam review and study assistance. It includes:
- Chat interface with streaming AI responses
- Course management
- Knowledge base (document upload + vector search)
- Quiz generation and grading
- Study plan generation
- Statistics and wrong answer book

## Tech Stack
- Backend: Java 17, Spring Boot 3.2, MyBatis-Plus, PostgreSQL
- Frontend: Thymeleaf templates, vanilla JS, CSS custom properties
- AI: Streaming SSE via Spring WebFlux

## UI/UX Design Skill: ui-ux-pro-max

When working on **any UI/visual design task**, use the ui-ux-pro-max skill database to make informed design decisions.

### How to Query the Design Database

Run the search tool from the project root:

```bash
python3 .skills/ui-ux-pro-max/scripts/search.py "<query>" --domain <domain>
```

**Available domains:**
- `product` - Product type recommendations (SaaS, e-commerce, portfolio)
- `style` - UI styles (glassmorphism, minimalism, brutalism) + CSS keywords
- `typography` - Font pairings with Google Fonts imports
- `color` - Color palettes by product type
- `landing` - Page structure and CTA strategies
- `chart` - Chart types and library recommendations
- `ux` - Best practices and anti-patterns (119 guidelines)
- `icons` - Icon recommendations (Phosphor, Heroicons, Lucide)
- `react` - React/Next.js performance patterns
- `web` - App interface guidelines (iOS/Android/React Native)
- `google-fonts` - Individual Google Fonts lookup
- `gsap` - GSAP animation presets

**Stack-specific search:**
```bash
python3 .skills/ui-ux-pro-max/scripts/search.py "<query>" --stack html-tailwind
```

Available stacks: `html-tailwind`, `react`, `nextjs`, `vue`, `svelte`, `angular`, `flutter`, `swiftui`, `react-native`

### Usage Rules

1. **Always search before making design decisions** - Don't guess colors, fonts, or spacing
2. **Use `--design-system` for new pages/projects** to get a coherent visual direction
3. **Use specific domains** for targeted concerns (e.g., `--domain ux` for accessibility)
4. **Match the query to the task** - 2-5 meaningful terms per query
5. **Verify results** before applying - check that the style fits the product type

### Design Priority (follow in order)
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

### Quick Examples

```bash
# Get a full design system for a SaaS product
python3 .skills/ui-ux-pro-max/scripts/search.py "SaaS dashboard analytics minimal" --design-system -p "AI Review Assistant"

# Find the best color palette for a study app
python3 .skills/ui-ux-pro-max/scripts/search.py "education study app modern clean" --domain color

# Get typography recommendations
python3 .skills/ui-ux-pro-max/scripts/search.py "clean readable modern interface" --domain typography

# Find UI patterns for a chat interface
python3 .skills/ui-ux-pro-max/scripts/search.py "chat messaging interface streaming" --domain style

# Get UX guidelines for forms
python3 .skills/ui-ux-pro-max/scripts/search.py "form validation error feedback" --domain ux

# Find icons for the navigation
python3 .skills/ui-ux-pro-max/scripts/search menu navigation hamburger" --domain icons
```

## Code Style
- Use CSS custom properties (variables) defined in `common.css`
- Follow the existing component patterns (`.card`, `.btn`, `.form-control`)
- Keep inline styles minimal - prefer CSS classes
- Use semantic HTML elements where appropriate
- Maintain consistent spacing using the design token scale
