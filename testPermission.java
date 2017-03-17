
public class testPermission {

	static pgm.page_table tbl;

	public static void main(String[] args) {
		pgm.init();
		tbl = pgm.create();
		int virt_page_start = 0;
		int virt_page_end = 10;
		System.out.println("Marking pages "+ virt_page_start
				+ " to "+ virt_page_end +" as read only...");
		markReadOnly(tbl, virt_page_start, virt_page_end);
		
		System.out.println("Trying to write to read only page...");
		try_one(40);
		
		System.out.println("\nTrying to write to read only page...");	// last page that was 			
		try_one(40959);													// marked read only
		
		System.out.println("\nTrying to write to a read/write page..."); // first page after
		try_one(40960);													 // read-only section
	}

	static void markReadOnly(pgm.page_table tbl, int start, int end) {
		int x = pgm.change_access(tbl, start, end, true);
		if (x == 1) {
			System.out.println("Correct return value received. Pages "+ start 
					+ " to "+ end +" marked read only.\n");
		}
		else {
			System.out.println("Incorrect return value received. Access change denied.\n");
		}
	}
	
	static void try_one(int addr) {
		int n = 17;
		int[] read_n = new int[1];
		int x = pgm.put_int(tbl, addr, n);
		if (x != 4) {
			System.out.printf("   Got error (%d) writing 17 to %d.\n", x, addr);
			return;
		}
		x = pgm.get_int(tbl, addr, read_n);
		if (x != 4) {
			System.out.printf("   Got error (%d) reading from %d.\n", x, addr);
			return;
		}
		if (read_n[0] != 17) {
			System.out.printf("   Got wrong value (%d) reading 17 from %d.\n", read_n[0], addr);
			return;
		}
		System.out.printf("   Successfully wrote and read 17 at address %d.\n", addr);
	}

}
