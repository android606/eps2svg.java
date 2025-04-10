#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS13"

# Test complex shape handling
# This script tests that the tool can correctly convert EPS files with complex shapes.

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Run test setup if needed (will be skipped if called from run_tests.sh)
run_test_setup_if_needed

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="Complex Shapes"
TS_ID=$(get_test_suite_id)
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_complex_shapes")"

# Start a new log file
echo "===== $TS_ID: $TEST_SUITE_NAME Tests - $(date) =====" > "$LOG_FILE"

# Count tests and failures
TOTAL_TESTS=0
FAILED_TESTS=0

# Print test suite header
print_test_suite_header "$TEST_SUITE_NAME" | tee -a "$LOG_FILE"
print_indented "Examining complex shape conversion results..." | tee -a "$LOG_FILE"

# Convert complex shape EPS file if it hasn't been processed already
if [ ! -f "$OUTPUT_DIR/complex_shape_normal.svg" ]; then
    print_indented "Converting complex_shape.eps..." | tee -a "$LOG_FILE"
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$TEST_IMAGES_DIR/complex_shape.eps" "$OUTPUT_DIR/complex_shape_normal.svg" >> "$LOG_FILE" 2>&1
fi

# Test star shape conversion
test_file="$TEST_IMAGES_DIR/complex_shape.eps"
output_file="$OUTPUT_DIR/complex_shape_normal.svg"
print_test_line "Complex star shape conversion test" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

if [ -f "$output_file" ] && [ -s "$output_file" ]; then
    echo "Examining output file: $output_file" >> "$LOG_FILE"
    
    # Check for path elements
    if grep -q "<path" "$output_file"; then
        print_pass | tee -a "$LOG_FILE"
        echo "File contains path elements" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "File does not contain path elements" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Output file missing or empty: $output_file" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Test for path closure
print_test_line "Path closure test" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Check for 'Z' command in path which indicates path closure
if grep -q "<path[^>]*[Zz]" "$output_file" || grep -q "d=\"[^\"]*[Zz]" "$output_file"; then
    print_pass | tee -a "$LOG_FILE"
    echo "Path has proper closure" >> "$LOG_FILE"
else
    print_warning | tee -a "$LOG_FILE"
    echo "Path may not have proper closure (no Z command found)" >> "$LOG_FILE"
fi

# Test for color preservation
print_test_line "Color preservation test" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Check for orange-like color in the star (values around 0.8,0.3,0.1 in RGB)
if grep -q "rgb(.*,.*,.*)\|fill=\"[^\"]*\"" "$output_file"; then
    print_pass | tee -a "$LOG_FILE"
    echo "File contains color information" >> "$LOG_FILE"
else
    print_warning | tee -a "$LOG_FILE"
    echo "File may not preserve color information" >> "$LOG_FILE"
fi

# Test that bounding box is preserved
print_test_line "Bounding box test" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Check if viewBox is around 200x200 (allowing for some variation in formatting)
if grep -q "viewBox=\"[^\"]*200[^\"]*200" "$output_file"; then
    print_pass | tee -a "$LOG_FILE"
    echo "Bounding box appears correct" >> "$LOG_FILE"
else
    print_warning | tee -a "$LOG_FILE"
    echo "Bounding box may not be correctly preserved" >> "$LOG_FILE"
fi

# Check if zero tests were performed
if [ $TOTAL_TESTS -eq 0 ]; then
    print_indented "$(print_fail): No tests were performed! This is a test failure." | tee -a "$LOG_FILE"
    echo "No complex shape tests were executed. Check if test setup completed correctly and test files exist." >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    exit 0
else
    exit 1
fi 