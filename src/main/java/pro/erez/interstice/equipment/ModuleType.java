package pro.erez.interstice.equipment;

public enum ModuleType {
    MAGNET("magnet"),
    FEEDER("feeder"),
    COMPRESSION("compression");

    private final String id;

    ModuleType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
