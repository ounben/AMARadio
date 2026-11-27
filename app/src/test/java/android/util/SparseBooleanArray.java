package android.util;

import java.util.TreeMap;

/**
 * Lightweight JVM unit-test implementation of {@code android.util.SparseBooleanArray} for Media3
 * extractor unit tests.
 */
public class SparseBooleanArray implements Cloneable {
    private final TreeMap<Integer, Boolean> map = new TreeMap<>();

    public SparseBooleanArray() {
    }

    public SparseBooleanArray(int initialCapacity) {
    }

    public boolean get(int key) {
        return get(key, false);
    }

    public boolean get(int key, boolean valueIfKeyNotFound) {
        Boolean v = map.get(key);
        return v != null ? v : valueIfKeyNotFound;
    }

    public void delete(int key) {
        map.remove(key);
    }

    public void put(int key, boolean value) {
        map.put(key, value);
    }

    public int size() {
        return map.size();
    }

    public int keyAt(int index) {
        int i = 0;
        for (Integer k : map.keySet()) {
            if (i++ == index) return k;
        }
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + map.size());
    }

    public boolean valueAt(int index) {
        int i = 0;
        for (Boolean v : map.values()) {
            if (i++ == index) return v;
        }
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + map.size());
    }

    public int indexOfKey(int key) {
        int i = 0;
        for (Integer k : map.keySet()) {
            if (k == key) return i;
            i++;
        }
        return -1;
    }

    public void clear() {
        map.clear();
    }

    public void append(int key, boolean value) {
        map.put(key, value);
    }

    @Override
    public SparseBooleanArray clone() {
        SparseBooleanArray copy = new SparseBooleanArray();
        for (java.util.Map.Entry<Integer, Boolean> entry : map.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }
}
