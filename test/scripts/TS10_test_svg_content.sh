#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS10"

# Test SVG content integrity
# This script checks all output SVG files and fails if any have no drawing elements.

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Run test setup if needed (will be skipped if called from run_tests.sh)
run_test_setup_if_needed

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="SVG Content Validation"
TS_ID=$(get_test_suite_id)
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_svg_content")"

# Start a new log file
echo "===== $TS_ID: $TEST_SUITE_NAME Tests - $(date) =====" > "$LOG_FILE"

# Count tests and failures
TOTAL_TESTS=0
FAILED_TESTS=0

# Print test suite header
print_test_suite_header "$TEST_SUITE_NAME" | tee -a "$LOG_FILE"
print_indented "Validating SVG files for content elements..." | tee -a "$LOG_FILE"

# Find all SVG files in the output directory
output_files=()
while IFS= read -r file; do
    filename=$(basename "$file")
    output_files+=("$filename")
done < <(find "$OUTPUT_DIR" -name "*.svg" 2>/dev/null)

# Log the number of files found
echo "Found ${#output_files[@]} SVG files to check" >> "$LOG_FILE"

if [ ${#output_files[@]} -eq 0 ]; then
    print_indented "$(print_warning): No SVG files found to check" | tee -a "$LOG_FILE"
    exit 1
fi

# Check each file for content elements
for svg_file in "${output_files[@]}"; do
    full_path="$OUTPUT_DIR/$svg_file"
    print_test_line "Checking content in $svg_file" | tee -a "$LOG_FILE"
    TOTAL_TESTS=$((TOTAL_TESTS+1))
    
    if [ -f "$full_path" ] && [ -s "$full_path" ]; then
        echo "Examining file: $full_path" >> "$LOG_FILE"
        
        # Check for various content elements that could be in an SVG
        has_content=false
        
        if grep -q "<path" "$full_path"; then
            has_content=true
            echo "File contains <path> elements" >> "$LOG_FILE"
        fi
        
        if grep -q "<rect" "$full_path"; then
            has_content=true
            echo "File contains <rect> elements" >> "$LOG_FILE"
        fi
        
        if grep -q "<circle" "$full_path"; then
            has_content=true
            echo "File contains <circle> elements" >> "$LOG_FILE"
        fi
        
        if grep -q "<ellipse" "$full_path"; then
            has_content=true
            echo "File contains <ellipse> elements" >> "$LOG_FILE"
        fi
        
        if grep -q "<line" "$full_path"; then
            has_content=true
            echo "File contains <line> elements" >> "$LOG_FILE"
        fi
        
        if grep -q "<polyline" "$full_path"; then
            has_content=true
            echo "File contains <polyline> elements" >> "$LOG_FILE"
        fi
        
        if grep -q "<polygon" "$full_path"; then
            has_content=true
            echo "File contains <polygon> elements" >> "$LOG_FILE"
        fi
        
        if grep -q "<text" "$full_path"; then
            has_content=true
            echo "File contains <text> elements" >> "$LOG_FILE"
        fi
        
        if [ "$has_content" = true ]; then
            print_pass | tee -a "$LOG_FILE"
            echo "SVG file has drawing elements" >> "$LOG_FILE"
        else
            print_fail | tee -a "$LOG_FILE"
            echo "SVG file has NO drawing elements" >> "$LOG_FILE"
            FAILED_TESTS=$((FAILED_TESTS+1))
        fi
    else
        print_fail | tee -a "$LOG_FILE"
        echo "File doesn't exist or is empty: $full_path" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
done

# List all empty files
if [ $FAILED_TESTS -gt 0 ]; then
    print_indented "Empty SVG files detected! The following files have no drawing elements:" | tee -a "$LOG_FILE"
    for svg_file in "${output_files[@]}"; do
        full_path="$OUTPUT_DIR/$svg_file"
        if [ -f "$full_path" ] && [ -s "$full_path" ]; then
            has_content=false
            if grep -q "<path\|<rect\|<circle\|<ellipse\|<line\|<polyline\|<polygon\|<text" "$full_path"; then
                has_content=true
            fi
            
            if [ "$has_content" = false ]; then
                print_indented "  - $svg_file" | tee -a "$LOG_FILE"
            fi
        fi
    done
    print_indented "These files need to be investigated for EPS parsing issues." | tee -a "$LOG_FILE"
fi

# Print summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

# Exit with error code if tests failed
if [ $FAILED_TESTS -eq 0 ]; then
    exit 0
else
    exit 1
fi 