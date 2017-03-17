/**
 *  Tests that pgm.c works when multiple threads are continually reading
 *  writing memory. 
 */
public class test2 {

	static final int MEM_SIZE = 1 << 22;  // virtual memory size

	static class TestThread extends Thread {
		// input data for thread
		int id;
		int size;  // amount of memory to allocate, fill with data and read
		int table_ct;  // number of page tables to create
		int write_ct;  // number of times to fill memory and read it back

		// output data from thread
		int page_faults;  // number of times that writing an int failed
		int read_faults;  // number of times that reading an int failed
		int bad_reads;   // number of times that a read got a bad value

		public void run() {
			page_faults = 0;
			read_faults = 0;
			bad_reads = 0;
			int[] read = new int[1];
			for (int k = 0; k < table_ct; k++) {
				pgm.page_table mem = pgm.create();
				for (int j = 0; j < write_ct; j++) {
					int x = id*10000 + k * 1000 + j * 100;
					int top = size;
					for (int i = 0; i < top; i += 4) {
						if ( pgm.put_int(mem, i, x+i)  != 4 ) {
							page_faults++;
							top = i;
							break;
						}
					}
					for (int i = 0; i < top; i += 4) {
						if ( pgm.get_int(mem, i, read)  != 4 ) {
							read_faults++;
							break;
						}
						else if ( read[0] != x+i) {
							bad_reads++;
						}
					}
				}
				pgm.discard(mem);
			}
		}
	}

	static void run_test( int thread_count, int size, int table_ct, int write_ct ) {
		TestThread[] thread = new TestThread[thread_count];
		for (int i = 0; i < thread_count; i++) {
			thread[i] = new TestThread();
			thread[i].id = i;
			thread[i].size = size;
			thread[i].table_ct = table_ct;
			thread[i].write_ct = write_ct;
		}
		for (int i = 0; i < thread_count; i++) {
			thread[i].start();
		}
		System.out.printf("\n   Started %d threads.\n\n", thread_count);
		for (int i = 0; i < thread_count; i++) {
			try {
				thread[i].join();
			}
			catch (InterruptedException e) {				
			}
			System.out.printf("   Thread %2d finished with %2d page faults, %2d read faults, %2d bad reads\n",
					i, thread[i].page_faults, thread[i].read_faults, thread[i].bad_reads);
		}
	}

	public static void main(String[] args) {
		pgm.init();

		System.out.printf("\nNote: in these tests, a 'page fault' means that a put request did not write\n");
		System.out.printf("as many bytes as requested.  A 'read fault' means that a get request did not\n");
		System.out.printf("read as many bytes as requested.  A 'bad read' means that a get read the right\n");
		System.out.printf("numbers of bytes, but the value that was read was not the same as what was written.\n");

		System.out.printf("\n\n\nRunning 4 threads, each writing all of their memory 5 times with each\n");
		System.out.printf("of 5 page tables.  Since this can fill but never overfill physical memory,\n");
		System.out.printf("there should be NO page faults, read faults, or bad reads.\n\n");
		run_test( 4, MEM_SIZE, 5, 5 );

		System.out.printf("\n\n\nNow run 16 threads, each writing 1/4 of their memory 5 times with each\n");
		System.out.printf("of 10 page tables.  Since this can fill but never overfill physical memory,\n");
		System.out.printf("there should again be NO page faults, read faults, or bad reads.\n\n");
		run_test( 16, MEM_SIZE/4, 5, 10 );

		System.out.printf("\n\n\nNow repeat with 17 threads.  Since this can overfill overfill physical\n");
		System.out.printf("memory, there WILL LIKELY be page faults, but there should still be NO\n");
		System.out.printf("read faults, or bad reads.\n\n");
		run_test( 17, MEM_SIZE/4, 5, 10 );

		System.out.printf("\n\n");
	}

}
