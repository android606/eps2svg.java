#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TSnn"  # Change nn to the next available test suite number (01, 02, etc.)
TEST_SUITE_NAME="Test Template"  # Replace with a friendly name for the test suite. Include git commit hash of the change being tested if available.
TEST_SUITE_FILENAME=$(basename "${BASH_SOURCE[0]}" .sh)  # Script name without extension, also used for log file name

# Test script template for convert2web project
# DESCRIPTION: Replace with a brief description of what this test script verifies
# This test script is a template for creating new test scripts. It is not intended to be used as a test case.

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Create test directories
create_test_dirs

# Set up log file
LOG_FILE="$LOGS_DIR/$TEST_SUITE_ID-$TEST_SUITE_FILENAME.log"  # Log file name
echo "===== $TEST_SUITE_ID: $TEST_SUITE_FILENAME Tests - $(date) =====" > "$LOG_FILE"

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
print_test_suite_header "$TEST_SUITE_FILENAME" | tee -a "$LOG_FILE"
print_indented "Testing $TEST_SUITE_NAME..." | tee -a "$LOG_FILE"

# Compile the project if needed
cd "$PROJECT_ROOT"
if [ "$TEST_SETUP_COMPLETE" != "true" ]; then
    print_indented "Compiling project..." | tee -a "$LOG_FILE"
    mvn clean package -DskipTests -q >> "$LOG_FILE" 2>&1
fi


# ====== TEST CASE 1 ======
print_test_line "Test case 1: Description of what is being tested" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Run the command being tested
if [ "$TEST_SETUP_COMPLETE" != "true" ]; then
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
        "$TEST_IMAGES_DIR/input_file.eps" \
        "$OUTPUT_DIR/output_file.svg" >> "$LOG_FILE" 2>&1
fi

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
if [ "$TEST_SETUP_COMPLETE" != "true" ]; then
    java -Dconvert2web.someProperty=value -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
        "$TEST_IMAGES_DIR/input_file.eps" \
        "$OUTPUT_DIR/output_file_with_flag.svg" >> "$LOG_FILE" 2>&1
fi

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

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"
