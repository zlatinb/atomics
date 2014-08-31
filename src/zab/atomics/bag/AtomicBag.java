package zab.atomics.bag;

import java.util.concurrent.atomic.AtomicLong;

/**
 * An atomic, lock-free and wait-free storage of items.  It can store up to 32 items.
 * 
 * The bag keeps references to the stored items after they are removed.  Use it
 * only for objects you do not expect to be garbage-collected.
 *  
 * @author zlatinb
 *
 * @param <T> type of the items stored
 */
public class AtomicBag<T> {
    
    private static final int FREE = 0;
    private static final int CLAIM = 1;
    private static final int FULL = 2;
    
    private final Object[] storage = new Object[32];
    
    private final AtomicLong state = new AtomicLong();
    
    private static long freeMask(int i) {
        long mask = 1 << (i << 1);
        mask |= (mask << 1);
        return ~mask;
    }
    
    private static long free(long state, int i) {
        return state & freeMask(i);
    }
    
    private static long claim(long state, int i) {
        long freed = free(state,i);
        return freed | (CLAIM << (i <<1));
    }
    
    private static long full(long state, int i) {
        long freed = free(state,i);
        return freed | (FULL << (i <<1));
    }
    
    private static int get(long state, final int i) {
        state &= (~freeMask(i));
        return (int)(state >>> (i << 1));
    }
    
    /**
     * @param item to store
     * @return true if stored, false if there was no space.
     */
    public boolean store(T item) {
        // find a free slot
        int slot;
        while(true) {
            final long s = state.get();
            slot = -1;
            for (int i = 0; i < 32; i++) {
                if (get(s,i) != FREE)
                    continue;
                slot = i;
                break;
            }
            if (slot < 0)
                return false;
            
            // try to claim it
            long claimState = claim(s,slot);
            if (state.compareAndSet(s,claimState))
                break;
        }
        
        // write
        storage[slot] = item;
        while(true) {
            final long s = state.get();
            long fullState = full(s,slot);
            if (state.compareAndSet(s,fullState))
                return true;
        }
    }
    
    /**
     * Puts the items currently in the bag in the destination array, in arbitrary order.
     * @param dest to store items
     * @return number of items stored
     */
    public int get(T[] dest) {
        // TODO: implement
        return 0;
    }
    
    public static void main(String[] ar) {
        AtomicBag<?> as = new AtomicBag<Object>();
        System.out.printf("%x\n",as.get(12,1));
    }

}
