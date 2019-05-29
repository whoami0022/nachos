package nachos.vm;
import java.awt.SystemTray;
import java.util.*;

import jdk.nashorn.internal.ir.Flags;
import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		// System.out.println("# pages: " + numPages);
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
		}	
	
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}
	
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		
		byte[] memory = Machine.processor().getMemory();
		if (vaddr < 0 || vaddr >= pageTable.length * (pageSize-1)) {
			return 0;
		}
		
		int amount = 0;

		while (amount < length) {
			int vpn = Processor.pageFromAddress(vaddr + amount);
			if (pageTable[vpn].valid == false) {
				handlePageFault(vpn);
			}
			int offsetCur = Processor.offsetFromAddress(vaddr + amount);
			int paddr = pageTable[vpn].ppn * pageSize + offsetCur;
			pageTable[vpn].used = true;
			
			if (paddr < 0 || paddr >= memory.length){
				Lib.assertNotReached("Invalid paddr in readVirtualMemory!");
				return amount;
			}

			// bytes that can still read from this page
			int rest = Math.min(Processor.pageSize - offsetCur, length - amount);
			int ppn = pageTable[vpn].ppn;
			VMKernel.invertedPT[ppn].pinned = true;
			VMKernel.pinCountLock.acquire();
			VMKernel.pinCount ++;
			VMKernel.unpinnedLock.acquire();
			System.arraycopy(memory, paddr, data, offset + amount, rest);
			VMKernel.invertedPT[ppn].pinned = false;
			VMKernel.pinCount --;
			VMKernel.pinCountLock.release();
			VMKernel.unpinnedCV.wake();
			VMKernel.unpinnedLock.release();
			amount += rest;
		}
		
		return amount;
	}
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {

		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		byte[] memory = Machine.processor().getMemory();
		if (vaddr < 0 || vaddr >= pageTable.length * (pageSize-1)) {
			return 0;
		}
		    
		// virtual 
		int amount = 0;

		while (amount < length) {
			int vpn = Processor.pageFromAddress(vaddr + amount);

			// System.out.println("unique vpn : " + vpn);
			// System.out.println("writeVirtualMemory ppn : " + pageTable[vpn].ppn);
			// System.out.println("writeVirtualMemory vpn : " + vpn);
			if (pageTable[vpn].valid == false) {
				handlePageFault(vpn);
			}
			pageTable[vpn].used = true; 
			pageTable[vpn].dirty = true; 
			int offsetCur = Processor.offsetFromAddress(vaddr + amount);
			int paddr = pageTable[vpn].ppn * pageSize + offsetCur;
			// System.out.println("-------test-------");
			// System.out.println(paddr);
			// System.out.println(pageTable[vpn].ppn);
			// System.out.println("-------test-------");
			if (paddr < 0 || paddr >= memory.length){
				Lib.assertNotReached("Invalid paddr in writeVirtualMemory!");
	
				return amount;
			}
			int rest = Math.min(Processor.pageSize - offsetCur, length - amount);
			int ppn = pageTable[vpn].ppn;
			VMKernel.invertedPT[ppn].pinned = true;
			VMKernel.pinCountLock.acquire();
			VMKernel.pinCount ++;
			VMKernel.unpinnedLock.acquire();
			System.arraycopy(data, offset + amount, memory, paddr, rest);
			VMKernel.invertedPT[ppn].pinned = false;
			VMKernel.pinCount --;
			VMKernel.pinCountLock.release();
			VMKernel.unpinnedCV.wake();
			VMKernel.unpinnedLock.release();
			amount += rest;
		}
		

		return amount;
	}

	private void SetInvertedPT(TranslationEntry entry){
		int ppn = entry.ppn;
		VMKernel.invertedPT[ppn].entry = entry;
		VMKernel.invertedPT[ppn].process = this; 
		VMKernel.invertedPT[ppn].pinned = false;
	}
	
	private void loadNewPage(TranslationEntry entry, int freePage){
		
		if(entry.valid == false) {
			entry.ppn = freePage;
			for (int s = 0; s < coff.getNumSections(); s++) {
				CoffSection section = coff.getSection(s);
				int firstVPN = section.getFirstVPN();
				if (firstVPN <= entry.vpn && entry.vpn < firstVPN + section.getLength()) {
					entry.readOnly = section.isReadOnly();
					section.loadPage(entry.vpn - firstVPN, freePage);
					break;
				}
			}
			entry.valid = true;
		}
		if(!entry.readOnly){
			entry.dirty = true;
		}
		entry.used = true;
		SetInvertedPT(entry);
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handlePageFault(int vpn) {
		
		if (vpn < 0 || vpn >= pageTable.length){
			Lib.assertNotReached("Invalid vpn!");
			return;
		}
		TranslationEntry entry = pageTable[vpn];
		if(UserKernel.freePage.size() != 0){
			int free = UserKernel.freePage.pollFirst();
			// swap in 
			if(entry.dirty == true){
				entry.ppn = free;
				// System.out.println("swap path!" );
				byte[] buffer = new byte[pageSize];
				VMKernel.swapFile.read(entry.vpn*pageSize, buffer, 0, pageSize);
				VMKernel.freeSwapPages.add(entry.vpn);
				entry.vpn = vpn;
				entry.used = true;
				entry.valid = true;
				if(writeVirtualMemory(vpn * pageSize, buffer, 0, pageSize) != pageSize){
					Lib.assertNotReached("Should not reach here!");
				}
				SetInvertedPT(entry);
			}
			else if (entry.dirty == false && vpn < numPages - stackPages - 1) {
				// free page is not empty
				// System.out.println("coff path!" );
				loadNewPage(entry, free);
			} else if (entry.dirty == false && vpn >= numPages - stackPages - 1) {
				// System.out.println("stack path!" );
				entry.ppn = free;
				entry.valid = true;
				byte[] buffer = new byte[pageSize];
				int size = writeVirtualMemory(vpn * pageSize, buffer, 0, pageSize);
				if(!entry.readOnly){
					entry.dirty = true;
				}
				entry.used = true;
				SetInvertedPT(entry);
			}

		}else{
			// System.out.println("free page is empty");
			// Select a victim for replacement use clock algorithm 
			
			while(VMKernel.invertedPT[victim].entry.used == true){
				if(VMKernel.invertedPT[victim].pinned != true){
		
					VMKernel.invertedPT[victim].entry.used = false;
				} 
				if(VMKernel.pinCount == Machine.processor().getNumPhysPages()){
					VMKernel.unpinnedCV.sleep();
				}
				
				// System.out.println(victim + "  " + VMKernel.invertedPT[victim].pinned);
				victim = (victim + 1) % VMKernel.invertedPT.length;
			}
			// System.out.println("victim: " + victim);
			int phi_victim = victim;
			victim = (victim + 1) % VMKernel.invertedPT.length;

			int vir_victim = VMKernel.invertedPT[phi_victim].entry.vpn;
			// check if swap
			// System.out.println("dirty?: " + pageTable[vir_victim].dirty);
			if (pageTable[vir_victim].dirty == true) {
				pageTable[vir_victim].used = true;
			
				// 2. swap out; 
				if(VMKernel.freeSwapPages.size() <= 0){
					// init swap page numbers is 16
					// if swap pages is not enough
					// add more swap pages
					// System.out.println("free swap page is not enough, expanding now");
					int n = VMKernel.freeSwapNumbers;
					for(int i = n; i < n + 16; i++){
						VMKernel.freeSwapPages.add(i);
					}
					VMKernel.freeSwapNumbers = n + 16;
					// System.out.println("# free swap pages: " + VMKernel.freeSwapPages.size());
					// System.out.println("# total swap pages: " + VMKernel.freeSwapNumbers);
				}
				int spn = VMKernel.freeSwapPages.pollFirst();
				byte[] buffer = new byte[pageSize];

				if(readVirtualMemory(pageTable[vir_victim].vpn * pageSize, buffer, 0, pageSize) == pageSize){
					// wirte to disk
					VMKernel.swapFile.write(spn*pageSize, buffer, 0, pageSize);
					// System.out.println("swap success!");
				}else{
					Lib.assertNotReached("Should not reach here!");
				}
				// use TE.vpn point to swap page number in swap file
				pageTable[vir_victim].vpn = spn;
				
			}
			// Invalidate PTE and TLB entry of the victim page
			// NOTE: do nothing if ReadOnly
			
			pageTable[vir_victim].valid = false;
			UserKernel.freePage.add(pageTable[vir_victim].ppn);
			// System.out.println("swap ppn : " + pageTable[vir_victim].ppn);
			// System.out.println("ppn " + pageTable[vir_victim].ppn);

			handlePageFault(vpn);
		} 

		
	}
	
	public void handleException(int cause) {
		Processor processor = Machine.processor();
		int badVAddr = processor.readRegister(Processor.regBadVAddr);
		int vpn = Processor.pageFromAddress(badVAddr);
		switch (cause) {
		case Processor.exceptionPageFault:
			handlePageFault(vpn);
			break;
		default:
			// System.out.println("default cause: " + cause);
			super.handleException(cause);
			break;
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	private static int tmp = 0;

	private static int victim = 0;
}
