package lab6_java;

public class pgm {

	//------- PRIVATE IMPLEMENTATION PART ---------------------------------

	// Physical memory is 2^24 (0x1000000 OR 16777216) bytes, with 2^12 (4096) physical pages.

	static private final int physical_page_count = 4096;  // Number of physical pages.
	static private final int physical_page_size = 4096;   // Size of each page.

	static byte[] physmem = new byte[physical_page_count*physical_page_size];  // Storage for physical memory.

	static boolean[] physpagetrack = new boolean[physical_page_count];

	static private final Object lock = new Object();

	// Virtual addresses are 22 bits, 10 bits for page number, 12 bits for offset in page.
	// The functions PAGE(n) and OFFSET(n) extract the page number and the offset within the
	// page from a virtual address.
	private static int PHYS_ADDR(int phys_page, int offset) { return (phys_page << 12) + offset; }

	private static int OFFSET(int n) { return n & 0xFFF; }

	private static int PAGE(int n)  { return (n >> 12) & 0x3FF; }

	// ---- PUBLIC INTERFACT FOR THIS CLASS, EVERYTHING ELSE SHOULD BE PRIVATE ----

	/** An object of type pgm.page_table is a page table for one virtual memory space.
	 *  Maps virtual page numbers to physical page numbers
	 */
	public static class page_table {

		public int[] pageTable = new int[1024];	

		public void addPage(int index, int val) {
			pageTable[index] = val;
		}
	}

	/**
	 * Allocates page from a given page table. Searches physpagetrack[] 
	 * for the next "false" element, which index is returned if found.
	 * If physpagetrack[] contains no "false" elements, -1 is returned.
	 * 
	 * @return index of empty page if exists, -1 if there are no open pages.
	 */
	static public int allocatePage() {

		int page = -1;

		// grab the lock
		synchronized(lock) {		

			// iterates the page allocation tracker
			for (int index = 0; index < physpagetrack.length; index++) {

				if (!physpagetrack[index]) {		// if open page at this index
					physpagetrack[index] = true;	// make page as allocated
					page = index;	
					break;
				} 
			}
		}
		return page;
	}

	/**
	 * Deallocates a given page.
	 * 
	 * @param index 
	 */
	static public void deallocatePage(int index) { 
		
	}

	/** Initialization routine, to be called ONCE before using any other pgm functions. 
	 */
	static public void init() {

		// initialize allocation status of pages to false
		for (boolean b : physpagetrack){
			b = false;
		}
	}	


	/** Creates a new page table and returns (a pointer to) it.  This creates a new
	 * "virtual address space" that is conceptually filled with negative 1's. 
	 */
	static public page_table create() {
		page_table p = new page_table();

		for (int i : p.pageTable) {
			p.pageTable[i] = -1;
		}

		return p;
	}

	/** Discards a page table that will never be used again. Physical memory pages
	 * corresponding to the virtual address space can be reclaimed. 
	 */
	static public void discard(page_table pgtab) {
		int[] pagetable = pgtab.pageTable;

		synchronized(lock) {
			for (int i = 0; i < pagetable.length; i++) {

				// if index in page table is allocated
				if (pagetable[i] != -1) {

					// get physical address
					int phys = PHYS_ADDR(pagetable[i], OFFSET(i));

					// mark physpagetrack[phys/4096] to unallocated status
					physpagetrack[phys/4096] = false;
				}
			}
		}
	}

	/** Copy bytes into paged memory; start_address gives the virtual memory address where
	 * the data will be placed; data_source is an array containing the data to be copied; offset
	 * is the index in the array where the data starts; and byte_count
	 * is the number of bytes to be copied.  Note that only the lower order bits corresponding
	 * to the size of the virtual memory space, are used; high-order bits are discarded.
	 * The return value will be the number of bytes actually copied.  A return value less
	 * than byte_count indicates an error (presumably, out of space in physical memory), or
	 * an illegal parameter such as a bad pointer or a negative byte count.
	 */
	static public int put(page_table pgtab, int start_address, 
			byte[] data_source, int offset, int byte_count) {

		int bytescounted = 0;

		// while loop has to do everything and translate each address seperately 
		while (bytescounted < byte_count) {

			int physpage = pgtab.pageTable[PAGE(start_address + bytescounted)];

			// if page is unallocated
			if (physpage == -1) {
				physpage = allocatePage();

				// if physpage still is -1, there are no free pages.
				// bytescounted is returned in this case.
				if (physpage == -1) {
					return bytescounted;
				}

				// add page to page table
				else {
					pgtab.addPage(PAGE(start_address + bytescounted), physpage);
				}
			}

			int physadd = PHYS_ADDR(physpage, OFFSET(start_address + bytescounted)); 

			// place data into physical memory
			physmem[physadd] = data_source[offset + bytescounted];
			bytescounted++;

		}
		return bytescounted;
	}


	/** Copies bytes from paged memory; start_address is the virtual memory address where
	 * the data starts; data_destination is an array into which the data will be copied; 
	 * offset is the starting index in the array for the copied data; and byte_count 
	 * is the number of bytes to copy.  The return value is the
	 * actual number of bytes copied.  A return value less than byte_count indicates an
	 * error (presumably a bad parameter value).  Note that bytes that have not been
	 * previously written with put will be read as zero.
	 */
	static public int get(page_table pgtab, int start_address,
			byte[] data_destination, int offset, int byte_count) {

		int bytescounted = 0;

		while (bytescounted < byte_count) {

			int physpage = pgtab.pageTable[PAGE(start_address + bytescounted)];

			// if page is unallocated, return bytescounted
			if (physpage == -1) {
				return bytescounted;
			}
			else {
				int physadd = PHYS_ADDR(physpage, OFFSET(start_address + bytescounted));
				
				// place data into destination from memory
				data_destination[offset + bytescounted] = physmem[physadd];
				bytescounted++;
			}
		}
		return bytescounted;
	}


	//---- Convenience methods, used for testing, that SHOULD NOT BE MODIFIED. -----------

	/** A convenience routine for copying an int value into paged memory, at the specified
	 * virtual memory address.  The return value is the number of bytes copied; a value
	 * less than 4 indicates an error.
	 */
	static public int put_int(page_table pgtab, int address, int value) {
		byte[] data = new byte[4];
		data[0] = (byte)value;
		data[1] = (byte)(value >> 8);
		data[2] = (byte)(value >> 16);
		data[3] = (byte)(value >> 24);
		return put(pgtab, address, data, 0, 4);
	}


	/** A convenience routine for fetching an int value from paged memory, at the specified
	 * virtual memory address.  The return value is the number of bytes copied; a value
	 * less than 4 indicates an error.  The actual value is placed into the pvalue array,
	 * which must already exist and be of length 1 or greated.
	 */
	static public int get_int(page_table pgtab, int address, int[] pvalue) {
		byte[] data = new byte[4];
		int ct = get(pgtab, address, data, 0, 4);
		pvalue[0] = data[0]&0xFF | (data[1] << 8)&0xFF00 | (data[2] << 16)&0xFF0000 | (data[3] << 24)&0xFF000000; 
		return ct;
	}

	/** A convenience routine for copying an array of int TO paged memory.  The return
	 * value is the number of bytes copied.  The offset is the starting index in the array
	 * where the incoming data starts.  The count is the number of ints to be copied,
	 * but the return value is the number of bytes copied.  It should be equal to 4*count
	 * if an error does not occur.
	 */
	static public int put_int_array(page_table pgtab, int address, int[] values, int offset, int count) {
		byte[] data = new byte[4*count];
		for (int i = 0; i < count; i++) {
			int value = values[i + offset];
			data[4*i] = (byte)value;
			data[4*i+1] = (byte)(value >> 8);
			data[4*i+2] = (byte)(value >> 16);
			data[4*i+3] = (byte)(value >> 24);
		}
		return put(pgtab, address, data, 0, 4*count);
	}

	/** A convenience routine for copying an array of int FROM paged memory.  The return
	 * value is the number of bytes copied.  The offset is the starting index in the array
	 * where the data will be written.  The count is the number of ints to be copied,
	 * but the return value is the number of bytes copied.  It should be equal to 4*count
	 * if an error does not occur.
	 */
	static public int get_int_array(page_table pgtab, int address, int[] values, int offset, int count) {
		byte[] data = new byte[4*count];
		int result = get(pgtab, address, data, 0, 4*count);
		count = (result+3)/4;
		for (int i = 0; i < count; i++) {
			values[i + offset] = 
					data[4*i]&0xFF | (data[4*i+1] << 8)&0xFF00 | (data[4*i+2] << 16)&0xFF0000 | (data[4*i+3] << 24)&0xFF000000;
		}
		return result;
	}
}