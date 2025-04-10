#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS07"

# Test script for curve preservation options
# This script tests whether the converter preserves or flattens curve operators

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="Curve Operators Preservation"
TS_ID=$(get_test_suite_id)
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_curve_preservation")"

# Start a new log file
echo "===== $TS_ID: $TEST_SUITE_NAME Tests - $(date) =====" > "$LOG_FILE"

# Count tests and failures
TOTAL_TESTS=0
FAILED_TESTS=0

# Print test suite header
print_test_suite_header "$TEST_SUITE_NAME" | tee -a "$LOG_FILE"
print_indented "Testing curve preservation options..." | tee -a "$LOG_FILE"

# Ensure we have a valid jar file
cd "$PROJECT_ROOT"
if [ ! -f "target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    print_indented "Compiling project..." | tee -a "$LOG_FILE"
    mvn clean package -DskipTests -q >> "$LOG_FILE" 2>&1
fi

# Copy curve test EPS files to test_images if they don't exist there
if [ ! -f "$TEST_IMAGES_DIR/curve_demo.eps" ]; then
    print_indented "Copying curve test files to test_images directory..." | tee -a "$LOG_FILE"
    cp "$TEST_DIR/curve_tests/"*.eps "$TEST_IMAGES_DIR/" >> "$LOG_FILE" 2>&1
fi

# Test with curve demonstration file
print_test_line "Converting curve_demo.eps with curve preservation (default)" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$TEST_IMAGES_DIR/curve_demo.eps" "$OUTPUT_DIR/curve_demo_preserved.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/curve_demo_preserved.svg" ] && [ -s "$OUTPUT_DIR/curve_demo_preserved.svg" ]; then
    print_pass | tee -a "$LOG_FILE"
else
    print_fail | tee -a "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

print_test_line "Converting curve_demo.eps with curve flattening enabled" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))
java -Dconvert2web.flattenCurves=true -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$TEST_IMAGES_DIR/curve_demo.eps" "$OUTPUT_DIR/curve_demo_flattened.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/curve_demo_flattened.svg" ] && [ -s "$OUTPUT_DIR/curve_demo_flattened.svg" ]; then
    print_pass | tee -a "$LOG_FILE"
else
    print_fail | tee -a "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Test with arc test file
print_test_line "Converting arc_test.eps with curve preservation (default)" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$TEST_IMAGES_DIR/arc_test.eps" "$OUTPUT_DIR/arc_test_preserved.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/arc_test_preserved.svg" ] && [ -s "$OUTPUT_DIR/arc_test_preserved.svg" ]; then
    print_pass | tee -a "$LOG_FILE"
else
    print_fail | tee -a "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

print_test_line "Converting arc_test.eps with curve flattening enabled" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))
java -Dconvert2web.flattenCurves=true -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$TEST_IMAGES_DIR/arc_test.eps" "$OUTPUT_DIR/arc_test_flattened.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/arc_test_flattened.svg" ] && [ -s "$OUTPUT_DIR/arc_test_flattened.svg" ]; then
    print_pass | tee -a "$LOG_FILE"
else
    print_fail | tee -a "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Check if curves were preserved/flattened correctly
print_test_line "Verifying curve preservation in preserved files" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

curves_preserved=$(grep -c "C" "$OUTPUT_DIR/arc_test_preserved.svg")
echo "arc_test_preserved.svg curve commands: $curves_preserved" >> "$LOG_FILE"

if [ $curves_preserved -gt 0 ]; then
    print_pass | tee -a "$LOG_FILE"
else
    print_fail | tee -a "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

print_test_line "Verifying curve flattening in flattened files" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

curves_in_flattened=$(grep -c "C" "$OUTPUT_DIR/arc_test_flattened.svg")
echo "arc_test_flattened.svg curve commands: $curves_in_flattened" >> "$LOG_FILE"

# Flattened curves should have fewer curve commands or none at all
if [ $curves_in_flattened -lt $curves_preserved ]; then
    print_pass | tee -a "$LOG_FILE"
else
    print_fail | tee -a "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    exit 0
else
    exit 1
fi 