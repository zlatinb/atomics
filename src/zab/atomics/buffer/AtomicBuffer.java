package zab.atomics.buffer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A buffer that is safe to use by multiple threads.  It has maximum capacity of 2MB.
 * @author zlatinb
 */
public class AtomicBuffer {

	private static final int MAX_SIZE = 21;

	private final long claimMask, writtenMask, readMask;

	private final AtomicLong state = new AtomicLong();

	private final byte[] data;

	/**
	 * @param sizePow2 size of the buffer as power of 2
	 */
	public AtomicBuffer(int sizePow2) {
		if (sizePow2 > MAX_SIZE)
			throw new IllegalArgumentException();
		
		data = new byte[1 << sizePow2];

		readMask = (1 << sizePow2) - 1;
		claimMask = readMask << MAX_SIZE;
		writtenMask = claimMask << MAX_SIZE;
	}

	private int getRead(long state) {
		return (int)(state & readMask);
	}

	private int getClaimed(long state) {
		return (int)((state & claimMask) >> MAX_SIZE);
	}

	private int getWritten(long state) {
		return (int)((state & writtenMask) >> (MAX_SIZE << 1));
	}

	/**
	 * put bytes into this buffer.  If the buffer is full, return false
	 * @param src source byte[] to copy data from
	 * @return true if all bytes were successfully put in the buffer
	 */
	public boolean put(byte[] src) {
		// TODO: implement
		return false;
	}

	public int get(byte[] dest) {
		// TODO: implement
		return 0;
	}
	
	public static void main(String []ar) {
	    AtomicBuffer ab = new AtomicBuffer(20);
	    System.out.printf("%x %x %x\n", ab.readMask, ab.claimMask, ab.writtenMask);
	}
}
