# Test Scripts for EPS to SVG Conversion

This directory contains automated test scripts for the EPS to SVG conversion project.

## Test Organization

- `test_utils.sh`: Common utilities used by all test scripts
- `test_template.sh`: Template for creating new test scripts
- `run_tests.sh`: Main script to run all tests (in parent directory)

## Available Tests

- `test_setup.sh`: Verifies test environment setup and project prerequisites
- `test_basic_conversion.sh`: Tests basic EPS to SVG conversion functionality
- `test_binary_eps.sh`: Tests binary EPS file handling
- `test_viewbox.sh`: Tests SVG viewBox correctness 
- `test_path_bounds.sh`: Tests path boundaries in the generated SVG
- `test_visual.sh`: Tests visual quality of the conversion
- `test_curve_operators.sh`: Tests curve operators functionality
- `test_curve_preservation.sh`: Tests curve preservation options
- `test_subtle_curves.sh`: Tests handling of subtle curves specifically

## Creating a New Test Script

1. Copy the template script:
   ```bash
   cp test_template.sh test_your_feature.sh
   ```

2. Update the script header with a description of what the test verifies:
   ```bash
   # DESCRIPTION: Replace with a brief description of what this test script verifies
   ```

3. Replace placeholder values:
   - `TEST_SUITE_NAME="Template Test Suite"` - Replace with your test name
   - `LOG_FILE="$LOGS_DIR/$(get_log_filename "test_template")"` - Replace "test_template" with your test name
   - `print_indented "Testing FEATURE_NAME..."` - Replace FEATURE_NAME with your feature

4. Implement test cases following the template patterns:
   - Basic test: File exists and conversion works
   - Property flag test: Testing with various Java system properties
   - Regression test: Compare against expected reference outputs

5. Remember to include command-line help by documenting any flags:
   ```bash
   # Usage: ./test_your_feature.sh [--debug]
   ```

## Tips for Writing Effective Tests

1. **Test isolation**: Each test should be independent and not rely on other tests
2. **Proper cleanup**: Clean up any temporary files created during testing
3. **Verbose logging**: Use the LOG_FILE to capture detailed information
4. **Regression protection**: Include reference files when possible
5. **Debug options**: Include a debug flag for easier troubleshooting

## Running Tests

Individual test:
```bash
./test_your_feature.sh
```

All tests:
```bash
cd ..
./run_tests.sh
```

## Test Output

Test results are stored in the logs directory with details about each test run.
The output SVG files are stored in the test/output directory for manual inspection. 