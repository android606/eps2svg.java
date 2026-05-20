# ASCII EPS Interpreter Milestone Plan

## Goal

Build a standards-focused ASCII EPS/PostScript interpreter in Java that produces a renderer-neutral in-memory document model. SVG is the first output renderer; later renderers should consume the same model without reinterpreting EPS.

Binary EPS cleanup and Ghostscript removal are deferred. For now, any binary EPS path should be treated only as compatibility plumbing that eventually feeds extracted ASCII PostScript into the same interpreter.

## Target Architecture

```mermaid
flowchart LR
    inputEps[EPS Input] --> converter[EpsToSvgConverter]
    converter --> lexer[PostScript Lexer]
    lexer --> parser[PostScript Parser]
    parser --> vm[PostScript VM]
    vm --> model[EpsDocument]
    model --> svgRenderer[SvgRenderer]
    svgRenderer --> svg[SVG Output]
```

Core layers:

- `PostScriptLexer`: stream-oriented tokenization for ASCII PostScript/EPS.
- `PostScriptParser`: converts tokens into executable and literal PostScript objects.
- `PostScriptVm`: executes PostScript objects with operand, execution, dictionary, and graphics stacks.
- `EpsDocument`: renderer-neutral memory object containing metadata, bounding boxes, resources, and graphics commands.
- `SvgRenderer`: renders `EpsDocument` to valid SVG.

## Work Items

### 1. Scope And Acceptance Criteria

- Define milestone 1 as ASCII EPSF-3.0 interpretation into `EpsDocument`, with SVG as the first renderer.
- Document non-goals: binary EPS replacement, page-device behavior, and any advanced features not selected for the first pass.
- Add a conformance matrix listing operator families, implementation status, fixtures, and assertions.

Verification:

- A future agent can decide whether a fixture failure is in scope by reading the docs.
- Every supported operator family has at least one JUnit fixture.

### 2. Lexer And Parser

- Tokenize numbers, booleans, executable names, literal names, literal strings, hex strings, arrays, dictionaries, procedures, marks, comments, and DSC comments.
- Support nested strings, escaped parentheses, octal escapes, line continuations, delimiter rules, and `%` comments.
- Keep input stream-based so extracted binary EPS PostScript can reuse the same path later.

Verification:

- Unit tests cover nested strings, escaped delimiters, arrays, dictionaries, procedures, comments, DSC lines, and representative EPS prologs.
- Parser tests verify executable vs literal names and nested arrays/procedures.

### 3. PostScript VM Core

- Replace untyped `Stack<Object>` behavior with explicit PostScript object types.
- Implement operand, execution, dictionary, and graphics stacks.
- Implement deterministic typed errors instead of silently skipping in-scope operators.
- Implement dictionary operators: `dict`, `begin`, `end`, `def`, `load`, `where`, `known`, `currentdict`, `systemdict`, `userdict`.
- Implement stack operators: `pop`, `dup`, `exch`, `copy`, `index`, `roll`, `clear`, `count`, `mark`, `cleartomark`, `counttomark`.
- Implement arithmetic/comparison/logical operators needed by EPS prologs.
- Implement procedure/control flow operators: `exec`, `if`, `ifelse`, `for`, `repeat`, `loop`, `exit`, and `stopped` if fixtures require it.
- Implement `bind` well enough for bound EPS prolog procedures.

Verification:

- VM tests assert stack-before and stack-after behavior for every supported non-graphics operator.
- Malformed programs fail with typed interpreter errors.
- In-scope EPS fixtures run without `Unhandled operator` warnings.

### 4. Renderer-Neutral Document Model

- Add model classes such as `EpsDocument`, `BoundingBox`, `GraphicsState`, `Path`, `PathSegment`, `PaintStyle`, `StrokeStyle`, `Clip`, `Transform`, `TextRun`, and image command types.
- Store path geometry separately from paint operations.
- Preserve EPS coordinate and CTM semantics in the model.
- Keep fill and stroke style independent.
- Store DSC metadata, especially `%%BoundingBox` and `%%HiResBoundingBox`.

Verification:

- A simple fill EPS produces one filled path command and the expected bounding box.
- Stroke/fill color fixtures produce distinct style values.
- Transform fixtures assert model CTM behavior without parsing SVG text.

### 5. EPS Graphics Operators

- Implement path operators: `newpath`, `moveto`, `rmoveto`, `lineto`, `rlineto`, `curveto`, `rcurveto`, `closepath`, `currentpoint`, `arc`, `arcn`, `arct`.
- Implement paint operators: `stroke`, `fill`, `eofill`, `clip`, `eoclip`, `rectfill`, `rectstroke`, `rectclip`.
- Implement graphics state operators: `gsave`, `grestore`, `setlinewidth`, `setlinecap`, `setlinejoin`, `setmiterlimit`, `setdash`, `setflat`, `setgray`, `setrgbcolor`, `setcmykcolor`.
- Implement transform operators: `matrix`, `initmatrix`, `currentmatrix`, `setmatrix`, `concat`, `concatmatrix`, `translate`, `scale`, `rotate`, `transform`, `itransform`, `dtransform`, `idtransform`, `invertmatrix` as needed by fixtures.
- Decide explicitly whether text and image operators are milestone 1 requirements.

Verification:

- Each supported graphics operator has a model-level fixture test.
- `gsave/grestore` restores graphics state without deleting already-emitted document commands.
- File-specific coordinate hacks are removed after general curve/path tests pass.

### 6. SVG Renderer

- Implement `SvgRenderer` from `EpsDocument`.
- Generate valid XML and SVG namespace declarations.
- Emit correct `viewBox`, dimensions, path data, fill rules, stroke attributes, clipping paths, transforms, and supported text/image elements.
- Batik may be used inside the renderer, but VM and model packages must not import Batik.
- Preserve CLI behavior: `input.eps output.svg`.

Verification:

- Renderer tests assert valid XML, root attributes, namespace, `viewBox`, path data, fill-rule, stroke attributes, clips, and transforms.
- End-to-end fixture tests parse EPS, build `EpsDocument`, render SVG, and validate SVG structure.

### 7. Compliance Test Suite

- Make Maven/JUnit tests the primary handoff contract.
- Keep shell tests as integration and regression smoke tests.
- Group EPS fixtures by behavior: DSC, stack/dictionary/procedure, paths, transforms, clipping, colors, strokes, curves, text, images if in scope, and malformed input.
- Add a rule that every fixed bug gets a fixture that fails before the fix and passes after it.

Verification:

- `mvn test` runs without Ghostscript.
- `mvn package` produces the runnable jar.
- `test/run_tests.sh` passes or documents intentionally deferred failures.
- Test failures identify the failed layer: lexer/parser, VM, model, renderer, or CLI.

## Completion Criteria

Milestone 1 is complete when ASCII EPS fixtures produce correct `EpsDocument` objects and standards-compliant SVG without fixture-specific output patches. Binary EPS replacement is explicitly not required for this milestone.
