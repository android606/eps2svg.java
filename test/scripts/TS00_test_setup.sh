#!/bin/bash

# Define test suite ID
TEST_SUITE_ID="TS00"

# TS00: Test Setup
# This script cleans and builds the software, then runs the conversion tool
# on all test images with different options to prepare files for testing

# Source the shared test utilities
source "$( dirname "${BASH_SOURCE[0]}" )/test_utils.sh"

# Create test directories
create_test_dirs

# Get test suite ID and set up log file
TEST_SUITE_NAME="Test Setup"
TS_ID="TS00"
LOG_FILE="$LOGS_DIR/$(get_log_filename "test_setup")"

# Start a new log file
echo "===== $TS_ID: $TEST_SUITE_NAME - $(date) =====" > "$LOG_FILE"

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
    
    # Check if file is binary EPS
    java -cp target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar com.convert2web.BinaryEpsInterpreter "$input_file" > /dev/null 2>&1
    is_binary=$?
    
    # Normal conversion for all files
    TOTAL_FILES=$((TOTAL_FILES+1))
    output_file="$OUTPUT_DIR/${base_name}_normal.svg"
    print_test_line "Processing $test_file (normal mode)" | tee -a "$LOG_FILE"
    
    echo "Running: java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \"$input_file\" \"$output_file\"" >> "$LOG_FILE"
    java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$input_file" "$output_file" >> "$LOG_FILE" 2>&1
    
    if [ -f "$output_file" ] && [ -s "$output_file" ]; then
        print_pass | tee -a "$LOG_FILE"
    else
        print_fail | tee -a "$LOG_FILE"
        FAILED_FILES=$((FAILED_FILES+1))
    fi
    
    # Only process binary files with GhostScript and TIFF modes
    if [[ "$test_file" == *"-binary"* ]] || [[ "$is_binary" -eq 0 ]]; then
        TOTAL_FILES=$((TOTAL_FILES+2))  # Add 2 more tests for binary files
        
        # GhostScript conversion
        output_file="$OUTPUT_DIR/${base_name}_gs.svg"
        print_test_line "Processing $test_file (GhostScript mode)" | tee -a "$LOG_FILE"
        
        echo "Running: java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \"$input_file\" \"$output_file\" --force-ghostscript" >> "$LOG_FILE"
        java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$input_file" "$output_file" --force-ghostscript >> "$LOG_FILE" 2>&1
        
        if [ -f "$output_file" ] && [ -s "$output_file" ]; then
            print_pass | tee -a "$LOG_FILE"
        else
            print_fail | tee -a "$LOG_FILE"
            FAILED_FILES=$((FAILED_FILES+1))
        fi
        
        # TIFF preview conversion
        output_file="$OUTPUT_DIR/${base_name}_tiff.svg"
        print_test_line "Processing $test_file (TIFF preview mode)" | tee -a "$LOG_FILE"
        
        echo "Running: java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar \"$input_file\" \"$output_file\" --force-tiff" >> "$LOG_FILE"
        java -jar target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar "$input_file" "$output_file" --force-tiff >> "$LOG_FILE" 2>&1
        
        if [ -f "$output_file" ] && [ -s "$output_file" ]; then
            print_pass | tee -a "$LOG_FILE"
        else
            print_warning | tee -a "$LOG_FILE"
            echo "Note: TIFF preview conversion may fail if the EPS doesn't contain a preview" >> "$LOG_FILE"
        fi
    fi
done

# Summary
print_test_summary $TOTAL_FILES $FAILED_FILES "$LOG_FILE" | tee -a "$LOG_FILE"

# Add a note about next steps
echo -e "\nSetup complete. All test files have been processed." | tee -a "$LOG_FILE"
echo -e "Run individual test scripts or the main test runner (test/run_tests.sh) to test the generated files." | tee -a "$LOG_FILE"

if [ $FAILED_FILES -eq 0 ]; then
    echo -e "\n$TS_ID Results: ${GREEN}✓ Test setup completed successfully${NC}" | tee -a "$LOG_FILE"
    exit 0
else
    echo -e "\n$TS_ID Results: ${YELLOW}⚠ Test setup completed with some conversion failures${NC}" | tee -a "$LOG_FILE"
    echo -e "Individual tests may still pass depending on which files failed conversion." | tee -a "$LOG_FILE"
    exit 1
fi 