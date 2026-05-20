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
```

## Plan

See [docs/ascii-eps-interpreter-plan.md](docs/ascii-eps-interpreter-plan.md).
