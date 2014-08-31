package zab.atomics.image;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 
 * A mirror contains a single image.  That image can be updated from a single
 * thread, but can be read from multiple threads.
 * 
 * It is fully wait-free.
 * 
 * COSTS: writes cost two CAS instructions. Reads cost volatile read.
 * 
 * @author zlatinb
 *
 * @param <T> type of the Image
 */
public class AtomicMirror<T> {

    private final Image<T> initial;
    
    private final AtomicLong before = new AtomicLong();
    private final AtomicLong after = new AtomicLong();
    
    /**
     * @param initial an object implementing Image that will be used for storage.
     */
    public AtomicMirror(Image<T> initial) {
        this.initial = initial;
    }
    
    /**
     * Updates the image stored in this mirror from the provided object.
     * This call is safe when only used from one thread.
     * 
     * Costs exactly two CAS instructions.
     * 
     * @param from to update from.  It cannot be the same as the initial image!
     */
    public void write(Image<? extends T> from) {
        if (from == initial)
            throw new IllegalArgumentException();
        
        long b = before.getAndIncrement();
        initial.mirrorFrom(from);
        if (!after.compareAndSet(b, b+1))
            throw new IllegalStateException();
    }
    
    /**
     * Reads the contents of this mirror and puts them in the target image.
     * This call is safe to use by many threads.
     * 
     * @param to image to update.  It cannot be the same as the initial image!
     */
    public void read(Image<? super T> to) {
        if (to == initial)
            throw new IllegalArgumentException();
        
        while(true) {
            long rev = after.get();
            to.mirrorFrom(initial);
            if (before.get() == rev)
                break;
        }
    }
    
}
