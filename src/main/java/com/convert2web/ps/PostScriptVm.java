package com.convert2web.ps;

import java.util.ArrayDeque;
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

    public void execute(PsValue value) {
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

    public Deque<PsValue> getOperandStack() {
        return operandStack;
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
            int n = popAsInt(vm);
            if (n < 0) {
                throw new PostScriptVmException("rangecheck");
            }
            PsValue[] items = new PsValue[n + 1];
            for (int i = n; i >= 0; i--) {
                items[i] = vm.pop();
            }
            for (int i = 0; i < n; i++) {
                vm.push(items[i]);
            }
            vm.push(items[n]);
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
            PsValue[] items = new PsValue[n];
            for (int i = n - 1; i >= 0; i--) {
                items[i] = vm.pop();
            }
            int shift = ((j % n) + n) % n;
            for (int i = 0; i < n; i++) {
                vm.push(items[(i + n - shift) % n]);
            }
        });
        register("clear", vm -> vm.operandStack.clear());
        register("count", vm -> vm.push(new PsValue.IntegerValue(vm.operandCount())));

        register("add", vm -> pushNumber(vm, popNumber(vm) + popNumber(vm)));
        register("sub", vm -> pushNumber(vm, popNumber(vm) - popNumber(vm)));
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

        register("eq", vm -> vm.push(bool(popNumber(vm) == popNumber(vm))));
        register("ne", vm -> vm.push(bool(popNumber(vm) != popNumber(vm))));
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
            PsValue value = vm.pop();
            String name = asName(vm.pop());
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

        register("gsave", vm -> graphicsStack.push(vm.graphicsState.copy()));
        register("grestore", vm -> {
            if (graphicsStack.isEmpty()) {
                throw new PostScriptVmException("graphicsstackunderflow");
            }
            vm.graphicsState = graphicsStack.pop();
        });

        for (Map.Entry<String, PostScriptOperator> entry : systemOperators.entrySet()) {
            systemDict.put(entry.getKey(), PsValue.NameValue.executable(entry.getKey()));
        }
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

    private static PsValue.BooleanValue bool(boolean value) {
        return new PsValue.BooleanValue(value);
    }
}
