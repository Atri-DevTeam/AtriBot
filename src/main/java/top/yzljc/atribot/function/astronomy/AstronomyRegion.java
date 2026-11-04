package top.yzljc.atribot.function.astronomy;

/**
 * @Author YZ_Ljc_
 * @ClassName AstronomyRegion
 * @Created_at 2026/10/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.astronomy
 */
public enum AstronomyRegion {
    NORTHEAST("东北", "沈阳", 41.8057, 123.4315),
    NORTH_CHINA("华北", "石家庄", 38.0428, 114.5149),
    EAST_CHINA("华东", "杭州", 30.2741, 120.1551),
    CENTRAL_CHINA("华中", "武汉", 30.5928, 114.3055),
    SOUTH_CHINA("华南", "广州", 23.1291, 113.2644),
    SOUTHWEST("西南", "成都", 30.5728, 104.0668),
    NORTHWEST("西北", "兰州", 36.0611, 103.8343),
    XINJIANG("新疆", "乌鲁木齐", 43.8256, 87.6168);

    private final String displayName;
    private final String referenceName;
    private final double lat;
    private final double lng;

    AstronomyRegion(String displayName, String referenceName, double lat, double lng) {
        this.displayName = displayName;
        this.referenceName = referenceName;
        this.lat = lat;
        this.lng = lng;
    }

    public String getDisplayName() { return displayName; }
    public String getReferenceName() { return referenceName; }
    public double getLat() { return lat; }
    public double getLng() { return lng; }

    public static AstronomyRegion parse(String input) {
        if (input == null) return null;
        for (var region : values()) if (region.displayName.equals(input.trim())) return region;
        return null;
    }
}
