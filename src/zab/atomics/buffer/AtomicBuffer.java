package zab.atomics.buffer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A buffer that is safe to use by multiple threads.  It has maximum capacity of 2^21 = 2MB.
 * 
 * Reading is guaranteed wait-free.  Writing is guaranteed wait-free if there is 
 * only one writing thread.  Writers may sometimes wait but on only each other, 
 * they do not wait on readers.
 * 
 * COSTS:
 * Writing costs at least two CAS instructions, unless the buffer is full.
 * Reading costs at least one CAS instruction, unless the buffer is empty.
 * 
 * You can pass a listener object to be notified when waits happen.
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
	 * Put bytes into this buffer.  This will spin until at least 1 byte is written
	 * unless the buffer is full.
	 * 
     * @param src source byte[] to copy data from
     * @return how much was written, 0 if buffer was full.
	 */
	public int put(byte[]src) {
	    return put(src,null);
	}
	
	/**
	 * Put bytes into this buffer.  If the buffer is full, return 0.
	 * @param src source byte[] to copy data from
	 * @param listener to notify if the write needs to wait for another write
	 * @return how much was written, 0 if buffer was full.
	 */
	public int put(byte[] src, WaitListener listener) {
	    // 1st claim space
	    int startPos, len;
	    while(true) {
	        final long s = state.get();
	        final int read = getRead(s);
	        final int write = getWritten(s);
	        final int claim = getClaimed(s);
	        
	        assert read <= write && write <= claim;
	        
	        // full, can't write
	        if (claim == data.length)
	            return 0;
	        
	        final int newClaim = Math.min(data.length, claim + src.length);
	        final long claimState = encode(read, newClaim, write);
	        if (state.compareAndSet(s,claimState)) {
	            startPos = claim;
	            len = newClaim - startPos;
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
            
            final int newWrite = write + len;
            System.arraycopy(src,0,data, startPos, len);
            final long newState = encode(read,claim,newWrite);
            if (state.compareAndSet(s,newState))
                return len;
	    }
	}

	public int get(byte[] dest) {
	    while(true) {
	        final long s = state.get();
	        final int read = getRead(s);
	        final int write = getWritten(s);
	        final int claim = getClaimed(s);
	        assert read <= write && write <= claim;
	        
	        if (read == write)
	            return 0;
	        
	        System.arraycopy(data, read, dest, 0, write - read);
	        
	        long newState;
	        if (write == claim)
	            newState = 0; // read everything in the buffer
	        else
	            newState = encode(write,claim,write);
	        
	        if (state.compareAndSet(s,newState))
	            return write - read;
	    }
	}
}
