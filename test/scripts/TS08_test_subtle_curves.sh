#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS08"

# Test script for subtle curves
# DESCRIPTION: Tests conversion of subtle bezier curves in EPS files with both preservation and flattening
# 
# Usage: ./test_subtle_curves.sh [--debug]
#   --debug: Enable verbose debug output

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="Subtle Curves Test"
TS_ID=$(get_test_suite_id)
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_subtle_curves")"

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
print_indented "Testing subtle curves with preservation and flattening..." | tee -a "$LOG_FILE"

# Ensure we have a valid jar file
cd "$PROJECT_ROOT"
if [ ! -f "target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar" ]; then
    print_indented "Compiling project..." | tee -a "$LOG_FILE"
    mvn clean package -DskipTests -q >> "$LOG_FILE" 2>&1
fi

# ====== TEST CASE 1: Subtle Curves Preservation ======
print_test_line "Test subtle curves with preservation (default)" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Run with curve preservation (default)
java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
  "$TEST_DIR/curve_tests/subtle_curve_test.eps" \
  "$OUTPUT_DIR/subtle_preserved.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/subtle_preserved.svg" ] && [ -s "$OUTPUT_DIR/subtle_preserved.svg" ]; then
    # Check for curve commands in the output
    curves_count=$(grep -c "C" "$OUTPUT_DIR/subtle_preserved.svg" 2>/dev/null || echo "0")
    echo "Curves found in preserved output: $curves_count" >> "$LOG_FILE"
    
    if [ "$curves_count" -gt 0 ]; then
        print_pass | tee -a "$LOG_FILE"
        echo "Curve preservation test passed" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "Expected curve commands in output but none found" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Output file missing or empty" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# ====== TEST CASE 2: Subtle Curves Flattening ======
print_test_line "Test subtle curves with flattening" | tee -a "$LOG_FILE"
TOTAL_TESTS=$((TOTAL_TESTS+1))

# Run with curve flattening
java -Dconvert2web.flattenCurves=true -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \
  "$TEST_DIR/curve_tests/subtle_curve_test.eps" \
  "$OUTPUT_DIR/subtle_flattened.svg" >> "$LOG_FILE" 2>&1

if [ -f "$OUTPUT_DIR/subtle_flattened.svg" ] && [ -s "$OUTPUT_DIR/subtle_flattened.svg" ]; then
    # Check if curves were correctly flattened
    # The flattened output should either have no curve commands or fewer than the preserved version
    preserved_curves=$(grep -c "C" "$OUTPUT_DIR/subtle_preserved.svg" 2>/dev/null || echo "0")
    flattened_curves=$(grep -c "C" "$OUTPUT_DIR/subtle_flattened.svg" 2>/dev/null || echo "0")
    
    echo "Curves in preserved output: $preserved_curves" >> "$LOG_FILE"
    echo "Curves in flattened output: $flattened_curves" >> "$LOG_FILE"
    
    if [ "$flattened_curves" -lt "$preserved_curves" ]; then
        print_pass | tee -a "$LOG_FILE"
        echo "Curve flattening test passed" >> "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        echo "Curve flattening didn't reduce curve count" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Flattened output file missing or empty" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

print_indented "Output files are in $OUTPUT_DIR:" | tee -a "$LOG_FILE"
print_indented "- $OUTPUT_DIR/subtle_preserved.svg (curves preserved)" | tee -a "$LOG_FILE"
print_indented "- $OUTPUT_DIR/subtle_flattened.svg (curves flattened)" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    print_indented "All tests passed" | tee -a "$LOG_FILE"
    exit 0
else
    print_indented "$FAILED_TESTS tests failed" | tee -a "$LOG_FILE"
    exit 1
fi 