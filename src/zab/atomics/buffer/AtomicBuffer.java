package zab.atomics.buffer;

import java.util.concurrent.atomic.AtomicLong;

public class AtomicBuffer {

	private static final int MAX_SIZE = 21;

	private final int claimMask, writtenMask, readMask;

	private final AtomicLong state = new AtomicLong();

	private final byte[] data;

	public AtomicBuffer(int sizePow2) {
		if (sizePow2 > MAX_SIZE)
			throw new IllegalArgumentException();
		
		data = new byte[1 << sizePow2];

		readMask = 1 << sizePow2 - 1;
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

	public boolean put(byte[] src) {
		// TODO: implement
		return false;
	}

	public int get(byte[] dest) {
		// TODO: implement
		return 0;
	}
}
