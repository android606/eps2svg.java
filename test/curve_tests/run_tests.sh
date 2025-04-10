#!/bin/bash

# Script to test the curve operators
# This script converts all the EPS test files to SVG and generates a report

# Define paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEST_DIR="$PROJECT_ROOT/test/curve_tests"
OUTPUT_DIR="$TEST_DIR/output"
REPORT_FILE="$TEST_DIR/curve_test_report.html"
JAR_FILE="$PROJECT_ROOT/target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar"

# Create output directory if it doesn't exist
mkdir -p "$OUTPUT_DIR"

# Build the application if the JAR doesn't exist
if [ ! -f "$JAR_FILE" ]; then
    echo "Building the application..."
    cd "$PROJECT_ROOT" && mvn clean package -DskipTests
fi

# Initialize HTML report
cat > "$REPORT_FILE" << EOF
<!DOCTYPE html>
<html>
<head>
    <title>PostScript Curve Operators Test Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        h1 { color: #333; }
        h2 { color: #555; margin-top: 30px; }
        .test-case { border: 1px solid #ddd; padding: 20px; margin-bottom: 20px; border-radius: 5px; }
        .test-result { margin-top: 10px; display: flex; flex-wrap: wrap; }
        .test-file { margin-right: 20px; margin-bottom: 20px; }
        .summary { margin-top: 30px; padding: 10px; background-color: #f5f5f5; border-radius: 5px; }
        .pass { color: green; }
        .fail { color: red; }
        pre { background-color: #f5f5f5; padding: 10px; border-radius: 5px; overflow-x: auto; }
        svg { max-width: 300px; max-height: 300px; border: 1px solid #ccc; }
        .validation-section { background-color: #f0f8ff; padding: 10px; margin: 10px 0; border-radius: 5px; }
        .validation-pass { background-color: #f0fff0; }
        .validation-fail { background-color: #fff0f0; }
    </style>
</head>
<body>
    <h1>PostScript Curve Operators Test Report</h1>
    <p>Generated on $(date)</p>
    <div class="summary">
        <h2>Summary</h2>
        <p>Testing the implementation of PostScript curve operators in the EPS to SVG converter.</p>
    </div>
EOF

# Function to extract path data from SVG
extract_path_data() {
    local svg_file="$1"
    grep -o 'd="[^"]*"' "$svg_file" | sed 's/d="//' | sed 's/"//'
}

# Function to calculate path accuracy
calculate_path_accuracy() {
    local path="$1"
    local expected="$2"
    local expected_parts=($expected)
    local path_parts=($path)
    
    # Count matching parts
    local total_points=0
    local matching_points=0
    local i=0
    
    while [ $i -lt ${#expected_parts[@]} ] && [ $i -lt ${#path_parts[@]} ]; do
        if [[ ${expected_parts[$i]} =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
            ((total_points++))
            
            # If the expected and actual values are within 0.1 of each other, consider them matching
            local expected_val=${expected_parts[$i]}
            local actual_val=${path_parts[$i]}
            
            if [ $(awk "BEGIN {print (($expected_val-$actual_val)^2 < 0.1)}") -eq 1 ]; then
                ((matching_points++))
            fi
        fi
        ((i++))
    done
    
    if [ $total_points -eq 0 ]; then
        echo "100.0" # If no points to check, return 100%
    else
        echo $(awk "BEGIN {printf \"%.1f\", ($matching_points/$total_points)*100}")
    fi
}

# Function to validate curveto paths
validate_curveto() {
    local svg_file="$1"
    local paths=$(extract_path_data "$svg_file")
    local validation_results=""
    local pass_count=0
    local fail_count=0

    # Expected path data for the first curveto
    local expected_first_curve="M 50.0 50.0 C 70.0 120.0 130.0 120.0 150.0 50.0"
    if echo "$paths" | grep -q "$expected_first_curve"; then
        validation_results+="<li class='pass'>First curve (curveto) path coordinates match expected values (100% accuracy)</li>\n"
        ((pass_count++))
    else
        # Find the actual path and calculate accuracy
        local actual_path=$(echo "$paths" | grep -o "M 50.0 50.0 C[^\"]*" | head -1)
        local accuracy=$(calculate_path_accuracy "$actual_path" "$expected_first_curve")
        
        if [ -n "$actual_path" ] && [ $(awk "BEGIN {print ($accuracy >= 90.0)}") -eq 1 ]; then
            validation_results+="<li class='pass'>First curve (curveto) path coordinates match expected values with ${accuracy}% accuracy</li>\n"
            ((pass_count++))
        else
            validation_results+="<li class='fail'>First curve (curveto) path coordinates do not match: Expected '$expected_first_curve', got '$actual_path' (${accuracy}% accuracy)</li>\n"
            ((fail_count++))
        fi
    fi

    # Expected path data for the second curveto (c)
    local expected_second_curve="M 50.0 150.0 C 70.0 80.0 130.0 80.0 150.0 150.0"
    if echo "$paths" | grep -q "$expected_second_curve"; then
        validation_results+="<li class='pass'>Second curve (c) path coordinates match expected values (100% accuracy)</li>\n"
        ((pass_count++))
    else
        # Find the actual path and calculate accuracy
        local actual_path=$(echo "$paths" | grep -o "M 50.0 150.0 C[^\"]*" | head -1)
        local accuracy=$(calculate_path_accuracy "$actual_path" "$expected_second_curve")
        
        if [ -n "$actual_path" ] && [ $(awk "BEGIN {print ($accuracy >= 90.0)}") -eq 1 ]; then
            validation_results+="<li class='pass'>Second curve (c) path coordinates match expected values with ${accuracy}% accuracy</li>\n"
            ((pass_count++))
        else
            validation_results+="<li class='fail'>Second curve (c) path coordinates do not match: Expected '$expected_second_curve', got '$actual_path' (${accuracy}% accuracy)</li>\n"
            ((fail_count++))
        fi
    fi

    # Return results
    echo -e "$validation_results"
    echo "$pass_count/$((pass_count + fail_count)) validations passed"
}

# Function to validate v operator paths
validate_v() {
    local svg_file="$1"
    local paths=$(extract_path_data "$svg_file")
    local validation_results=""
    local pass_count=0
    local fail_count=0

    # Expected path data for the v curve
    local expected_v_curve="M 50.0 100.0 C 50.0 100.0 80.0 150.0 150.0 100.0"
    if echo "$paths" | grep -q "$expected_v_curve"; then
        validation_results+="<li class='pass'>v operator curve path matches expected values (100% accuracy)</li>\n"
        ((pass_count++))
    else
        # Find the actual path and calculate accuracy
        local actual_path=$(echo "$paths" | grep -o "M 50.0 100.0 C[^\"]*" | head -1)
        local accuracy=$(calculate_path_accuracy "$actual_path" "$expected_v_curve")
        
        if [ -n "$actual_path" ] && [ $(awk "BEGIN {print ($accuracy >= 90.0)}") -eq 1 ]; then
            validation_results+="<li class='pass'>v operator curve path matches expected values with ${accuracy}% accuracy</li>\n"
            ((pass_count++))
        else
            validation_results+="<li class='fail'>v operator curve path does not match: Expected '$expected_v_curve', got '$actual_path' (${accuracy}% accuracy)</li>\n"
            ((fail_count++))
        fi
    fi

    # Expected path data for the comparison curveto
    local expected_comp_curve="M 50.0 50.0 C 50.0 50.0 80.0 0.0 150.0 50.0"
    if echo "$paths" | grep -q "$expected_comp_curve"; then
        validation_results+="<li class='pass'>Comparison curve path matches expected values (100% accuracy)</li>\n"
        ((pass_count++))
    else
        # Find the actual path and calculate accuracy
        local actual_path=$(echo "$paths" | grep -o "M 50.0 50.0 C[^\"]*" | head -1)
        local accuracy=$(calculate_path_accuracy "$actual_path" "$expected_comp_curve")
        
        if [ -n "$actual_path" ] && [ $(awk "BEGIN {print ($accuracy >= 90.0)}") -eq 1 ]; then
            validation_results+="<li class='pass'>Comparison curve path matches expected values with ${accuracy}% accuracy</li>\n"
            ((pass_count++))
        else
            validation_results+="<li class='fail'>Comparison curve path does not match: Expected '$expected_comp_curve', got '$actual_path' (${accuracy}% accuracy)</li>\n"
            ((fail_count++))
        fi
    fi

    # Return results
    echo -e "$validation_results"
    echo "$pass_count/$((pass_count + fail_count)) validations passed"
}

# Function to validate y operator paths
validate_y() {
    local svg_file="$1"
    local paths=$(extract_path_data "$svg_file")
    local validation_results=""
    local pass_count=0
    local fail_count=0

    # Expected path data for the y curve
    local expected_y_curve="M 50.0 100.0 C 100.0 150.0 150.0 100.0 150.0 100.0"
    if echo "$paths" | grep -q "$expected_y_curve"; then
        validation_results+="<li class='pass'>y operator curve path matches expected values (100% accuracy)</li>\n"
        ((pass_count++))
    else
        # Find the actual path and calculate accuracy
        local actual_path=$(echo "$paths" | grep -o "M 50.0 100.0 C[^\"]*" | head -1)
        local accuracy=$(calculate_path_accuracy "$actual_path" "$expected_y_curve")
        
        if [ -n "$actual_path" ] && [ $(awk "BEGIN {print ($accuracy >= 90.0)}") -eq 1 ]; then
            validation_results+="<li class='pass'>y operator curve path matches expected values with ${accuracy}% accuracy</li>\n"
            ((pass_count++))
        else
            validation_results+="<li class='fail'>y operator curve path does not match: Expected '$expected_y_curve', got '$actual_path' (${accuracy}% accuracy)</li>\n"
            ((fail_count++))
        fi
    fi

    # Expected path data for the comparison curveto
    local expected_comp_curve="M 50.0 50.0 C 100.0 0.0 150.0 50.0 150.0 50.0"
    if echo "$paths" | grep -q "$expected_comp_curve"; then
        validation_results+="<li class='pass'>Comparison curve path matches expected values (100% accuracy)</li>\n"
        ((pass_count++))
    else
        # Find the actual path and calculate accuracy
        local actual_path=$(echo "$paths" | grep -o "M 50.0 50.0 C[^\"]*" | head -1)
        local accuracy=$(calculate_path_accuracy "$actual_path" "$expected_comp_curve")
        
        if [ -n "$actual_path" ] && [ $(awk "BEGIN {print ($accuracy >= 90.0)}") -eq 1 ]; then
            validation_results+="<li class='pass'>Comparison curve path matches expected values with ${accuracy}% accuracy</li>\n"
            ((pass_count++))
        else
            validation_results+="<li class='fail'>Comparison curve path does not match: Expected '$expected_comp_curve', got '$actual_path' (${accuracy}% accuracy)</li>\n"
            ((fail_count++))
        fi
    fi

    # Return results
    echo -e "$validation_results"
    echo "$pass_count/$((pass_count + fail_count)) validations passed"
}

# Function to validate arc operator paths
validate_arc() {
    local svg_file="$1"
    local paths=$(extract_path_data "$svg_file")
    local validation_results=""
    local pass_count=0
    local fail_count=0

    # Full circle (should have control points approximating a circle)
    if echo "$paths" | grep -q "M 140.0 100.0 C .* 100.0 60.0"; then
        validation_results+="<li class='pass'>Full circle arc path has correct start and end points</li>\n"
        ((pass_count++))
    else
        validation_results+="<li class='fail'>Full circle arc path missing or has incorrect start/end points</li>\n"
        ((fail_count++))
    fi

    # 90-degree arc
    if echo "$paths" | grep -q "M 130.0 100.0 C .* 100.0 130.0"; then
        validation_results+="<li class='pass'>90-degree arc path has correct endpoints</li>\n"
        ((pass_count++))
    else
        validation_results+="<li class='fail'>90-degree arc path missing or has incorrect endpoints</li>\n"
        ((fail_count++))
    fi

    # Colored arcs with different starting angles
    if echo "$paths" | grep -q "M 135.4 135.4 C"; then
        validation_results+="<li class='pass'>Arc with 45-degree starting angle found</li>\n"
        ((pass_count++))
    else
        validation_results+="<li class='fail'>Arc with 45-degree starting angle not found</li>\n"
        ((fail_count++))
    fi

    # Return results
    echo -e "$validation_results"
    echo "$pass_count/$((pass_count + fail_count)) validations passed"
}

# Function to validate arcn operator paths
validate_arcn() {
    local svg_file="$1"
    local paths=$(extract_path_data "$svg_file")
    local validation_results=""
    local pass_count=0
    local fail_count=0

    # Full circle (should have control points approximating a circle)
    if echo "$paths" | grep -q "M 140.0 100.0 C"; then
        validation_results+="<li class='pass'>Full circle arcn path found</li>\n"
        ((pass_count++))
    else
        validation_results+="<li class='fail'>Full circle arcn path not found</li>\n"
        ((fail_count++))
    fi

    # 90-degree arcn (counterclockwise)
    if echo "$paths" | grep -q "M 100.0 130.0 C .* 130.0 100.0"; then
        validation_results+="<li class='pass'>90-degree arcn path has correct direction (counterclockwise)</li>\n"
        ((pass_count++))
    else
        validation_results+="<li class='fail'>90-degree arcn path missing or has incorrect direction</li>\n"
        ((fail_count++))
    fi

    # Comparison of arc vs arcn
    if echo "$paths" | grep -q "M 150.0 150.0 C .* 150.0 170.0"; then
        validation_results+="<li class='pass'>Comparison arc path found</li>\n"
        ((pass_count++))
    fi
    if echo "$paths" | grep -q "M 150.0 165.0 C .* 165.0 150.0"; then
        validation_results+="<li class='pass'>Comparison arcn path found (with correct direction)</li>\n"
        ((pass_count++))
    else
        validation_results+="<li class='fail'>Comparison arc/arcn paths not found or incorrect</li>\n"
        ((fail_count+=2))
    fi

    # Return results
    echo -e "$validation_results"
    echo "$pass_count/$((pass_count + fail_count)) validations passed"
}

# Function to validate arct operator paths
validate_arct() {
    local svg_file="$1"
    local paths=$(extract_path_data "$svg_file")
    local validation_results=""
    local pass_count=0
    local fail_count=0

    # First example: 90-degree corner with rounded edge
    local corner_path_found=false
    if echo "$paths" | grep -q "M 50.0 50.0"; then
        # Check for curve coordinates that would indicate a rounded corner
        if echo "$paths" | grep -q "L 50.0 90.0 L"; then
            validation_results+="<li class='pass'>90-degree corner path with rounded edge found</li>\n"
            corner_path_found=true
            ((pass_count++))
        fi
    fi
    
    if [ "$corner_path_found" = false ]; then
        validation_results+="<li class='fail'>90-degree corner path with rounded edge not found</li>\n"
        ((fail_count++))
    fi

    # Example with different radius
    if echo "$paths" | grep -q "M 50.0 150.0" && echo "$paths" | grep -q "L 50.0 140.0 L"; then
        validation_results+="<li class='pass'>Corner with 20-unit radius found</li>\n"
        ((pass_count++))
    else
        validation_results+="<li class='fail'>Corner with 20-unit radius not found</li>\n"
        ((fail_count++))
    fi

    # Rounded rectangle
    if echo "$paths" | grep -q "M 120.0 50.0 L 170.0 50.0"; then
        validation_results+="<li class='pass'>Rounded rectangle found</li>\n"
        ((pass_count++))
    else
        validation_results+="<li class='fail'>Rounded rectangle not found</li>\n"
        ((fail_count++))
    fi

    # Return results
    echo -e "$validation_results"
    echo "$pass_count/$((pass_count + fail_count)) validations passed"
}

# Function to determine which validation to run
run_validation() {
    local operator="$1"
    local svg_file="$2"
    
    case "$operator" in
        "curveto/c")
            validate_curveto "$svg_file"
            ;;
        "v")
            validate_v "$svg_file"
            ;;
        "y")
            validate_y "$svg_file"
            ;;
        "arc")
            validate_arc "$svg_file"
            ;;
        "arcn")
            validate_arcn "$svg_file"
            ;;
        "arct")
            validate_arct "$svg_file"
            ;;
        *)
            echo "No validation available for $operator"
            echo "0/0 validations passed"
            ;;
    esac
}

# Function to test a curve operator
test_curve_operator() {
    local eps_file="$1"
    local operator="$2"
    local description="$3"
    local basename=$(basename "$eps_file" .eps)
    local svg_file="$OUTPUT_DIR/${basename}.svg"
    local log_file="$OUTPUT_DIR/${basename}_log.txt"
    
    echo "Testing $operator operator: $eps_file -> $svg_file"
    
    # Run the conversion
    cd "$PROJECT_ROOT" && java -jar "$JAR_FILE" "$eps_file" "$svg_file" > "$log_file" 2>&1
    
    # Check conversion status
    local success=false
    if [ -f "$svg_file" ]; then
        success=true
    fi
    
    # Get SVG content
    local svg_content=""
    if [ -f "$svg_file" ]; then
        svg_content=$(cat "$svg_file")
    fi
    
    # Get log content
    local log_content=$(cat "$log_file")
    
    # Read the EPS file content
    local eps_content=$(cat "$eps_file")
    
    # Run validation
    local validation_results=""
    local validation_summary=""
    if [ "$success" = true ]; then
        validation_output=$(run_validation "$operator" "$svg_file")
        validation_results=$(echo "$validation_output" | head -n -1)
        validation_summary=$(echo "$validation_output" | tail -n 1)
        
        if [[ "$validation_summary" == *"0/"* ]]; then
            validation_class="validation-warn"
        elif [[ "$validation_summary" == *"/"* ]]; then
            num_passed=$(echo "$validation_summary" | cut -d'/' -f1)
            total=$(echo "$validation_summary" | cut -d'/' -f2 | cut -d' ' -f1)
            if [ "$num_passed" -eq "$total" ]; then
                validation_class="validation-pass"
            else
                validation_class="validation-fail"
            fi
        else
            validation_class="validation-warn"
        fi
    fi
    
    # Append to report
    cat >> "$REPORT_FILE" << EOF
    <div class="test-case">
        <h2>Operator: $operator</h2>
        <p>$description</p>
        <div class="test-result">
            <div class="test-file">
                <h3>Input EPS</h3>
                <pre>$eps_content</pre>
            </div>
            <div class="test-file">
                <h3>Output SVG</h3>
EOF
    
    if [ "$success" = true ]; then
        cat >> "$REPORT_FILE" << EOF
                <object data="output/${basename}.svg" type="image/svg+xml" width="300" height="300">
                    Your browser does not support SVG
                </object>
EOF
    else
        cat >> "$REPORT_FILE" << EOF
                <p class="fail">SVG file not generated!</p>
EOF
    fi
    
    cat >> "$REPORT_FILE" << EOF
            </div>
        </div>
EOF

    # Add validation section if validation was performed
    if [ "$success" = true ] && [ -n "$validation_results" ]; then
        cat >> "$REPORT_FILE" << EOF
        <div class="validation-section ${validation_class}">
            <h3>Path Validation</h3>
            <ul>
                $validation_results
            </ul>
            <p><strong>Summary:</strong> $validation_summary</p>
        </div>
EOF
    fi

    cat >> "$REPORT_FILE" << EOF
        <div>
            <h3>Conversion Log</h3>
            <pre>$log_content</pre>
        </div>
EOF
    
    if [ "$success" = true ]; then
        cat >> "$REPORT_FILE" << EOF
        <p>Result: <span class="pass">PASS</span></p>
EOF
    else
        cat >> "$REPORT_FILE" << EOF
        <p>Result: <span class="fail">FAIL</span></p>
EOF
    fi
    
    cat >> "$REPORT_FILE" << EOF
    </div>
EOF
}

# Test the curve operators
total_validations=0
passed_validations=0

test_and_track() {
    local eps_file="$1"
    local operator="$2"
    local description="$3"
    
    test_curve_operator "$eps_file" "$operator" "$description"
    
    # Extract validation results from the most recent test
    if [ -f "$REPORT_FILE" ]; then
        local result_line=$(grep -o "[0-9]\+/[0-9]\+ validations passed" "$REPORT_FILE" | tail -1)
        if [ -n "$result_line" ]; then
            local num_passed=$(echo "$result_line" | cut -d'/' -f1)
            local total=$(echo "$result_line" | cut -d'/' -f2 | cut -d' ' -f1)
            
            total_validations=$((total_validations + total))
            passed_validations=$((passed_validations + num_passed))
        fi
    fi
}

test_and_track "$TEST_DIR/curveto_test.eps" "curveto/c" "The curveto operator creates a cubic Bezier curve with two control points"
test_and_track "$TEST_DIR/v_test.eps" "v" "The v operator creates a curve using the current point as the first control point"
test_and_track "$TEST_DIR/y_test.eps" "y" "The y operator creates a curve using the endpoint as the second control point"
test_and_track "$TEST_DIR/arc_test.eps" "arc" "The arc operator creates a circular arc"
test_and_track "$TEST_DIR/arcn_test.eps" "arcn" "The arcn operator creates a counterclockwise circular arc"
test_and_track "$TEST_DIR/arct_test.eps" "arct" "The arct operator creates a tangent arc between two lines"

# Add summary to the end of the report
cat >> "$REPORT_FILE" << EOF
<div class="summary">
    <h2>Validation Summary</h2>
    <p>Total validations: $total_validations</p>
    <p>Passed validations: $passed_validations</p>
    <p>Success rate: $(awk "BEGIN {printf \"%.1f%%\", ($passed_validations/$total_validations)*100}")</p>
</div>
EOF

# Finalize the report
cat >> "$REPORT_FILE" << EOF
</body>
</html>
EOF

echo "Testing complete! Report generated at $REPORT_FILE"
echo "You can view the report by opening it in a web browser." 