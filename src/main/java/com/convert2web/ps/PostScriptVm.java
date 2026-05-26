package com.convert2web.ps;

import com.convert2web.model.EpsDocument;
import com.convert2web.model.EpsDocumentBuilder;
import com.convert2web.model.Matrix;
import com.convert2web.model.PaintStyle;
import com.convert2web.model.StrokeStyle;
import com.convert2web.model.WindingRule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes parsed PostScript {@link PsValue} programs.
 */
public final class PostScriptVm {
    private final Deque<PsValue> operandStack = new ArrayDeque<>();
    private final Deque<PsDictionary> dictStack = new ArrayDeque<>();
    private final Deque<VmGraphicsState> graphicsStack = new ArrayDeque<>();
    private final Map<String, PostScriptOperator> systemOperators = new HashMap<>();
    private final PsDictionary systemDict = new PsDictionary(0);
    private final PsDictionary globalDict = new PsDictionary(0);
    private VmGraphicsState graphicsState = new VmGraphicsState();
    private final EpsDocumentBuilder documentBuilder = new EpsDocumentBuilder();
    private final EpsDocumentRecorder documentRecorder = new EpsDocumentRecorder(documentBuilder);
    private String indexedPaletteContext;
    private int loopDepth;
    private boolean exitRequested;

    public PostScriptVm() {
        dictStack.push(globalDict);
        dictStack.push(new PsDictionary(0)); // userdict
        registerBuiltinOperators();
        systemDict.put("systemdict", new PsValue.RuntimeDictionaryValue(systemDict));
        globalDict.put("globaldict", new PsValue.RuntimeDictionaryValue(globalDict));
        dictStack.peek().put("userdict", new PsValue.RuntimeDictionaryValue(dictStack.peek()));
    }

    public void executeAll(List<PsValue> program) {
        for (PsValue value : program) {
            execute(value);
        }
    }

    /**
     * Runs a program while continuing after {@link PostScriptVmException}.
     * Used when executing Illustrator AGM prologs before the VM implements every operator.
     */
    public void executeAllLenient(List<PsValue> program) {
        for (PsValue value : program) {
            try {
                execute(value);
            } catch (PostScriptVmException ignored) {
                // Keep going so partial prolog setup can still define shorthands or draw paths.
            }
        }
    }

    public void setIndexedPaletteContext(String pageBody) {
        this.indexedPaletteContext = pageBody;
    }

    public String getIndexedPaletteContext() {
        return indexedPaletteContext;
    }

    public void execute(PsValue value) {
        if (value instanceof PsValue.AgmBinaryInvokeValue) {
            AgmImagePaint.apply(this, (PsValue.AgmBinaryInvokeValue) value);
            return;
        }
        if (value instanceof PsValue.NameValue) {
            PsValue.NameValue name = (PsValue.NameValue) value;
            if (name.isLiteral()) {
                push(name);
                return;
            }
            executeName(name.getName());
            return;
        }
        push(value);
    }

    private void executeName(String name) {
        PostScriptOperator operator = lookupOperator(name);
        if (operator != null) {
            operator.execute(this);
            return;
        }
        PsValue bound = lookupValue(name);
        if (bound == null) {
            throw new PostScriptVmException("undefined: " + name);
        }
        if (bound instanceof PsValue.NameValue && !((PsValue.NameValue) bound).isLiteral()) {
            executeName(((PsValue.NameValue) bound).getName());
            return;
        }
        if (bound instanceof PsValue.ProcedureValue) {
            executeProcedure(this, bound);
            return;
        }
        execute(bound);
    }

    public void push(PsValue value) {
        operandStack.push(value);
    }

    public PsValue pop() {
        if (operandStack.isEmpty()) {
            throw new PostScriptVmException("stackunderflow");
        }
        return operandStack.pop();
    }

    public PsValue peek() {
        if (operandStack.isEmpty()) {
            throw new PostScriptVmException("stackunderflow");
        }
        return operandStack.peek();
    }

    public int operandCount() {
        return operandStack.size();
    }

    public VmGraphicsState getGraphicsState() {
        return graphicsState;
    }

    private void applyGraphicsState(VmGraphicsState next) {
        int depthBefore = graphicsState.getClipDepth();
        graphicsState = next;
        documentRecorder.popClipsToDepth(next.getClipDepth(), depthBefore);
    }

    public Deque<PsValue> getOperandStack() {
        return operandStack;
    }

    /**
     * Resets operand/graphics stacks and collapses the dict stack to initial global+userdict
     * before running the shorthand preamble and page body.
     */
    public void resetForPageBody() {
        operandStack.clear();
        graphicsStack.clear();
        graphicsState = new VmGraphicsState();
        while (dictStack.size() > 2) {
            dictStack.pop();
        }
    }

    public EpsDocumentBuilder getDocumentBuilder() {
        return documentBuilder;
    }

    public EpsDocumentRecorder getDocumentRecorder() {
        return documentRecorder;
    }

    public EpsDocument getDocument() {
        return documentBuilder.build();
    }

    private void registerBuiltinOperators() {
        register("pop", vm -> vm.pop());
        register("dup", vm -> {
            PsValue top = vm.peek();
            vm.push(copyValue(top));
        });
        register("exch", vm -> {
            PsValue a = vm.pop();
            PsValue b = vm.pop();
            vm.push(a);
            vm.push(b);
        });
        register("copy", vm -> {
            int n = popAsInt(vm);
            if (n < 0) {
                throw new PostScriptVmException("rangecheck");
            }
            PsValue[] copy = new PsValue[n];
            for (int i = n - 1; i >= 0; i--) {
                copy[i] = vm.pop();
            }
            for (PsValue value : copy) {
                vm.push(copyValue(value));
            }
            for (PsValue value : copy) {
                vm.push(value);
            }
        });
        register("index", vm -> {
            int indexPos = popAsInt(vm);
            if (indexPos < 0) {
                throw new PostScriptVmException("rangecheck");
            }
            PsValue[] items = new PsValue[indexPos + 1];
            for (int i = 0; i <= indexPos; i++) {
                items[i] = vm.pop();
            }
            for (int i = indexPos; i >= 0; i--) {
                vm.push(items[i]);
            }
            vm.push(items[indexPos]);
        });
        register("roll", vm -> {
            int j = popAsInt(vm);
            int n = popAsInt(vm);
            if (n == 0) {
                return;
            }
            if (n < 0) {
                throw new PostScriptVmException("rangecheck");
            }
            PsValue[] topFirst = new PsValue[n];
            for (int i = 0; i < n; i++) {
                topFirst[i] = vm.pop();
            }
            int rolls = ((j % n) + n) % n;
            if (rolls != 0) {
                PsValue[] bottomTop = new PsValue[n];
                for (int i = 0; i < n; i++) {
                    bottomTop[i] = topFirst[n - 1 - i];
                }
                PsValue[] rotated = new PsValue[n];
                System.arraycopy(bottomTop, bottomTop.length - rolls, rotated, 0, rolls);
                System.arraycopy(bottomTop, 0, rotated, rolls, n - rolls);
                for (int i = 0; i < n; i++) {
                    topFirst[i] = rotated[n - 1 - i];
                }
            }
            for (int i = n - 1; i >= 0; i--) {
                vm.push(topFirst[i]);
            }
        });
        register("clear", vm -> vm.operandStack.clear());
        register("count", vm -> vm.push(new PsValue.IntegerValue(vm.operandCount())));
        register("mark", vm -> vm.push(PsValue.MarkValue.INSTANCE));
        register("cleartomark", vm -> {
            while (!vm.operandStack.isEmpty() && !(vm.peek() instanceof PsValue.MarkValue)) {
                vm.pop();
            }
            if (vm.operandStack.isEmpty()) {
                throw new PostScriptVmException("stackunderflow");
            }
            vm.pop();
        });
        register("counttomark", vm -> {
            int count = 0;
            for (PsValue value : vm.operandStack) {
                if (value instanceof PsValue.MarkValue) {
                    break;
                }
                count++;
            }
            vm.push(new PsValue.IntegerValue(count));
        });

        register("add", vm -> pushNumber(vm, popNumber(vm) + popNumber(vm)));
        register("sub", vm -> {
            double subtrahend = popNumber(vm);
            double minuend = popNumber(vm);
            pushNumber(vm, minuend - subtrahend);
        });
        register("mul", vm -> pushNumber(vm, popNumber(vm) * popNumber(vm)));
        register("div", vm -> {
            double b = popNumber(vm);
            double a = popNumber(vm);
            pushNumber(vm, a / b);
        });
        register("idiv", vm -> {
            long b = popAsInt(vm);
            long a = popAsInt(vm);
            vm.push(new PsValue.IntegerValue(a / b));
        });
        register("mod", vm -> {
            long b = popAsInt(vm);
            long a = popAsInt(vm);
            vm.push(new PsValue.IntegerValue(a % b));
        });
        register("neg", vm -> pushNumber(vm, -popNumber(vm)));
        register("abs", vm -> pushNumber(vm, Math.abs(popNumber(vm))));
        register("sqrt", vm -> pushNumber(vm, Math.sqrt(popNumber(vm))));
        register("sin", vm -> pushNumber(vm, Math.sin(Math.toRadians(popNumber(vm)))));
        register("cos", vm -> pushNumber(vm, Math.cos(Math.toRadians(popNumber(vm)))));
        register("atan", vm -> {
            double x = popNumber(vm);
            double y = popNumber(vm);
            pushNumber(vm, Math.toDegrees(Math.atan2(y, x)));
        });

        register("eq", vm -> {
            PsValue b = vm.pop();
            PsValue a = vm.pop();
            vm.push(bool(valuesEqual(a, b)));
        });
        register("ne", vm -> {
            PsValue b = vm.pop();
            PsValue a = vm.pop();
            vm.push(bool(!valuesEqual(a, b)));
        });
        register("lt", vm -> vm.push(bool(popNumber(vm) > popNumber(vm))));
        register("le", vm -> vm.push(bool(popNumber(vm) >= popNumber(vm))));
        register("gt", vm -> vm.push(bool(popNumber(vm) < popNumber(vm))));
        register("ge", vm -> vm.push(bool(popNumber(vm) <= popNumber(vm))));

        register("and", vm -> vm.push(bool(popAsBoolean(vm) && popAsBoolean(vm))));
        register("or", vm -> vm.push(bool(popAsBoolean(vm) || popAsBoolean(vm))));
        register("not", vm -> vm.push(bool(!popAsBoolean(vm))));

        register("dict", vm -> {
            int capacity = popAsInt(vm);
            vm.push(new PsValue.RuntimeDictionaryValue(new PsDictionary(capacity)));
        });
        register("begin", vm -> {
            PsValue value = vm.pop();
            PsDictionary dict = asRuntimeDictionary(value);
            dictStack.push(dict);
        });
        register("end", vm -> {
            if (dictStack.size() <= 2) {
                throw new PostScriptVmException("dictstackunderflow");
            }
            dictStack.pop();
        });
        register("def", vm -> {
            PsValue value = vm.pop();
            PsValue key = vm.pop();
            String name = asName(key);
            dictStack.peek().put(name, value);
        });
        register("load", vm -> {
            String name = asName(vm.pop());
            PsValue value = lookupValue(name);
            if (value == null) {
                throw new PostScriptVmException("undefined: " + name);
            }
            vm.push(copyValue(value));
        });
        register("store", vm -> {
            String name = asName(vm.pop());
            PsValue value = vm.pop();
            PsDictionary dict = findDefiningDict(name);
            if (dict == null) {
                throw new PostScriptVmException("undefined: " + name);
            }
            dict.put(name, value);
        });
        register("where", vm -> {
            String name = asName(vm.pop());
            PsDictionary dict = findDefiningDict(name);
            if (dict == null) {
                vm.push(new PsValue.BooleanValue(false));
            } else {
                vm.push(new PsValue.RuntimeDictionaryValue(dict));
                vm.push(new PsValue.BooleanValue(true));
            }
        });
        register("known", vm -> {
            String name = asName(vm.pop());
            PsDictionary dict = asRuntimeDictionary(vm.pop());
            vm.push(new PsValue.BooleanValue(dict.containsKey(name)));
        });
        register("currentdict", vm -> vm.push(new PsValue.RuntimeDictionaryValue(dictStack.peek())));
        register("bind", vm -> {
            PsValue proc = vm.pop();
            vm.push(proc);
        });

        register("exec", vm -> {
            PsValue proc = vm.pop();
            executeProcedure(vm, proc);
        });
        register("if", vm -> {
            PsValue proc = vm.pop();
            boolean condition = popAsBoolean(vm);
            if (condition) {
                executeProcedure(vm, proc);
            }
        });
        register("ifelse", vm -> {
            PsValue falseProc = vm.pop();
            PsValue trueProc = vm.pop();
            boolean condition = popAsBoolean(vm);
            executeProcedure(vm, condition ? trueProc : falseProc);
        });
        register("repeat", vm -> {
            int count = popAsInt(vm);
            PsValue proc = vm.pop();
            for (int i = 0; i < count; i++) {
                executeProcedure(vm, proc);
            }
        });
        register("for", vm -> {
            PsValue proc = vm.pop();
            long limit = popAsInt(vm);
            long increment = popAsInt(vm);
            long initial = popAsInt(vm);
            if (increment == 0) {
                throw new PostScriptVmException("rangecheck");
            }
            if (increment > 0) {
                for (long i = initial; i <= limit; i += increment) {
                    vm.push(new PsValue.IntegerValue(i));
                    executeProcedure(vm, proc);
                }
            } else {
                for (long i = initial; i >= limit; i += increment) {
                    vm.push(new PsValue.IntegerValue(i));
                    executeProcedure(vm, proc);
                }
            }
        });
        register("loop", vm -> {
            PsValue proc = vm.pop();
            vm.loopDepth++;
            try {
                while (!vm.exitRequested) {
                    executeProcedure(vm, proc);
                }
            } finally {
                vm.exitRequested = false;
                vm.loopDepth--;
            }
        });
        register("exit", vm -> {
            if (vm.loopDepth <= 0) {
                throw new PostScriptVmException("invalidexit");
            }
            vm.exitRequested = true;
        });
        register("stopped", vm -> {
            PsValue proc = vm.pop();
            boolean error = false;
            try {
                executeProcedure(vm, proc);
            } catch (PostScriptVmException e) {
                error = true;
            }
            vm.push(new PsValue.BooleanValue(error));
        });

        registerArrayAndFontOperators();

        registerGraphicsOperators();

        registerAgmImageOperators();
        register("showpage", vm -> { });
        register("save", vm -> vm.push(new PsValue.SaveStateValue(vm.graphicsState.copy())));
        register("restore", vm -> {
            PsValue token = vm.pop();
            if (token instanceof PsValue.SaveStateValue) {
                vm.applyGraphicsState(((PsValue.SaveStateValue) token).getGraphicsState());
            } else if (!graphicsStack.isEmpty()) {
                vm.pop();
                vm.applyGraphicsState(graphicsStack.pop());
            }
        });
        register("gsave", vm -> graphicsStack.push(vm.graphicsState.copy()));
        register("grestore", vm -> {
            if (graphicsStack.isEmpty()) {
                throw new PostScriptVmException("graphicsstackunderflow");
            }
            vm.applyGraphicsState(graphicsStack.pop());
        });

        for (Map.Entry<String, PostScriptOperator> entry : systemOperators.entrySet()) {
            systemDict.put(entry.getKey(), PsValue.NameValue.executable(entry.getKey()));
        }
    }

    private void registerArrayAndFontOperators() {
        register("length", vm -> {
            PsValue value = vm.pop();
            if (value instanceof PsValue.StringValue) {
                vm.push(new PsValue.IntegerValue(((PsValue.StringValue) value).getValue().length()));
            } else if (value instanceof PsValue.ArrayValue) {
                vm.push(new PsValue.IntegerValue(((PsValue.ArrayValue) value).getElements().size()));
            } else if (value instanceof PsValue.RuntimeDictionaryValue) {
                vm.push(new PsValue.IntegerValue(
                        ((PsValue.RuntimeDictionaryValue) value).getDictionary().snapshot().size()));
            } else {
                throw new PostScriptVmException("typecheck");
            }
        });
        register("get", vm -> {
            PsValue container = vm.pop();
            PsValue key = vm.pop();
            if (container instanceof PsValue.ArrayValue) {
                int index = indexFromValue(key);
                PsValue.ArrayValue array = (PsValue.ArrayValue) container;
                if (index < 0 || index >= array.getElements().size()) {
                    throw new PostScriptVmException("rangecheck");
                }
                vm.push(copyValue(array.getElement(index)));
                return;
            }
            if (container instanceof PsValue.RuntimeDictionaryValue) {
                String name = asName(key);
                PsValue result = ((PsValue.RuntimeDictionaryValue) container).getDictionary().get(name);
                if (result == null) {
                    throw new PostScriptVmException("undefined");
                }
                vm.push(copyValue(result));
                return;
            }
            throw new PostScriptVmException("typecheck");
        });
        register("put", vm -> {
            PsValue container = vm.pop();
            PsValue key = vm.pop();
            PsValue value = vm.pop();
            if (container instanceof PsValue.ArrayValue) {
                int index = indexFromValue(key);
                PsValue.ArrayValue array = (PsValue.ArrayValue) container;
                if (index < 0 || index >= array.getElements().size()) {
                    throw new PostScriptVmException("rangecheck");
                }
                array.setElement(index, value);
                return;
            }
            if (container instanceof PsValue.RuntimeDictionaryValue) {
                String name = asName(key);
                ((PsValue.RuntimeDictionaryValue) container).getDictionary().put(name, value);
                return;
            }
            throw new PostScriptVmException("typecheck");
        });
        register("aload", vm -> {
            PsValue.ArrayValue array = asArray(vm.pop());
            for (PsValue element : array.getElements()) {
                vm.push(copyValue(element));
            }
        });
        register("astore", vm -> {
            PsValue.ArrayValue array = asArray(vm.pop());
            for (int i = array.getElements().size() - 1; i >= 0; i--) {
                array.setElement(i, vm.pop());
            }
        });
        register("type", vm -> vm.push(PsValue.NameValue.literal(typeName(vm.pop()))));
        register("forall", vm -> {
            PsValue proc = vm.pop();
            PsValue container = vm.pop();
            if (container instanceof PsValue.ArrayValue) {
                for (PsValue element : ((PsValue.ArrayValue) container).getElements()) {
                    vm.push(copyValue(element));
                    executeProcedure(vm, proc);
                }
                return;
            }
            if (container instanceof PsValue.RuntimeDictionaryValue) {
                for (Map.Entry<String, PsValue> entry :
                        ((PsValue.RuntimeDictionaryValue) container).getDictionary().snapshot().entrySet()) {
                    vm.push(copyValue(entry.getValue()));
                    vm.push(PsValue.NameValue.literal(entry.getKey()));
                    executeProcedure(vm, proc);
                }
                return;
            }
            if (container instanceof PsValue.StringValue) {
                for (char ch : ((PsValue.StringValue) container).getValue().toCharArray()) {
                    vm.push(new PsValue.IntegerValue(ch));
                    executeProcedure(vm, proc);
                }
                return;
            }
            throw new PostScriptVmException("typecheck");
        });

        register("findfont", vm -> {
            String fontName = asName(vm.pop());
            vm.push(createFontDictionary(fontName, 1.0));
        });
        register("scalefont", vm -> {
            double size = popNumber(vm);
            PsValue font = vm.pop();
            vm.push(scaleFontDictionary(font, size));
        });
        register("setfont", vm -> setCurrentFont(vm, vm.pop()));
        register("selectfont", vm -> {
            double size = popNumber(vm);
            popMatrix(vm);
            setCurrentFont(vm, vm.pop());
            vm.getGraphicsState().setFontSize(size);
        });
        register("show", vm -> showText(vm));
        register("sh", vm -> showText(vm));
        register("xsh", vm -> vm.pop());
        register("xshow", vm -> {
            vm.pop();
            showText(vm);
        });
        register("setcachedevice", vm -> {
            popNumber(vm);
            popNumber(vm);
            popNumber(vm);
            popNumber(vm);
            popNumber(vm);
            popNumber(vm);
        });

        register("rect", vm -> {
            double height = popNumber(vm);
            double width = popNumber(vm);
            double y = popNumber(vm);
            double x = popNumber(vm);
            vm.graphicsState.appendRectangle(x, y, width, height);
        });
    }

    private void registerAgmImageOperators() {
        register("snap_to_device", vm -> { });
        register("sepimg", vm -> { });
        register("img", vm -> { });
        register("idximg", vm -> { });
    }

    private void registerGraphicsOperators() {
        register("newpath", vm -> vm.graphicsState.clearPath());
        register("n", vm -> vm.graphicsState.clearPath());
        register("moveto", vm -> {
            double y = popNumber(vm);
            double x = popNumber(vm);
            vm.graphicsState.moveTo(x, y);
        });
        register("rmoveto", vm -> {
            double dy = popNumber(vm);
            double dx = popNumber(vm);
            vm.graphicsState.moveTo(
                    vm.graphicsState.getCurrentX() + dx,
                    vm.graphicsState.getCurrentY() + dy);
        });
        register("lineto", vm -> linetoOperands(vm));
        register("l", vm -> linetoOperands(vm));
        register("li", vm -> linetoOperands(vm));
        register("rlineto", vm -> {
            double dy = popNumber(vm);
            double dx = popNumber(vm);
            double[] pt = new double[2];
            vm.graphicsState.currentPoint(pt);
            vm.graphicsState.lineTo(pt[0] + dx, pt[1] + dy);
        });
        register("curveto", vm -> curveToOperands(vm));
        register("c", vm -> curveToOperands(vm));
        register("cu", vm -> curveToOperands(vm));
        register("v", vm -> curveToVOperands(vm));
        register("rcurveto", vm -> {
            double dy3 = popNumber(vm);
            double dx3 = popNumber(vm);
            double dy2 = popNumber(vm);
            double dx2 = popNumber(vm);
            double dy1 = popNumber(vm);
            double dx1 = popNumber(vm);
            double x0 = vm.graphicsState.getCurrentX();
            double y0 = vm.graphicsState.getCurrentY();
            vm.graphicsState.curveTo(
                    x0 + dx1, y0 + dy1,
                    x0 + dx2, y0 + dy2,
                    x0 + dx3, y0 + dy3);
        });
        register("closepath", vm -> vm.graphicsState.closePath());
        register("cl", vm -> vm.graphicsState.closePath());
        register("h", vm -> vm.graphicsState.closePath());
        register("currentpoint", vm -> {
            double[] pt = new double[2];
            vm.graphicsState.currentPoint(pt);
            pushNumber(vm, pt[1]);
            pushNumber(vm, pt[0]);
        });
        register("arc", vm -> {
            double ang2 = popNumber(vm);
            double ang1 = popNumber(vm);
            double r = popNumber(vm);
            double y = popNumber(vm);
            double x = popNumber(vm);
            vm.graphicsState.appendArc(x, y, r, ang1, ang2, false);
        });
        register("arcn", vm -> {
            double ang2 = popNumber(vm);
            double ang1 = popNumber(vm);
            double r = popNumber(vm);
            double y = popNumber(vm);
            double x = popNumber(vm);
            vm.graphicsState.appendArc(x, y, r, ang1, ang2, true);
        });
        register("arct", vm -> {
            double ang2 = popNumber(vm);
            double ang1 = popNumber(vm);
            double r = popNumber(vm);
            double y = popNumber(vm);
            double x = popNumber(vm);
            vm.graphicsState.appendArc(x, y, r, ang1, ang2, false);
        });
        register("stroke", vm -> vm.documentRecorder.recordStroke(vm.graphicsState));
        register("st", vm -> vm.documentRecorder.recordStroke(vm.graphicsState));
        register("S", vm -> vm.documentRecorder.recordStroke(vm.graphicsState));
        register("fill", vm -> vm.documentRecorder.recordFill(vm.graphicsState, WindingRule.NON_ZERO));
        register("f", vm -> vm.documentRecorder.recordFill(vm.graphicsState, WindingRule.NON_ZERO));
        register("fi", vm -> vm.documentRecorder.recordFill(vm.graphicsState, WindingRule.NON_ZERO));
        register("eofill", vm -> vm.documentRecorder.recordFill(vm.graphicsState, WindingRule.EVEN_ODD));
        register("u", vm -> { });
        register("U", vm -> { });
        register("*u", vm -> vm.graphicsState.beginCompoundPath());
        register("*U", vm -> vm.documentRecorder.finishCompoundPath(vm.graphicsState));
        register("D", vm -> popNumber(vm));
        register("clip", vm -> vm.documentRecorder.recordClip(vm.graphicsState, WindingRule.NON_ZERO));
        register("eoclip", vm -> vm.documentRecorder.recordClip(vm.graphicsState, WindingRule.EVEN_ODD));
        register("rectfill", vm -> {
            double h = popNumber(vm);
            double w = popNumber(vm);
            double y = popNumber(vm);
            double x = popNumber(vm);
            vm.graphicsState.appendRectangle(x, y, w, h);
            vm.documentRecorder.recordFill(vm.graphicsState, WindingRule.NON_ZERO);
        });
        register("rectstroke", vm -> {
            double h = popNumber(vm);
            double w = popNumber(vm);
            double y = popNumber(vm);
            double x = popNumber(vm);
            vm.graphicsState.appendRectangle(x, y, w, h);
            vm.documentRecorder.recordStroke(vm.graphicsState);
        });
        register("rectclip", vm -> {
            double h = popNumber(vm);
            double w = popNumber(vm);
            double y = popNumber(vm);
            double x = popNumber(vm);
            vm.graphicsState.appendRectangle(x, y, w, h);
            vm.documentRecorder.recordClip(vm.graphicsState, WindingRule.NON_ZERO);
        });

        register("setlinewidth", vm -> vm.graphicsState.setLineWidth(popNumber(vm)));
        register("setlinecap", vm -> {
            int cap = popAsInt(vm);
            StrokeStyle style = vm.graphicsState.getStrokeStyle();
            vm.graphicsState.setStrokeStyle(new StrokeStyle(
                    style.getLineWidth(), cap, style.getLineJoin(),
                    style.getMiterLimit(), style.getDashPattern(), style.getDashOffset()));
        });
        register("setlinejoin", vm -> {
            int join = popAsInt(vm);
            StrokeStyle style = vm.graphicsState.getStrokeStyle();
            vm.graphicsState.setStrokeStyle(new StrokeStyle(
                    style.getLineWidth(), style.getLineCap(), join,
                    style.getMiterLimit(), style.getDashPattern(), style.getDashOffset()));
        });
        register("setmiterlimit", vm -> {
            double limit = popNumber(vm);
            StrokeStyle style = vm.graphicsState.getStrokeStyle();
            vm.graphicsState.setStrokeStyle(new StrokeStyle(
                    style.getLineWidth(), style.getLineCap(), style.getLineJoin(),
                    limit, style.getDashPattern(), style.getDashOffset()));
        });
        register("setdash", vm -> {
            double offset = popNumber(vm);
            PsValue.ArrayValue array = asArray(vm.pop());
            StrokeStyle style = vm.graphicsState.getStrokeStyle();
            vm.graphicsState.setStrokeStyle(new StrokeStyle(
                    style.getLineWidth(), style.getLineCap(), style.getLineJoin(),
                    style.getMiterLimit(), toDoubleArray(array), offset));
        });
        register("setflat", vm -> popNumber(vm));
        register("setgray", vm -> {
            double gray = popNumber(vm);
            PaintStyle paint = PaintStyle.gray(gray);
            vm.graphicsState.setFillColor(paint);
            vm.graphicsState.setStrokeColor(paint);
        });
        register("setrgbcolor", vm -> {
            double b = popNumber(vm);
            double g = popNumber(vm);
            double r = popNumber(vm);
            PaintStyle paint = PaintStyle.rgb(r, g, b);
            vm.graphicsState.setFillColor(paint);
            vm.graphicsState.setStrokeColor(paint);
        });
        register("setcmykcolor", vm -> setCmykOperands(vm));
        register("cmyk", vm -> setCmykOperands(vm));
        register("setcolorspace", vm -> vm.pop());

        register("matrix", vm -> vm.push(matrixToArray(Matrix.identity())));
        register("initmatrix", vm -> vm.graphicsState.setCtm(Matrix.identity()));
        register("currentmatrix", vm -> vm.push(matrixToArray(vm.graphicsState.getCtm())));
        register("setmatrix", vm -> vm.graphicsState.setCtm(popMatrix(vm)));
        register("concat", vm -> {
            Matrix m = popMatrix(vm);
            vm.graphicsState.setCtm(vm.graphicsState.getCtm().postConcat(m));
        });
        register("cm", vm -> {
            double f = popNumber(vm);
            double e = popNumber(vm);
            double d = popNumber(vm);
            double c = popNumber(vm);
            double b = popNumber(vm);
            double a = popNumber(vm);
            vm.graphicsState.setCtm(vm.graphicsState.getCtm().postConcat(new Matrix(a, b, c, d, e, f)));
        });
        register("concatmatrix", vm -> {
            Matrix result = popMatrix(vm);
            Matrix m2 = popMatrix(vm);
            Matrix m1 = popMatrix(vm);
            vm.push(matrixToArray(m1.postConcat(m2).postConcat(result)));
        });
        register("translate", vm -> translateOperands(vm));
        register("tr", vm -> translateOperands(vm));
        register("scale", vm -> scaleOperands(vm));
        register("sc", vm -> scaleOperands(vm));
        register("rotate", vm -> {
            double angle = Math.toRadians(popNumber(vm));
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            vm.graphicsState.setCtm(vm.graphicsState.getCtm().postConcat(new Matrix(cos, sin, -sin, cos, 0, 0)));
        });
        register("transform", vm -> {
            double y = popNumber(vm);
            double x = popNumber(vm);
            Matrix ctm = vm.graphicsState.getCtm();
            pushNumber(vm, ctm.transformY(x, y));
            pushNumber(vm, ctm.transformX(x, y));
        });
        register("itransform", vm -> {
            double y = popNumber(vm);
            double x = popNumber(vm);
            Matrix inverse = vm.graphicsState.getCtm().invert();
            pushNumber(vm, inverse.transformY(x, y));
            pushNumber(vm, inverse.transformX(x, y));
        });
        register("dtransform", vm -> {
            double y = popNumber(vm);
            double x = popNumber(vm);
            Matrix ctm = vm.graphicsState.getCtm();
            pushNumber(vm, ctm.getB() * x + ctm.getD() * y);
            pushNumber(vm, ctm.getA() * x + ctm.getC() * y);
        });
        register("idtransform", vm -> {
            double y = popNumber(vm);
            double x = popNumber(vm);
            Matrix inverse = vm.graphicsState.getCtm().invert();
            pushNumber(vm, inverse.getB() * x + inverse.getD() * y);
            pushNumber(vm, inverse.getA() * x + inverse.getC() * y);
        });
        register("invertmatrix", vm -> {
            Matrix target = popMatrix(vm);
            Matrix source = popMatrix(vm);
            vm.push(matrixToArray(source.invert().postConcat(target)));
        });
    }

    private void register(String name, PostScriptOperator operator) {
        systemOperators.put(name, operator);
    }

    private PostScriptOperator lookupOperator(String name) {
        return systemOperators.get(name);
    }

    private PsValue lookupValue(String name) {
        for (int i = dictStack.size() - 1; i >= 0; i--) {
            PsDictionary dict = stackAt(i);
            if (dict.containsKey(name)) {
                return dict.get(name);
            }
        }
        return systemDict.get(name);
    }

    private PsDictionary findDefiningDict(String name) {
        for (int i = dictStack.size() - 1; i >= 0; i--) {
            PsDictionary dict = stackAt(i);
            if (dict.containsKey(name)) {
                return dict;
            }
        }
        return systemDict.containsKey(name) ? systemDict : null;
    }

    private PsDictionary stackAt(int index) {
        int i = 0;
        for (PsDictionary dict : dictStack) {
            if (i == index) {
                return dict;
            }
            i++;
        }
        throw new PostScriptVmException("dictstackunderflow");
    }

    private static void executeProcedure(PostScriptVm vm, PsValue proc) {
        if (!(proc instanceof PsValue.ProcedureValue)) {
            throw new PostScriptVmException("typecheck");
        }
        for (PsValue step : ((PsValue.ProcedureValue) proc).getBody()) {
            vm.execute(step);
        }
    }

    private static PsValue copyValue(PsValue value) {
        return value;
    }

    private static PsDictionary asRuntimeDictionary(PsValue value) {
        if (!(value instanceof PsValue.RuntimeDictionaryValue)) {
            throw new PostScriptVmException("typecheck");
        }
        return ((PsValue.RuntimeDictionaryValue) value).getDictionary();
    }

    private static String asName(PsValue value) {
        if (value instanceof PsValue.NameValue) {
            return ((PsValue.NameValue) value).getName();
        }
        throw new PostScriptVmException("typecheck");
    }

    private static double popNumber(PostScriptVm vm) {
        PsValue value = vm.pop();
        if (value instanceof PsValue.IntegerValue) {
            return ((PsValue.IntegerValue) value).getValue();
        }
        if (value instanceof PsValue.RealValue) {
            return ((PsValue.RealValue) value).getValue();
        }
        throw new PostScriptVmException("typecheck");
    }

    private static int popAsInt(PostScriptVm vm) {
        PsValue value = vm.pop();
        if (value instanceof PsValue.IntegerValue) {
            return Math.toIntExact(((PsValue.IntegerValue) value).getValue());
        }
        if (value instanceof PsValue.RealValue) {
            return (int) ((PsValue.RealValue) value).getValue();
        }
        throw new PostScriptVmException("typecheck");
    }

    private static boolean popAsBoolean(PostScriptVm vm) {
        PsValue value = vm.pop();
        if (!(value instanceof PsValue.BooleanValue)) {
            throw new PostScriptVmException("typecheck");
        }
        return ((PsValue.BooleanValue) value).getValue();
    }

    private static void pushNumber(PostScriptVm vm, double value) {
        if (value == Math.rint(value) && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
            vm.push(new PsValue.IntegerValue((long) value));
        } else {
            vm.push(new PsValue.RealValue(value));
        }
    }

    private static void movetoOperands(PostScriptVm vm) {
        double y = popNumber(vm);
        double x = popNumber(vm);
        vm.graphicsState.moveTo(x, y);
    }

    private static void linetoOperands(PostScriptVm vm) {
        double y = popNumber(vm);
        double x = popNumber(vm);
        vm.graphicsState.lineTo(x, y);
    }

    private static void curveToOperands(PostScriptVm vm) {
        if (vm.operandCount() >= 6) {
            double y3 = popNumber(vm);
            double x3 = popNumber(vm);
            double y2 = popNumber(vm);
            double x2 = popNumber(vm);
            double y1 = popNumber(vm);
            double x1 = popNumber(vm);
            vm.graphicsState.curveTo(x1, y1, x2, y2, x3, y3);
            return;
        }
        if (vm.operandCount() >= 4) {
            curveToVOperands(vm);
            return;
        }
        throw new PostScriptVmException("typecheck");
    }

    /** {@code v}: first control point is the current point. */
    private static void curveToVOperands(PostScriptVm vm) {
        double y3 = popNumber(vm);
        double x3 = popNumber(vm);
        double y2 = popNumber(vm);
        double x2 = popNumber(vm);
        double x1 = vm.graphicsState.getCurrentX();
        double y1 = vm.graphicsState.getCurrentY();
        vm.graphicsState.curveTo(x1, y1, x2, y2, x3, y3);
    }

    private static void translateOperands(PostScriptVm vm) {
        double ty = popNumber(vm);
        double tx = popNumber(vm);
        vm.graphicsState.setCtm(vm.graphicsState.getCtm().postConcat(new Matrix(1, 0, 0, 1, tx, ty)));
    }

    private static void scaleOperands(PostScriptVm vm) {
        double sy = popNumber(vm);
        double sx = popNumber(vm);
        vm.graphicsState.setCtm(vm.graphicsState.getCtm().postConcat(new Matrix(sx, 0, 0, sy, 0, 0)));
    }

    private static void setCmykOperands(PostScriptVm vm) {
        double k = popNumber(vm);
        double y = popNumber(vm);
        double m = popNumber(vm);
        double c = popNumber(vm);
        PaintStyle paint = PaintStyle.cmyk(c, m, y, k);
        vm.graphicsState.setFillColor(paint);
        vm.graphicsState.setStrokeColor(paint);
    }

    private static PsValue.BooleanValue bool(boolean value) {
        return new PsValue.BooleanValue(value);
    }

    private static PsValue.ArrayValue matrixToArray(Matrix matrix) {
        List<PsValue> elements = new ArrayList<>(6);
        for (double value : matrix.toArray()) {
            pushNumberElement(elements, value);
        }
        return new PsValue.ArrayValue(elements);
    }

    private static void pushNumberElement(List<PsValue> elements, double value) {
        if (value == Math.rint(value) && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
            elements.add(new PsValue.IntegerValue((long) value));
        } else {
            elements.add(new PsValue.RealValue(value));
        }
    }

    private static Matrix popMatrix(PostScriptVm vm) {
        return matrixFromArray(asArray(vm.pop()));
    }

    private static Matrix matrixFromArray(PsValue.ArrayValue array) {
        List<PsValue> elements = array.getElements();
        if (elements.size() != 6) {
            throw new PostScriptVmException("rangecheck");
        }
        double[] values = new double[6];
        for (int i = 0; i < 6; i++) {
            values[i] = numberFromValue(elements.get(i));
        }
        return new Matrix(values[0], values[1], values[2], values[3], values[4], values[5]);
    }

    private static PsValue.ArrayValue asArray(PsValue value) {
        if (!(value instanceof PsValue.ArrayValue)) {
            throw new PostScriptVmException("typecheck");
        }
        return (PsValue.ArrayValue) value;
    }

    private static double[] toDoubleArray(PsValue.ArrayValue array) {
        double[] values = new double[array.getElements().size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = numberFromValue(array.getElements().get(i));
        }
        return values;
    }

    private static double numberFromValue(PsValue value) {
        if (value instanceof PsValue.IntegerValue) {
            return ((PsValue.IntegerValue) value).getValue();
        }
        if (value instanceof PsValue.RealValue) {
            return ((PsValue.RealValue) value).getValue();
        }
        throw new PostScriptVmException("typecheck");
    }

    private static int indexFromValue(PsValue value) {
        if (value instanceof PsValue.IntegerValue) {
            return Math.toIntExact(((PsValue.IntegerValue) value).getValue());
        }
        if (value instanceof PsValue.RealValue) {
            return (int) ((PsValue.RealValue) value).getValue();
        }
        throw new PostScriptVmException("typecheck");
    }

    private static boolean valuesEqual(PsValue a, PsValue b) {
        if (a instanceof PsValue.IntegerValue && b instanceof PsValue.IntegerValue) {
            return ((PsValue.IntegerValue) a).getValue() == ((PsValue.IntegerValue) b).getValue();
        }
        if (a instanceof PsValue.RealValue || b instanceof PsValue.RealValue
                || a instanceof PsValue.IntegerValue || b instanceof PsValue.IntegerValue) {
            return numberFromValue(a) == numberFromValue(b);
        }
        if (a instanceof PsValue.BooleanValue && b instanceof PsValue.BooleanValue) {
            return ((PsValue.BooleanValue) a).getValue() == ((PsValue.BooleanValue) b).getValue();
        }
        if (a instanceof PsValue.NameValue && b instanceof PsValue.NameValue) {
            return ((PsValue.NameValue) a).getName().equals(((PsValue.NameValue) b).getName());
        }
        if (a instanceof PsValue.StringValue && b instanceof PsValue.StringValue) {
            return ((PsValue.StringValue) a).getValue().equals(((PsValue.StringValue) b).getValue());
        }
        return a.equals(b);
    }

    private static String typeName(PsValue value) {
        if (value instanceof PsValue.IntegerValue) {
            return "integertype";
        }
        if (value instanceof PsValue.RealValue) {
            return "realtype";
        }
        if (value instanceof PsValue.BooleanValue) {
            return "booleantype";
        }
        if (value instanceof PsValue.NameValue) {
            return "nametype";
        }
        if (value instanceof PsValue.StringValue || value instanceof PsValue.HexStringValue) {
            return "stringtype";
        }
        if (value instanceof PsValue.ArrayValue) {
            return "arraytype";
        }
        if (value instanceof PsValue.ProcedureValue) {
            return "arraytype";
        }
        if (value instanceof PsValue.RuntimeDictionaryValue || value instanceof PsValue.DictionaryValue) {
            return "dicttype";
        }
        if (value instanceof PsValue.SaveStateValue) {
            return "savetype";
        }
        if (value instanceof PsValue.MarkValue) {
            return "marktype";
        }
        return "nulltype";
    }

    private static PsValue.RuntimeDictionaryValue createFontDictionary(String fontName, double size) {
        PsDictionary dict = new PsDictionary(4);
        dict.put("FontName", PsValue.NameValue.literal(fontName));
        dict.put("FontSize", numberValue(size));
        return new PsValue.RuntimeDictionaryValue(dict);
    }

    private static PsValue scaleFontDictionary(PsValue font, double size) {
        if (!(font instanceof PsValue.RuntimeDictionaryValue)) {
            throw new PostScriptVmException("typecheck");
        }
        PsDictionary source = ((PsValue.RuntimeDictionaryValue) font).getDictionary();
        PsValue nameValue = source.get("FontName");
        String fontName = nameValue instanceof PsValue.NameValue
                ? ((PsValue.NameValue) nameValue).getName()
                : "Helvetica";
        return createFontDictionary(fontName, size);
    }

    private static void setCurrentFont(PostScriptVm vm, PsValue font) {
        if (!(font instanceof PsValue.RuntimeDictionaryValue)) {
            throw new PostScriptVmException("typecheck");
        }
        PsDictionary dict = ((PsValue.RuntimeDictionaryValue) font).getDictionary();
        PsValue nameValue = dict.get("FontName");
        if (nameValue instanceof PsValue.NameValue) {
            vm.graphicsState.setFontName(((PsValue.NameValue) nameValue).getName());
        }
        PsValue sizeValue = dict.get("FontSize");
        if (sizeValue != null) {
            vm.graphicsState.setFontSize(numberFromValue(sizeValue));
        }
    }

    private static void showText(PostScriptVm vm) {
        PsValue value = vm.pop();
        if (!(value instanceof PsValue.StringValue)) {
            throw new PostScriptVmException("typecheck");
        }
        String text = ((PsValue.StringValue) value).getValue();
        if (text.isEmpty()) {
            return;
        }
        VmGraphicsState state = vm.graphicsState;
        vm.documentRecorder.recordText(
                text,
                state.getCurrentX(),
                state.getCurrentY(),
                state.getFontName(),
                state.getFontSize(),
                state.getFillColor(),
                state.getCtm());
        state.clearIfOnlyMoveToPath();
    }

    private static PsValue numberValue(double value) {
        if (value == Math.rint(value) && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
            return new PsValue.IntegerValue((long) value);
        }
        return new PsValue.RealValue(value);
    }
}
