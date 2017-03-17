
public class testCOW {

	static pgm.page_table tbl;
	static pgm.page_table tbl2;

	public static void main(String[] args) {
		pgm.init();
		tbl = pgm.create();
		tbl2 = pgm.create();

		// virtual address written to, marked as copy-on-write
		// and written to again.
		int virt_addr = 44430;

		put_then_cow_then_put(virt_addr);

		check_val(tbl, tbl2, virt_addr);
	}

	static void put_then_cow_then_put(int addr){
		int virt_page = pgm.getVirtPage(addr);

		System.out.println("Trying to write 15 at address " + addr +"...");
		try_one(addr, 15);

		System.out.println("\nPage " + virt_page + ", which contains the address "
				+ addr + ", has been copied into another page table.");
		pgm.copyPages(tbl, tbl2, virt_page, virt_page + 1);

		System.out.println("\nTrying to write 18 at address " + addr + " on page " 
				+ virt_page +". This page has been copied into another page table...");
		try_one(addr, 18);
	}

	static void check_val(pgm.page_table tbl, pgm.page_table tbl2, int addr) {
		System.out.println("\nReading value in both tables...");

		System.out.print("   First table: ");
		get_val(tbl2, addr, 15);

		System.out.print("   Second table: ");
		get_val(tbl, addr, 18);
	}

	static void get_val(pgm.page_table tbl, int addr, int val) {
		int[] v = new int[1];
		int ret = pgm.get_int(tbl, addr, v);

		if (val == v[0]) {
			System.out.println("Successfully read "+val+ " from table.");
		}
		else {
			System.out.printf("   Got wrong value (%d) reading "+val+" from %d.\n", v[0], addr);
		}
	}

	static void try_one(int addr, int val) {
		int n = val;
		int[] read_n = new int[1];
		int x = pgm.put_int(tbl, addr, n);
		if (x != 4) {
			System.out.printf("   Got error (%d) writing "+val+" to %d.\n", x, addr);
			return;
		}
		x = pgm.get_int(tbl, addr, read_n);
		if (x != 4) {
			System.out.printf("   Got error (%d) reading from %d.\n", x, addr);
			return;
		}
		if (read_n[0] != val) {
			System.out.printf("   Got wrong value (%d) reading "+val+" from %d.\n", read_n[0], addr);
			return;
		}
		System.out.printf("   Successfully wrote and read "+val+" at address %d.\n", addr);
	}
}
