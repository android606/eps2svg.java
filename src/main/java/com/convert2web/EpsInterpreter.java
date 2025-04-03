package com.convert2web;

import java.util.Stack;
import java.util.logging.Logger;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.awt.geom.Point2D;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Interprets PostScript commands from an EPS file and calls a GraphicsHandler 
 * to perform graphical operations.
 */
public class EpsInterpreter {
    private static final Logger logger = Logger.getLogger(EpsInterpreter.class.getName());

    private final Stack<Object> operandStack = new Stack<>();
    private final Stack<Map<String, Object>> dictStack = new Stack<>();
    private final Stack<List<Object>> procDefStack = new Stack<>();
    private boolean isBuildingArray = false;
    private List<Object> currentArray = null;
    private final GraphicsHandler graphicsHandler;
    private double[] parsedBbox = null;
    // private static final double EPSILON = 1e-6; // Tolerance for point comparison (Unused)

    public EpsInterpreter(GraphicsHandler handler) {
        this.graphicsHandler = handler;
        dictStack.push(new HashMap<>()); // Initialize user dictionary
    }

    public void processEps(String inputEpsPath) throws IOException {
        logger.info("Starting EPS interpretation for: " + inputEpsPath);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputEpsPath), StandardCharsets.ISO_8859_1))) {
            boolean headerProcessed = false;
            String line = null;

            // --- Header Processing Phase ---
            while (!headerProcessed && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("%%BoundingBox:")) {
                    parseBoundingBox(line);
                } else if (line.startsWith("%%EndComments")) {
                    headerProcessed = true;
                } else if (line.startsWith("%!PS-Adobe-") || line.startsWith("%%")) {
                    logger.fine("(Header) Ignoring comment/directive: " + line);
                } else {
                    logger.warning("Non-comment line encountered before %%EndComments: " + line + ". Treating header as processed.");
                    headerProcessed = true;
                }
                if (headerProcessed && parsedBbox == null) {
                    logger.warning("%%BoundingBox not found before end of header comments.");
                }
            }

            // Initialize the graphics handler
            if (parsedBbox != null && parsedBbox.length == 4) {
                graphicsHandler.initialize(parsedBbox[0], parsedBbox[1], parsedBbox[2], parsedBbox[3]);
            } else {
                logger.log(Level.WARNING, "BoundingBox not found or invalid. Using default initialization (0,0,100,100).");
                graphicsHandler.initialize(0.0, 0.0, 100.0, 100.0);
            }

            // --- Body Processing Phase ---
            boolean processCurrentLine = (line != null); 
            do {
                if (processCurrentLine) {
                   processCurrentLine = false;
                } else {
                   line = reader.readLine();
                }
                
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("%%EOF")) {
                    logger.fine("%%EOF encountered. Finalizing.");
                    break;
                }
                 if (line.startsWith("%")) {
                     logger.fine("(Body) Ignoring comment: " + line);
                    continue;
                }

                // Add spaces around delimiters BEFORE tokenizing
                String lineToProcess = line
                    .replace("[", " [ ")
                    .replace("]", " ] ")
                    .replace("{", " { ")
                    .replace("}", " } ")
                    .replaceAll("\\s+", " ").trim(); // Collapse multiple spaces

                // Use StringTokenizer again
                StringTokenizer tokenizer = new StringTokenizer(lineToProcess);
                while (tokenizer.hasMoreTokens()) {
                    interpretToken(tokenizer.nextToken());
                }
            } while (true);

            logger.info("Finished interpreting EPS file.");

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error reading EPS file: " + e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during EPS interpretation: " + e.getMessage(), e);
            throw new RuntimeException("Interpretation failed: " + e.getMessage(), e);
        }
    }

    private void parseBoundingBox(String line) {
         try {
             String[] parts = line.substring("%%BoundingBox:".length()).trim().split("\\s+");
             if (parts.length >= 4) {
                 parsedBbox = new double[4];
                 parsedBbox[0] = Double.parseDouble(parts[0]);
                 parsedBbox[1] = Double.parseDouble(parts[1]);
                 parsedBbox[2] = Double.parseDouble(parts[2]);
                 parsedBbox[3] = Double.parseDouble(parts[3]);
                 logger.fine("Parsed %%BoundingBox: " + Arrays.toString(parsedBbox));
             } else {
                 logger.warning("Invalid %%BoundingBox format: " + line);
             }
         } catch (NumberFormatException e) {
             logger.warning("Could not parse number in %%BoundingBox: " + line + " -> " + e.getMessage());
         }
     }

    private void interpretToken(String tokenString) {
        if (tokenString == null || tokenString.isEmpty()) return;

        // Special case for the exact token "{0}" which is found in some EPS files
        // and is causing issues with our parser
        if (tokenString.equals("{0}")) {
            logger.log(Level.FINE, "Found special token {0}, treating as literal", tokenString);
            operandStack.push(tokenString);
            return;
        }

        // Special handling for array construction
        if (isBuildingArray) {
            if (tokenString.equals("]")) {
                handleArrayEnd();
                return;
            } else {
                try {
                    Double number = Double.parseDouble(tokenString);
                    currentArray.add(number);
                    logger.log(Level.FINE, "Added number {0} to array", number);
                } catch (NumberFormatException e) {
                    // Not a number, check for name
                    if (tokenString.startsWith("/")) {
                        currentArray.add(tokenString);
                        logger.log(Level.FINE, "Added name {0} to array", tokenString);
                    } else {
                        currentArray.add(tokenString); // Add as string/operator
                        logger.log(Level.FINE, "Added token {0} to array", tokenString);
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error adding to array: " + e.getMessage(), e);
                    isBuildingArray = false; // Reset state on error
                }
                return; // Handled token in array state
            }
        }

        // Handle string literals (enclosed in parentheses)
        if (tokenString.startsWith("(")) {
            if (tokenString.endsWith(")")) {
                // A complete string literal in a single token
                String string = tokenString; // Keep parentheses for now; they'll be removed when the string is used
                operandStack.push(string);
                logger.log(Level.FINE, "Pushed string literal: {0}", string);
            } else {
                logger.log(Level.WARNING, "Unclosed string literal: {0}", tokenString);
                // Push it anyway and hope operators handle it correctly
                operandStack.push(tokenString);
            }
            return;
        }

        // Handle numeric literals
        try {
            Double number = Double.parseDouble(tokenString);
            operandStack.push(number);
            logger.log(Level.FINE, "Pushed number: {0}", number);
            return;
        } catch (NumberFormatException e) {
            // Not a number, continue processing
        }

        // Handle array start
        if (tokenString.equals("[")) {
            handleArrayStart();
            return;
        }

        // Handle name literals (PostScript /name objects)
        if (tokenString.startsWith("/")) {
            operandStack.push(tokenString);
            logger.log(Level.FINE, "Pushed name: {0}", tokenString);
            return;
        }

        // Handle procedure definition start/end
        if (tokenString.equals("{")) {
            handleProcedureStart();
            return;
        } else if (tokenString.equals("}")) {
            handleProcedureEnd();
            return;
        }
        
        // Handle comments (lines starting with %)
        if (tokenString.startsWith("%")) {
            logger.log(Level.FINE, "Ignoring comment: {0}", tokenString);
            return;
        }

        // Check if it's a defined name (procedure or value)
        Object procOrValue = findInDictStack(tokenString);
        if (procOrValue != null) {
            if (procOrValue instanceof List) {
                // It's a procedure, execute its tokens (List of tokens)
                logger.log(Level.FINE, "Executing procedure: {0}", tokenString);
                @SuppressWarnings("unchecked")
                List<Object> procedure = (List<Object>) procOrValue;
                executeProcedure(procedure);
            } else {
                // It's a value, push to operand stack
                operandStack.push(procOrValue);
                logger.log(Level.FINE, "Pushed value from dict: {0} = {1}", new Object[]{tokenString, procOrValue});
            }
        } else {
            // Assume it's an operator
            processOperator(tokenString);
        }
    }

    private void executeProcedure(List<Object> procedure) {
         logger.log(Level.FINEST, "Executing procedure content: {0}", procedure); // Changed level
         for (Object procToken : procedure) {
             if (procToken instanceof String) {
                 interpretToken((String)procToken); 
             } else if (procToken instanceof Double) {
                 operandStack.push(procToken);
                 logger.log(Level.FINEST, "(Proc) Pushed number: {0}", procToken);
             } else if (procToken instanceof List) {
                 // Handle nested procedures or arrays
                 operandStack.push(procToken);
                 logger.log(Level.FINEST, "(Proc) Pushed nested procedure/array: {0}", procToken);
             } else {
                 // For any other type, just push it to the operand stack as is
                 operandStack.push(procToken);
                 logger.log(Level.WARNING, "(Proc) Pushing unknown type to stack: {0}", procToken);
             }
         }
    }

    private Object lookup(String name) {
        for (int i = dictStack.size() - 1; i >= 0; i--) {
            Map<String, Object> dict = dictStack.get(i);
            if (dict.containsKey(name)) {
                return dict.get(name);
            }
        }
        return null;
    }

    private void processOperator(String operator) {
        logger.log(Level.FINE, "Processing operator: {0}", operator);

        // Check if we're inside a procedure definition, except for '{' and '}'
        if (!operator.equals("{") && !operator.equals("}") && procDefStack.size() > 0) {
            // When inside a procedure definition, accumulate tokens except for def
            if (!operator.equals("def")) {
                List<Object> currentProcDef = procDefStack.peek();
                currentProcDef.add(operator);
                logger.log(Level.FINE, "Added operator ''{0}'' to procedure definition", operator);
                return;
            }
        }

        switch (operator) {
            // --- Array Handling ---
            case "[":
                handleArrayStart();
                break;
            case "]":
                handleArrayEnd();
                break;

            // --- Procedure Definition ---
            case "{":
                handleProcedureStart();
                break;
            case "}":
                handleProcedureEnd();
                break;
            case "def":
                handleDefinition();
                break;

            // --- Graphics State Operators ---
            case "gsave":
                graphicsHandler.gsave();
                break;
            case "grestore":
                graphicsHandler.grestore();
                break;
            case "setlinewidth":
                if (operandStack.size() >= 1 && operandStack.peek() instanceof Double) {
                    graphicsHandler.setLineWidth((Double) operandStack.pop());
                } else logger.log(Level.WARNING, "setlinewidth: stack underflow or non-numeric operand");
                break;
            case "setrgbcolor":
                if (operandStack.size() >= 3) {
                    Object bObj = operandStack.pop();
                    Object gObj = operandStack.pop();
                    Object rObj = operandStack.pop();
                    if (bObj instanceof Double && gObj instanceof Double && rObj instanceof Double) {
                        graphicsHandler.setRGBColor((Double) rObj, (Double) gObj, (Double) bObj);
                    } else {
                        logger.log(Level.WARNING, "setrgbcolor: non-numeric operands.");
                        // Attempt to restore stack state
                        operandStack.push(rObj); operandStack.push(gObj); operandStack.push(bObj);
                    }
                } else logger.log(Level.WARNING, "setrgbcolor: stack underflow");
                break;
            case "concat":
                handleConcatMatrix();
                break;

            // --- Path Construction Operators ---
            case "newpath":
                graphicsHandler.newPath();
                break;
            case "moveto":
                if (operandStack.size() >= 2) {
                    Object yObj = operandStack.pop();
                    Object xObj = operandStack.pop();
                    if (xObj instanceof Double && yObj instanceof Double) {
                        graphicsHandler.moveTo((Double) xObj, (Double) yObj);
                    } else {
                        logger.log(Level.WARNING, "moveto: non-numeric coords.");
                        // Attempt to restore stack state
                        operandStack.push(xObj); operandStack.push(yObj);
                    }
                } else logger.log(Level.WARNING, "moveto: stack underflow");
                break;
            case "lineto":
                if (operandStack.size() >= 2) {
                    Object yObj = operandStack.pop();
                    Object xObj = operandStack.pop();
                    if (xObj instanceof Double && yObj instanceof Double) {
                        graphicsHandler.lineTo((Double) xObj, (Double) yObj);
                    } else {
                        logger.log(Level.WARNING, "lineto: non-numeric coords.");
                        // Attempt to restore stack state
                        operandStack.push(xObj); operandStack.push(yObj);
                    }
                } else logger.log(Level.WARNING, "lineto: stack underflow");
                break;
            case "curveto":
                if (operandStack.size() >= 6) {
                    Object y3 = operandStack.pop(); Object x3 = operandStack.pop();
                    Object y2 = operandStack.pop(); Object x2 = operandStack.pop();
                    Object y1 = operandStack.pop(); Object x1 = operandStack.pop();
                    if (x1 instanceof Double && y1 instanceof Double && x2 instanceof Double && 
                        y2 instanceof Double && x3 instanceof Double && y3 instanceof Double) {
                        graphicsHandler.curveTo((Double)x1,(Double)y1,(Double)x2,(Double)y2,(Double)x3,(Double)y3);
                    } else {
                        logger.log(Level.WARNING, "curveto: non-numeric coords.");
                        // Attempt to restore stack state
                        operandStack.push(x1); operandStack.push(y1); operandStack.push(x2);
                        operandStack.push(y2); operandStack.push(x3); operandStack.push(y3);
                    }
                } else logger.log(Level.WARNING, "curveto: stack underflow");
                break;
            case "closepath":
                graphicsHandler.closePath();
                break;

            // --- Painting Operators ---
            case "fill":
                graphicsHandler.fill();
                break;
            case "eofill":
                graphicsHandler.eoFill();
                break;
            case "stroke":
                graphicsHandler.stroke();
                break;

            // --- Text Operators ---
            case "BT":
                graphicsHandler.beginText();
                break;
            case "ET":
                graphicsHandler.endText();
                break;
            case "Tf":
                if (operandStack.size() >= 2) {
                    Object sizeObj = operandStack.pop();
                    Object fontObj = operandStack.pop();
                    if (sizeObj instanceof Double && fontObj instanceof String) {
                        double fontSize = (Double) sizeObj;
                        String fontName = (String) fontObj;
                        // Remove leading '/' if present
                        if (fontName.startsWith("/")) {
                            fontName = fontName.substring(1);
                        }
                        graphicsHandler.setFont(fontName, fontSize);
                    } else {
                        logger.log(Level.WARNING, "Tf: invalid font or size operands");
                        // Restore stack
                        operandStack.push(fontObj);
                        operandStack.push(sizeObj);
                    }
                } else {
                    logger.log(Level.WARNING, "Tf: stack underflow");
                }
                break;
            case "Tm":
                if (operandStack.size() >= 6) {
                    double[] matrix = new double[6];
                    for (int i = 5; i >= 0; i--) {
                        Object obj = operandStack.pop();
                        if (obj instanceof Double) {
                            matrix[i] = (Double) obj;
                        } else {
                            logger.log(Level.WARNING, "Tm: non-numeric matrix element: {0}", obj);
                            // Matrix is invalid, don't process
                            return;
                        }
                    }
                    graphicsHandler.setTextMatrix(matrix);
                } else {
                    logger.log(Level.WARNING, "Tm: stack underflow");
                }
                break;
            case "Td":
                if (operandStack.size() >= 2) {
                    Object yObj = operandStack.pop();
                    Object xObj = operandStack.pop();
                    if (xObj instanceof Double && yObj instanceof Double) {
                        graphicsHandler.moveText((Double) xObj, (Double) yObj);
                    } else {
                        logger.log(Level.WARNING, "Td: non-numeric coordinates");
                        // Restore stack
                        operandStack.push(xObj);
                        operandStack.push(yObj);
                    }
                } else {
                    logger.log(Level.WARNING, "Td: stack underflow");
                }
                break;
            case "Tj":
            case "show":
                if (operandStack.size() >= 1) {
                    Object textObj = operandStack.pop();
                    if (textObj instanceof String) {
                        String text = (String) textObj;
                        // Remove enclosing parentheses if present
                        if (text.startsWith("(") && text.endsWith(")")) {
                            text = text.substring(1, text.length() - 1);
                        }
                        graphicsHandler.showText(text);
                    } else {
                        logger.log(Level.WARNING, "show/Tj: non-string operand: {0}", textObj);
                        operandStack.push(textObj);
                    }
                } else {
                    logger.log(Level.WARNING, "show/Tj: stack underflow");
                }
                break;

            // --- Ignored Operators ---
            case "showpage": logger.log(Level.FINE, "Ignoring known token: showpage"); break;
            case "setdash": 
                handleSetDash();
                break;

            // --- Unhandled Operators ---
            default:
                logger.log(Level.WARNING, "Unhandled operator: {0}", operator);
                break;
        }
    }

    private void handleArrayStart() {
        logger.log(Level.FINE, "ARRAY START DETECTED: '[' received."); // Changed from SEVERE to FINE
        isBuildingArray = true;
        currentArray = new ArrayList<>();
        logger.log(Level.FINE, "Started array construction.");
    }

    private void handleArrayEnd() {
        operandStack.push(currentArray);
        isBuildingArray = false;
        Object arrayObj = operandStack.peek();
        logger.log(Level.FINE, "Ended array construction. Pushed array: {0}", arrayObj); // Fixed logging
        currentArray = null;
    }

    private void handleProcedureStart() {
        procDefStack.push(new ArrayList<>());
        logger.log(Level.FINE, "Started procedure definition.");
    }

    private void handleProcedureEnd() {
        List<Object> procedure = procDefStack.pop();
        operandStack.push(procedure);
        logger.log(Level.FINE, "Ended procedure definition. Pushed procedure.");
    }

    private void handleDefinition() {
        if (operandStack.size() >= 2) {
            Object value = operandStack.pop(); 
            Object keyObj = operandStack.pop();
            if (keyObj instanceof String && ((String) keyObj).startsWith("/")) {
                String key = ((String) keyObj).substring(1);
                if (!dictStack.isEmpty()) {
                    dictStack.peek().put(key, value);
                    String valueStr = (value instanceof List) ? "[Procedure]" : String.valueOf(value);
                    logger.log(Level.FINE, "Defined ''{0}'' = {1}", new Object[]{key, valueStr});
                } else {
                    logger.log(Level.WARNING, "'def' operator: No dictionary on stack.");
                    operandStack.push(keyObj); operandStack.push(value);
                }
            } else {
                logger.log(Level.WARNING, "'def' operator: key ''{0}'' is not a name literal.", keyObj);
                operandStack.push(keyObj); operandStack.push(value);
            }
        } else logger.log(Level.WARNING, "'def' operator: stack underflow");
    }

    private void handleConcatMatrix() {
        if (operandStack.peek() instanceof List) {
            // Handle array format: expects [a b c d e f]
            List<?> matrix = (List<?>) operandStack.pop();
            if (matrix.size() != 6) {
                logger.log(Level.WARNING, "concat: array has wrong size: {0} (expected 6)", matrix.size());
                return;
            }
            
            double[] affineMatrix = new double[6];
            boolean validMatrix = true;
            
            for (int i = 0; i < 6; i++) {
                if (matrix.get(i) instanceof Double) {
                    affineMatrix[i] = (Double)matrix.get(i);
                } else {
                    validMatrix = false;
                    logger.log(Level.WARNING, "concat: matrix element at index {0} is not a number: {1}", 
                              new Object[]{i, matrix.get(i)});
                    break;
                }
            }
            
            if (validMatrix) {
                graphicsHandler.concatMatrix(affineMatrix);
                logger.log(Level.FINE, "Applied concatenated matrix: [{0}, {1}, {2}, {3}, {4}, {5}]", 
                          new Object[]{affineMatrix[0], affineMatrix[1], affineMatrix[2], 
                                      affineMatrix[3], affineMatrix[4], affineMatrix[5]});
            }
        } else if (operandStack.size() >= 6) {
            // Handle individual elements on stack: expects f e d c b a (PS places first element at top)
            double[] affineMatrix = new double[6];
            boolean validMatrix = true;
            
            for (int i = 5; i >= 0; i--) {
                Object element = operandStack.pop();
                if (element instanceof Double) {
                    affineMatrix[i] = (Double)element;
                } else {
                    validMatrix = false;
                    logger.log(Level.WARNING, "concat: stack element is not a number: {0}", element);
                    // Can't really restore stack here, but we'll try to continue
                    break;
                }
            }
            
            if (validMatrix) {
                graphicsHandler.concatMatrix(affineMatrix);
                logger.log(Level.FINE, "Applied concatenated matrix: [{0}, {1}, {2}, {3}, {4}, {5}]", 
                          new Object[]{affineMatrix[0], affineMatrix[1], affineMatrix[2], 
                                      affineMatrix[3], affineMatrix[4], affineMatrix[5]});
            }
        } else {
            logger.log(Level.WARNING, "concat: stack underflow or invalid operand");
        }
    }

    private void handleSetDash() {
        if (operandStack.size() >= 2) {
            Object offsetObj = operandStack.pop();
            Object patternObj = operandStack.pop();
            
            if (offsetObj instanceof Double && patternObj instanceof List) {
                double offset = (Double) offsetObj;
                List<?> patternList = (List<?>) patternObj;
                double[] pattern = new double[patternList.size()];
                
                boolean validPattern = true;
                for (int i = 0; i < patternList.size(); i++) {
                    Object item = patternList.get(i);
                    if (item instanceof Double) {
                        pattern[i] = (Double) item;
                    } else {
                        validPattern = false;
                        logger.log(Level.WARNING, "setdash: pattern element {0} is not a number: {1}", 
                                  new Object[]{i, item});
                        break;
                    }
                }
                
                if (validPattern) {
                    graphicsHandler.setDash(pattern, offset);
                    logger.log(Level.FINE, "Set dash pattern with {0} elements and offset {1}", 
                              new Object[]{pattern.length, offset});
                } else {
                    // Restore stack if pattern was invalid
                    operandStack.push(patternObj);
                    operandStack.push(offsetObj);
                }
            } else {
                logger.log(Level.WARNING, "setdash: Invalid operands. Expected array and number, got {0} and {1}", 
                          new Object[]{patternObj.getClass().getName(), offsetObj.getClass().getName()});
                // Restore stack
                operandStack.push(patternObj);
                operandStack.push(offsetObj);
            }
        } else {
            logger.log(Level.WARNING, "setdash: stack underflow, expected pattern array and offset");
        }
    }

    private Object findInDictStack(String name) {
        for (int i = dictStack.size() - 1; i >= 0; i--) {
            Map<String, Object> dict = dictStack.get(i);
            if (dict.containsKey(name)) {
                return dict.get(name);
            }
        }
        return null;
    }
} 