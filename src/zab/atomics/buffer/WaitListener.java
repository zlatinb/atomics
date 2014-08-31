package zab.atomics.buffer;

/** 
 * something to be notified when a write to an AtomicBuffer needs to wait
 * for another write to complete.
 * The notification will happen in a loop until the write succeeds.
 * @author zlatinb
 */
public interface WaitListener {
    /**
     * a write in progress needs to wait.
     */
    public void onWait();
}
