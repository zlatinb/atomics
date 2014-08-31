package zab.atomics.buffer;

/**
 * yields whenever  apending write needs to wait for others.
 * @author zlatinb
 */
public class YieldingListener implements WaitListener {

    @Override
    public void onWait() {
        Thread.yield();
    }

}
