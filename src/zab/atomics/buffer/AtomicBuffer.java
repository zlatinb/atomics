package zab.atomics.buffer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A buffer that is safe to use by multiple threads.  It has maximum capacity of 2MB.
 * 
 * It is guaranteed wait-free if there is only one writing thread.  If there are more,
 * there can sometimes be a wait.  You can pass a listener object to be notified
 * when waits happen.
 * 
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
	
	private long encode(int read, int claim, int write) {
	    return read | (claim << MAX_SIZE) | (write << (MAX_SIZE <<1));
	}

	/**
	 * Put bytes into this buffer.  If the buffer is full, return false.  This will
	 * spin until the write succeeds.
     * @param src source byte[] to copy data from
     * @return true if there was enough space for all bytes
	 */
	public boolean put(byte[]src) {
	    return put(src,null);
	}
	
	/**
	 * put bytes into this buffer.  If the buffer is full, return false
	 * @param src source byte[] to copy data from
	 * @param listener to notify if the write needs to wait
	 * @return true if there was enough space for all bytes
	 */
	public boolean put(byte[] src, WaitListener listener) {
	    // 1st claim space
	    int startPos;
	    while(true) {
	        final long s = state.get();
	        final int read = getRead(s);
	        final int write = getWritten(s);
	        final int claim = getClaimed(s);
	        
	        assert read <= write && write <= claim;
	        
	        if (claim + write + src.length > data.length )
	            return false;
	        
	        final int newClaim = claim + src.length;
	        final long claimState = encode(read, newClaim, write);
	        if (state.compareAndSet(s,claimState)) {
	            startPos = claim;
	            break;
	        }
	    }
	    
	    // then write in the claimed space
	    while(true) {
	        final long s = state.get();
            final int read = getRead(s);
            final int write = getWritten(s);
            final int claim = getClaimed(s);
            assert read <= write && write <= claim;
            
            // wait until all earlier writers catch up with us
            if (write < startPos) {
                if (listener != null)
                    listener.onWait();
                continue;
            }
            
            final int newWrite = write + src.length;
            System.arraycopy(src,0,data, startPos, src.length);
            final long newState = encode(read,claim,newWrite);
            if (state.compareAndSet(s,newState))
                return true;
	    }
	}

	public int get(byte[] dest) {
	    while(true) {
	        final long s = state.get();
	        final int read = getRead(s);
	        final int write = getWritten(s);
	        final int claim = getClaimed(s);
	        assert read <= write && write <= claim;
	        
	        System.arraycopy(data, read, dest, 0, write - read);
	        
	        long newState;
	        if (write == claim)
	            newState = 0;
	        else
	            newState = encode(write,claim,write);
	        
	        if (state.compareAndSet(s,newState))
	            return write - read;
	    }
	}
}
