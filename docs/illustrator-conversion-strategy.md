# Illustrator EPS Conversion Strategy

## Priority

Faithful interpretation over speed. The pipeline always attempts the most complete
execution path first and only narrows scope when the VM cannot run further.

## Tier order (Adobe Illustrator EPS)

1. **Full embedded PostScript** (`runFullPostScript`)
   - Execute the entire file: AGM procsets, setup, page body, trailer.
   - Uses `PostScriptVm.executeAllLenient` so partial AGM support still records paths.
   - Goal: eventual full AGM compatibility without a separate page-body shortcut.

2. **Prolog then page body** (`runPrologThenPageBody`)
   - Execute from `%!PS` through `%%EndPageSetup` / `%%EndSetup`, then the page slice in the same VM.
   - Shorthand operators (`mo`, `lw`, `@`, …) come from the file prolog, not a static table.

3. **Page body + shorthand preamble** (`runPageBody`)
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
