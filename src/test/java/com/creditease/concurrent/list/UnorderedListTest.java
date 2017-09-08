
package com.creditease.concurrent.list;

import java.util.concurrent.CountDownLatch;

import junit.framework.*;

public class UnorderedListTest extends TestCase {

    private final static int THREADS = 8;
    private final static int TEST_SIZE = 512;
    private final static int PER_THREAD = TEST_SIZE / THREADS;
    UnorderedList<Integer, Integer> instance;
    Thread[] thread = new Thread[THREADS];

    public UnorderedListTest(String testName) {
        super(testName);
        instance = new UnorderedList<Integer, Integer>();
    }

    public static Test suite() {

        TestSuite suite = new TestSuite(UnorderedListTest.class);

        return suite;
    }

    /**
     * Sequential calls.
     */
    public void testSequential() {

        System.out.println("sequential add, contains, and remove");

        for (int i = 0; i < TEST_SIZE; i++) {
            instance.put(i, i);
        }
        for (int i = 0; i < TEST_SIZE; i++) {
            if (!instance.containsKey(i)) {
                fail("bad containsKey: " + i);
            }
            if (!instance.containsValue(i)) {
                fail("bad containsValue: " + i);
            }
            if (!instance.get(i).equals(i)) {
                fail("bad get: " + i);
            }
        }
        for (int i = 0; i < TEST_SIZE; i++) {
            if (instance.remove(i) == null) {
                fail("bad remove: " + i);
            }
        }
        System.out.println(instance.dump());
    }

    /**
     * Parallel add, sequential removes
     */
    public void testParallelAdd() throws Exception {

        System.out.println("parallel add");
        final CountDownLatch startGate = new CountDownLatch(1);
        for (int i = 0; i < THREADS; i++) {
            thread[i] = new AddThread(i * PER_THREAD, startGate);
        }
        for (int i = 0; i < THREADS; i++) {
            thread[i].start();
        }
        startGate.countDown();
        for (int i = 0; i < THREADS; i++) {
            thread[i].join();
        }
        for (int i = 0; i < TEST_SIZE; i++) {
            if (!instance.containsKey(i)) {
                fail("bad containsKey: " + i);
            }
            if (!instance.containsValue(i)) {
                fail("bad containsValue: " + i);
            }
            if (!instance.get(i).equals(i)) {
                fail("bad get: " + i);
            }
        }
        for (int i = 0; i < TEST_SIZE; i++) {
            if (instance.remove(i) == null) {
                fail("bad remove: " + i);
            }
        }
        System.out.println(instance.dump());
    }

    /**
     * Sequential adds, parallel removes
     */
    public void testParallelRemove() throws Exception {

        System.out.println("parallel remove");
        final CountDownLatch startGate = new CountDownLatch(1);
        for (int i = 0; i < TEST_SIZE; i++) {
            instance.put(i, i);
        }
        for (int i = 0; i < TEST_SIZE; i++) {
            if (!instance.containsKey(i)) {
                fail("bad containsKey: " + i);
            }
            if (!instance.containsValue(i)) {
                fail("bad containsValue: " + i);
            }
            if (!instance.get(i).equals(i)) {
                fail("bad get: " + i);
            }
        }
        for (int i = 0; i < THREADS; i++) {
            thread[i] = new RemoveThread(i * PER_THREAD, startGate);
        }
        for (int i = 0; i < THREADS; i++) {
            thread[i].start();
        }
        startGate.countDown();
        for (int i = 0; i < THREADS; i++) {
            thread[i].join();
        }
        System.out.println(instance.dump());
    }

    /**
     * Parallel adds, removes
     */
    public void testParallelBoth() throws Exception {

        System.out.println("parallel both");
        final CountDownLatch startGate = new CountDownLatch(1);
        Thread[] myThreads = new Thread[2 * THREADS];
        for (int i = 0; i < THREADS; i++) {
            myThreads[i] = new AddThread(i * PER_THREAD, startGate);
            myThreads[i + THREADS] = new RemoveThread(i * PER_THREAD, startGate);
        }
        for (int i = 0; i < 2 * THREADS; i++) {
            myThreads[i].start();
        }
        startGate.countDown();
        for (int i = 0; i < 2 * THREADS; i++) {
            myThreads[i].join();
        }
        System.out.println(instance.dump());
    }

    public void testParallelPut() throws Exception {

        System.out.println("parallel put");
        for (int i = 0; i < TEST_SIZE; i++) {
            instance.put(i, i);
        }
        final CountDownLatch startGate = new CountDownLatch(1);
        Thread[] myThreads = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            myThreads[i] = new putThread(i * PER_THREAD, startGate);

        }
        for (int i = 0; i < THREADS; i++) {
            myThreads[i].start();
        }
        startGate.countDown();
        for (int i = 0; i < THREADS; i++) {
            myThreads[i].join();
        }
        System.out.println(instance.dump());
    }

    class AddThread extends Thread {

        int value;
        CountDownLatch startGate;

        AddThread(int i, CountDownLatch startGate) {
            value = i;
            this.startGate = startGate;
        }

        public void run() {

            try {
                startGate.await();
            }
            catch (InterruptedException e) {

                e.printStackTrace();
            }

            for (int i = 0; i < PER_THREAD; i++) {

                instance.put(value + i, value + i);
            }

        }
    }

    private static final int BASE = 10000;

    class putThread extends Thread {

        int value;
        CountDownLatch startGate;

        putThread(int i, CountDownLatch startGate) {
            value = i;
            this.startGate = startGate;
        }

        public void run() {

            try {
                startGate.await();
            }
            catch (InterruptedException e) {

                e.printStackTrace();
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < PER_THREAD; i++) {

                sb.append(instance.put(value + i, value + i + BASE) + ",");
            }
            System.out.println(Thread.currentThread().getName() + "\n" + sb.toString());
        }
    }

    class RemoveThread extends Thread {

        int value;
        CountDownLatch startGate;

        RemoveThread(int i, CountDownLatch startGate) {
            value = i;
            this.startGate = startGate;
        }

        public void run() {

            try {
                startGate.await();
            }
            catch (InterruptedException e) {

                e.printStackTrace();
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < PER_THREAD; i++) {
                if (instance.remove(value + i) == null) {
                    sb.append(value + i + "->");

                }
            }
            if (sb.length() > 0) {
                fail(Thread.currentThread() + " not remove \n" + sb.toString());
            }
        }
    }

}
