package com.convert2web.ps;

import com.convert2web.model.SourceSpan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Parsed PostScript object produced by {@link PostScriptParser}.
 */
public abstract class PsValue {
    private final SourceSpan sourceSpan;

    protected PsValue() {
        this(null);
    }

    protected PsValue(SourceSpan sourceSpan) {
        this.sourceSpan = sourceSpan;
    }

    public Optional<SourceSpan> sourceSpan() {
        return Optional.ofNullable(sourceSpan);
    }

    public abstract PsValueKind getKind();

    public static final class IntegerValue extends PsValue {
        private final long value;

        public IntegerValue(long value) {
            this(value, null);
        }

        public IntegerValue(long value, SourceSpan sourceSpan) {
            super(sourceSpan);
            this.value = value;
        }

        public long getValue() {
            return value;
        }

        @Override
        public PsValueKind getKind() {
            return PsValueKind.INTEGER;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof IntegerValue && value == ((IntegerValue) o).value;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(value);
        }
    }

    public static final class RealValue extends PsValue {
        private final double value;

        public RealValue(double value) {
            this(value, null);
        }

        public RealValue(double value, SourceSpan sourceSpan) {
            super(sourceSpan);
            this.value = value;
        }

        public double getValue() {
            return value;
        }

        @Override
        public PsValueKind getKind() {
            return PsValueKind.REAL;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RealValue && Double.compare(value, ((RealValue) o).value) == 0;
        }

        @Override
        public int hashCode() {
            return Double.hashCode(value);
        }
    }

    public static final class BooleanValue extends PsValue {
        private final boolean value;

        public BooleanValue(boolean value) {
            this(value, null);
        }

        public BooleanValue(boolean value, SourceSpan sourceSpan) {
            super(sourceSpan);
            this.value = value;
        }

        public boolean getValue() {
            return value;
        }

        @Override
        public PsValueKind getKind() {
            return PsValueKind.BOOLEAN;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BooleanValue && value == ((BooleanValue) o).value;
        }

        @Override
        public int hashCode() {
            return Boolean.hashCode(value);
        }
    }

    public static final class NameValue extends PsValue {
        private final String name;
        private final boolean literal;

        public NameValue(String name, boolean literal) {
            this(name, literal, null);
        }

        public NameValue(String name, boolean literal, SourceSpan sourceSpan) {
            super(sourceSpan);
            this.name = Objects.requireNonNull(name, "name");
            this.literal = literal;
        }

        public static NameValue literal(String name) {
            return new NameValue(name, true, null);
        }

        public static NameValue executable(String name) {
            return new NameValue(name, false, null);
        }

        public static NameValue literal(String name, SourceSpan sourceSpan) {
            return new NameValue(name, true, sourceSpan);
        }

        public static NameValue executable(String name, SourceSpan sourceSpan) {
            return new NameValue(name, false, sourceSpan);
        }

        public String getName() {
            return name;
        }

        public boolean isLiteral() {
            return literal;
        }

        @Override
        public PsValueKind getKind() {
            return PsValueKind.NAME;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof NameValue)) {
                return false;
            }
            NameValue that = (NameValue) o;
            return literal == that.literal && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, literal);
        }
    }

    public static final class StringValue extends PsValue {
        private final String value;

        public StringValue(String value) {
            this(value, null);
        }

        public StringValue(String value, SourceSpan sourceSpan) {
            super(sourceSpan);
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public PsValueKind getKind() {
            return PsValueKind.STRING;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof StringValue && value.equals(((StringValue) o).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    public static final class HexStringValue extends PsValue {
        private final String hexDigits;

        public HexStringValue(String hexDigits) {
            this(hexDigits, null);
        }

        public HexStringValue(String hexDigits, SourceSpan sourceSpan) {
            super(sourceSpan);
            this.hexDigits = hexDigits;
        }

        public String getHexDigits() {
            return hexDigits;
        }

        @Override
        public PsValueKind getKind() {
            return PsValueKind.HEX_STRING;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof HexStringValue && hexDigits.equals(((HexStringValue) o).hexDigits);
        }

        @Override
        public int hashCode() {
            return hexDigits.hashCode();
        }
    }

    public static final class ArrayValue extends PsValue {
        private final List<PsValue> elements;

        public ArrayValue(List<PsValue> elements) {
            this(elements, null);
        }

        public ArrayValue(List<PsValue> elements, SourceSpan sourceSpan) {
            super(sourceSpan);
            this.elements = new java.util.ArrayList<>(elements);
        }

        public List<PsValue> getElements() {
            return elements;
        }

        public void setElement(int index, PsValue value) {
            elements.set(index, value);
        }

        public PsValue getElement(int index) {
            return elements.get(index);
        }

        @Override
        public PsValueKind getKind() {
            return PsValueKind.ARRAY;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ArrayValue && elements.equals(((ArrayValue) o).elements);
        }

        @Override
        public int hashCode() {
            return elements.hashCode();
        }
    }

    public static final class ProcedureValue extends PsValue {
        private final List<PsValue> body;

        public ProcedureValue(List<PsValue> body) {
            this(body, null);
        }

        public ProcedureValue(List<PsValue> body, SourceSpan sourceSpan) {
            super(sourceSpan);
            this.body = List.copyOf(body);
        }

        public List<PsValue> getBody() {
            return body;
        }

        @Override
        public PsValueKind getKind() {
            return PsValueKind.PROCEDURE;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ProcedureValue && body.equals(((ProcedureValue) o).body);
        }

        @Override
        public int hashCode() {
            return body.hashCode();
        }
    }

    /**
     * Executable AGM paint step: image dictionary is already on the operand stack;
     * this value carries the {@code %%BeginBinary} operator and payload.
     */
    public static final class AgmBinaryInvokeValue extends PsValue {
        private final String operator;
        private final String payload;

        public AgmBinaryInvokeValue(String operator, String payload) {
            this(operator, payload, null);
        }

        public AgmBinaryInvokeValue(String operator, String payload, SourceSpan sourceSpan) {
            super(sourceSpan);
            this.operator = Objects.requireNonNull(operator, "operator");
            this.payload = payload == null ? "" : payload;
        }

        public String getOperator() {
            return operator;
        }

        public String getPayload() {
            return payload;
        }

        @Override
        public PsValueKind getKind() {
            return PsValueKind.AGM_BINARY_INVOKE;
        }
    }

    public static final class DictionaryValue extends PsValue {
        private final Map<String, PsValue> entries;

        public DictionaryValue(Map<String, PsValue> entries) {
            this(entries, null);
        }

        public DictionaryValue(Map<String, PsValue> entries, SourceSpan sourceSpan) {
            super(sourceSpan);
            this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }

        public Map<String, PsValue> getEntries() {
            return entries;
        }

        @Override
        public PsValueKind getKind() {
            return PsValueKind.DICTIONARY;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof DictionaryValue && entries.equals(((DictionaryValue) o).entries);
        }

        @Override
        public int hashCode() {
            return entries.hashCode();
        }
    }

    /**
     * Live dictionary on the VM operand stack (from {@code dict} / {@code begin}).
     */
    /** Sentinel pushed by {@code mark} for stack segment boundaries. */
    /** Operand-stack token pushed by {@code save}, consumed by {@code restore}. */
    public static final class SaveStateValue extends PsValue {
        private final VmGraphicsState graphicsState;

        public SaveStateValue(VmGraphicsState graphicsState) {
            this.graphicsState = graphicsState;
        }

        public VmGraphicsState getGraphicsState() {
            return graphicsState;
        }

        @Override
        public PsValueKind getKind() {
            return PsValueKind.SAVE;
        }
    }

    public static final class MarkValue extends PsValue {
        public static final MarkValue INSTANCE = new MarkValue();

        private MarkValue() {
        }

        @Override
        public PsValueKind getKind() {
            return PsValueKind.MARK;
        }
    }

    public static final class RuntimeDictionaryValue extends PsValue {
        private final PsDictionary dictionary;

        public RuntimeDictionaryValue(PsDictionary dictionary) {
            this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
        }

        public PsDictionary getDictionary() {
            return dictionary;
        }

        @Override
        public PsValueKind getKind() {
            return PsValueKind.RUNTIME_DICTIONARY;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RuntimeDictionaryValue
                    && dictionary.equals(((RuntimeDictionaryValue) o).dictionary);
        }

        @Override
        public int hashCode() {
            return dictionary.hashCode();
        }
    }
}
