# All-the-DITA conversion failures and Batik fallbacks

Batch: 949 OK, 229 FAIL from `/Users/android/Downloads/All-the-DITA`.
Outputs: `test/output/all-the-dita/`.

## Two files that did not use the VM preamble

These appeared in the OK count but bypassed `AsciiEpsConverter` / Illustrator VM tiers.

| File | Actual format | VM failure | What ran instead |
|------|---------------|------------|------------------|
| `v1843879.eps` | ASCII Illustrator 16 EPS | `Unterminated string at line 3418` (lexer) | Legacy Batik `EpsInterpreter` |
| `rtv_G_ill_secure_sensor.eps` | **PDF 1.6** (not PostScript) | Not Illustrator; parse error `Dictionary key must be a name, got INTEGER` | Batik (garbled tokens; not a reliable SVG) |

Neither should be treated as a successful VM conversion. Only `v1843879` is a real EPS; the secure-sensor file is mislabeled PDF.

## 229 failures

| Category | Count | Cause |
|----------|------:|-------|
| DOS binary Illustrator EPS | 228 | VM could not parse page body |
| PDF with `.eps` extension | 1 | `v1660384.eps` (`file` reports PDF 1.5) |

### Binary Illustrator failures (228)

Tier probe on a representative file (`rtv_G_icon_SensorCountdown_Days_CMYK.eps`):

- `full=false`, `page=false` (all tiers empty)
- FINE logs:
  - Prolog: `Unexpected token: PROCEDURE_END`
  - Page: `Invalid character in hex string` at page lines containing `JcP<@...`

Root cause: newer Illustrator exports embed raster/preview data in DSC sections:

```
%%BeginBinary: 1
img
JcLB&JcP<@...
%%EndBinary
```

The `<` in encoded data starts a PostScript hex string in the lexer; `@` and `&` are invalid hex digits.

Most failing binaries include `%%BeginBinary` blocks. OK binaries (e.g. `v15602737.eps`, AI 25.4) either omit them or convert without hitting this path.

**Fix applied:** `AdobeIllustratorPageRunner.extractPageBody` now strips `(?s)%%BeginBinary:.*?%%EndBinary` (same layer as subset-font stripping). Sample failure converts after rebuild.

### PDF mislabeled as EPS (1)

`Image_Libraries/GC059892-00-BASE_IMAGES/v1660384.eps` is PDF, not EPS. No VM or Batik path applies until PDF input is supported or files are preprocessed.

## Format detection (implemented)

`EpsFormatDetector.validateEpsFile()` runs before any converter. PDF/PNG/JPEG or
missing `%!PS` header returns a clear error; Batik is not used for non-EPS inputs.

Example:

```
Not an EPS file: "v1660384.eps" is a PDF document. Provide a PostScript or DOS binary EPS file.
```

## Retry batches (after fixes)

| Batch | Newly OK | Still fail | Fixes applied |
|-------|--------:|-----------:|---------------|
| retry1 | 21 | 208 | `%%BeginBinary` strip |
| retry2 | 107 | 101 | angle-dict, CSA/image dict, ASCII85 lexer |
| retry3 | 77 | 24 | level-wrapper strip, index-based sanitization (no regex stack overflow) |
| retry4 | 10 | 14 | parser nested-composite fix (reverted broken depth counter) |
| retry4b | 13 | 1 | `stripDictionaryRemnants`, ASCII85 `~>` line strip (many outputs were blank 633 B SVGs) |

**Cumulative (real vector SVG):** 1164 / 1178 (~98.8%). **14** do not produce visible vector art.

| Outcome | Count | Notes |
|---------|------:|-------|
| PDF mislabeled `.eps` | 1 | `v1660384.eps` |
| Pattern/raster + glyph `sh` icons | 13 | SVG shows hatched **raster placeholder** rects from `/PaintProc` `/BBox` (not real pixels) |

The retry4b "successes" for `v16415493` et al. were **false positives**: 633-byte SVGs with only white background fills and clip paths. Art lives in stripped `<< >>` pattern tiles (ASCII85 raster) and `(X)sh` glyph ops not implemented as `show`.

Logs: `test/output/logs/all-the-dita_retry{1,2,3,4,4b}_*.log`

## Remaining failure

| File | Cause |
|------|-------|
| `Image_Libraries/GC059892-00-BASE_IMAGES/v1660384.eps` | PDF 1.5, not PostScript (`EpsFormatDetector` rejects) |

Earlier 24-failure triage (ARRAY_END / DICTIONARY_END / PROCEDURE_END) was resolved by
parser fixes plus stripping dict remnants left when `<< >>` closed on an inner `>>`.

## Rerun parse-failure cleanup

The full rerun (`all-the-dita_rerun_progress.txt`) had 47 parser-class failures:
46 `Unterminated composite object` page-body failures and 1 prolog `PROCEDURE_END`
failure (`v16415501.eps`).

Root cause for the 46 page-body failures: `stripDictionaryRemnants` deleted every
standalone `]` line, which broke valid Illustrator `xsh` spacing arrays whose
closing bracket appears on its own line. The fix keeps those brackets and only
strips actual dictionary tail fragments such as `} >>` and `/O N >>`.

Retry log: `test/output/logs/all-the-dita_parse47_retry_progress.txt`

| Retry | OK | Fail | Notes |
|-------|---:|-----:|-------|
| parse47 | 46 | 1 | All page bodies parse; `v16415501.eps` now fails as only-background/no visible art, not parser failure |

## Sanitization pipeline (`sanitizeIllustratorPageText`)

Applied to prolog and page body before VM parse:

- Font subset blocks (`%ADOBeginSubsetFont`, `%ADOt1write`)
- `%%BeginBinary` image data
- Illustrator `< /Key ... >` and ASCII85 `<~ ... ~>` / `< ... ~>`
- CSA `/Name` and image `/T` dictionaries
- Gradient `ShadingType` + `shfill` blocks
- `levelN{ ... }if` wrapper procedures
- Orphan `]`, `} >>`, `/O N >>` after `<< >>` strip (`stripDictionaryRemnants`)
- Illustrator ASCII85 lines ending with `~>` without `<~` prefix
- Y-flip scale line, subset font markers, etc.

## Follow-up work

1. Prefer VM execution of AGM dicts over text stripping (see `docs/illustrator-conversion-strategy.md`).
2. `v1843879.eps`: unterminated string at line 3418 (ASCII Illustrator 16; Batik fallback today).
3. PDF inputs: reject early (done) or add PDF raster/vector path.

## Quick repro

```bash
mvn package -DskipTests
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
  "/path/to/rtv_G_icon_SensorCountdown_Days_CMYK.eps" /tmp/out.svg
```

With FINE logging:

```bash
java -Djava.util.logging.config.file=/tmp/eps-log.properties -jar target/eps2svg-....jar in.eps out.svg
```
