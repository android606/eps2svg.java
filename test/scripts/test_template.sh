#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS99"  # Template has special ID

# Test script template for convert2web project
# DESCRIPTION: Replace with a brief description of what this test script verifies
# 
# Usage: ./test_template.sh [--debug]
#   --debug: Enable verbose debug output

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="Template Test Suite"  # REPLACE with your test suite name
TS_ID=$(get_test_suite_id)
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_template")"  # REPLACE 'test_template' with your test name

# Start a new log file
echo "===== $TS_ID: $TEST_SUITE_NAME Tests - $(date) =====" > "$LOG_FILE"

# Count tests and failures
TOTAL_TESTS=0
FAILED_TESTS=0

# Process command line arguments
DEBUG_MODE=false
for arg in "$@"; do
  case $arg in
    --debug)
      DEBUG_MODE=true
      ;;
  esac
done

if [ "$DEBUG_MODE" = true ]; then
  print_indented "Debug mode enabled" | tee -a "$LOG_FILE"
fi

# Print test suite header
print_test_suite_header "$TEST_SUITE_NAME" | tee -a "$LOG_FILE"
print_indented "Testing FEATURE_NAME..." | tee -a "$LOG_FILE"  # REPLACE with your feature name

# Ensure we have a valid jar file
cd "$PROJECT_ROOT"
if [ ! -f "target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    print_indented "Compiling project..." | tee -a "$LOG_FILE"
    mvn clean package -DskipTests -q >> "$LOG_FILE" 2>&1
fi

# Optional: Copy test files to test_images if they don't exist there
# Uncomment and modify this section if needed
# if [ ! -f "$TEST_IMAGES_DIR/your_test_file.eps" ]; then
#     print_indented "Copying test files to test_images directory..." | tee -a "$LOG_FILE"
#     cp "$TEST_DIR/your_test_dir/"*.eps "$TEST_IMAGES_DIR/" >> "$LOG_FILE" 2>&1
# fi

# ====== TEST CASE 1 ======
print_test_line "Test case 1: Description of what is being tested" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Run the command being tested
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
    "$TEST_IMAGES_DIR/input_file.eps" \
    "$OUTPUT_DIR/output_file.svg" >> "$LOG_FILE" 2>&1

# Verify the results
if [ -f "$OUTPUT_DIR/output_file.svg" ] && [ -s "$OUTPUT_DIR/output_file.svg" ]; then
    # Optional: More specific validation
    # if grep -q "expected content" "$OUTPUT_DIR/output_file.svg"; then
        print_pass | tee -a "$LOG_FILE"
    # else
    #     print_fail | tee -a "$LOG_FILE"
    #     echo "Expected content not found in output file" >> "$LOG_FILE"
    #     FAILED_TESTS=$((FAILED_TESTS+1))
    # fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Output file missing or empty" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# ====== TEST CASE 2 ======
print_test_line "Test case 2: Description with system property flag" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Run the command with a Java system property flag
java -Dconvert2web.someProperty=value -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
    "$TEST_IMAGES_DIR/input_file.eps" \
    "$OUTPUT_DIR/output_file_with_flag.svg" >> "$LOG_FILE" 2>&1

# Verify the results
if [ -f "$OUTPUT_DIR/output_file_with_flag.svg" ] && [ -s "$OUTPUT_DIR/output_file_with_flag.svg" ]; then
    # Validate the expected difference when the flag is used
    # Example: check for specific differences between files
    diff_count=$(diff -y --suppress-common-lines "$OUTPUT_DIR/output_file.svg" "$OUTPUT_DIR/output_file_with_flag.svg" | wc -l)
    if [ $diff_count -gt 0 ]; then
        print_pass | tee -a "$LOG_FILE"
        echo "Found $diff_count differences as expected" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "No differences found despite flag being used" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Output file with flag missing or empty" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# ====== TEST CASE with REGRESSION TEST ======
print_test_line "Regression test: Ensure feature X still works" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Create or use a reference file that represents the expected output
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
    "$TEST_IMAGES_DIR/regression_test.eps" \
    "$OUTPUT_DIR/regression_output.svg" >> "$LOG_FILE" 2>&1

# Check output against expected results (use a reference file if available)
if [ -f "$OUTPUT_DIR/regression_output.svg" ] && [ -s "$OUTPUT_DIR/regression_output.svg" ]; then
    if [ -f "$TEST_DIR/references/expected_regression_output.svg" ]; then
        # Option 1: Exact comparison (strict)
        if diff "$OUTPUT_DIR/regression_output.svg" "$TEST_DIR/references/expected_regression_output.svg" >> "$LOG_FILE" 2>&1; then
            print_pass | tee -a "$LOG_FILE"
        else
            print_fail | tee -a "$LOG_FILE"
            echo "Regression test failed - output differs from expected reference" >> "$LOG_FILE"
            FAILED_TESTS=$((FAILED_TESTS+1))
        fi
        
        # Option 2: Check for presence of specific patterns (more flexible)
        # expected_patterns=("pattern1" "pattern2" "pattern3")
        # failed=false
        # for pattern in "${expected_patterns[@]}"; do
        #     if ! grep -q "$pattern" "$OUTPUT_DIR/regression_output.svg"; then
        #         echo "Missing expected pattern: $pattern" >> "$LOG_FILE"
        #         failed=true
        #     fi
        # done
        # if [ "$failed" = true ]; then
        #     print_fail | tee -a "$LOG_FILE"
        #     FAILED_TESTS=$((FAILED_TESTS+1))
        # else
        #     print_pass | tee -a "$LOG_FILE"
        # fi
    else
        # No reference file, just check if it was created
        print_pass | tee -a "$LOG_FILE"
        print_indented "Warning: No reference file for regression test" | tee -a "$LOG_FILE"
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Regression output file missing or empty" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    print_indented "All tests passed" | tee -a "$LOG_FILE"
    exit 0
else
    print_indented "$FAILED_TESTS tests failed" | tee -a "$LOG_FILE"
    exit 1
fi 