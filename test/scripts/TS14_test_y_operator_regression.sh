#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS14"

# Test script for regression testing of the 'y' operator
# DESCRIPTION: Tests that the 'y' operator is correctly implemented and functioning
# 
# Usage: ./test_y_operator_regression.sh [--debug]
#   --debug: Enable verbose debug output

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="Y Operator Regression"
TS_ID=$(get_test_suite_id)
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_y_operator_regression")"

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
print_indented "Testing 'y' operator implementation..." | tee -a "$LOG_FILE"

# Ensure we have a valid jar file
cd "$PROJECT_ROOT"
if [ ! -f "target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    print_indented "Compiling project..." | tee -a "$LOG_FILE"
    mvn clean package -DskipTests -q >> "$LOG_FILE" 2>&1
fi

# ====== TEST CASE: Y Operator Implementation ======
print_test_line "Test 'y' operator implementation" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Run with y_test.eps file
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
    "$TEST_DIR/curve_tests/y_test.eps" \
    "$OUTPUT_DIR/y_test_regression.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/y_test_regression.svg" ] && [ -s "$OUTPUT_DIR/y_test_regression.svg" ]; then
    # Check for the presence of Bézier curve commands in the SVG
    curve_count=$(grep -c "C" "$OUTPUT_DIR/y_test_regression.svg")
    echo "Curve commands found: $curve_count" >> "$LOG_FILE"
    
    if [ "$curve_count" -gt 0 ]; then
        # Check for at least one proper path segment with both moveto and curve
        if grep -q "M.*C" "$OUTPUT_DIR/y_test_regression.svg"; then
            print_pass | tee -a "$LOG_FILE"
            echo "Y operator implementation test passed" >> "$LOG_FILE"
        else
            print_fail | tee -a "$LOG_FILE"
            echo "Path data missing expected move-to/curve sequence" >> "$LOG_FILE"
            FAILED_TESTS=$((FAILED_TESTS+1))
        fi
    else
        print_fail | tee -a "$LOG_FILE"
        echo "No curve commands found in output - 'y' operator likely not implemented correctly" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
    
    # Verify the content of the first path to check it contains a curve
    first_path=$(grep -m1 "path" "$OUTPUT_DIR/y_test_regression.svg")
    echo "First path: $first_path" >> "$LOG_FILE"
    
    # Output debug information
    if [ "$DEBUG_MODE" = true ]; then
        echo "------ Debug: SVG output ------" >> "$LOG_FILE"
        cat "$OUTPUT_DIR/y_test_regression.svg" >> "$LOG_FILE"
        echo "------ End Debug ------" >> "$LOG_FILE"
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Y operator test file missing or empty" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    print_indented "Y operator regression test passed" | tee -a "$LOG_FILE"
    exit 0
else
    print_indented "Y operator regression test failed" | tee -a "$LOG_FILE"
    exit 1
fi 