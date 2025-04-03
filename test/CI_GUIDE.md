# CI Guide for EPS to SVG Conversion Tool

This document provides guidance on continuous integration (CI) best practices for the EPS to SVG conversion tool.

## Test Framework Overview

The test framework consists of several test scripts that validate different aspects of the EPS to SVG conversion:

1. **Basic Conversion Tests** - Verify that the tool can convert EPS files to SVG
2. **Binary EPS Tests** - Test support for binary EPS files with various conversion methods
3. **ViewBox Tests** - Ensure the SVG output has the correct viewbox dimensions
4. **Path Boundary Tests** - Check that paths are properly contained within the viewbox
5. **Visual Quality Tests** - Detect issues like skewed or upside-down rendering

All tests return exit code `0` for passing tests and non-zero for failures.

## Running Tests Locally

To run all tests locally:

```bash
cd /path/to/project
./test/run_tests.sh
```

To run individual test suites:

```bash
cd /path/to/project
./test/scripts/test_basic_conversion.sh
```

## CI Setup Best Practices

### 1. Gitea Actions Integration

When integrating with Gitea Actions, consider the following best practices:

- Use the main test script as the entry point: `./test/test_all.sh`
- Cache Maven dependencies to speed up builds
- Save test artifacts for review (SVG outputs)

Example Gitea Actions workflow:

```yaml
name: EPS to SVG Conversion Tests

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v2
    
    - name: Set up JDK 11
      uses: actions/setup-java@v2
      with:
        java-version: '11'
        distribution: 'adopt'
        cache: maven
    
    - name: Install GhostScript
      run: sudo apt-get update && sudo apt-get install -y ghostscript
    
    - name: Run Tests
      run: ./test/test_all.sh
    
    - name: Archive test results
      uses: actions/upload-artifact@v2
      with:
        name: test-output
        path: test/output/
```

### 2. Environment-Specific Dependencies

The conversion tool depends on GhostScript for certain operations. Ensure this is installed in your CI environment:

- **Linux/Ubuntu**: `apt-get install ghostscript`
- **Windows**: GhostScript should be in PATH (gswin64c.exe)
- **macOS**: `brew install ghostscript`

### 3. Test Data Management

- Keep reference test files in version control
- Consider using Git LFS for larger binary test files
- Document what each test file is designed to verify

### 4. Essential CI Tests

The following tests should always be run in CI:

1. Basic format conversion tests
2. Binary EPS file support tests 
3. ViewBox correctness tests

### 5. Test Result Reporting

The test script outputs clear PASS/FAIL results with appropriate exit codes for CI integration.

For more detailed reporting, consider:

- Generating JUnit XML reports
- Capturing and comparing visual snapshots of SVG outputs
- Adding SVG validation using tools like svglint

### 6. Handling Platform Differences

The EPS to SVG converter may behave differently across platforms due to:

- Path separators (Windows vs Unix)
- GhostScript implementation differences
- Font rendering differences

Design tests with these differences in mind and use platform-specific assertions when needed.

## Adding New Tests

To add new tests:

1. Create appropriate test EPS files in `test/test_images/`
2. Add reference SVG files if needed for comparison
3. Create a new test script in `test/scripts/` following existing patterns
4. Update the master test script if necessary

## Common CI Issues and Solutions

1. **GhostScript not found**: Ensure GhostScript is installed and in PATH
2. **Permission denied**: Make sure test scripts are executable (`chmod +x *.sh`)
3. **Font differences**: Use basic fonts or embed them in test files
4. **Platform-specific paths**: Use `$SCRIPT_DIR` pattern for path resolution

## Future CI Enhancements

Consider these enhancements for the CI system:

1. Parallel test execution for faster CI builds
2. Visual regression testing with screenshot comparison
3. Automated benchmarking for performance regression detection
4. Cross-platform testing (Windows, Linux, macOS)
5. Container-based testing with Docker to ensure consistent environments 