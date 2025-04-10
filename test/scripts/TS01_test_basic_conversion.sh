#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS01"

# Test basic conversion functionality
# This script tests that the tool can convert simple EPS files to SVG, and checks the validity of the output.

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Run test setup if needed (will be skipped if called from run_tests.sh)
run_test_setup_if_needed

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="Basic Conversion"
TS_ID=$(get_test_suite_id)
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_basic_conversion")"

# Start a new log file
echo "===== $TS_ID: $TEST_SUITE_NAME Tests - $(date) =====" > "$LOG_FILE"

# Count tests and failures
TOTAL_TESTS=0
FAILED_TESTS=0

# Print test suite header
print_test_suite_header "$TEST_SUITE_NAME" | tee -a "$LOG_FILE"
print_indented "Examining basic conversion results..." | tee -a "$LOG_FILE"

# Test basic fill
test_file="$TEST_IMAGES_DIR/test_basic_fill.eps"
output_file="$OUTPUT_DIR/test_basic_fill_normal.svg"
print_test_line "Basic fill test" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

if [ -f "$output_file" ] && [ -s "$output_file" ]; then
    echo "Examining output file: $output_file" >> "$LOG_FILE"
    print_pass | tee -a "$LOG_FILE"
else
    echo "Output file missing or empty: $output_file" >> "$LOG_FILE"
    print_fail | tee -a "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Test basic stroke
test_file="$TEST_IMAGES_DIR/test_basic_stroke.eps"
output_file="$OUTPUT_DIR/test_basic_stroke_normal.svg"
print_test_line "Basic stroke test" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

if [ -f "$output_file" ] && [ -s "$output_file" ]; then
    echo "Examining output file: $output_file" >> "$LOG_FILE"
    print_pass | tee -a "$LOG_FILE"
else
    echo "Output file missing or empty: $output_file" >> "$LOG_FILE"
    print_fail | tee -a "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Basic SVG validation - check if output SVG contains expected elements
print_test_line "SVG validation check" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

echo "Checking if SVG contains required elements..." >> "$LOG_FILE"
if [ -f "$OUTPUT_DIR/test_basic_fill_normal.svg" ] && 
   grep -q "<.*svg" "$OUTPUT_DIR/test_basic_fill_normal.svg" && 
   grep -q "<.*path" "$OUTPUT_DIR/test_basic_fill_normal.svg"; then
    print_pass | tee -a "$LOG_FILE"
    echo "File contains expected SVG elements" >> "$LOG_FILE"
else
    print_fail | tee -a "$LOG_FILE"
    echo "File missing expected SVG elements" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Check if zero tests were performed
if [ $TOTAL_TESTS -eq 0 ]; then
    print_indented "$(print_fail): No tests were performed! This is a test failure." | tee -a "$LOG_FILE"
    echo "No tests were executed. Check if test setup completed correctly." >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    exit 0
else
    exit 1
fi 