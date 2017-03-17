
public class pgm {

	//------- PRIVATE IMPLEMENTATION PART ---------------------------------

	//---------------------- Manage physical memory -----------------------

	// Physical memory is 2^24 (0x1000000 OR 16777216) bytes, with 2^12 (4096) physical pages.

	static private final int physical_page_count = 4096;  // Number of physical pages.
	static private final int physical_page_size = 4096;   // Size of each page.

	static byte[] physmem = new byte[4096*4096];  // Storage for physical memory.

	// Virtual addresses are 22 bits, 10 bits for page number, 12 bits for offset in page.
	// The functions PAGE(n) and OFFSET(n) extract the page number and the offset within the
	// page from a virtual address.

	private static int PHYS_ADDR(int phys_page, int offset) { return (phys_page << 12) + offset; }
	private static int OFFSET(int n) { return n & 0xFFF; }
	private static int PAGE(int n)  { return (n >> 12) & 0x3FF; }

	private static Object memlock = new Object(); // protects core map

	private static class PhysPageInfo {  // A core map entry.
		boolean free;       	// allocation status
		boolean cow;			// True if Copy-on-Write page.
		int cow_copies;			// number of page tables that use this page for COW
		int shared_id;			// index into sharedRegion[] if this page is shared,
								// this is initialized to -1
	}

	public static class SharedRegionInfo {
		boolean free;		// allocation status
		int num_shared;		// number of virtual pages sharing this page
		int phys_page;		// a region's physical page number
		int id;				// index into sharedRegion[] 
	}

	private static PhysPageInfo[] ppinfo = new PhysPageInfo[4096]; // core map
	
	// shared region with size of ten
	private static SharedRegionInfo[] sharedRegion = new SharedRegionInfo[10];	

	static short alloc_phys_page() { // return physical page number or -1 if mem is full
		synchronized(memlock) {
			short val = -1;
			for (short i = 0; i < 4096; i++) {
				if (ppinfo[i].free && !ppinfo[i].cow) {
					ppinfo[i].free = false;
					val = i;
					for (int j = i*4096; j < (i+1)*4096; j++)
						physmem[j] = 0;
					break;
				}
			}
			return val;
		}
	}

	static void free_phys_page(int pagenum) { // Frees one physical page
		synchronized(memlock) {
			
			// cannot free a page that is marked as copy-on-write,
			// or is being shared in another virtual space.
			if (ppinfo[pagenum].cow || (ppinfo[pagenum].shared_id > -1)){
				return;
			}
			
			else {
				ppinfo[pagenum].free = true;
				ppinfo[pagenum].cow = false;			// Not Copy-on-Write
				ppinfo[pagenum].cow_copies = 1;			// one copy to being with
			}
		}
	}

	// Frees all physical pages that are allocated to a page table.
	static void free_all_phys_pages(page_table pgtab) {
		int virt_page;
		synchronized(memlock) {
			for (int i = 0; i < 1024; i++) {
				virt_page = pgtab.map[i];

				// cannot free a page that is marked as copy-on-write,
				// or is being shared in another virtual space.
				if (ppinfo[virt_page].cow || (ppinfo[virt_page].cow_copies >= 2)){
					//do nothing
				}
				else {
					ppinfo[virt_page].free = true;
					ppinfo[virt_page].cow = false;			
					ppinfo[virt_page].cow_copies = 1;
				}
			}
		}
	}

	int free_page_count() { // Returns the number of free physical pages, for testing
		int ct = 0;
		for (int i = 0; i < 4096; i++) {
			if (ppinfo[i].free)
				ct++;
		}
		return ct;
	}

	// ---- PUBLIC INTERFACT FOR THIS CLASS, EVERYTHING ELSE SHOULD BE PRIVATE ----

	private static final int page_magic = 389472399; // ("Magic number" for page tables.)

	/** An object of type pgm.page_table is a page table for one virtual memory space.
	 */
	public static class page_table {
		int magic;  // For testing that a pointer really points to a page table.
		short[] map = new short[1024]; 	// Maps virtaul page numbers to physical page numbers.
		// map[i] is -1 if page i is unallocated.
		boolean[] access = new boolean[1024];	// stores read or read/write access. If false,
		//  read/write allowed. If true, read only.

	}          

	/** Initialization routine, to be called ONCE before using any other pgm functions. 
	 */
	static public void init() {

		// core map initialization
		for (int i = 0; i < 4096; i++) {
			ppinfo[i] = new PhysPageInfo();
			ppinfo[i].free = true;			// Unallocated page
			ppinfo[i].cow = false;			// Not Copy-on-Write
			ppinfo[i].cow_copies = 1;		// one copy to begin with
			ppinfo[i].shared_id = -1;		// not a shared page
		}
	}

	/** Creates a new page table and returns (a pointer to) it.  This creates a new
	 * "virtual address space" that is conceptually filled with zeros. 
	 */
	static public page_table create() {
		page_table tbl = new page_table();
		tbl.magic = page_magic;

		// initialize pages as unallocated and 
		// read/write access allowed
		for (int i = 0; i < 1024; i++) {
			tbl.map[i] = -1;
			tbl.access[i] = false;
		}
		return tbl;
	}

	/** Discards a page table that will never be used again. Physical memory pages
	 * corresponding to the virtual address space can be reclaimed. 
	 */
	static public void discard(page_table pgtab) {
		if (pgtab == null || pgtab.magic != page_magic)
			return;  // pgtab is not a valid page table
		pgtab.magic = 0;  // to avoid discarding it again
		free_all_phys_pages(pgtab);
	}

	/**
	 * This method marks pages as copy-on-write and then copies 
	 * specific pages from one page table to another. The page
	 * table entries where the copies are put are overwritten.
	 * Copied pages must be allocated before they are copied.
	 * 
	 * @param tbl: page table with the pages being copied
	 * @paran tbl2: page table where copied pages are added to
	 * @param start: first index of page in tbl to be marked cow
	 * @param end: last index of page in tbl to be marked cow
	 */
	static void copyPages(page_table tbl, page_table tbl2, int start, int end) {
		for (int virt_page = start; virt_page < end; virt_page++) {

			// if page is allocated in tbl, it is
			// copied into tbl2
			if (tbl.map[virt_page] >= 0) {
				tbl2.map[virt_page] = tbl.map[virt_page];	// overwrite page in tbl2
				synchronized(memlock) {
					ppinfo[tbl.map[virt_page]].cow = true;
					ppinfo[tbl.map[virt_page]].cow_copies++;
				}
			}
			else {
				// do nothing, page is unallocated in this case
			}
		}
	}

	/**
	 * This method is called when a process tries to write to a 
	 * page that is a copy-on-write page. A copy is made of the 
	 * copy-on-write page
	 * 
	 * @param tbl: the page table trying to write to cow page
	 * @param page: virtual page in tbl 
	 * @param addr: physical address 
	 * @return
	 */
	static boolean copyPageData(page_table tbl, int virt_page, int phys_addr) {
		boolean isSuccessful = false;
		byte[] page_data;

		// try to allocate a new page
		short phys_page = alloc_phys_page();
		if (phys_page == -1) {		// out of memory
			return isSuccessful;		
		}
		else {
			page_data = new byte[4096];	

			// get data from page, ret should be 4096
			int ret = get(tbl, phys_addr, page_data, 0, 4096);

			if (ret != 4096) {
				return isSuccessful;
			}
			synchronized(memlock) {
				// before the cow page is thrown out, its 
				// instance variables are updated.
				ppinfo[tbl.map[virt_page]].cow_copies--;		// decrement num_copies

				// if num_copies is 1, no longer copy on write
				if (ppinfo[tbl.map[virt_page]].cow_copies == 1) {
					ppinfo[tbl.map[virt_page]].cow = false;
				}
			}
			tbl.map[virt_page] = phys_page;		// allocated page written to page table

			// add data into new page
			for (int i = 0; i < 4096; i++) {
				int p = PHYS_ADDR(phys_page, i); 
				physmem[p] = page_data[i];
			}
			isSuccessful = true;
		}
		return isSuccessful;
	}

	/** 
	 * Initialization routine, to be called ONCE before using
	 * shared memory functions. 
	 */
	static public void initShared() {
		for (int i = 0; i < 10; i++) {
			sharedRegion[i] = new SharedRegionInfo();
			sharedRegion[i].free = true;
			sharedRegion[i].id = i;
			sharedRegion[i].num_shared = 0;
			sharedRegion[i].phys_page = -1;
		}
	}

	/**
	 * This method creates a shared memory region with 
	 * the number of pages specified in the parameter. 
	 * There is a limit to 10 pages being shared; shared 
	 * pages are stored in sharedRegion[].
	 * 
	 * @param pages: number of pages requested
	 * @return: int array holding physical page numbers, or 
	 * null if the creation of the shared region fails. 
	 */
	static int[] createShared(int pages) {
		int[] phys_pages = null;
		if (sharedRegion[0] == null) {
			initShared();
		}

		// find how many pages are available
		int available_shared_pages = 0;
		for (int i = 0; i < sharedRegion.length; i++) {
			if (sharedRegion[i].free) {
				available_shared_pages++;
			}
		}
		
		// number of shared pages limited to 10.
		if (pages > available_shared_pages) {
			return phys_pages;
		}
		else {
			phys_pages = new int[pages];

			int start = sharedRegion.length - available_shared_pages;
			int counter = 0;

			// fills phys_pages[] with physical pages
			for (int i = start; i < (start + pages); i++) {
				int phys_page = alloc_phys_page();

				if (phys_page == -1) {
					return phys_pages;
				}
				sharedRegion[i].phys_page = phys_page;
				sharedRegion[i].free = false;
				synchronized(memlock) {		// shared_id created
					ppinfo[phys_page].shared_id = i;
				}

				phys_pages[counter] = phys_page;
				counter++;
			}
			available_shared_pages = available_shared_pages - pages;
			return phys_pages;
		}
	}

	/**
	 * This method maps a shared region of memory into a 
	 * virtual address space. 
	 * 
	 * @param tbl: tbl where pages are added
	 * @param phys_pages: data representing physical pages added to page table
	 */
	static void mapShared(page_table tbl, int[] phys_pages) {
		int count = 0;
		for (int i = 0; i < 1024; i++) {
			if (tbl.map[i] == -1) {
				tbl.map[i] = (short)phys_pages[count];
				count++;
			}
			if (count == phys_pages.length){
				break;
			}
		}
	}
	
	/**
	 * This method changes access of pages in tbl beginning at 
	 * tbl[start] and ending at tbl[end]. If access change is 
	 * successful, 1 is returned. If access change is unsuccessful, 
	 * -1 is returned.
	 * 
	 * @param tbl: page table where access is being changed
	 * @param start: starting index in page table to change page acess
	 * @param end: ending index in page table to change page acess
	 * @param rw: true if making read only, false if marking read/write
	 * @return: -1 if unsuccessful, 1 if successful access change
	 */
	static int change_access(page_table tbl, int start, int end, boolean rw) {

		// validate starting and ending indices
		if (start > tbl.access.length || start < 0) {
			return -1;
		}
		else if (end > tbl.access.length || end < 0) {
			return -1;
		}
		else if (end < start) {
			return -1;
		}
		else {
			// if rw == true, access is marked read only.
			if (rw){
				for (int i = start; i < end; i++) {
					tbl.access[i] = true;
				}
				return 1;
			}

			// if rw == false, access is marked read/write
			else {
				for (int i = start; i < end; i++) {
					tbl.access[i] = false;
				}
				return 1;
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
		if (pgtab == null || pgtab.magic != page_magic)
			return -1; // pgtab is not a valid page table
		if (data_source == null)
			return -2; // data source is null
		if (byte_count < 0)
			return -3; // can't request to put a negative number of bytes
		int i;

		for (i = 0; i < byte_count; i++) {
			int virt_page = PAGE(start_address+i);
			int mem_offset = OFFSET(start_address+i);
			short phys_page = pgtab.map[virt_page];

			if (phys_page == -1) { 	// need to allocate a page
				phys_page = alloc_phys_page();
				if (phys_page == -1) {
					break;  	// out of memory
				}
				pgtab.map[virt_page] = phys_page;
			}
			boolean cow = false;

			// check if current page is copy-on-write
			synchronized(memlock) {
				cow = ppinfo[phys_page].cow;
			}

			// if current page is copy on write
			if (cow) {

				boolean isCopied = copyPageData(pgtab, virt_page, 
						PHYS_ADDR(phys_page, mem_offset));

				if (!isCopied) {
					return i;
				}
				phys_page = pgtab.map[virt_page];
			}

			// if current page is read only
			if (pgtab.access[virt_page]) {

				return i;
			}
			int addr = PHYS_ADDR(phys_page, mem_offset);
			physmem[addr] = data_source[offset+i];
		}
		return i;
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
		if (pgtab == null || pgtab.magic != page_magic)
			return -1;  // pgtab is not a valid page table
		if (data_destination == null)
			return -2;  // data destination is null
		if (byte_count < 0)
			return -3;  // can't request to get a negative number of bytes
		int i;
		for (i = 0; i < byte_count; i++) {
			byte val;
			int page = PAGE(start_address+i);
			int mem_offset = OFFSET(start_address+i);
			int phys_page = pgtab.map[page];
			if (phys_page == -1) { // no physical page; use default value zero
				val = 0;
			}
			else {
				int addr = PHYS_ADDR(phys_page, mem_offset);
				val = physmem[addr];
			}
			data_destination[offset + i] = val;
		}
		return i;
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

	/** A convenience routine for getting a virtual page.
	 */
	static public int getVirtPage(int num) {
		int virt = PAGE(num);
		return virt;
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