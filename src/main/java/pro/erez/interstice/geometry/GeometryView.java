package pro.erez.interstice.geometry;

/** A view owns its world's immutable profile even after the active client dimension changes. */
public interface GeometryView {
    GeometryProfile intersticeGeometry();
}
