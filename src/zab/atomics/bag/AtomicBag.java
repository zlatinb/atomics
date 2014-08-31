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
    
    private long freeMask(int i) {
        long mask = 1 << (i << 1);
        mask |= (mask << 1);
        return ~mask;
    }
    
    private long free(long state, int i) {
        return state & freeMask(i);
    }
    
    private long claim(long state, int i) {
        long freed = free(state,i);
        return freed | (CLAIM << (i <<1));
    }
    
    private long full(long state, int i) {
        long freed = free(state,i);
        return freed | (FULL << (i <<1));
    }
    
    private int find(final long state, final int type) {
        for (int i = 0; i < 32; i++) {
            long s = state & (~freeMask(i));
            s >>>= (i << 1);
            if (s == type)
                return i;
        }
        return -1;
    }

    /**
     * @param item to store
     * @return true if stored, false if there was no space.
     */
    public boolean store(T item) {
        while(true) {
            final long s = state.get();
        }
    }
    
    public static void main(String[] ar) {
        AtomicBag<?> as = new AtomicBag<Object>();
        System.out.printf("%x\n",as.find(0,CLAIM));
    }

}
