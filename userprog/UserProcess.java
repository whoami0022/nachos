package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.*;

import java.nio.ByteBuffer;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		// int numPhysPages = Machine.processor().getNumPhysPages();
		// pageTable = new TranslationEntry[numPhysPages];
		// for (int i = 0; i < numPhysPages; i++)
		// 	pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		UserKernel.pidLock.acquire();
		this.PID = UserKernel.globalPID++;
		UserKernel.pidLock.release();

		UserKernel.numproLock.acquire();
		UserKernel.numProcess ++;
		UserKernel.numproLock.release();

		// init stdin and out
		descriptorMap.put(0, UserKernel.console.openForReading());
		descriptorMap.put(1, UserKernel.console.openForWriting());

		UserKernel.allProcesses.put(PID, this);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	    String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;
		
		KThread thread = new UThread(this).setName(name);
		thread.fork();

		UserKernel.threadMap.put(this.PID, thread);

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 

	 * @param vaddr: the first byte of virtual memory to read.
	 * @param data: the array where the data will be stored.
	 * @param offset: the first byte to write in the array.
	 * @param length: the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	
	/**
	 * Processor.pageFromAddress: Extract the page number component from 
	 * 							a 32-bit address.
	 * Processor.offsetFromAddress: Extract the offset component from 
	 * 							an address.	
	 * Processor.makeAddress(int page, int offset): 
	 * 				Concatenate a page number and an offset into an address.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);
		
		byte[] memory = Machine.processor().getMemory();
		if (vaddr < 0 || vaddr >= pageTable.length * (pageSize-1))   
		    return 0;

		int amount = 0;
		int done = 0;
		int left = 0;
		while (amount < length) {
			
			// pageTable[vpn].ppn: 0x2000
			int vpn = Processor.pageFromAddress(vaddr + amount);
			int offsetCur = Processor.offsetFromAddress(vaddr + amount);
			// System.out.println("vpn is : " + vpn);
			// System.out.println("offset is : " + offsetCur);
			
			int newVaddr = pageTable[vpn].ppn * pageSize + offsetCur;
			//int newVaddr = Processor.makeAddress(pageTable[vpn].ppn, offsetCur);
			if (newVaddr < 0 || newVaddr >= memory.length)
				return amount;
			// pageSize = 0x400;
			// bytes that can still read from this page
			int rest = Math.min(Processor.pageSize - offsetCur, length - amount);
			System.arraycopy(memory, newVaddr, data, offset + amount, rest);
			amount += rest;
		}

		// for now, just assume that virtual addresses equal physical addresses
		// if (vaddr < 0 || vaddr >= memory.length)
		// 	return 0;

		// int amount = Math.min(length, memory.length - vaddr);
		// System.arraycopy(memory, vaddr, data, offset, amount);
		// System.out.println("final amount : " + amount);

		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {

		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		if (vaddr < 0 || vaddr >= pageTable.length * (pageSize-1))   
		    return 0;
		//virtual 
		int amount = 0;
		int done = 0;
		int left = 0;
	
		while (amount < length) {
			// pageTable[vpn].ppn: 0x2000
			int vpn = Processor.pageFromAddress(vaddr + amount);
			int offsetCur = Processor.offsetFromAddress(vaddr + amount);
			int newVaddr = pageTable[vpn].ppn * pageSize + offsetCur;
			//int newVaddr = Processor.makeAddress(pageTable[vpn].ppn, offsetCur);
			if (newVaddr < 0 || newVaddr >= memory.length)
				return amount;
			// pageSize = 0x400;
			// bytes that can still read from this page
			int rest = Math.min(Processor.pageSize - offsetCur, length - amount);
			System.arraycopy(data, offset + amount, memory, newVaddr, rest);
			amount += rest;
		}
		
		// for now, just assume that virtual addresses equal physical addresses
		// if (vaddr < 0 || vaddr >= memory.length)
		// 	return 0;

		// int amount = Math.min(length, memory.length - vaddr);
		// System.arraycopy(data, offset, memory, vaddr, amount);
		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		// numPage: The number of contiguous pages occupied by the program
		// getNumPhysPages(): the number of pages of physical memory
		// System.out.println("numpages : " + numPages);
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		
		//********************add things***************
		UserKernel.lock.acquire();

		if (numPages > UserKernel.freePage.size()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			UserKernel.lock.release();
			return false;
		} 
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++)
			pageTable[i] = new TranslationEntry(i, UserKernel.freePage.pollFirst(),
								 true, false, false, false);
		
		
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			
			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				// for now, just assume virtual addresses=physical addresses
				// section.loadPage(i, vpn);
				/**
				 * The field TranslationEntry.readOnly should be set to true 
				 * if the page is coming from a COFF section which is marked 
				 * as read-only
				 */
				pageTable[vpn].readOnly = section.isReadOnly();
				section.loadPage(i, pageTable[vpn].ppn);
				// System.out.println(i + " virtual page : ppn" + pageTable[vpn].ppn);
			}
		}
		UserKernel.lock.release();

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		/**
		 * All of a process's memory should be freed on exit 
		 * (whether it exits normally, via the syscall exit, 
		 * or abnormally, due to an illegal operation). 
		 * As a result, its physical pages can be subsequently 
		 * reused by future processes.
		 */
		UserKernel.lock.acquire();
		// System.out.println("Before unload : " + UserKernel.freePage.size());
		for (int i = 0; i < numPages; i++) {
			UserKernel.freePage.add(pageTable[i].ppn);
		}
		// System.out.println("After unload : " + UserKernel.freePage.size());
		UserKernel.lock.release();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		System.out.println("Call Halt!");
		
		if(this.PID != 0){
			return -1;
		}

		for (Integer key : UserKernel.allProcesses.keySet()) {
			System.out.println(key);
			UserKernel.allProcesses.get(key).unloadSections();
		}
		Machine.halt();
		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	    // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		// 1. close file 
		for(int i = 0; i < 16; i++){
			OpenFile of = descriptorMap.get(i);
			if(of != null)
				of.close();
		}

		// 2. save status
		if(parentProcess != null){
			parentProcess.childrenId2Status.put(PID, status);
		}

		// 3. free memory 
		unloadSections();
		coff.close();

		// 4. exit
		// child process can continue to execute while
		// the parent may finish early and exit
		UserKernel.numproLock.acquire();
		UserKernel.numProcess --;
		if(UserKernel.numProcess == 0){
			Kernel.kernel.terminate();
		}
		UserKernel.numproLock.release();

		// close thread
		KThread.currentThread().finish();

		// should not return
		Lib.assertNotReached("Exit() did not exit process!");
		return 0;
	}

	/**
	 * Handle the exec() system call.
	 * int exec(char *file, int argc, char *argv[])
	 */
	private int handleExec(int addrFile, int numArgc, int argvAddr){
		// 1. get coff file and check
		String file = readVirtualMemoryString(addrFile, 256);
		if(file == null) return -1;

		// 2. check numArgc, get argv and check 
		if(numArgc < 0) return -1;
		String[] argv = new String[numArgc];
		for(int i = 0; i < numArgc; i++){
			byte[] b = new byte[4];
			int l = readVirtualMemory(argvAddr, b);
			int addr = b[0] & 0xFF | (b[1] & 0xFF) << 8 |
					  (b[2] & 0xFF) << 16 | (b[3] & 0xFF) << 24;

			argv[i] = readVirtualMemoryString(addr, 256);
			if(argv[i] == null) return -1;
			argvAddr += 4;

			// System.out.println(argv[i]);
		}

		// 3. create new process
		UserProcess childProcess = UserProcess.newUserProcess();
		if (!childProcess.execute(file, argv)) return -1;

		// id 
		childrenId2Status.put(childProcess.PID, 0);
		childProcess.parentProcess = this;
		
		// System.out.println("Child PID: " + childProcess.PID);

		return childProcess.PID;
	}

	/**
	 * Handle the create() system call.
	 * int join(int processID, int *status)
	 */
	 private int handleJoin(int processID, int addrStatus){
		// check if child 
		if (childrenId2Status.get(processID) == null) return -1;

		UserKernel.threadMap.get(processID).join();

		// resume
		int status = childrenId2Status.get(processID);
		
		if(status == -10000){
			return 0;
		}

		byte[] b = new byte[]  {(byte)((status >> 0) & 0xff), 
								(byte)((status >> 8) & 0xff),
								(byte)((status >> 16) & 0xff),
								(byte)((status >> 24) & 0xff)};

		int writeSize = writeVirtualMemory(addrStatus, b);
		childrenId2Status.remove(processID);

		return 1;
	 }


	/**
	 * Handle the create() system call.
	 */
	private int handleCreate(int addrName) {
		// get the name of file
		String name = readVirtualMemoryString(addrName, 256);
		if (name == null){
				return -1;
		}
		OpenFile of = ThreadedKernel.fileSystem.open(name, true);
		if(of == null){
			// error
			return -1;
		}
		int fd = nextDescriptor();
		descriptorMap.put(fd, of);
		filePosition.put(fd, 0);
		return fd;
	}

	/**
	 * Handle the open() system call.
	 */
	private int handleOpen(int addrName) {
		// get the name of file
		String name = readVirtualMemoryString(addrName, 256);
			if (name == null){
				return -1;
		}

		OpenFile of = ThreadedKernel.fileSystem.open(name, false);
		if(of == null){
			// error
			return -1;
		}
		int fd = nextDescriptor();
		descriptorMap.put(fd, of);
		filePosition.put(fd, 0);
		return fd;
	}

	/**
	 * Handle the read() system call.
	 */
	private int handleRead(int fd, int addrBuf, int size){
		// checks arguments for correctness
		OpenFile of = descriptorMap.get(fd);
		// invalid file descriptor
		if (of == null) return -1;
		if (size < 0) return -1;

		int totalSize = 0;	

		while(size > 0){
			byte[] buffer;
			if(size / maxBufferSize >= 1){
				buffer = new byte[maxBufferSize];
			}
			else
				buffer = new byte[size];

			int readSize = 0;
			if(fd <= 1){
				readSize = of.read(buffer, 0, buffer.length);
			}else{
				readSize = of.read(filePosition.get(fd), buffer, 0, buffer.length);
				filePosition.put(fd, filePosition.get(fd) + readSize);
			} 
			totalSize += writeVirtualMemory(addrBuf, buffer, 0, readSize);
			size -= maxBufferSize;
			addrBuf += maxBufferSize;

			// System.out.println("test1: " + size);
			// System.out.println("test2: " + totalSize);
			// System.out.println("test3: " + readSize);
		}
		return totalSize;
	}

	/**
	 * Handle the write() system call.
	 * write(int fileDescriptor, void *buffer, int count);
	 */
	private int handleWrite(int fd, int addrBuf, int size){
		// checks arguments for correctness
		OpenFile of = descriptorMap.get(fd);
		// invalid file descriptor
		if (of == null) return -1;
		if (size < 0) return -1;

		int totalSize = 0;		
		while(size > 0){
			byte[] buffer;
			if(size >= maxBufferSize){
				buffer = new byte[maxBufferSize];
			}
			else
				buffer = new byte[size];

			int bufSize = readVirtualMemory(addrBuf, buffer);
			if(bufSize == 0){
				return -1;
			}
			// different implementation for write
			int writeSize = 0;
			if(fd <= 1){
				writeSize = of.write(buffer, 0, bufSize);
			}else{
				writeSize = of.write(filePosition.get(fd), buffer, 0, bufSize);
				filePosition.put(fd, filePosition.get(fd) + writeSize);
			}
			totalSize += writeSize;
			size -= maxBufferSize;
			addrBuf += maxBufferSize;
		}
		return totalSize;
	}


	/**
	 * Handle the close() system call.
	 */
	private int handleClose(int fd){
		// checks arguments for correctness
		OpenFile of = descriptorMap.get(fd);
		if (of == null){
			// invalid file descriptor
			return -1;
		}
		of.close();
		// free descriptor
		descriptorMap.remove(fd);
		filePosition.remove(fd);
		return 0;
	}

	/**
	 * Handle the unlink() system call.
	 */
	private int handleUnlink(int addrName){
		String name = readVirtualMemoryString(addrName, 256);
		if (name == null){
			return -1;
		}

		// whether take care of the openning file?
		// OpenFile of = descriptorMap.get(fd);
		// if (of == null){
		// 	// invalid file descriptor
		// 	return -1;
		// }
		// // close first in case of opening
		// of.close();
		// // free descriptor
		// descriptorMap.remove(name);

		if(ThreadedKernel.fileSystem.remove(name)){
			return 0;
		}else{
			return -1;
		}
	}


	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	private static final int maxBufferSize = 1024;
	
	int nextDescriptor(){
		int des = -1;
		for(int i = 2; i < 16; i++){
			if(descriptorMap.get(i) == null){
				des = i;
				break;
			}
		}
		return des;
	}

	private Map<Integer, OpenFile> descriptorMap = new HashMap();
	private Map<Integer, Integer> filePosition = new HashMap();

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);	
		case syscallJoin:
			return handleJoin(a0, a1);	

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			// abnormal exit
			handleExit(-10000);
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	public int PID;

	private UserProcess parentProcess;

	public Map<Integer, Integer> childrenId2Status = new HashMap<>();
}
