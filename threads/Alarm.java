package nachos.threads;

import nachos.machine.*;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

	private long numberOfTicks = 0;
	PriorityQueue<priorityThread> waitQueue = null;

	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable();
		// System.out.println((numberOfTicks++) + " " + KThread.currentThread().getName());

		if(waitQueue != null && waitQueue.size() != 0){
			long curTime = Machine.timer().getTime();
			while(waitQueue.size() > 0 && curTime >= waitQueue.peek().wakeTime){
				// System.out.println(waitQueue.peek().wakeTime);
				waitQueue.poll().thread.ready();
				// System.out.println("Pop");
			}
		}
		Machine.interrupt().restore(intStatus);
		
		// System.out.println("Test for intrrupt: " + KThread.currentThread().getName());
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		boolean intStatus = Machine.interrupt().disable();
		if(x <= 0){
			Machine.interrupt().restore(intStatus);
			return;
		}
		long wakeTime = Machine.timer().getTime() + x;
		if (waitQueue == null){
			// System.out.println("creating a new queue");
			createNewQueue();
		}
		// System.out.println(KThread.currentThread().getName());

		waitQueue.add(new priorityThread(KThread.currentThread(), wakeTime));
		// System.out.println("Queue size: " + waitQueue.size());

		// Note: sleep rather than yield because yield 
		// will put this thread into ready queue
		KThread.currentThread().sleep();
		Machine.interrupt().restore(intStatus);
	}

	private void createNewQueue(){
		waitQueue = new PriorityQueue<priorityThread>(new Comparator<priorityThread>(){
			public int compare(priorityThread thread1, priorityThread thread2) {
				if(thread1.wakeTime > thread2.wakeTime) return 1;
				else if(thread1.wakeTime < thread2.wakeTime) return -1;
				else return 0;
			}
		});
	}

	private class priorityThread{
		public KThread thread = null;
		public long wakeTime = 0;
		
		public priorityThread(KThread thread, long wakeTime){
			this.thread = thread;
			this.wakeTime = wakeTime;
		}
	}

	// Add Alarm testing code to the Alarm class
    public static void alarmTest1() {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;
		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
    }

	// Multi-threads test
	public static void alarmTestMultiThreads() {

		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;
		KThread thread = new KThread(new Runnable(){
			public void run(){
				int ds[] = {5000, 50*1000};
				long t2, t3;
				for (int d: ds){
					t2 = Machine.timer().getTime();
					ThreadedKernel.alarm.waitUntil(d);
					t3 = Machine.timer().getTime();
					// System.out.println (KThread.currentThread().getName() + " " + "alarmTest1: waited for " + (t3 - t2) + " ticks");
				}	
			}
		});

		thread.setName("child").fork();

		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil(d);
			t1 = Machine.timer().getTime();
			System.out.println (KThread.currentThread().getName() + " " + "alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
    }

    // Implement more test methods here ...
    // Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
    public static void selfTest() {
		// alarmTest1();
		// Invoke your other test methods here ...
		// alarmTestMultiThreads();
    }
}
