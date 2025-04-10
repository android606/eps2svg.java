#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS05"

# Test visual output
# This script tests that the tool produces visually correct SVG output.

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Run test setup if needed (will be skipped if called from run_tests.sh)
run_test_setup_if_needed

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="Visual Quality"
TS_ID=$(get_test_suite_id)
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_visual")"

# Start a new log file
echo "===== $TS_ID: $TEST_SUITE_NAME Tests - $(date) =====" > "$LOG_FILE"

# Count tests and failures
TOTAL_TESTS=0
FAILED_TESTS=0

# Print test suite header
print_test_suite_header "$TEST_SUITE_NAME" | tee -a "$LOG_FILE"
print_indented "Examining visual quality of output SVG files..." | tee -a "$LOG_FILE"

# Check all SVG files in the output directory for basic quality
print_indented "Checking all output files for basic quality..." | tee -a "$LOG_FILE"

# Find all SVG files in the output directory
output_files=()
while IFS= read -r file; do
    filename=$(basename "$file")
    # Include all SVG files
    output_files+=("$filename")
done < <(find "$OUTPUT_DIR" -maxdepth 1 -name "*.svg" 2>/dev/null)

# Debug: Print the number of files found
echo "Found ${#output_files[@]} SVG files to examine" | tee -a "$LOG_FILE"

for svg_file in "${output_files[@]}"; do
    full_path="$OUTPUT_DIR/$svg_file"
    print_test_line "Basic visual check for $svg_file" | tee -a "$LOG_FILE"
    TOTAL_TESTS=$((TOTAL_TESTS+1))
    
    if [ -f "$full_path" ] && [ -s "$full_path" ]; then
        echo "Examining output file: $full_path" >> "$LOG_FILE"
        
        # Check for SVG elements
        if grep -q "<svg" "$full_path"; then
            # Check for paths or other content elements
            if grep -q "<path" "$full_path" || 
               grep -q "<rect" "$full_path" || 
               grep -q "<circle" "$full_path" || 
               grep -q "<ellipse" "$full_path" || 
               grep -q "<line" "$full_path" || 
               grep -q "<polyline" "$full_path" || 
               grep -q "<polygon" "$full_path"; then
                print_pass | tee -a "$LOG_FILE"
                echo "File contains SVG content elements" >> "$LOG_FILE"
            else
                print_warning | tee -a "$LOG_FILE"
                echo "File is valid SVG but contains no drawing elements" >> "$LOG_FILE"
            fi
        else
            print_fail | tee -a "$LOG_FILE"
            echo "File does not appear to be valid SVG" >> "$LOG_FILE"
            FAILED_TESTS=$((FAILED_TESTS+1))
        fi
    else
        print_fail | tee -a "$LOG_FILE"
        echo "Output file doesn't exist or is empty: $full_path" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
done

# Check if zero tests were performed
if [ $TOTAL_TESTS -eq 0 ]; then
    print_indented "$(print_fail): No tests were performed! This is a test failure." | tee -a "$LOG_FILE"
    echo "No SVG files were found to test. Check if test setup completed correctly and files were generated." >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    exit 0
else
    exit 1
fi 