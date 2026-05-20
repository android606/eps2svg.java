package com.convert2web.ps;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parsed PostScript object produced by {@link PostScriptParser}.
 */
public abstract class PsValue {
    public abstract PsValueKind getKind();

    public static final class IntegerValue extends PsValue {
        private final long value;

        public IntegerValue(long value) {
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
            this.name = Objects.requireNonNull(name, "name");
            this.literal = literal;
        }

        public static NameValue literal(String name) {
            return new NameValue(name, true);
        }

        public static NameValue executable(String name) {
            return new NameValue(name, false);
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
            this.elements = List.copyOf(elements);
        }

        public List<PsValue> getElements() {
            return elements;
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

    public static final class DictionaryValue extends PsValue {
        private final Map<String, PsValue> entries;

        public DictionaryValue(Map<String, PsValue> entries) {
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
}
