package com.creditease.concurrent.list;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 
 * <p>
 * null item is not supported
 * <p>
 * contains() is wait-free, add() and remove() are lock-free
 * 
 * @author pengfeili23
 *         <p>
 * @see
 * 
 *      Kunlong Zhang, Yujiao Zhao, Yajun Yang, Yujie Liu and Michael Spear. Practical Non-blocking Unordered Lists. In
 *      Proceedings of the 27th International Symposium on Distributed Computing (DISC), 2013
 * @param <K>
 * @param <V>
 */
@SuppressWarnings("restriction")
public class UnorderedList<K, V> implements Map<K, V> {

    static enum Status {
        DATA, DEAD, INSERT, REMOVE
    }

    /**
     * Set node at head of the list.
     */
    private final void enlist(Entry<K, V> n) {

        Entry<K, V> old = null;
        do {
            old = head;
            n.next = old;
        }
        while (!this.casHead(old, n));
    }

    /**
     * if exists, return old value. otherwise return null
     * 
     * @param home
     * @param key
     * @param value
     * @return
     */
    private final V helpInsert(Entry<K, V> home, K key, V value) {

        Entry<K, V> pred = home;
        Entry<K, V> curr = pred.next;
        Status s = null;
        while (curr != null) {
            s = curr.getStatus();
            // when see logically removed Entry, do physically remove
            if (s == Status.DEAD) {
                Entry<K, V> succ = curr.next;
                pred.next = succ;
                curr = succ;
                continue;
            }
            // found and equal
            if (curr.getKey().equals(key)) {
                if (s == Status.REMOVE) {
                    return null;
                }
                if (s == Status.INSERT || s == Status.DATA) {

                    return curr.setValue(value);
                }
            }
            // otherwise
            pred = curr;
            curr = pred.next;

        }
        return null;
    }

    /**
     * The core remove protocol.
     */
    private final Entry<K, V> helpRemove(Entry<K, V> home, Object key) {

        Entry<K, V> pred = home;
        Entry<K, V> curr = pred.next;
        Status s = null;
        while (curr != null) {
            s = curr.getStatus();
            // when see logically removed Entry, do physically remove
            if (s == Status.DEAD) {
                Entry<K, V> succ = curr.next;
                pred.next = succ;
                curr = succ;
                continue;
            }
            // found and equal
            if (curr.getKey().equals(key)) {
                if (s == Status.DATA) {
                    // logically remove
                    curr.setStatus(Status.DEAD);
                    // do physically remove
                    Entry<K, V> succ = curr.next;
                    pred.next = succ;
                    return curr;
                }
                if (s == Status.REMOVE) {
                    return null;
                }

                if ((s == Status.INSERT) && curr.casStatus(Status.INSERT, Status.REMOVE)) {
                    return curr;
                }
                // curr state has changed, need check
                continue;
            }
            // otherwise
            pred = curr;
            curr = pred.next;
        }

        return null;

    }

    /**
     * Constructor.
     */
    public UnorderedList() {

        head = null;
    }

    /**
     * List head.
     */
    private volatile Entry<K, V> head;

    // UNSAFE mechanics

    private static final sun.misc.Unsafe UNSAFE;
    private static final long headOffset;

    static {
        try {
            java.lang.reflect.Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) theUnsafe.get(null);
            // UNSAFE = sun.misc.Unsafe.getUnsafe();

            headOffset = UNSAFE.objectFieldOffset(UnorderedList.class.getDeclaredField("head"));

        }
        catch (Exception e) {
            throw new Error(e);
        }
    }

    private boolean casHead(Entry<K, V> cmp, Entry<K, V> val) {

        return UNSAFE.compareAndSwapObject(this, headOffset, cmp, val);
    }

    private static class Entry<K, V> implements Map.Entry<K, V> {

        final K key;
        volatile V value;
        volatile Entry<K, V> next;
        volatile Status status;

        /**
         * Creates new entry.
         */
        Entry(K k, V v, Status s) {

            value = v;
            key = k;
            status = s;
        }

        public final Status getStatus() {

            return status;
        }

        public final void setStatus(Status s) {

            status = s;
        }

        public final K getKey() {

            return key;
        }

        public final V getValue() {

            return value;
        }

        public final V setValue(V newValue) {

            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        public final boolean equals(Object o) {

            if (!(o instanceof Map.Entry))
                return false;
            @SuppressWarnings("rawtypes")
            Map.Entry e = (Map.Entry) o;
            Object k1 = getKey();
            Object k2 = e.getKey();
            if (k1 == k2 || (k1 != null && k1.equals(k2))) {
                Object v1 = getValue();
                Object v2 = e.getValue();
                if (v1 == v2 || (v1 != null && v1.equals(v2)))
                    return true;
            }
            return false;
        }

        public final int hashCode() {

            return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
        }

        public final String toString() {

            return getKey() + "=" + getValue();
        }

        // UNSAFE mechanics

        private static final sun.misc.Unsafe UNSAFE;
        private static final long nextOffset;
        private static final long statusOffset;
        static {
            try {
                java.lang.reflect.Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                UNSAFE = (sun.misc.Unsafe) theUnsafe.get(null);
                // UNSAFE = sun.misc.Unsafe.getUnsafe();

                nextOffset = UNSAFE.objectFieldOffset(Entry.class.getDeclaredField("next"));
                statusOffset = UNSAFE.objectFieldOffset(Entry.class.getDeclaredField("status"));
            }
            catch (Exception e) {
                throw new Error(e);
            }
        }

        @SuppressWarnings("unused")
        boolean casNext(Entry<K, V> cmp, Entry<K, V> val) {

            return UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
        }

        boolean casStatus(Status cmp, Status val) {

            return UNSAFE.compareAndSwapObject(this, statusOffset, cmp, val);
        }

    }

    @Override
    public int size() {

        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {

        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsKey(Object key) {

        if (key == null) {
            throw new NullPointerException();

        }
        Entry<K, V> curr = head;
        while (curr != null) {
            if (curr.getKey().equals(key)) {
                Status s = curr.getStatus();
                if (s != Status.DEAD) {
                    return (s != Status.REMOVE);
                }
            }
            curr = curr.next;
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {

        if (value == null) {
            throw new NullPointerException();

        }
        Entry<K, V> curr = head;
        while (curr != null) {
            if (curr.getValue().equals(value)) {
                Status s = curr.getStatus();
                if (s != Status.DEAD) {
                    return (s != Status.REMOVE);
                }
            }
            curr = curr.next;
        }
        return false;
    }

    @Override
    public V get(Object key) {

        if (key == null) {
            throw new NullPointerException();

        }
        Entry<K, V> curr = head;
        while (curr != null) {
            if (curr.getKey().equals(key)) {
                Status s = curr.getStatus();
                if (s != Status.DEAD) {
                    return (s == Status.REMOVE) ? null : curr.getValue();
                }
            }
            curr = curr.next;
        }
        return null;
    }

    @Override
    public V put(K key, V value) {

        if (key == null) {
            throw new NullPointerException();

        }
        Entry<K, V> n = new Entry<K, V>(key, value, Status.INSERT);

        enlist(n);
        V b = helpInsert(n, key, value);
        Status s = (b == null) ? Status.DATA : Status.DEAD;
        if (!n.casStatus(Status.INSERT, s)) {
            helpRemove(n, key);
            n.setStatus(Status.DEAD);
        }
        return b;
    }

    @Override
    public V remove(Object key) {

        if (key == null) {
            throw new NullPointerException();

        }
        @SuppressWarnings("unchecked")
        Entry<K, V> n = new Entry<K, V>((K) key, null, Status.REMOVE);
        enlist(n);
        Entry<K, V> b = helpRemove(n, key);
        n.setStatus(Status.DEAD);
        return (b == null ? null : b.value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {

        throw new UnsupportedOperationException();

    }

    @Override
    public void clear() {

        throw new UnsupportedOperationException();

    }

    @Override
    public Set<K> keySet() {

        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<V> values() {

        throw new UnsupportedOperationException();
    }

    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {

        throw new UnsupportedOperationException();
    }

    public int dump() {

        Entry<K, V> curr = this.head;
        int cnt = 0;

        while (curr != null) {

            if (curr.getStatus() == Status.DATA || curr.getStatus() == Status.INSERT) {
                cnt++;
            }
            curr = curr.next;

        }

        return cnt;
    }

}
