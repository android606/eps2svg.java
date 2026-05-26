#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS00"
TEST_SUITE_NAME="Test Setup"
TEST_SUITE_FILENAME=$(basename "${BASH_SOURCE[0]}" .sh)  # Script name without extension, also used for log file name

# TS00: Test Setup
# This script cleans and builds the software, then runs the conversion tool
# on all test images with different options to prepare files for testing

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
LOG_FILE="$LOGS_DIR/$TEST_SUITE_ID-$TEST_SUITE_FILENAME.log"  # Log file name

# Start a new log file
echo "===== $TEST_SUITE_ID: $TEST_SUITE_NAME Tests - $(date) =====" > "$LOG_FILE"

# Clean up previous test results
print_indented "Cleaning all previous test files..." | tee -a "$LOG_FILE"
rm -rf "$OUTPUT_DIR"/*

# Clean and build the project
cd "$PROJECT_ROOT"
print_indented "Cleaning and building project..." | tee -a "$LOG_FILE"
mvn clean package -q >> "$LOG_FILE" 2>&1

# Print test suite header
print_test_suite_header "$TEST_SUITE_NAME" | tee -a "$LOG_FILE"
print_indented "Preparing test files with different conversion options..." | tee -a "$LOG_FILE"

# Find all EPS files in the test_images directory
test_files=()
while IFS= read -r file; do
    # Extract just the filename without path
    filename=$(basename "$file")
    test_files+=("$filename")
done < <(find "$TEST_IMAGES_DIR" -name "*.eps")

# If no EPS files found, provide a warning
if [ ${#test_files[@]} -eq 0 ]; then
    print_indented "$(print_fail): No EPS files found in test_images directory" | tee -a "$LOG_FILE"
    exit 2
fi

print_indented "Found ${#test_files[@]} EPS files to process" | tee -a "$LOG_FILE"

# Count successful and failed conversions
TOTAL_FILES=0
FAILED_FILES=0

# Process each test file with different options
for test_file in "${test_files[@]}"; do
    input_file="$TEST_IMAGES_DIR/$test_file"
    base_name="${test_file%.*}"
    
    # Check if file is DOS binary EPS (exit 0 = binary)
    java -cp target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar com.convert2web.BinaryEpsInterpreter "$input_file" > /dev/null 2>&1
    is_binary=$?

    # Normal conversion for all files
    TOTAL_FILES=$((TOTAL_FILES+1))
    output_file="$OUTPUT_DIR/${base_name}_normal.svg"
    print_test_line "Processing $test_file (normal mode)" | tee -a "$LOG_FILE"
    
    echo "Running: run_eps2svg $EPS2SVG_DISPLAY_OPTS \"$input_file\" \"$output_file\"" >> "$LOG_FILE"
    run_eps2svg "$input_file" "$output_file" >> "$LOG_FILE" 2>&1
    
    if [ -f "$output_file" ] && [ -s "$output_file" ]; then
        print_pass | tee -a "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        FAILED_FILES=$((FAILED_FILES+1))
    fi
done

# Summary
print_test_summary $TOTAL_FILES $FAILED_FILES "$LOG_FILE" | tee -a "$LOG_FILE"

# Add a note about next steps
echo -e "\nSetup complete. All test files have been processed." | tee -a "$LOG_FILE"
echo -e "Run individual test scripts or the main test runner (test/run_tests.sh) to test the generated files." | tee -a "$LOG_FILE"
