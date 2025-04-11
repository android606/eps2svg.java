#!/bin/bash

# Script to fix any remaining issues with test headers

echo "Fixing test script headers..."

for script in test/scripts/TS*_*.sh; do
  echo "Checking $script"
  
  # Extract test suite ID
  ts_id=$(grep -E "TEST_SUITE_ID\s*=\s*\"TS[0-9]+\"" "$script" | grep -oE "TS[0-9]+" || echo "")
  
  if [ -z "$ts_id" ]; then
    echo "  Warning: Could not extract TEST_SUITE_ID from $script, skipping"
    continue
  fi
  
  # Check if TEST_SUITE_NAME is missing or empty
  if ! grep -q "TEST_SUITE_NAME" "$script" || grep -q "TEST_SUITE_NAME=\"\"" "$script"; then
    # Generate a friendly name from the filename
    filename=$(basename "$script" .sh)
    friendly_name=$(echo "$filename" | sed -E 's/TS[0-9]+_test_//g' | sed 's/_/ /g' | sed -E 's/\b(\w)/\U\1/g')
    
    echo "  Adding TEST_SUITE_NAME=\"$friendly_name\" to $script"
    
    # Back up the file
    cp "$script" "${script}.bak2"
    
    # Add TEST_SUITE_NAME after TEST_SUITE_ID line
    sed -i "/TEST_SUITE_ID=\"$ts_id\"/a TEST_SUITE_NAME=\"$friendly_name\"" "$script"
  fi
  
  # Make sure log file uses the correct variables
  if ! grep -q "LOG_FILE=\"\$LOGS_DIR/\$TEST_SUITE_ID-\$TEST_SUITE_FILENAME.log\"" "$script"; then
    echo "  Fixing LOG_FILE in $script"
    sed -i 's|LOG_FILE=.*|LOG_FILE="$LOGS_DIR/$TEST_SUITE_ID-$TEST_SUITE_FILENAME.log"  # Log file name|g' "$script"
  fi
  
  # Fix the test header output
  if ! grep -q "echo \"===== \$TEST_SUITE_ID: \$TEST_SUITE_NAME Tests" "$script"; then
    echo "  Fixing log header in $script"
    sed -i 's|echo "===== .*|echo "===== $TEST_SUITE_ID: $TEST_SUITE_NAME Tests - $(date) =====" > "$LOG_FILE"|g' "$script"
  fi
  
  # Remove TS_ID line if present
  if grep -q "TS_ID=" "$script"; then
    echo "  Removing TS_ID variable from $script"
    sed -i '/TS_ID=/d' "$script"
  fi
  
  # Fix the print_test_suite_header line
  if ! grep -q "print_test_suite_header \"\$TEST_SUITE_NAME\"" "$script"; then
    echo "  Fixing print_test_suite_header in $script"
    sed -i 's|print_test_suite_header.*|print_test_suite_header "$TEST_SUITE_NAME" | tee -a "$LOG_FILE"|g' "$script"
  fi
done

echo "All test scripts fixed!" 