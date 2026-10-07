package pro.erez.interstice.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.LevelHeightAccessor;

/** Version 1 fixes the existing wave shape; only its vertical origin and world bounds vary. */
public record GeometryProfile(int version, int minY, int height, int lowerSeaTop,
                              int upperReference, int clearance) {
    public static final GeometryProfile LEGACY = new GeometryProfile(1, 0, 128, 34, 94, 6);
    /** Default for new expeditions; never reinterpret a legacy world's geometry. */
    public static final GeometryProfile TALL = new GeometryProfile(1, 0, 256, 34, 222, 6);

    // Decode the definition first so invalid combinations produce a codec error, not a thrown constructor.
    private record Definition(int version, int minY, int height, int lowerSeaTop,
                              int upperReference, int clearance) {
        private DataResult<GeometryProfile> decode() {
            try {
                return DataResult.success(new GeometryProfile(version, minY, height, lowerSeaTop, upperReference, clearance));
            } catch (IllegalArgumentException error) {
                return DataResult.error(error::getMessage);
            }
        }
    }
    public static final Codec<GeometryProfile> CODEC = RecordCodecBuilder.<Definition>create(instance -> instance.group(
            Codec.INT.fieldOf("version").forGetter(Definition::version),
            Codec.INT.fieldOf("min_y").forGetter(Definition::minY),
            Codec.INT.fieldOf("height").forGetter(Definition::height),
            Codec.INT.fieldOf("lower_sea_top").forGetter(Definition::lowerSeaTop),
            Codec.INT.fieldOf("upper_reference").forGetter(Definition::upperReference),
            Codec.INT.fieldOf("clearance").forGetter(Definition::clearance)
    ).apply(instance, Definition::new)).flatXmap(Definition::decode, profile -> DataResult.success(
            new Definition(profile.version, profile.minY, profile.height, profile.lowerSeaTop,
                    profile.upperReference, profile.clearance)));

    public GeometryProfile {
        if (version != 1) throw new IllegalArgumentException("Unsupported Interstice geometry version: " + version);
        if (minY < -2032 || minY > 2016 || minY % 16 != 0 || height < 32 || height > 4064
                || height % 16 != 0 || (long) minY + height > 2032)
            throw new IllegalArgumentException("Geometry bounds must be section aligned and fit Minecraft's vertical range");
        long roof = (long) minY + height - 1;
        if (lowerSeaTop <= minY || lowerSeaTop >= roof || upperReference < minY || upperReference > roof
                || (long) upperReference + 3 > roof || clearance < 0 || clearance > height
                || (long) lowerSeaTop + 1 + clearance > (long) upperReference - 11 - clearance)
            throw new IllegalArgumentException("Seas, clearance and a nonempty island band must fit inside the world shell");
    }

    public int maxYExclusive() { return minY + height; }
    public int roof() { return maxYExclusive() - 1; }
    public int upperMinimum() { return upperReference - 10; }
    public int upperMaximum() { return upperReference + 2; }
    public int labCeiling() { return upperReference + 3; }
    public double upperMidpoint() { return upperReference - 4.0; }
    public int minLand() { return lowerSeaTop + 1 + clearance; }
    public int maxLand() { return upperMinimum() - clearance - 1; }

    public void checkHeight(LevelHeightAccessor level) {
        if (level.getMinBuildHeight() != minY || level.getHeight() != height)
            throw new IllegalArgumentException("Geometry/world height mismatch: profile=" + minY + "/" + height
                    + ", world=" + level.getMinBuildHeight() + "/" + level.getHeight());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(version); buffer.writeVarInt(minY); buffer.writeVarInt(height);
        buffer.writeVarInt(lowerSeaTop); buffer.writeVarInt(upperReference); buffer.writeVarInt(clearance);
    }
    public static GeometryProfile read(FriendlyByteBuf buffer) {
        return new GeometryProfile(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }
}
