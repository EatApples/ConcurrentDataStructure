package com.creditease.concurrent.list;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * The Harris-Michael Lock-free linked list with RTTI optimization
 * <p>
 * null item is not supported
 * <p>
 * contains() is wait-free, add() and remove() are lock-free
 * 
 * @author pengfeili23
 *
 * @see
 *      <p>
 *      T. Harris. A pragmatic implementation of non-blocking linked-lists. In Proc. of Fifteenth International
 *      Symposium on Distributed Computing (DISC 2001), Lisbon, Portugal, volume 2180 of Lecture Notes in Computer
 *      Science, pp. 300–314, October 2001, Springer-Verlag
 *      <p>
 *      M. M. Michael. High performance dynamic lock-free hash tables and list-based sets.
 *      InSPAA’02:Proc.oftheFourteenthAnnualACMSymposium on Parallel Algorithms and Architectures, pp. 73–82. Winnipeg,
 *      Manitoba, Canada, NY, USA, 2002, ACM Press
 * @param <E>
 */
public class HarrisListRTTI<E> implements Set<E> {

    /**
     * Internal Node class.
     */
    @SuppressWarnings("restriction")
    private static class Node<E> {

        final int key;
        E item;
        volatile Node<E> next;

        Node(int k) {
            this.key = k;
        }

        Node(E item) {
            this.item = item;
            this.key = item.hashCode();
        }

        // UNSAFE mechanics

        private static final sun.misc.Unsafe UNSAFE;
        private static final long nextOffset;

        static {
            try {
                java.lang.reflect.Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                UNSAFE = (sun.misc.Unsafe) theUnsafe.get(null);
                // UNSAFE = sun.misc.Unsafe.getUnsafe();

                nextOffset = UNSAFE.objectFieldOffset(Node.class.getDeclaredField("next"));
            }
            catch (Exception e) {
                throw new Error(e);
            }
        }

        boolean casNext(Node<E> cmp, Node<E> val) {

            return UNSAFE.compareAndSwapObject(this, nextOffset, cmp, val);
        }
    }

    private static final class Marker<E> extends Node<E> {

        private Marker(Node<E> n) {
            super(Integer.MIN_VALUE);
            this.next = n;
        }
    }

    /** Sentinel nodes. */
    private volatile Node<E> head;
    private volatile Node<E> tail;

    /**
     * Constructor.
     */
    public HarrisListRTTI() {
        head = new Node<E>(Integer.MIN_VALUE);
        tail = new Node<E>(Integer.MAX_VALUE);
        head.next = tail;
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
    public boolean contains(Object o) {

        if (o == null) {
            throw new NullPointerException();

        }
        int key = o.hashCode();
        Node<E> curr = this.head;

        while (true) {
            // not found
            if (curr.key > key) {
                return false;
            }
            // found and equal
            if ((curr.key == key) && (o.equals(curr.item))) {
                return !(curr.next instanceof Marker);
            }
            // otherwise
            curr = curr.next;
        }

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
    public boolean add(E e) {

        if (e == null) {
            throw new NullPointerException();
        }
        int key = e.hashCode();
        Node<E> pred = null, curr = null, succ = null;
        retry:
        // purpose of outermost while loop is for implementing goto only..
        while (true) {
            // initialization
            pred = this.head;
            curr = pred.next;
            // traverse linked list
            while (true) {
                succ = curr.next;
                while (succ instanceof Marker) {
                    succ = succ.next;
                    // snip curr and marker
                    if (!pred.casNext(curr, succ))
                        continue retry;
                    curr = succ;
                    succ = curr.next;
                }
                // not found
                if (curr.key > key) {
                    Node<E> node = new Node<E>(e);
                    node.next = curr;
                    if (pred.casNext(curr, node)) {
                        // if CAS success, then add a new node
                        return true;
                    }
                    // otherwise retry
                    continue retry;
                }
                // found and equal
                if ((curr.key == key) && (e.equals(curr.item))) {
                    return false;
                }
                // otherwise
                pred = curr;
                curr = pred.next;

            }

        }
    }

    @Override
    public boolean remove(Object o) {

        if (o == null) {
            throw new NullPointerException();
        }
        int key = o.hashCode();
        Node<E> pred = null, curr = null, succ = null;
        retry:
        // purpose of outermost while loop is for implementing goto only..
        while (true) {
            // initialization
            pred = this.head;
            curr = pred.next;
            // traverse linked list
            while (true) {
                succ = curr.next;
                while (succ instanceof Marker) {
                    succ = succ.next;
                    // snip curr and marker
                    if (!pred.casNext(curr, succ))
                        continue retry;
                    curr = succ;
                    succ = curr.next;
                }
                // not found
                if (curr.key > key) {
                    return false;
                }
                // found and equal
                if ((curr.key == key) && (o.equals(curr.item))) {
                    // logically remove
                    if (!curr.casNext(succ, new Marker<E>(succ))) {
                        continue retry;
                    }
                    // physically remove
                    pred.casNext(curr, succ);
                    return true;
                }
                // otherwise
                pred = curr;
                curr = pred.next;

            }

        }
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

    public String dump() {

        Node<E> curr = this.head;
        StringBuilder sb = new StringBuilder();
        while (curr != null) {
            sb.append(curr.item);
            if (curr.next instanceof Marker) {
                sb.append("X");
                curr = curr.next;
            }
            sb.append("->");
            curr = curr.next;
        }
        return sb.toString();
    }

}
