# EPS2SVG Agent Notes

## Current Focus

The first milestone is an ASCII EPS/PostScript interpreter that builds a Java in-memory graphics document. SVG should be generated from that document model, not directly from interpreter callbacks.

Read [docs/ascii-eps-interpreter-plan.md](docs/ascii-eps-interpreter-plan.md) before implementing interpreter or renderer changes.

Illustrator EPS uses a three-tier pipeline (full prolog, prolog+page, page+shorthand). See [docs/illustrator-conversion-strategy.md](docs/illustrator-conversion-strategy.md).

All-the-DITA batch failure analysis: [docs/all-the-dita-failures-analysis.md](docs/all-the-dita-failures-analysis.md).

## Lessons Learned

1. The existing code can convert selected fixtures, but it is renderer-coupled and regression-driven.
2. Do not add more file-specific SVG path fixes. Fix the interpreter, graphics state, document model, or renderer instead.
3. Binary DOS EPS goes through `BinaryEpsConverter` only; Ghostscript was removed. Unsupported binary files error out.
4. Use Maven for builds and tests.

## Basic Handoff Steps

1. Pick one work item from the milestone plan.
2. Add or update JUnit tests for that layer first.
3. Implement the smallest code change that makes the tests pass.
4. Run `mvn test`.
5. Run `mvn package` before handing off a runnable change.
6. Run `test/run_tests.sh` for broader regression coverage when the change touches CLI output or existing fixture behavior.
