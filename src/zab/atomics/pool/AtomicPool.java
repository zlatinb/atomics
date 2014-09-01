package zab.atomics.pool;

import java.util.concurrent.atomic.AtomicReference;

public class AtomicPool<T> {

    private final AtomicReference<Wrapper<T>> first = 
            new AtomicReference<Wrapper<T>>();
    
    public Wrapper<T> acquire() {
        while(true) {
            Wrapper<T> f = first.get();
            if (f == null)
                return null;
            if (first.compareAndSet(f,f.getNext()))
                return f;
        }
    }
    
    public void release(Wrapper<T> item) {
        while(true) {
            Wrapper<T> f = first.get();
            item.setNext(f);
            if (first.compareAndSet(f, item))
                return;
        }
    }
}
