package com.convert2web.ps;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable PostScript dictionary used by the VM.
 */
public final class PsDictionary {
    private final int maxCapacity;
    private final Map<String, PsValue> entries = new LinkedHashMap<>();

    public PsDictionary(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void put(String key, PsValue value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (maxCapacity > 0 && !entries.containsKey(key) && entries.size() >= maxCapacity) {
            throw new PostScriptVmException("dictfull");
        }
        entries.put(key, value);
    }

    public PsValue get(String key) {
        return entries.get(key);
    }

    public boolean containsKey(String key) {
        return entries.containsKey(key);
    }

    public Map<String, PsValue> snapshot() {
        return Map.copyOf(entries);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PsDictionary)) {
            return false;
        }
        PsDictionary that = (PsDictionary) o;
        return maxCapacity == that.maxCapacity && entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxCapacity, entries);
    }
}
