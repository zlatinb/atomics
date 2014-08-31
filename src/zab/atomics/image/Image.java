package zab.atomics.image;

/** 
 * an Image is something that can be mirrored from another image.
 * 
 * @author zlatinb
 *
 * @param <T> type of the image.
 */
public interface Image<T> {
    
    /**
     * Updates this instance of image from the other image.
     */
    public void mirrorFrom(Image<? extends T> other);
}
