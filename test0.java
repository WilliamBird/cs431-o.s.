
public class test0 {

	static pgm.page_table tab;
	static byte[] ch = new byte[10000];
	static int[] big = new int[0x100000]; // size is same as virtual mem address space.

	static void try_one(int addr) {
		int n = 17;
		int[] read_n = new int[1];
		int x = pgm.put_int(tab, addr, n);
		if (x != 4) {
			System.out.printf("   Got error (%d) writing 17 to %d.\n", x, addr);
			return;
		}
		x = pgm.get_int(tab, addr, read_n);
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

	static void try_array(int addr) {
		for (int i = 0; i < 10000; i++) {
			ch[i] = (byte)i;
		}
		int x = pgm.put(tab, addr, ch, 0, 10000);
		if (x != 10000) {
			System.out.printf("   Got error (%d) writing array to %d.\n", x, addr);
			return;
		}
		x = pgm.get(tab, addr, ch, 0, 10000);
		if (x != 10000) {
			System.out.printf("   Got error (%d) reading from %d.\n", x, addr);
			return;
		}
		for (int i = 0; i < 10000; i++) {
			if (ch[i] != (byte)i) {
				System.out.printf("   Got wrong value (%d) reading array element %d from %d.\n", ch[i], i, addr);
				return;
			}
		}
		System.out.printf("   Successfully wrote and read array at address %d.\n", addr);
	}

	static void try_all_vmem() {
		for (int i = 0; i < 0x100000; i++) {
			big[i] = i + 12345;
		}
		int c = pgm.put_int_array(tab, 0, big, 0, 0x100000);
		if (c != 0x400000) {
			System.out.printf("   Error writing data, %d bytes written.\n", c);
			return;
		}
		c = pgm.get_int_array(tab, 0, big, 0, 0x100000);
		if (c != 0x400000) {
			System.out.printf("   Error reading data, %d bytes read.\n", c);
			return;
		}
		for (int i = 0; i < 0x100000; i++) {
			if (big[i] != i + 12345) {
				System.out.printf("    Error, got the woung value for integer number %d.\n", i);
				return;
			}
		}
		System.out.printf("   Successful.\n");
	}

	static void try_all_physmem() {
		for (int i = 0; i < 0x100000; i++) {
			big[i] = i + 12345;
		}
		for (int i = 0; i < 3; i++) { 
			// fill 3 more copies of virtual memory, except for 1 page each.
			// which should fill physical memory except for 3 pages
			pgm.page_table foo = pgm.create();
			int x = pgm.put_int_array(foo, 0, big, 0, (0x400000 - 5000)/4);
			if (x != 0x400000 - 5000) {
				System.out.printf("   Error after writing %d bytes.\n", i*(0x400000-5000) + x);
				return;
			}
		}
		//   System.out.printf("Free pages %d\n", free_page_count());  // should be 3
		System.out.printf("   Wrote enough data to almost fill physical memory.\n");
		System.out.printf("   Attempting to write more than should fit in remaining memory...\n");
		pgm.page_table bar = pgm.create();
		int x = pgm.put_int_array(bar, 0, big, 0, 20000/4);  // should'nt fit, but 3*4096 should
		if (x < 3*4096) {
			System.out.printf("   Memory filled too soon.\n");
			return;
		}
		if (x > 3*4096) {
			System.out.printf("   Didn't get expected error when writing too much data.\n");
			return;
		}
		System.out.printf("   Wrote expected amount of data before failure.\n");
		System.out.printf("   Successful.\n");
	}

	public static void main(String[] args) {
		pgm.init();
		//   System.out.printf("Free pages %d\n", pgm.free_page_count()); // free_page_count() is not a required function
		tab = pgm.create();

		System.out.printf("\nTry writing and reading 17s to various addresses...\n");
		try_one(42);
		try_one(384888);
		try_one(23894788);

		System.out.printf("\nTry writing and reading array of 10000 chars to various addresses...\n");
		try_array(42);
		try_array(384888);
		try_array(23894788);

		System.out.printf("\nTry to read and write all of virtual memory...\n");
		try_all_vmem();
		//   System.out.printf("Free pages %d\n", pgm.free_page_count());

		System.out.printf("\nTry filling physical memory, then try to write more...\n");
		try_all_physmem();

	}

}
