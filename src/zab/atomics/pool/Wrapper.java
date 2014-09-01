package zab.atomics.pool;

/**
 * You need to wrap items you want pooled with wrappers.
 * 
 * @author zlatinb
 *
 * @param <T> type of the item contained
 */
public class Wrapper<T> {
    
    private final T item;
    private Wrapper<T> next;
    
    public Wrapper(T item) {
        this.item = item;
    }

    public T getItem() {
        return item;
    }
    
    void setNext(Wrapper<T> next) {
        this.next = next;
    }
    
    Wrapper<T> getNext() {
        return next;
    }
}
