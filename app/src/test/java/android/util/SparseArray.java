package android.util;

import java.util.TreeMap;

/**
 * Lightweight JVM unit-test implementation of {@code android.util.SparseArray} so Media3 extractors
 * (such as {@code TsExtractor}) can execute in pure JVM unit tests without Robolectric.
 */
public class SparseArray<E> implements Cloneable {
    private final TreeMap<Integer, E> map = new TreeMap<>();

    public SparseArray() {
    }

    public SparseArray(int initialCapacity) {
    }

    public E get(int key) {
        return map.get(key);
    }

    public E get(int key, E valueIfKeyNotFound) {
        return map.containsKey(key) ? map.get(key) : valueIfKeyNotFound;
    }

    public void delete(int key) {
        map.remove(key);
    }

    public void remove(int key) {
        map.remove(key);
    }

    public void removeAt(int index) {
        map.remove(keyAt(index));
    }

    public void put(int key, E value) {
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

    public E valueAt(int index) {
        int i = 0;
        for (E v : map.values()) {
            if (i++ == index) return v;
        }
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + map.size());
    }

    public void setValueAt(int index, E value) {
        map.put(keyAt(index), value);
    }

    public int indexOfKey(int key) {
        int i = 0;
        for (Integer k : map.keySet()) {
            if (k == key) return i;
            i++;
        }
        return -1;
    }

    public boolean contains(int key) {
        return map.containsKey(key);
    }

    public void clear() {
        map.clear();
    }

    public void append(int key, E value) {
        map.put(key, value);
    }

    @Override
    public SparseArray<E> clone() {
        SparseArray<E> copy = new SparseArray<>();
        for (java.util.Map.Entry<Integer, E> entry : map.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }
}
