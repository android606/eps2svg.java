# eps2svg.java

Java EPS-to-SVG conversion experiment.

The current milestone is an ASCII EPS/PostScript interpreter that builds a renderer-neutral Java document model, with SVG as the first output renderer.

## Build

```bash
mvn package
```

## Run

```bash
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar input.eps output.svg
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar --help
```

Optional display size limits (proportional; `viewBox` unchanged):

```bash
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
  --min-width 100 --min-height 100 --max-width 8.5in --max-height 11in \
  input.eps output.svg
```

## Plan

See [docs/ascii-eps-interpreter-plan.md](docs/ascii-eps-interpreter-plan.md).
