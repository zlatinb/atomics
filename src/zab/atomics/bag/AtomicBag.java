package zab.atomics.bag;

import java.util.concurrent.atomic.AtomicLong;

/**
 * An atomic, lock-free and wait-free storage of items.  It can store up to 32 items.
 * 
 * In some very rare cases, the bag can be full and empty at the same time, i.e. you cannot
 * add any items to it, but you can't take any items out either.  States like this should last
 * very short periods of time.
 * 
 * COSTS: 
 * the store(..) and remove(..) operations cost at least two CAS instructions.
 * the copyTo and get() operations cost a single volatile read.
 *  
 * @author zlatinb
 *
 * @param <T> type of the items stored
 */
public class AtomicBag<T> {
    
    private static final int FREE = 0;
    private static final int CLAIM = 1;
    private static final int FULL = 2;
    private static final int REMOVING = 3;
    
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
    
    private static long removing(long state, int i) {
        return state | (REMOVING << (i << 1));
    }
    
    private static int get(long state, final int i) {
        state &= (~freeMask(i));
        return (int)(state >>> (i << 1));
    }
    
    /**
     * Stores an item in the bag, if there is space.
     * Costs at least two CAS instructions, unless there is no room in the bag.
     * 
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
     * Bulk store operation.  More efficient than calling
     * store() multiple times.
     * 
     * Costs at least two CAS instructions unless there is no room in the bag.
     * 
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
     * Costs at least two CAS instructions unless there is no room in the bag.
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
        long storedMask = 0xFFFFFFFFFFFFFFFFL;
        for (int i = 0; i < 32 && stored < found; i++) {
            if ((slots & ( 1 << i)) == 0)
                continue;
            storage[i] = items[start + stored++];
            storedMask = full(storedMask,i);
        }
        
        // update state
        while(true) {
            final long s = state.get();
            if (state.compareAndSet(s,s & storedMask))
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
     * Removes an item from the bag.
     * 
     * Costs at least two CAS instructions unless there are no items in the bag.
     * 
     * @return an arbitrary item from the bag, null if empty
     */
    @SuppressWarnings("unchecked")
    public T remove() {
        // first find a full slot
        int slot;
        T item;
        while(true) {
            final long s = state.get();
            slot = -1;
            for (int i = 0; i < 32; i++) {
                if (get(s,i) != FULL)
                    continue;
                slot = i;
                break;
            }
            if (slot == -1)
                return null;
            
            item = (T)storage[slot];
            long newState = removing(s,slot);
            if (state.compareAndSet(s,newState))
                break;
        }
        
        // null the slot
        storage[slot] = null;
        
        // mark it as empty
        while(true) {
            final long s = state.get();
            if (state.compareAndSet(s,free(s,slot)))
                return item;
        }
    }
    
    /**
     * Removes the items currently in the bag  and puts them in the 
     * destination array, in arbitrary order.
     * 
     * More efficient than calling remove() repeatedly.
     * 
     * Costs at least one CAS instruction unless there are no items in the bag.
     * 
     * @param dest to store items
     * @param start starting position within dest
     * @param num up to how many to store
     * @return number of items removed
     */
    @SuppressWarnings("unchecked")
    public int removeTo(final T[] dest, final int start, final int num) {
        // find the full slots
        int slots;
        int idx;
        while(true) {
            slots = 0;
            final long s = state.get();
            long newState = s;
            idx = 0;
            for(int i = 0; i < 32 && idx < num; i++) {
                if (get(s,i) != FULL)
                    continue;
                dest[start + idx++] = (T)storage[i];
                newState = free(newState,i);
                slots |= (1 << i);
            }
            if (idx == 0)
                return 0;
            if (state.compareAndSet(s,newState))
                break;
        }
        
        // null them
        long freedMask = 0xFFFFFFFFFFFFFFFFL;
        for (int i = 0; i < 32; i++) {
            if ((slots & (1 << i)) == 0)
                continue;
            storage[i] = null;
            freedMask = free(freedMask,i);
        }
        
        // mark them as free
        while(true) {
            final long s = state.get();
            if (state.compareAndSet(s, s & freedMask))
                return idx;
        }
    }
    
    /**
     * Removes the items currently in the bag  and puts them in the 
     * destination array, in arbitrary order.
     * 
     * More efficient than calling remove() repeatedly.
     * 
     * Costs at least one CAS instruction unless there are no items in the bag.
     * 
     * @param dest to store items
     * @return number of items removed
     */
    public int removeTo(T[] dest) {
        return removeTo(dest,0,dest.length);
    }
    
    /**
     * Retrieves, but does not remove an arbitrary item from the bag
     * @return an item that is in the bag, null if there are no items in the bag.
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
