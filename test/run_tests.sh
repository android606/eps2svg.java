#!/bin/bash

# Master script to run all tests for EPS to SVG conversion

# Source the shared test utilities from the scripts directory
source "$( dirname "${BASH_SOURCE[0]}" )/scripts/test_utils.sh"

# Create logs directory
mkdir -p "$TEST_DIR/logs"
MASTER_LOG="$TEST_DIR/logs/master_test_run.log"
# Start a new master log file
echo "===== Master Test Run - $(date) =====" > "$MASTER_LOG"

# Define colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Make all test scripts executable
for test_script in "$TEST_DIR/scripts/TS"*.sh; do
    if [ -f "$test_script" ]; then
        chmod +x "$test_script"
    fi
done

# Count tests and failures
TOTAL_SUITES=0
FAILED_SUITES=0

echo -e "${BLUE}==========================================${NC}" | tee -a "$MASTER_LOG"
echo -e "${BLUE}Starting EPS to SVG Conversion Test Suites${NC}" | tee -a "$MASTER_LOG"
echo -e "${BLUE}==========================================${NC}" | tee -a "$MASTER_LOG"

# Function to extract TEST_SUITE_NAME from a test script
get_test_suite_name() {
    local ts_name=$1
    # Extract the TEST_SUITE_NAME value from the script
    grep -m 1 "TEST_SUITE_NAME=" "$ts_name" | cut -d'"' -f2 || echo "Unknown Test Suite Name"
}

# Function to extract TEST_SUITE_ID from a test script
get_test_suite_id() {
    local ts_name=$1
    # Extract the TEST_SUITE_ID value from the script
    grep -m 1 "TEST_SUITE_ID=" "$ts_name" | cut -d'"' -f2 || echo "Unknown Test Suite ID"
}

# Function to run a test script and track results
run_test_suite() {
    local test_script=$1
    local script_name=$(basename "$test_script")
    local ts_id=$(get_test_suite_id "$test_script")
    local test_name=$(get_test_suite_name "$test_script")
    
    echo -e "\n-----------------------------------------" | tee -a "$MASTER_LOG"
    echo -e "$ts_id: Running test suite: $test_name" | tee -a "$MASTER_LOG"
    echo -e "-----------------------------------------" | tee -a "$MASTER_LOG"
    
    TOTAL_SUITES=$((TOTAL_SUITES+1))
    
    # Run the test script
    echo "Running: $test_script" >> "$MASTER_LOG"
    "$test_script" 2>&1 | grep -v "Results:" | tee -a "$MASTER_LOG"
    local result=${PIPESTATUS[0]}
    
    if [ $result -eq 0 ]; then
        echo -e "\n$ts_id Results: ${GREEN}✓ Test suite $test_name PASSED${NC}" | tee -a "$MASTER_LOG"
    else
        echo -e "\n$ts_id Results: ${RED}✗ Test suite $test_name FAILED${NC}" | tee -a "$MASTER_LOG"
        FAILED_SUITES=$((FAILED_SUITES+1))
    fi
    
    return $result
}

# First run the setup script
run_test_suite "$TEST_DIR/scripts/TS00_test_setup.sh"
SETUP_RESULT=$?

# Only continue with other tests if setup was successful or had only warnings about specific files
if [ $SETUP_RESULT -eq 0 ] || [ $SETUP_RESULT -eq 1 ]; then
    # Mark setup as complete so individual test scripts don't try to run it again
    export TEST_SETUP_COMPLETE=true
    
    # We'll continue with tests but note the warning
    if [ $SETUP_RESULT -eq 1 ]; then
        echo -e "\n${YELLOW}⚠ Setup had warnings or failures with some files. Continuing with tests...${NC}" | tee -a "$MASTER_LOG"
    fi
    
    # Find and run all test scripts except TS00 (setup), sorted by number
    for test_script in $(find "$TEST_DIR/scripts" -type f -name "TS[0-9][0-9]_*.sh" | sort); do
        # Skip the setup script as we already ran it
        if [[ $(basename "$test_script") != "TS00_test_setup.sh" ]]; then
            run_test_suite "$test_script"
        fi
    done
else
    echo -e "\n${RED}✗ Setup failed critically. Skipping remaining tests.${NC}" | tee -a "$MASTER_LOG"
    # Count remaining test scripts for summary
    remaining_scripts=$(find "$TEST_DIR/scripts" -type f -name "TS[0-9][0-9]_*.sh" | grep -v "TS00_" | wc -l)
    TOTAL_SUITES=$((TOTAL_SUITES+remaining_scripts))
    FAILED_SUITES=$((FAILED_SUITES+remaining_scripts))
fi

# Summary
echo -e "\n-----------------------------------------" | tee -a "$MASTER_LOG"
echo -e "Test Summary" | tee -a "$MASTER_LOG"
echo -e "-----------------------------------------" | tee -a "$MASTER_LOG"
echo "Total test suites: $TOTAL_SUITES" | tee -a "$MASTER_LOG"
echo "Failed test suites: $FAILED_SUITES" | tee -a "$MASTER_LOG"
echo "Master log file: $MASTER_LOG" | tee -a "$MASTER_LOG"

if [ $FAILED_SUITES -eq 0 ]; then
    echo -e "\n${GREEN}✓ ALL TEST SUITES PASSED${NC}" | tee -a "$MASTER_LOG"
    exit 0
else
    echo -e "\n${RED}✗ SOME TEST SUITES FAILED${NC}" | tee -a "$MASTER_LOG"
    exit 1
fi 