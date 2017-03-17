package lab6_java;

import java.util.Random;

/**
 *  Runs some additional single-thread tests on pgm.  
 *  pages from different page tables are interleaved.
 */
public class test1 {

	static class testinfo {
		pgm.page_table pgtab;
		int val;   // starting value stored into virtual address
		int ct;    // number of items stored
		int vmem;  // starting virtual address
	}

	static testinfo info[] = new testinfo[8];

	static int[] big = new int[2 << 18];  // as big as the virtual memory address space

	static void store(testinfo info) {
		int val = info.val;
		int ct = info.ct;
		int addr = info.vmem;
		pgm.page_table tbl = info.pgtab;
		for (int i = 0; i < ct; i++) {
			int x = pgm.put_int(tbl, addr+(4*i), val+i);
			if ( x != 4 ) {
				System.out.printf("Error writing int number %d: %d.\n", i, x);
				return;
			}
		}
		System.out.printf("Store of %d values succeeded.\n", ct);
	}

	static void read_back(testinfo info) {
		int val = info.val;
		int ct = info.ct;
		int addr = info.vmem;
		pgm.page_table tbl = info.pgtab;
		int[] num = new int[1];
		for (int i = 0; i < ct; i++) {
			int x = pgm.get_int(tbl, addr+(4*i), num);
			if ( x != 4 ) {
				System.out.printf("Error getting int number %d: %d.", i, x);
				return;
			}
			if (num[0] != val+i) {
				System.out.printf("Got wrong number; got %d expected %d on read number %d.\n", num, val+i, i);
				return;
			}
		}
		System.out.printf("Read of %d values succeeded.\n", ct);
	}

	public static void main(String[] args) {
		Random random = new Random();
		int seed = 1;
		if (args.length > 0) {
			try {
				seed = Integer.parseInt(args[0]);
			}
			catch (NumberFormatException e) {
			}
		}
		random.setSeed(seed);

		pgm.init();
		//   System.out.printf("%d free pages\n", pgm.free_page_count());

		for (int i = 0; i < 8; i++) {
			info[i] = new testinfo();
			info[i].vmem = Math.abs(random.nextInt()) % 0x3FFFFF;
			info[i].val = random.nextInt();
			info[i].ct = 10000 + Math.abs(random.nextInt()%100000);
			info[i].pgtab = pgm.create();
			//      System.out.printf("Created info with vmem=%d, val=%d, ct=%d, tab=%p\n", info[i].vmem,info[i].val,info[i].ct,info[i].pgtab);
		}

		System.out.printf("\nStoring data using 8 page tables...\n");
		for (int i = 0; i < 8; i++) {
			System.out.printf("   %d. ",i);
			store(info[i]);
		}

		System.out.printf("\nReading back the data...\n");
		for (int i = 0; i < 8; i++) {
			System.out.printf("   %d. ",i);
			read_back(info[i]);
		}

		System.out.printf("\nWriting interleaved data using 8 page tables...\n");
		for (int i = 0; i < 100000; i++) {
			big[i] = i;
		}
		boolean ok = true;
		for (int i = 0; i < 10 && ok; i++) {
			int offset = 10000*i;
			int destination = 10000*i * 4;
			for (int j = 0; j < 8; j++) {
				int x = pgm.put_int_array(info[j].pgtab, destination, big, offset, 10000);
				if (x != 40000) {
					System.out.printf("Error writing block %d for page table %d\n", j, i);
					ok = false;
					break;
				}
			}
		}
		if (ok) {
			System.out.printf("   Data written successfully.  Reading back data...\n");
			for (int i = 0; i < 8 && ok; i++) {
				for (int j = 0; j < 100000; j++)
					big[j] = -1;
				int x = pgm.get_int_array(info[i].pgtab, 0, big, 0, 100000);
				if (x != 400000) {
					System.out.printf("Error reading data for page table %d.\n", i);
					ok = false;
					break;
				}
				for (int j = 0; j < 100000; j++)
					if (big[j] != j) {
						System.out.printf("   Incorrect value (%d expecting %d) read for page table %d.\n", big[j], j, i);
						ok = false;
						break;
					}
			}
			if (ok) {
				System.out.printf("   Successful.\n");
			}
		}
		//   System.out.printf("%d free pages.\n",free_page_count());

		System.out.printf("\nDiscarding half of the page tables...\n");
		for (int i = 4; i < 8; i++)
			pgm.discard(info[i].pgtab);
		//   System.out.printf("%d free pages.\n",free_page_count());

		System.out.printf("\nCreating another page table, then writing and reading lots of data...\n");
		pgm.page_table tab = pgm.create();
		int size = 2 << 18; // number of ints in big
		for (int i = 0; i < size; i++) {
			big[i] = i;
		}
		int x = pgm.put_int_array(tab, 0, big, 0, size);
		if (x != size*4) {
			System.out.printf("   Error writing data.\n");
		}
		else {
			for (int j = 0; j < size; j++)
				big[j] = -1;
			x = pgm.get_int_array(tab, 0, big, 0, size);
			if (x != size*4) {
				System.out.printf("    Error reading data.\n");
			}
			else {
				ok = true;
				for (int j = 0; j < size; j++)
					if (big[j] != j) {
						System.out.printf("   Incorrect value read back.\n");
						ok = false;
						break;
					}
				if (ok)
					System.out.printf("   Successful.\n");
			}
		}
		//   System.out.printf("%d free pages.\n",free_page_count());
	}

}