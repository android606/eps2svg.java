#!/bin/bash

# Test viewbox handling
# This script tests that the tool correctly handles viewbox and dimensions.

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Run test setup if needed (will be skipped if called from run_tests.sh)
run_test_setup_if_needed

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="ViewBox Correctness"
TS_ID=$(get_test_suite_id)
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_viewbox")"

# Start a new log file
echo "===== $TS_ID: $TEST_SUITE_NAME Tests - $(date) =====" > "$LOG_FILE"

# Count tests and failures
TOTAL_TESTS=0
FAILED_TESTS=0

# Print test suite header
print_test_suite_header "$TEST_SUITE_NAME" | tee -a "$LOG_FILE"
print_indented "Examining viewbox in output SVG files..." | tee -a "$LOG_FILE"

# Test files with known bounding boxes
declare -A test_files_bbox
test_files_bbox["test_basic_fill_normal.svg"]="0 0 100 100"
test_files_bbox["test_basic_stroke_normal.svg"]="0 0 100 100"

# Function to compare viewBox values ignoring precision formatting
compare_viewbox() {
    local actual="$1"
    local expected="$2"
    
    # Extract the values as arrays
    local actual_values=($actual)
    local expected_values=($expected)
    
    # Convert values to integers (truncate decimal places)
    local actual_int0=$(printf "%.0f" "${actual_values[0]}")
    local actual_int1=$(printf "%.0f" "${actual_values[1]}")
    local actual_int2=$(printf "%.0f" "${actual_values[2]}")
    local actual_int3=$(printf "%.0f" "${actual_values[3]}")
    
    local expected_int0=$(printf "%.0f" "${expected_values[0]}")
    local expected_int1=$(printf "%.0f" "${expected_values[1]}")
    local expected_int2=$(printf "%.0f" "${expected_values[2]}")
    local expected_int3=$(printf "%.0f" "${expected_values[3]}")
    
    # Compare values as integers (ignore decimal places)
    if [ "$actual_int0" -eq "$expected_int0" ] && 
       [ "$actual_int1" -eq "$expected_int1" ] && 
       [ "$actual_int2" -eq "$expected_int2" ] && 
       [ "$actual_int3" -eq "$expected_int3" ]; then
        return 0  # Success
    else
        return 1  # Failure
    fi
}

# For each test file, check if viewBox is set correctly
for test_file in "${!test_files_bbox[@]}"; do
    output_file="$OUTPUT_DIR/$test_file"
    expected_bbox="${test_files_bbox[$test_file]}"
    
    print_test_line "ViewBox test for $test_file" | tee -a "$LOG_FILE"
    TOTAL_TESTS=$((TOTAL_TESTS+1))
    
    # Check viewBox attribute
    if [ -f "$output_file" ] && [ -s "$output_file" ]; then
        echo "Examining output file: $output_file" >> "$LOG_FILE"
        echo "Expected ViewBox: $expected_bbox" >> "$LOG_FILE"
        
        viewbox=$(grep -o 'viewBox="[^"]*"' "$output_file" | sed 's/viewBox="\([^"]*\)"/\1/')
        
        if [ -n "$viewbox" ]; then
            # Compare ignoring decimal precision
            if compare_viewbox "$viewbox" "$expected_bbox"; then
                print_pass | tee -a "$LOG_FILE"
                echo "ViewBox correct - Found: $viewbox, Expected: $expected_bbox" >> "$LOG_FILE"
            else
                print_fail | tee -a "$LOG_FILE"
                echo "ViewBox incorrect - Found: $viewbox, Expected: $expected_bbox" >> "$LOG_FILE"
                FAILED_TESTS=$((FAILED_TESTS+1))
            fi
        else
            print_fail | tee -a "$LOG_FILE"
            echo "ViewBox attribute not found" >> "$LOG_FILE"
            FAILED_TESTS=$((FAILED_TESTS+1))
        fi
    else
        print_fail | tee -a "$LOG_FILE"
        echo "Output file doesn't exist or is empty: $output_file" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
done

# Check if zero tests were performed
if [ $TOTAL_TESTS -eq 0 ]; then
    print_indented "$(print_fail): No tests were performed! This is a test failure." | tee -a "$LOG_FILE"
    echo "No viewbox tests were executed. Check if test setup completed correctly and test files exist." >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    exit 0
else
    exit 1
fi 