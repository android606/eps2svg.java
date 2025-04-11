#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS12"
TEST_SUITE_NAME="Color Preservation"
TEST_SUITE_FILENAME=$(basename "${BASH_SOURCE[0]}" .sh)  # Script name without extension, also used for log file name

# Test color preservation
# This script tests that the tool correctly preserves colors during conversion.

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Run test setup if needed (will be skipped if called from run_tests.sh)
run_test_setup_if_needed

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
LOG_FILE="$LOGS_DIR/$TEST_SUITE_ID-$TEST_SUITE_FILENAME.log"  # Log file name

# Start a new log file
echo "===== $TEST_SUITE_ID: $TEST_SUITE_NAME Tests - $(date) =====" > "$LOG_FILE"

# Count tests and failures
TOTAL_TESTS=0
FAILED_TESTS=0

# Print test suite header
print_test_suite_header "$TEST_SUITE_NAME" | tee -a "$LOG_FILE"
print_indented "Examining color preservation in conversion results..." | tee -a "$LOG_FILE"

# Convert arrow EPS files if they haven't been processed already
if [ ! -f "$OUTPUT_DIR/arrow_up_normal.svg" ]; then
    print_indented "Converting arrow_up.eps..." | tee -a "$LOG_FILE"
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$TEST_IMAGES_DIR/arrow_up.eps" "$OUTPUT_DIR/arrow_up_normal.svg" >> "$LOG_FILE" 2>&1
fi

if [ ! -f "$OUTPUT_DIR/arrow_right_normal.svg" ]; then
    print_indented "Converting arrow_right.eps..." | tee -a "$LOG_FILE"
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$TEST_IMAGES_DIR/arrow_right.eps" "$OUTPUT_DIR/arrow_right_normal.svg" >> "$LOG_FILE" 2>&1
fi

# Test blue color in up arrow
print_test_line "Blue color preservation test" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

if [ -f "$OUTPUT_DIR/arrow_up_normal.svg" ] && [ -s "$OUTPUT_DIR/arrow_up_normal.svg" ]; then
    echo "Examining output file: $OUTPUT_DIR/arrow_up_normal.svg" >> "$LOG_FILE"
    
    # Check for blue color in the up arrow (0,0,1 in RGB or some variation of "blue")
    if grep -q "rgb(0,0,\(1\|255\)\|blue" "$OUTPUT_DIR/arrow_up_normal.svg"; then
        print_pass | tee -a "$LOG_FILE"
        echo "Arrow up preserves blue color" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "Arrow up does not preserve blue color" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Output file missing or empty: $OUTPUT_DIR/arrow_up_normal.svg" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Test red color in right arrow
print_test_line "Red color preservation test" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

if [ -f "$OUTPUT_DIR/arrow_right_normal.svg" ] && [ -s "$OUTPUT_DIR/arrow_right_normal.svg" ]; then
    echo "Examining output file: $OUTPUT_DIR/arrow_right_normal.svg" >> "$LOG_FILE"
    
    # Check for red color in the right arrow (1,0,0 in RGB or some variation of "red")
    if grep -q "rgb(1\|255,0,0)\|red" "$OUTPUT_DIR/arrow_right_normal.svg"; then
        print_pass | tee -a "$LOG_FILE"
        echo "Arrow right preserves red color" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "Arrow right does not preserve red color" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Output file missing or empty: $OUTPUT_DIR/arrow_right_normal.svg" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Check if zero tests were performed
if [ $TOTAL_TESTS -eq 0 ]; then
    print_indented "$(print_fail): No tests were performed! This is a test failure." | tee -a "$LOG_FILE"
    echo "No color tests were executed. Check if test setup completed correctly and test files exist." >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    exit 0
else
    exit 1
fi
