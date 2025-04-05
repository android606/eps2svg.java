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
chmod +x "$TEST_DIR/scripts/test_*.sh"

# Count tests and failures
TOTAL_SUITES=0
FAILED_SUITES=0

echo -e "${BLUE}==========================================${NC}" | tee -a "$MASTER_LOG"
echo -e "${BLUE}Starting EPS to SVG Conversion Test Suites${NC}" | tee -a "$MASTER_LOG"
echo -e "${BLUE}==========================================${NC}" | tee -a "$MASTER_LOG"

# Define test suites with their IDs and names
declare -A TEST_SUITE_NAMES
TEST_SUITE_NAMES["test_setup.sh"]="Test Setup"
TEST_SUITE_NAMES["test_basic_conversion.sh"]="Basic Conversion"
TEST_SUITE_NAMES["test_binary_eps.sh"]="Binary EPS Support"
TEST_SUITE_NAMES["test_viewbox.sh"]="ViewBox Correctness"
TEST_SUITE_NAMES["test_path_bounds.sh"]="Path Boundaries"
TEST_SUITE_NAMES["test_visual.sh"]="Visual Quality"

# Function to run a test script and track results
run_test_suite() {
    local test_script=$1
    local script_name=$(basename "$test_script")
    local ts_id="${TEST_SUITE_IDS[$script_name]:-TSXX}"
    local test_name="${TEST_SUITE_NAMES[$script_name]:-Unknown Test}"
    
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
run_test_suite "$TEST_DIR/scripts/test_setup.sh"
SETUP_RESULT=$?

# Only continue with other tests if setup was successful or had only warnings about specific files
if [ $SETUP_RESULT -eq 0 ] || [ $SETUP_RESULT -eq 1 ]; then
    # Mark setup as complete so individual test scripts don't try to run it again
    export TEST_SETUP_COMPLETE=true
    
    # We'll continue with tests but note the warning
    if [ $SETUP_RESULT -eq 1 ]; then
        echo -e "\n${YELLOW}⚠ Setup had warnings or failures with some files. Continuing with tests...${NC}" | tee -a "$MASTER_LOG"
    fi
    
    # Run each test suite
    run_test_suite "$TEST_DIR/scripts/test_basic_conversion.sh"
    run_test_suite "$TEST_DIR/scripts/test_binary_eps.sh"
    run_test_suite "$TEST_DIR/scripts/test_viewbox.sh"
    run_test_suite "$TEST_DIR/scripts/test_path_bounds.sh"
    run_test_suite "$TEST_DIR/scripts/test_visual.sh"
else
    echo -e "\n${RED}✗ Setup failed critically. Skipping remaining tests.${NC}" | tee -a "$MASTER_LOG"
    # Count remaining suites as failed for summary
    TOTAL_SUITES=$((TOTAL_SUITES+5))
    FAILED_SUITES=$((FAILED_SUITES+5))
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