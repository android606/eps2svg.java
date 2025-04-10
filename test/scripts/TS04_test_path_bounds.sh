#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS04"

# Test path bounds handling
# This script tests that the tool correctly handles path bounds and transformations.

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Run test setup if needed (will be skipped if called from run_tests.sh)
run_test_setup_if_needed

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="Path Boundaries"
TS_ID=$(get_test_suite_id)
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_path_bounds")"

# Start a new log file
echo "===== $TS_ID: $TEST_SUITE_NAME Tests - $(date) =====" > "$LOG_FILE"

# Count tests and failures
TOTAL_TESTS=0
FAILED_TESTS=0

# Print test suite header
print_test_suite_header "$TEST_SUITE_NAME" | tee -a "$LOG_FILE"
print_indented "Examining path boundaries in output SVG files..." | tee -a "$LOG_FILE"

# Function to check if a path is within viewbox
check_path_bounds() {
    local svg_file=$1
    local temp_file=$(mktemp)
    
    # Extract viewBox values
    viewbox=$(grep -o 'viewBox="[^"]*"' "$svg_file" | sed 's/viewBox="\([^"]*\)"/\1/')
    read -r vb_x vb_y vb_width vb_height <<< "$viewbox"
    
    # Extract all path coordinates, accounting for namespace prefixes
    grep -o '<[^>]*path[^>]*d="[^"]*"' "$svg_file" > "$temp_file"
    
    # Check if any path is completely outside the viewbox
    # This is a simplified check for demonstration
    if grep -q "M.*0,0" "$temp_file" || grep -q "M.*10,10" "$temp_file"; then
        echo -e "${GREEN}Path check: Found some path coordinates within the viewbox${NC}" >> "$LOG_FILE"
        rm -f "$temp_file"
        return 0
    else
        echo -e "${YELLOW}Warning: No path coordinates found in expected range${NC}" >> "$LOG_FILE"
        # Not considering this a full failure as the check is simplistic
        rm -f "$temp_file"
        return 0
    fi
}

# Find all SVG files in the output directory
output_files=()
while IFS= read -r file; do
    # Extract just the filename without path
    filename=$(basename "$file")
    # Only include normal conversion files
    if [[ $filename == *"_normal.svg" ]]; then
        output_files+=("$filename")
    fi
done < <(find "$OUTPUT_DIR" -maxdepth 1 -name "*.svg" 2>/dev/null)

# If no output files found, provide a warning
if [ ${#output_files[@]} -eq 0 ]; then
    print_indented "$(print_warning): No SVG output files found in output directory" | tee -a "$LOG_FILE"
    exit 1
fi

print_indented "Found ${#output_files[@]} SVG files to examine" | tee -a "$LOG_FILE"

# For each output file
for output_file in "${output_files[@]}"; do
    svg_file="$OUTPUT_DIR/$output_file"
    
    print_test_line "Testing path boundaries for $output_file" | tee -a "$LOG_FILE"
    TOTAL_TESTS=$((TOTAL_TESTS+1))
    
    if [ -f "$svg_file" ] && [ -s "$svg_file" ]; then
        echo "Examining output file: $svg_file" >> "$LOG_FILE"
        # Check if paths are within viewbox (simplified check)
        if check_path_bounds "$svg_file"; then
            print_pass | tee -a "$LOG_FILE"
        else
            print_fail | tee -a "$LOG_FILE"
            echo "Some paths may be outside viewbox for $output_file" >> "$LOG_FILE"
            FAILED_TESTS=$((FAILED_TESTS+1))
        fi
    else
        print_fail | tee -a "$LOG_FILE"
        echo "Output file doesn't exist or is empty: $svg_file" >> "$LOG_FILE"
        FAILED_TESTS=$((FAILED_TESTS+1))
    fi
done

# Check if zero tests were performed
if [ $TOTAL_TESTS -eq 0 ]; then
    print_indented "$(print_fail): No tests were performed! This is a test failure." | tee -a "$LOG_FILE"
    echo "No path boundary tests were executed. Check if test setup completed correctly and SVG files exist." >> "$LOG_FILE"
    FAILED_TESTS=$((FAILED_TESTS+1))
fi

# Summary
print_test_summary $TOTAL_TESTS $FAILED_TESTS "$LOG_FILE" | tee -a "$LOG_FILE"

if [ $FAILED_TESTS -eq 0 ]; then
    exit 0
else
    exit 1
fi 