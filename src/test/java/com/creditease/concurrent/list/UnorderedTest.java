
package com.creditease.concurrent.list;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.*;

public class UnorderedTest extends TestCase {

    private final static int THREADS = 8;
    private final static int TEST_SIZE = 10240;
    private final static int PER_THREAD = TEST_SIZE / THREADS;
    Unordered<Integer> instance;
    Thread[] thread = new Thread[THREADS];

    public UnorderedTest(String testName) {

        super(testName);
        instance = new Unordered<Integer>();
    }

    public static Test suite() {

        TestSuite suite = new TestSuite(UnorderedTest.class);

        return suite;
    }

    /**
     * Sequential calls.
     */
    public void testSequential() {

        System.out.println("sequential add, contains, and remove");

        for (int i = 0; i < TEST_SIZE; i++) {
            instance.add(i);
        }
        for (int i = 0; i < TEST_SIZE; i++) {

            if (!instance.contains(i)) {
                fail("bad containsValue: " + i);
            }

        }
        for (int i = 0; i < TEST_SIZE; i++) {
            if (!instance.remove(i)) {
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

            if (!instance.contains(i)) {
                fail("bad containsValue: " + i);
            }

        }
        for (int i = 0; i < TEST_SIZE; i++) {
            if (!instance.remove(i)) {
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
            instance.add(i);
        }
        for (int i = 0; i < TEST_SIZE; i++) {

            if (!instance.contains(i)) {
                fail("bad containsValue: " + i);
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
        System.out.println("total:" + COUNT);
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

                instance.add(value + i);
            }

        }
    }

    final AtomicInteger COUNT = new AtomicInteger(0);

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
            // StringBuilder sb = new StringBuilder();
            int cnt = 0;
            for (int i = 0; i < PER_THREAD; i++) {
                if (!instance.remove(value + i)) {
                    // sb.append(value + i + "->");
                    cnt++;
                }
            }
            // if (sb.length() > 0) {
            // fail(Thread.currentThread() + " not remove \n" + sb.toString());
            // }
            if (cnt > 0) {
                COUNT.getAndAdd(cnt);
                fail(Thread.currentThread() + " not remove \n" + cnt);

            }
        }
    }

}
