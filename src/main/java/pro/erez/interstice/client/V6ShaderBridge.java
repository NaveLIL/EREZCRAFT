package pro.erez.interstice.client;

import java.lang.reflect.Field;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import pro.erez.interstice.IntersticeClientConfig;
import pro.erez.interstice.worldgen.IslandWorld;

/** In-memory compatibility for the pinned original Complementary pack; never writes its ZIP. */
public final class V6ShaderBridge {
    public static final int LIGHT_SEA_MATERIAL = 29998;
    public static final int HEAVY_SEA_MATERIAL = 29999;
    private static volatile boolean adaptedPipeline;
    private static int patchedSources;

    private V6ShaderBridge() {}

    public static boolean materialsActive() {
        var level = Minecraft.getInstance().level;
        return adaptedPipeline && level != null && level.dimension().equals(IslandWorld.TENSION_WORLD);
    }

    public static Object programSet(Object pack, Object dimension, Object original) {
        adaptedPipeline = false;
        if (!dimension.toString().equals("interstice:islands_v6")) return original;
        try {
            var iris = Class.forName("net.irisshaders.iris.Iris");
            if (!"ComplementaryReimagined_r5.9.3.zip".equals(iris.getMethod("getCurrentPackName").invoke(null))) return original;
            var pathClass = Class.forName("net.irisshaders.iris.shaderpack.include.AbsolutePackPath");
            Object path = pathClass.getMethod("fromAbsolutePath", String.class).invoke(null, "/world0");
            @SuppressWarnings("unchecked")
            Function<Object, String> provider = (Function<Object, String>)field(pack, "sourceProvider");
            Object probePath = pathClass.getMethod("resolve", String.class).invoke(path, "gbuffers_terrain.fsh");
            String probe = provider.apply(probePath);
            if (probe == null || !probe.contains("Complementary Shaders by EminGT")
                    || !probe.contains("vec3 GetMinimumLighting(float lightmapYM)")) return original;
            boolean darkness = IntersticeClientConfig.V6_DARKNESS.get();
            patchedSources = 0;
            Function<Object, String> adapted = sourcePath -> adapt(provider.apply(sourcePath), darkness);
            var properties = Class.forName("net.irisshaders.iris.shaderpack.properties.ShaderProperties");
            var packClass = Class.forName("net.irisshaders.iris.shaderpack.ShaderPack");
            var setClass = Class.forName("net.irisshaders.iris.shaderpack.programs.ProgramSet");
            Object result = setClass.getConstructor(pathClass, Function.class, properties, packClass)
                    .newInstance(path, adapted, field(pack, "shaderProperties"), pack);
            adaptedPipeline = true;
            System.out.println("INTERSTICE_V6_SHADER_BRIDGE sources=" + patchedSources + " darkness=" + darkness);
            return result;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Pinned Iris V6 shader bridge no longer matches", error);
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    public static String adapt(String source, boolean darkness) {
        if (source == null) return null;
        String result = source;
        // V6 has neither a sky nor sunlight. Keep block/held lighting, AO, bloom and night vision.
        result = result.replaceAll("(?m)^([ \\t]*#define CLOUD_STYLE_DEFINE) [-0-9]+", "$1 0");
        result = result.replaceAll("(?m)^([ \\t]*#define LIGHTSHAFT_QUALI_DEFINE) [0-9]+", "$1 0");
        if (darkness) {
            result = result.replace("vec3 minLighting = vec3(0.005625 + vsBrightness * 0.043);", "vec3 minLighting = vec3(0.0); // Interstice V6");
            result = result.replace("vec3 sceneLighting = lightColorM * shadowLightMult + ambientColorM * ambientMult;",
                    "vec3 sceneLighting = vec3(0.0); // Interstice V6: no celestial ambient light");
            result = result.replace("color.rgb += lightHighlight;", "color.rgb += lightHighlight * lightmapYM;");
        }
        String fog = "void DoFog(inout vec4 color, inout float skyFade, float lViewPos, vec3 playerPos, float VdotU, float VdotS, float dither, bool isReflection, float lBlockPos) {";
        result = result.replace(fog, fog + "\n"
                + "    // Interstice fluid extensions supply their real immersion color through fogColor.\n"
                + "    if (isEyeInWater == 0 && dot(fogColor, fogColor) > 0.01) {\n"
                + "        float immersion = 1.0 - exp(-lViewPos * (fogColor.r > 0.5 ? 0.7 : 1.0));\n"
                + "        color.rgb = mix(color.rgb, fogColor, immersion);\n"
                + "    } else if (isEyeInWater == 1) DoWaterFog(color, lViewPos);\n"
                + "    else if (isEyeInWater == 2) DoLavaFog(color, lViewPos);\n"
                + "    else if (isEyeInWater == 3) DoPowderSnowFog(color, lViewPos);\n"
                + "    if (blindness > 0.00001) DoBlindnessFog(color, lViewPos);\n"
                + "    if (darknessFactor > 0.00001) DoDarknessFog(color, lViewPos);\n"
                + "    return;\n");
        String material = "float fresnelM = pow3(fresnel);";
        int start = result.indexOf(material);
        int end = start < 0 ? -1 : result.indexOf("// Blending", start);
        if (end >= 0 && result.indexOf("vec4 translucentMult = vec4(1.0);") >= 0) {
            int insertion = start + material.length();
            result = result.substring(0, insertion) + "\n"
                    + "    if (mat == 29998 || mat == 29999) {\n"
                    + "        color = colorP * vec4(glColor.rgb, 1.0);\n"
                    + "        color.a = mat == 29998 ? 0.8470588 : 1.0;\n"
                    + "        emission = mat == 29998 ? 1.0 : 0.0;\n"
                    + "        reflectMult = 0.0; translucentMultCalculated = true;\n"
                    + "        translucentMult = vec4(0.0, 0.0, 0.0, 1.0);\n"
                    + "    } else {\n" + result.substring(insertion, end) + "    }\n" + result.substring(end);
        }
        if (!result.equals(source)) patchedSources++;
        return result;
    }
}
