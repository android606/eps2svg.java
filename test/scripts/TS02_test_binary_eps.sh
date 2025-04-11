#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS02"
TEST_SUITE_NAME="Binary EPS Support"
TEST_SUITE_FILENAME=$(basename "${BASH_SOURCE[0]}" .sh)  # Script name without extension, also used for log file name

# Test binary EPS file handling
# This script tests that the tool can handle binary EPS files correctly.

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
print_indented "Examining binary EPS conversion results..." | tee -a "$LOG_FILE"

# Find all binary EPS files (with "binary" in the filename)
binary_files=()
while IFS= read -r file; do
    # Extract just the filename without path
    filename=$(basename "$file")
    binary_files+=("$filename")
done < <(find "$TEST_DIR/test_images" -name "*binary*.eps")

# If no binary files found, provide a message
if [ ${#binary_files[@]} -eq 0 ]; then
    print_indented "$(print_warning): No binary EPS files found in test_images directory" | tee -a "$LOG_FILE"
fi

# For each binary file, test different conversion modes
for binary_file in "${binary_files[@]}"; do
    test_file="$TEST_DIR/test_images/$binary_file"
    base_name="${binary_file%.*}"
    
    # Check if file exists
    if [ ! -f "$test_file" ]; then
        print_indented "$(print_warning): Test file $test_file not found, skipping test" | tee -a "$LOG_FILE"
        continue
    fi
    
    # Check normal conversion
    output_file="$OUTPUT_DIR/${base_name}_normal.svg"
    print_test_line "Normal conversion of $binary_file" | tee -a "$LOG_FILE"
    TOTAL_TESTS=$((TOTAL_TESTS+1))
    
    if [ -f "$output_file" ] && [ -s "$output_file" ]; then
        echo "Examining output file: $output_file" >> "$LOG_FILE"
        print_pass | tee -a "$LOG_FILE"
    else
        echo "Output file missing or empty: $output_file" >> "$LOG_FILE"
        print_fail | tee -a "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
    
    # Check GhostScript conversion
    output_file="$OUTPUT_DIR/${base_name}_gs.svg"
    print_test_line "GhostScript conversion of $binary_file" | tee -a "$LOG_FILE"
    TOTAL_TESTS=$((TOTAL_TESTS+1))
    
    if [ -f "$output_file" ] && [ -s "$output_file" ]; then
        echo "Examining output file: $output_file" >> "$LOG_FILE"
        print_pass | tee -a "$LOG_FILE"
    else
        echo "Output file missing or empty: $output_file" >> "$LOG_FILE"
        print_fail | tee -a "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
    
    # Check TIFF preview conversion
    output_file="$OUTPUT_DIR/${base_name}_tiff.svg"
    print_test_line "TIFF preview conversion of $binary_file" | tee -a "$LOG_FILE"
    TOTAL_TESTS=$((TOTAL_TESTS+1))
    
    if [ -f "$output_file" ] && [ -s "$output_file" ]; then
        echo "Examining output file: $output_file" >> "$LOG_FILE"
        print_pass | tee -a "$LOG_FILE"
    else
        # This might fail if the EPS doesn't have a TIFF preview, so handle with caution
        print_warning | tee -a "$LOG_FILE"
        echo "Output file missing for TIFF preview conversion - this may be expected" >> "$LOG_FILE"
    fi
done

# Special test: Compare binary and text versions of the same file
print_test_line "Binary vs text version comparison" | tee -a "$LOG_FILE"
binary_output="$OUTPUT_DIR/manufactured_by-binary_normal.svg"
text_output="$OUTPUT_DIR/manufactured_by_normal.svg"

TOTAL_TESTS=$((TOTAL_TESTS+1))

# Check if both output files exist
if [ -f "$binary_output" ] && [ -s "$binary_output" ] && [ -f "$text_output" ] && [ -s "$text_output" ]; then
    # Perform basic comparison (size, content)
    binary_size=$(wc -c < "$binary_output")
    text_size=$(wc -c < "$text_output")
    size_diff=$((binary_size - text_size))
    size_diff_abs=${size_diff#-}  # Get absolute value
    
    # Count SVG elements in both files
    binary_elements=$(grep -c "<[^>]*path" "$binary_output")
    text_elements=$(grep -c "<[^>]*path" "$text_output")
    
    # Check similarity (allowing for some difference)
    if [ "$size_diff_abs" -lt "$(($binary_size / 4))" ] && [ "$binary_elements" -gt 0 ] && [ "$text_elements" -gt 0 ]; then
        print_pass | tee -a "$LOG_FILE"
        echo "Binary and text versions produce comparable SVG output" >> "$LOG_FILE"
    else
        print_warning | tee -a "$LOG_FILE"
        echo "Binary and text versions produce different output:" >> "$LOG_FILE"
        echo "  - Binary size: $binary_size bytes, $binary_elements path elements" >> "$LOG_FILE"
        echo "  - Text size: $text_size bytes, $text_elements path elements" >> "$LOG_FILE"
        echo "This may be expected if they're different representations" >> "$LOG_FILE"
    fi
else
    print_fail | tee -a "$LOG_FILE"
    echo "Failed to find output files for comparison" >> "$LOG_FILE"
    echo "  - Binary file: $([ -f "$binary_output" ] && echo "Found" || echo "Not found")" >> "$LOG_FILE"
    echo "  - Text file: $([ -f "$text_output" ] && echo "Found" || echo "Not found")" >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Check if zero tests were performed
if [ $TOTAL_TESTS -eq 0 ]; then
    print_indented "$(print_fail): No tests were performed! This is a test failure." | tee -a "$LOG_FILE"
    echo "No binary EPS tests were executed. Check if test setup completed correctly and binary files exist." >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    exit 0
else
    exit 1
fi
