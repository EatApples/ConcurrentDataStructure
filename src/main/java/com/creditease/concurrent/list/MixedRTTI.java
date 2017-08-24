package com.creditease.concurrent.list;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * 
 * @author pengfeili23
 *         <p>
 *         null item is not supported
 *         <p>
 *         contains() is wait-free, add() and remove() are lock-free
 *         <p>
 *         RTTI机制，用插入标记结点代替标记指针
 *         <p>
 *         跳过 冲突密集点，快速定位；如非必要，不去帮助(可能没什么用，可能留下逻辑删除节点)
 *         <p>
 *         回溯机制，快速恢复，尽可能不重头来过，维护backlink（可能得不偿失）
 *         <p>
 *         有序，减少扫描长度
 * @param <E>
 */
public class MixedRTTI<E> implements Set<E> {

    /**
     * Internal Node class.
     */
    @SuppressWarnings("restriction")
    private static class Node<E> {

        final int key;
        E item;
        volatile Node<E> next;
        volatile Node<E> backlink;

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
    public MixedRTTI() {
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
        Node<E> target = find(e);
        return TryInsert(target, e);

    }

    @Override
    public boolean remove(Object o) {

        if (o == null) {
            throw new NullPointerException();

        }
        Node<E> target = find(o);
        return TryDelete(target, o);

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
     * fast locate: find left-most-unmarked node
     * 
     * @param item
     * @return
     */
    private Node<E> find(Object item) {

        if (item == null) {
            throw new NullPointerException();

        }
        int key = item.hashCode();
        Node<E> pred = this.head;
        Node<E> curr = pred.next;
        while (curr.key < key) {
            if (!(curr instanceof Marker)) {
                pred = curr;
            }
            curr = curr.next;
        }
        return pred;

    }

    private Node<E> CheckBacklink(Node<E> start, Node<E> end) {

        Node<E> temp = start;
        while (temp.key < end.key) {
            start = temp;
            if (temp.next == end) {
                end.backlink = start;
                break;
            }

            temp = start.next;
            if (!(temp instanceof Marker)) {
                continue;
            }
            temp = temp.next;

        }
        // we keep start.key<end.key
        return start;

    }

    private boolean TryMark(Node<E> prev_node, Node<E> del_node) {

        // do while del_node is marked
        del_node.backlink = prev_node;
        Node<E> next_node = null;
        boolean result = false;
        while (true) {
            next_node = del_node.next;
            // attempt mark
            if (!(next_node instanceof Marker)) {
                if (del_node.casNext(next_node, new Marker<E>(next_node))) {

                    result = true;
                }
                continue;
            }
            // marked
            break;
        }
        return result;

    }

    // remove返回是tricky方式，remove线程可能在删除链中看到了一个被mark的相关节点，它返回false，
    // 但是此时链表中存在相关节点
    // 这种情况下，remove失败的可线性化点取在一个并发的成功的remove（将相关节点mark成功的那一个）之后
    private boolean TryDelete(Node<E> prev_node, Object o) {

        if (o == null) {
            throw new NullPointerException();

        }
        int key = o.hashCode();
        Node<E> pred = null, curr = null, succ = null;
        pred = prev_node;
        while (true) {
            curr = pred.next;

            if (curr instanceof Marker) {
                succ = curr.next;
                curr = pred;
                pred = curr.backlink;
                // 做物理删除前，先更新backlink，使得pred是curr的直接前驱
                pred = CheckBacklink(pred, curr);
                if (pred.casNext(curr, succ)) {
                    CheckBacklink(pred, succ);
                }
                continue;
            }
            // not found
            if (curr.key > key) {
                return false;
            }
            // found and equal
            if ((curr.key == key) && (o.equals(curr.item))) {
                // TryMark保证curr节点后插入了标记节点，返回的bool值表明自己的本次操作是否成功
                boolean result = TryMark(pred, curr);
                succ = curr.next;
                if (succ instanceof Marker) {
                    succ = succ.next;
                    // 做物理删除前，先更新backlink，使得pred是curr的直接前驱
                    pred = CheckBacklink(pred, curr);
                    if (pred.casNext(curr, succ)) {
                        CheckBacklink(pred, succ);
                    }
                }
                return result;
            }
            // otherwise
            CheckBacklink(pred, curr);
            pred = curr;
        }
    }

    private boolean TryInsert(Node<E> prev_node, E e) {

        if (e == null) {
            throw new NullPointerException();
        }
        int key = e.hashCode();
        Node<E> pred = null, curr = null, succ = null;
        Node<E> newNode = new Node<E>(e);
        pred = prev_node;
        while (true) {
            curr = pred.next;

            if (curr instanceof Marker) {
                succ = curr.next;
                curr = pred;
                pred = curr.backlink;
                pred = CheckBacklink(pred, curr);
                // 在物理删除前，先更新curr（逻辑删除）的直接前驱，并复制给pred
                // pred的键值肯定比curr的键值小，这是由CheckBacklink方法保证的
                // 如果物理删除成功，则更新succ的直接前驱。这里为了防止succ被标记，backlink指向错误的直接前驱
                // 这里pred没有更新，因为succ还没有被访问到，防止更新错过了相关节点
                if (pred.casNext(curr, succ)) {
                    CheckBacklink(pred, succ);
                }
                continue;
            }

            // not found
            if (curr.key > key) {
                newNode.next = curr;
                // 只有操作成功，才更新backlink
                if (pred.casNext(curr, newNode)) {
                    CheckBacklink(newNode, curr);
                    return true;
                }
                continue;
            }
            // found and equal
            if ((curr.key == key) && (e.equals(curr.item))) {
                succ = curr.next;
                if (!(succ instanceof Marker)) {
                    return false;
                }
            }
            // otherwise
            CheckBacklink(pred, curr);
            pred = curr;
        }
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

    public static void main(String[] args) {

        MixedRTTI<Integer> instance = new MixedRTTI<Integer>();
        instance.add(1);
        instance.add(3);
        instance.add(5);
        System.out.println(instance.dump());
        Node<Integer> prev_node = instance.find(3);
        Node<Integer> del_node = instance.find(5);
        System.out.println("prev_node: " + prev_node.item);
        System.out.println("del_node: " + del_node.item);
        instance.TryMark(prev_node, del_node);
        System.out.println(instance.dump());
        instance.add(2);
        System.out.println(instance.dump());
        instance.add(7);
        System.out.println(instance.dump());
        instance.add(4);
        System.out.println(instance.dump());
    }
}
