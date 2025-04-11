#!/bin/bash

# Script to update all test scripts to include standardized headers
# Adds TEST_SUITE_NAME and TEST_SUITE_FILENAME to all scripts

echo "Updating test script headers..."

# Function to get a friendly name from the filename
get_friendly_name() {
  local filename=$1
  # Remove TS prefix and path, convert underscores to spaces, title case
  name=$(echo "$filename" | sed -E 's/.*TS[0-9]+_test_//g' | sed -E 's/\.sh$//g' | sed 's/_/ /g')
  # Capitalize first letter of each word
  name=$(echo "$name" | sed -E 's/\b(\w)/\U\1/g')
  echo "$name"
}

# Process each test script
for script in test/scripts/TS*_*.sh; do
  echo "Processing $script"
  
  # Extract the test number and generate a friendly name
  ts_id=$(grep -E "TEST_SUITE_ID\s*=\s*\"TS[0-9]+\"" "$script" | grep -oE "TS[0-9]+" || echo "")
  
  if [ -z "$ts_id" ]; then
    echo "  Warning: Could not extract TEST_SUITE_ID from $script, skipping"
    continue
  fi
  
  # Generate the friendly name from the filename
  friendly_name=$(get_friendly_name "$script")
  
  # Check if the script already has TEST_SUITE_FILENAME
  if grep -q "TEST_SUITE_FILENAME" "$script"; then
    echo "  Already has TEST_SUITE_FILENAME, skipping"
    continue
  fi
  
  # Back up the original file
  cp "$script" "${script}.bak"
  
  # Add TEST_SUITE_NAME and TEST_SUITE_FILENAME after TEST_SUITE_ID line
  sed -i -E "s/(TEST_SUITE_ID=\"$ts_id\".*)/\1\nTEST_SUITE_NAME=\"$friendly_name\"\nTEST_SUITE_FILENAME=\$(basename \"\${BASH_SOURCE[0]}\" .sh)  # Script name without extension, also used for log file name/g" "$script"
  
  # Update log file path if it uses get_log_filename
  if grep -q "get_log_filename" "$script"; then
    sed -i -E "s/LOG_FILE=\".*get_log_filename.*\"/LOG_FILE=\"\$LOGS_DIR\/\$TEST_SUITE_ID-\$TEST_SUITE_FILENAME.log\"  # Log file name/g" "$script"
  fi
  
  # Remove existing TEST_SUITE_NAME= line if it appears after the header
  sed -i -E "/^TEST_SUITE_NAME=.*$/d" "$script"
  
  # Update log header line
  sed -i -E "s/echo \"=====.*=====\"/echo \"===== \$TEST_SUITE_ID: \$TEST_SUITE_NAME Tests - \$(date) =====\"/g" "$script"
  
  echo "  Updated $script"
done

echo "All test scripts updated!" 