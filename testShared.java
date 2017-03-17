
public class testShared {

	static pgm.page_table tbl;
	static pgm.page_table tbl2;

	public static void main(String[] args) {
		pgm.init();
		tbl = pgm.create();
		tbl2 = pgm.create();
		
		create_and_map(5);		// five shared pages 
		
		int virt_addr = 1234;
		
		// write to tbl and read from tbl2
		check_val_in_both(virt_addr, 15);
	}

	static void create_and_map(int pages){
		int[] temp = new int[pages];
		temp = pgm.createShared(pages);		//five pages
		
		for (int i = 0; i < pages; i++) {
			if (temp[i] < 0) {
				System.out.println("Error. Only "+ i + " shared page(s) created.");
			}
		}
		System.out.println("Successfully created "+ pages+ " shared pages.");
		
		// map shared pages into multipe virtual address spaces
		pgm.mapShared(tbl, temp);		
		pgm.mapShared(tbl2, temp);
		
		int t = 0;
		for (int i = 0; i < 1024; i++) {
			if (tbl.map[i] >= 0 && (tbl.map[i] == tbl2.map[i])) {
				t++;
			}
			if (t == pages) {
				System.out.println("Mapping of "+ pages+ " pages complete in both"
						+ " tables.");
				break;
			}
		}
		
	}
	
	static void check_val_in_both(int addr, int val) {
		int n = val;
		int[] read_n = new int[1];
		
		// put in first page table
		int x = pgm.put_int(tbl, addr, n);
		if (x != 4) {
			System.out.printf("   Got error (%d) writing "+val+" to %d.\n", x, addr);
			return;
		}
		
		// get from second page table
		x = pgm.get_int(tbl2, addr, read_n);
		if (x != 4) {
			System.out.printf("   Got error (%d) reading from %d.\n", x, addr);
			return;
		}
		if (read_n[0] != val) {
			System.out.printf("   Got wrong value (%d) reading "+val+" from %d.\n", read_n[0], addr);
			return;
		}
		System.out.println("   Successfully wrote "+ val +" to the first table and read "
				+ val +" from the second table.");
	}
}
