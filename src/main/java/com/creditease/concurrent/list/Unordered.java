package com.creditease.concurrent.list;

import java.util.Collection;
import java.util.Iterator;
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
public class Unordered<E> implements Set<E> {

    private static enum Status {
        DATA, DEAD, INSERT, REMOVE
    }

    /**
     * Set node at head of the list.
     */
    private final void enlist(Node n) {

        Node old = null;
        do {
            old = head;
            n.next = old;
        }
        while (!this.casHead(old, n));
    }

    private final boolean helpInsert(Node home, E e) {

        Node pred = home;
        Node curr = pred.next;
        Status s = null;
        while (curr != null) {
            s = curr.getStatus();
            // when see logically removed Entry, do physically remove
            if (s == Status.DEAD) {
                Node succ = curr.next;
                pred.next = succ;
                curr = succ;
                continue;
            }
            // found and equal
            if (curr.getValue().equals(e)) {
                if (s == Status.REMOVE) {
                    return true;
                }
                if (s == Status.INSERT || s == Status.DATA) {

                    return false;
                }
            }
            // otherwise
            pred = curr;
            curr = pred.next;

        }
        return true;
    }

    /**
     * The core remove protocol.
     */
    private final boolean helpRemove(Node home, Object o) {

        Node pred = home;
        Node curr = pred.next;
        Status s = null;
        while (curr != null) {
            s = curr.getStatus();
            // when see logically removed Entry, do physically remove
            if (s == Status.DEAD) {
                Node succ = curr.next;
                pred.next = succ;
                curr = succ;
                continue;
            }
            // found and equal
            if (curr.getValue().equals(o)) {
                if (s == Status.DATA) {
                    // logically remove
                    curr.setStatus(Status.DEAD);
                    // do physically remove
                    Node succ = curr.next;
                    pred.next = succ;
                    return true;
                }
                if (s == Status.REMOVE) {
                    return false;
                }

                if ((s == Status.INSERT) && curr.casStatus(Status.INSERT, Status.REMOVE)) {
                    return true;
                }
                // curr state has changed, need check
                continue;
            }
            // otherwise
            pred = curr;
            curr = pred.next;
        }

        return false;

    }

    /**
     * Constructor.
     */
    public Unordered() {

        head = null;
    }

    /**
     * List head.
     */
    private volatile Node head;

    // UNSAFE mechanics

    private static final sun.misc.Unsafe UNSAFE;
    private static final long headOffset;

    static {
        try {
            java.lang.reflect.Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) theUnsafe.get(null);
            // UNSAFE = sun.misc.Unsafe.getUnsafe();

            headOffset = UNSAFE.objectFieldOffset(Unordered.class.getDeclaredField("head"));

        }
        catch (Exception e) {
            throw new Error(e);
        }
    }

    private boolean casHead(Node cmp, Node val) {

        return UNSAFE.compareAndSwapObject(this, headOffset, cmp, val);
    }

    private static class Node {

        volatile Object value;
        volatile Node next;
        volatile Status status;

        Node(Object v, Status s) {

            value = v;
            status = s;
        }

        public final Status getStatus() {

            return status;
        }

        public final void setStatus(Status s) {

            status = s;
        }

        public final Object getValue() {

            return value;
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

                nextOffset = UNSAFE.objectFieldOffset(Node.class.getDeclaredField("next"));
                statusOffset = UNSAFE.objectFieldOffset(Node.class.getDeclaredField("status"));
            }
            catch (Exception e) {
                throw new Error(e);
            }
        }

        @SuppressWarnings("unused")
        boolean casNext(Node cmp, Node val) {

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
    public Iterator<E> iterator() {

        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray() {

        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] a) {

        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {

        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {

        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {

        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {

        throw new UnsupportedOperationException();

    }

    @Override
    public boolean contains(Object o) {

        if (o == null) {
            throw new NullPointerException();

        }
        Node curr = head;
        while (curr != null) {
            if (curr.getValue().equals(o)) {
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
    public boolean add(E e) {

        if (e == null) {
            throw new NullPointerException();

        }
        Node n = new Node(e, Status.INSERT);
        enlist(n);
        boolean b = helpInsert(n, e);
        Status s = b ? Status.DATA : Status.DEAD;
        if (!n.casStatus(Status.INSERT, s)) {
            helpRemove(n, e);
            n.setStatus(Status.DEAD);
        }
        return b;
    }

    @Override
    public boolean remove(Object o) {

        if (o == null) {
            throw new NullPointerException();

        }

        Node n = new Node(o, Status.REMOVE);
        enlist(n);
        boolean b = helpRemove(n, o);
        n.setStatus(Status.DEAD);
        return b;

    }

    public int dump() {

        Node curr = this.head;
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
