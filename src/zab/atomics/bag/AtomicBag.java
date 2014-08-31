package zab.atomics.bag;

import java.util.concurrent.atomic.AtomicLong;

/**
 * An atomic, lock-free and wait-free storage of items.  It can store up to 32 items.
 * 
 * The bag may keep references to up to 32 items after they have been removed.  Use it
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
     * @return number of items in the bag
     */
    public int size() {
        final long s = state.get();
        int size = 0;
        for (int i = 0; i < 32; i++) {
            if (get(s,i) == FULL)
                size++;
        }
        return size;
    }
    
    /**
     * @return an arbitrary item from the bag, null if empty
     */
    @SuppressWarnings("unchecked")
    public T get() {
        while(true) {
            final long s = state.get();
            int slot = -1;
            for (int i = 0; i < 32; i++) {
                if (get(s,i) != FULL)
                    continue;
                slot = i;
                break;
            }
            if (slot == -1)
                return null;
            
            T item = (T)storage[slot];
            long newState = free(s,slot);
            if (state.compareAndSet(s,newState))
                return item;
        }
    }
    
    /**
     * Puts the items currently in the bag in the destination array, in arbitrary order.
     * More efficient than calling get() repeatedly.
     * 
     * @param dest to store items
     * @return number of items stored
     */
    @SuppressWarnings("unchecked")
    public int get(T[] dest) {
        while(true) {
            final long s = state.get();
            long newState = s;
            int idx = 0;
            for(int i = 0; i < 32; i++) {
                if (get(s,i) != FULL)
                    continue;
                dest[idx++] = (T)storage[i];
                newState = free(newState,i);
            }
            if (idx == 0)
                return 0;
            if (state.compareAndSet(s,newState))
                return idx;
        }
    }
}
