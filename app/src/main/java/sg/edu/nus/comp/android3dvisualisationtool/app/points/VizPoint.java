package sg.edu.nus.comp.android3dvisualisationtool.app.points;

/** Position and measured LiDAR intensity for visualization. */
public class VizPoint {
    public final float x;
    public final float y;
    public final float z;
    public final float intensity;

    public VizPoint(float x, float y, float z, float intensity) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.intensity = intensity;
    }
}
