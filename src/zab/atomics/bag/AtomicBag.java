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
     * @param items to store in the bag
     * @return how many were stored
     */
    public int store(T[] items) {
        return store(items, 0, items.length);
    }
    
    /**
     * Bulk store operation.  More efficient than calling
     * store() multiple times.
     * 
     * @param items to store
     * @param start index within the array where to start
     * @param num how many of them, starting at 0
     * @return number actually stored
     */
    public int store(final T[] items, final int start, int num) {
        num = Math.min(32,num);
        
        // find free slots
        int slots, found;
        while(true) {
            slots = 0;
            found = 0;
            final long s = state.get();
            long newState = s;
            for(int i = 0; i<32 && num > found ;i++) {
                if (get(s,i) == FREE) {
                    slots |= 1 << i;
                    newState = newState | claim(s, i);
                    found++;
                }
            }
            if (found == 0)
                return 0;
            
            if (state.compareAndSet(s,newState))
                break;
        }
        
        // store in slots
        int stored = 0;
        long storedMask = 0;
        for (int i = 0; i < 32 && stored < found; i++) {
            if ((slots & ( 1 << i)) == 0)
                continue;
            storage[i] = items[start + stored++];
            storedMask = full(storedMask,i);
        }
        
        // update state
        while(true) {
            final long s = state.get();
            if (state.compareAndSet(s,s | storedMask))
                return found;
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
    public T remove() {
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
     * Removes the items currently in the bag  and puts them in the 
     * destination array, in arbitrary order.
     * 
     * More efficient than calling remove() repeatedly.
     * 
     * @param dest to store items
     * @param start starting position within dest
     * @param num up to how many to store
     * @return number of items removed
     */
    @SuppressWarnings("unchecked")
    public int removeTo(final T[] dest, final int start, final int num) {
        while(true) {
            final long s = state.get();
            long newState = s;
            int idx = 0;
            for(int i = 0; i < 32 && idx < num; i++) {
                if (get(s,i) != FULL)
                    continue;
                dest[start + idx++] = (T)storage[i];
                newState = free(newState,i);
            }
            if (idx == 0)
                return 0;
            if (state.compareAndSet(s,newState))
                return idx;
        }
    }
    
    /**
     * Removes the items currently in the bag  and puts them in the 
     * destination array, in arbitrary order.
     * 
     * More efficient than calling remove() repeatedly.
     * 
     * @param dest to store items
     * @return number of items removed
     */
    public int removeTo(T[] dest) {
        return removeTo(dest,0,dest.length);
    }
    
    /**
     * Retrieves, but does not remove an arbitrary item from the bag
     * @return an item that is in the bag, null if the bag was empty.
     */
    @SuppressWarnings("unchecked")
    public T get() {
        final long s = state.get();
        for (int i = 0; i < 32; i++) {
            if (get(s,i) != FULL)
                continue;
            return (T)storage[i];
        }
        return null;
    }
    
    /**
     * Copies the items currently in the bag  and puts them in the 
     * destination array, in arbitrary order.
     * 
     * More efficient than calling get() repeatedly.
     * 
     * @param dest to store items
     * @param start starting position within dest
     * @param num up to how many to store
     * @return number of items copied
     */
    @SuppressWarnings("unchecked")
    public int copyTo(final T[] dest, final int start, final int num) {
        final long s = state.get();
        int idx = 0;
        for(int i = 0; i < 32 && idx < num; i++) {
            if (get(s,i) != FULL)
                continue;
            dest[start + idx++] = (T)storage[i];
        }
        return idx;
    }
    
    /**
     * Copies the items currently in the bag  and puts them in the 
     * destination array, in arbitrary order.
     * 
     * More efficient than calling get() repeatedly.
     * 
     * @param dest to store items
     * @param start starting position within dest
     * @param num up to how many to store
     * @return number of items copied
     */
    public int copyTo(final T[] dest) {
        return copyTo(dest,0,dest.length);
    }
}
