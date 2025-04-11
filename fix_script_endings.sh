#!/bin/bash

# Script to fix the unexpected EOF error by adding missing 'fi' to all test scripts

echo "Fixing missing 'fi' in test scripts..."

# Process each test script
for script in test/scripts/TS*_*.sh; do
  # Check if the script ends with 'exit 1' without a closing 'fi'
  if grep -q "exit 1$" "$script"; then
    echo "Fixing $script"
    # Add a closing 'fi' after the last line
    echo "fi" >> "$script"
  fi
done

echo "All scripts fixed!" 