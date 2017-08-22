
package com.creditease.concurrent.list;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * null item is not supported
 * <p>
 * 
 * contains() is wait-free, add() and remove() use optimistic lock
 * <p>
 * 
 * @author pengfeili23
 * @see
 *      <p>
 *      S. Heller, M. Herlihy, V. Luchangco, M. Moir, W. N. Scherer III, and N. Shavit. A Lazy Concurrent List-Based Set
 *      Algorithm. Proc. of the Ninth International Conference on Principles of Distributed Systems (OPODIS 2005), Pisa,
 *      Italy, pp. 3–16, 2005
 * @param <E>
 */
public class LazyList<E> implements Set<E> {

    private Node head;

    public LazyList() {
        // Add sentinels to start and end
        this.head = new Node(Integer.MIN_VALUE);
        this.head.next = new Node(Integer.MAX_VALUE);

    }

    /**
     * Check that prev and curr are still in list and adjacent
     */
    private boolean validate(Node pred, Node curr) {

        return ((!pred.marked) && (!curr.marked) && (pred.next == curr));
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
        Node curr = this.head;

        while (true) {
            // not found
            if (curr.key > key) {
                return false;
            }
            // found and equal
            if ((curr.key == key) && (o.equals(curr.item))) {
                return !curr.marked;
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
        retry: while (true) {
            Node pred = this.head;
            Node curr = pred.next;

            while (true) {
                // not found
                if (curr.key > key) {
                    pred.lock();
                    try {
                        curr.lock();
                        try {
                            if (validate(pred, curr)) {
                                Node Node = new Node(e);
                                Node.next = curr;
                                pred.next = Node;
                                return true;
                            }
                            continue retry;
                        }
                        finally {
                            curr.unlock();
                        }
                    }
                    finally {
                        pred.unlock();
                    }
                }
                // found and equal
                if ((curr.key == key) && (e.equals(curr.item))) {
                    pred.lock();
                    try {
                        curr.lock();
                        try {
                            if (validate(pred, curr)) {
                                return false;
                            }
                            continue retry;
                        }
                        finally {
                            curr.unlock();
                        }
                    }
                    finally {
                        pred.unlock();
                    }
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
        retry: while (true) {
            Node pred = this.head;
            Node curr = pred.next;

            while (true) {
                // not found
                if (curr.key > key) {
                    pred.lock();
                    try {
                        curr.lock();
                        try {
                            if (validate(pred, curr)) {
                                return false;
                            }
                            continue retry;
                        }
                        finally {
                            curr.unlock();
                        }
                    }
                    finally {
                        pred.unlock();
                    }
                }
                // found and equal
                if ((curr.key == key) && (o.equals(curr.item))) {
                    pred.lock();
                    try {
                        curr.lock();
                        try {
                            if (validate(pred, curr)) {
                                curr.marked = true; // logically remove
                                pred.next = curr.next; // physically remove
                                return true;
                            }
                            continue retry;
                        }
                        finally {
                            curr.unlock();
                        }
                    }
                    finally {
                        pred.unlock();
                    }
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

    /**
     * list Node
     */
    private final class Node {

        /**
         * actual item
         */
        E item;
        /**
         * item's hash code
         */
        int key;
        /**
         * next Node in list
         */
        Node next;
        /**
         * If true, Node is logically deleted.
         */
        boolean marked;
        /**
         * Synchronizes Node.
         */
        Lock lock;

        /**
         * Constructor for usual Node
         * 
         * @param item
         *            element in list
         */
        Node(E item) { // usual constructor
            this.item = item;
            this.key = item.hashCode();
            this.next = null;
            this.marked = false;
            this.lock = new ReentrantLock();
        }

        /**
         * Constructor for sentinel Node
         * 
         * @param key
         *            should be min or max int value
         */
        Node(int key) { // sentinel constructor
            this.item = null;
            this.key = key;
            this.next = null;
            this.marked = false;
            this.lock = new ReentrantLock();
        }

        /**
         * Lock Node
         */
        void lock() {

            lock.lock();
        }

        /**
         * Unlock Node
         */
        void unlock() {

            lock.unlock();
        }
    }

    public String dump() {

        Node curr = this.head;
        StringBuilder sb = new StringBuilder();
        while (curr != null) {
            sb.append(curr.item);
            if (curr.marked == true) {
                sb.append("X");
            }
            sb.append("->");
            curr = curr.next;
        }
        return sb.toString();
    }
}
