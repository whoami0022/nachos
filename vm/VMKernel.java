package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.security.Principal;
import java.util.*;
// import java.util.concurrent.locks.Lock;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		swapFile = ThreadedKernel.fileSystem.open(swapFileName, true);

		pinCountLock = new Lock();
		unpinnedLock = new Lock();
		unpinnedCV = new Condition(unpinnedLock);

		freeSwapPages = new LinkedList<Integer>();
		for(int i = 0; i < freeSwapNumbers; i++){
			freeSwapPages.add(i);
		}
		
		int pageNum = Machine.processor().getNumPhysPages();
		invertedPT = new invertedPageTable[pageNum];
		for(int i = 0; i < pageNum; i++){
			invertedPT[i] = new invertedPageTable();
		}
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		swapFile.close();
		// ThreadedKernel.fileSystem.remove(swapFileName);

		super.terminate();
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	public static OpenFile swapFile;

	private String swapFileName = "swap.out";

	public static int freeSwapNumbers = 16;

	public static LinkedList<Integer> freeSwapPages;

	public class invertedPageTable {
		public TranslationEntry entry;
		public VMProcess process;
		public boolean pinned;
		public boolean used;

		public invertedPageTable(){ 
			this.entry = null;
			this.process = null; 
			this.pinned = false;
			this.used = false;
		}
	}

	public static invertedPageTable[] invertedPT;

	public static Lock unpinnedLock;

	public static Condition unpinnedCV;

	public static int pinCount = 0;
	public static Lock pinCountLock;
}
