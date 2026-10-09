import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/** Ready whole CC0 bag/sack icons; bijective palette only, without changing pixel layout or alpha. */
public final class GenerateBackpackPalettes {
    record Entry(String source, String hash, String name, int color, Map<Integer, Integer> semanticMap) {
        Entry(String source, String hash, String name, int color) {
            this(source, hash, name, color, null);
        }
    }

    static final Path SOURCES = Path.of("art/sources/cc0/backpacks");
    static final Path ASSETS = Path.of("src/main/resources/assets/interstice");

    public static void main(String[] args) throws Exception {
        // Semantic bijective mapping for bag.png (Option 1: Expedition Classic):
        // 0xff000000: outline
        // 0xffa46422: body canvas
        // 0xffeb8931: straps / flap highlights
        // 0xff493c2b: canvas shadows
        // 0xfff7e26b: metal buckle / clasp
        // 0x00a46422, 0x00493c2b, 0x00eb8931: zero-alpha colors preserved with matching RGB
        var fieldMap = createBagMap(
                0x181424, // outline: dark charcoal-twilight
                0x4e4468, // body: twilight indigo canvas
                0xaf6934, // straps: warm saddle expedition leather
                0x2d263e, // shadow: deep twilight shadow
                0xd2f0f5  // buckle: gleaming riftsilver
        );

        var expeditionMap = createBagMap(
                0x0e1c22, // outline: dark abyss cyan-charcoal
                0x285f69, // body: abyssal deep teal reinforced fabric
                0x96cdd7, // straps: riftsilver reinforced webbing
                0x16363c, // shadow: dark deep teal
                0xffc32d  // buckle: glowing vitriolite amber crystal
        );

        var entries = List.of(
                new Entry("bag.png", "1cd9bb658456761a14013e925724f4f50ce61e498d76c870161e2a8421c64551", "field_backpack", 0x4e4468, fieldMap),
                new Entry("bag.png", "1cd9bb658456761a14013e925724f4f50ce61e498d76c870161e2a8421c64551", "expedition_backpack", 0x285f69, expeditionMap),
                new Entry("sack.png", "026b97306dd1e3ac02798907e9312c72e9dd8cae93106763c935f688cd693f86", "chemotrophic_fabric", 0x9b858e, null));

        var records = new ArrayList<String>();
        for (var entry : entries) {
            Path source = SOURCES.resolve(entry.source);
            if (!sha(source).equals(entry.hash)) throw new IllegalStateException("Original source SHA changed: " + source);
            var original = ImageIO.read(source.toFile());
            if (original.getWidth() != 16 || original.getHeight() != 16) throw new IllegalStateException("Ready 16x16 icon required");

            var colors = new TreeSet<Integer>(Comparator.comparingDouble((Integer c) -> brightness(c)).thenComparingInt(c -> c));
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) colors.add(original.getRGB(x, y));

            Map<Integer, Integer> map;
            if (entry.semanticMap != null) {
                map = entry.semanticMap;
            } else {
                map = new HashMap<>();
                var used = new HashSet<Integer>();
                int index = 0;
                for (int sourceColor : colors) {
                    int alpha = sourceColor >>> 24;
                    double scale = .30 + 1.15 * index++ / Math.max(1., colors.size() - 1.);
                    int r = (int) Math.min(255, ((entry.color >>> 16) & 255) * scale);
                    int g = (int) Math.min(255, ((entry.color >>> 8) & 255) * scale);
                    int b = (int) Math.min(255, (entry.color & 255) * scale);
                    int targetColor = (alpha << 24) | (r << 16) | (g << 8) | b;
                    while (!used.add(targetColor)) targetColor = (alpha << 24) | ((targetColor + 1) & 0xffffff);
                    map.put(sourceColor, targetColor);
                }
            }

            var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setRGB(x, y, map.get(original.getRGB(x, y)));
            Path target = ASSETS.resolve("textures/item/expedition/" + entry.name + ".png");
            Files.createDirectories(target.getParent());
            ImageIO.write(image, "PNG", target.toFile());

            var stored = ImageIO.read(target.toFile());
            var forward = new HashMap<Integer, Integer>();
            var reverse = new HashMap<Integer, Integer>();
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int a = original.getRGB(x, y), b = stored.getRGB(x, y);
                if ((a >>> 24) != (b >>> 24) || (forward.containsKey(a) && forward.get(a) != b) || (reverse.containsKey(b) && reverse.get(b) != a))
                    throw new IllegalStateException("Pattern or alpha changed: " + target);
                forward.put(a, b);
                reverse.put(b, a);
            }
            if (forward.size() != colors.size()) throw new IllegalStateException("Pixel class count changed");

            var model = ASSETS.resolve("models/item/" + entry.name + ".json");
            Files.createDirectories(model.getParent());
            Files.writeString(model, "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\"layer0\": \"interstice:item/expedition/" + entry.name + "\"}\n}\n");
            records.add("{\"source\":\"" + entry.source + "\",\"source_member\":\"" + entry.source + "\",\"output\":\"item/expedition/" + entry.name + ".png\",\"source_sha256\":\"" + sha(source) + "\",\"output_sha256\":\"" + sha(target) + "\",\"width\":16,\"height\":16,\"classes\":" + colors.size() + ",\"base_color\":\"#" + String.format("%06x", entry.color) + "\"}");
        }

        Path record = ASSETS.resolve("provenance/backpacks.json");
        Files.createDirectories(record.getParent());
        Files.writeString(record, "{\"license\":\"CC0-1.0\",\"author\":\"twiswist\",\"source_url\":\"https://opengameart.org/content/inventory-filter-icons\",\"sources\":\"art/sources/cc0/backpacks/sources.json\",\"generator\":\"tools/GenerateBackpackPalettes.java\",\"pattern_and_alpha_verified\":true,\"operation\":\"Bijective palette substitution; whole source coordinates, resolution and alpha preserved\",\"outputs\":[" + String.join(",", records) + "]}\n");
        System.out.println("Verified 3 whole CC0 backpack/fabric palettes and item models");
    }

    private static Map<Integer, Integer> createBagMap(int outlineRgb, int bodyRgb, int strapsRgb, int shadowRgb, int buckleRgb) {
        var map = new HashMap<Integer, Integer>();
        map.put(0x00000000, 0x00000000);
        map.put((int) 0xff000000L, (int) (0xff000000L | (outlineRgb & 0xffffff)));
        map.put((int) 0xffa46422L, (int) (0xff000000L | (bodyRgb & 0xffffff)));
        map.put((int) 0xffeb8931L, (int) (0xff000000L | (strapsRgb & 0xffffff)));
        map.put((int) 0xff493c2bL, (int) (0xff000000L | (shadowRgb & 0xffffff)));
        map.put((int) 0xfff7e26bL, (int) (0xff000000L | (buckleRgb & 0xffffff)));
        // Alpha=0 colors in source preserved with corresponding RGB
        map.put(0x00a46422, bodyRgb & 0xffffff);
        map.put(0x00493c2b, shadowRgb & 0xffffff);
        map.put(0x00eb8931, strapsRgb & 0xffffff);
        return map;
    }

    static double brightness(int color) {
        return ((color >>> 16) & 255) * .2126 + ((color >>> 8) & 255) * .7152 + (color & 255) * .0722;
    }

    static String sha(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
