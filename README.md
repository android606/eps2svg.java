# eps2svg.java

Java EPS-to-SVG converter focused on Illustrator and ASCII PostScript EPS.

The converter interprets EPS into a renderer-neutral document model, then writes SVG. DOS binary EPS files are unpacked and routed through the same PostScript pipeline when possible.

## Build

```bash
mvn package
```

## Run

```bash
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar input.eps output.svg
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar --help
```

All value options use `--name=value` syntax.

Optional display size limits (proportional; `viewBox` unchanged):

```bash
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
  --min-width=100 --min-height=100 --max-width=8.5in --max-height=11in \
  input.eps output.svg
```

Batch mode (one JVM, recursive `**/*.eps` by default):

```bash
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
  --batch --substitute-fonts=no /path/to/eps-dir /path/to/svg-dir
```

Useful batch options:

```bash
--glob='*.eps' --font-metrics=relative --trace-source
```

Unsupported or mislabeled inputs, such as PDF files with an `.eps` extension, are rejected with a clear error.

## Plan

See [docs/ascii-eps-interpreter-plan.md](docs/ascii-eps-interpreter-plan.md).
