# Illustrator EPS Conversion Strategy

## Priority

Faithful interpretation over speed. The pipeline always attempts the most complete
execution path first and only narrows scope when the VM cannot run further.

## Tier order (Adobe Illustrator EPS)

1. **Full embedded PostScript** (`runFullPostScript`)
   - Run AGM prolog leniently (partial operator support).
   - `PostScriptVm.resetForPageBody()` clears operand/graphics/dict stacks before the page.
   - Reinforce file `ldf` shorthand aliases, then run the sanitized page body (trailer skipped).
   - Goal: extend lenient prolog until trailer/AGM cleanup can run without losing paths.

2. **Prolog then page body** (`runPrologThenPageBody`)
   - Same execution path as tier 1 (prolog lenient, reset VM, shorthand preamble, page body).

3. **Page body + shorthand preamble** (`runPageBody`) — fallback only
   - Extract page content only.
   - Build preamble from prolog `ldf` lines (`/short /long ldf`) where `long` maps to a VM operator.
   - Merge with static fallbacks in `AdobeIllustratorShorthand` for operators not found in the file.

Non-Illustrator binary EPS still uses lenient full embedded PostScript only.

## Shorthand sources

| Source | When used |
|--------|-----------|
| AGM prolog at runtime | Tiers 1–2 |
| `ldf` scan of prolog text | Tier 3 |
| `AdobeIllustratorShorthand.STATIC_BODY` | Tier 3 gaps |

Canonical `ldf` mappings live in the EPS prolog (e.g. `test/test_images/reuse_1.eps` around the AGM block). Example: `/@ /stroke ldf`, `/mo /moveto ldf`.

## AGM roadmap

Lenient full-prolog execution is intentional technical debt: it records graphics when possible while the VM gains AGM operators (`currentfile`, color servers, `ldf`/`bdf` helpers, subset fonts, etc.). As coverage improves, tiers 2–3 should rarely run.

## Entry points

- `BinaryEpsConverter.tryConvert` → `AdobeIllustratorPageRunner.convertIllustratorPostScript`
- `AsciiEpsConverter.convertToDocument` → same for Illustrator ASCII/binary-extracted PS
