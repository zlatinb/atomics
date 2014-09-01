package zab.atomics.pool;

import java.util.concurrent.atomic.AtomicReference;

/**
 * An atomic pool of objects.  It is based on an inverted list.
 * 
 * The pool has no maximum capacity and starts off empty.  You need to load it
 * with objects you want pooled by calling the "release" method.
 * 
 * @author zlatinb
 *
 * @param <T> type of the objects in the pool
 */
public class AtomicPool<T> {

    private final AtomicReference<Wrapper<T>> first = 
            new AtomicReference<Wrapper<T>>();
    
    /**
     * @return an item from the pull, or null if it's empty.
     */
    public Wrapper<T> acquire() {
        while(true) {
            Wrapper<T> f = first.get();
            if (f == null)
                return null;
            if (first.compareAndSet(f,f.getNext()))
                return f;
        }
    }
    
    /**
     * Stores or returns an item to the pool
     */
    public void release(Wrapper<T> item) {
        while(true) {
            Wrapper<T> f = first.get();
            item.setNext(f);
            if (first.compareAndSet(f, item))
                return;
        }
    }
}
