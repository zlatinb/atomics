package zab.atomics.pool;

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
