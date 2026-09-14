# AI Review Assistant - Design System

## Pattern: Product Demo + Features
- Clear feature hierarchy
- One key message per card
- Strong CTA repetition
- Sections: Hero → Features → Use Cases → Social Proof → CTA

## Style: Flat Design (AI-Native UI)
- 2D, minimalist, bold colors, no shadows
- Clean lines, simple shapes, typography-focused
- Streaming text animations, typing indicators
- Context cards, smooth reveals

## Colors
| Token | Value | Usage |
|-------|-------|-------|
| --color-primary | #0D9488 | Primary actions, links |
| --color-on-primary | #000000 | Text on primary |
| --color-secondary | #14B8A6 | Secondary elements |
| --color-accent | #EA580C | CTA, highlights |
| --color-background | #F0FDFA | Page background |
| --color-foreground | #134E4A | Main text |
| --color-card | #FFFFFF | Card background |
| --color-muted | #E8F1F4 | Muted sections |
| --color-border | #99F6E4 | Borders |
| --color-destructive | #DC2626 | Errors, delete |

## Typography
- Font: Inter (300, 400, 500, 600, 700)
- Base size: 16px
- Line height: 1.5
- Headings: 600-700 weight

## Icons
- Library: Lucide (SVG)
- Style: Outline, 20px
- Navigation: house, chat-circle, book-open, quiz, calendar, bar-chart

## Key Effects
- No gradients/shadows (flat design)
- Simple hover (color/opacity shift)
- Fast loading, clean transitions (150-200ms ease)
- Streaming text animation for AI responses

## Accessibility
- Contrast 4.5:1 minimum
- Focus states visible
- Keyboard navigation
- prefers-reduced-motion respected
