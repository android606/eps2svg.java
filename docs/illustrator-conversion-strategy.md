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

## AGM binary tiles (page body)

Illustrator page bodies keep `<< /W /H … >>` image dictionaries and `%%BeginBinary` … `%%EndBinary` blocks.

- `PostScriptLexer` emits one `BEGIN_BINARY_INVOKE` token per block (operator + ASCII85 payload).
- `PostScriptParser` builds `PsValue.AgmBinaryInvokeValue`; the image dict must already be on the operand stack.
- `AgmImagePaint` decodes via `AgmTileDecoder` and appends `GraphicsCommand.EmbeddedImage` in stream order.
- `sanitizeIllustratorPageBody` strips color-server `<< /Name (…) >>` blocks (not static dicts) but does not strip image dicts or binaries.
- If the VM records no tiles, `IllustratorAgmImageExtractor.mergeIntoDocument` remains a fallback.

## AGM `/DS` image data handling

Illustrator image dictionaries use `/DS` as the data source for `img`, `sepimg`, and `idximg`.
The current implementation handles the common forms needed by Illustrator EPS output:

- Inline sample data: `/DS <~...~>` is decoded from the dictionary first.
- Empty `%%BeginBinary` wrapper: if the wrapper only invokes `sepimg`, inline `/DS` supplies the samples.
- Common executable data source: `/DS cf /ASCII85Decode fl /RunLengthDecode filter` is modeled as `currentfile` bytes from the following `%%BeginBinary` payload, decoded as ASCII85 then RunLength.
- Short binary payloads are valid; RunLength can represent solid tiles in only a few bytes, so payload length is not used as a validity shortcut.

Future work: implement complete `/DS` execution in the PostScript VM instead of recognizing only these forms. Missing pieces include:

1. Real `currentfile` stream objects that preserve unread bytes after tokenization.
2. PostScript filter objects (`ASCII85Decode`, `RunLengthDecode`, and any other filters Illustrator emits).
3. Lazy byte consumption by image operators from `/DS` streams at paint time.
4. Correct execution of arbitrary `/DS` procedures or names beyond `cf ... fl ... filter`.
5. Clear diagnostics when a `/DS` program is unsupported, including operator, dimensions, and dictionary summary.

## Entry points

- `BinaryEpsConverter.tryConvert` → `AdobeIllustratorPageRunner.convertIllustratorPostScript`
- `AsciiEpsConverter.convertToDocument` → same for Illustrator ASCII/binary-extracted PS
