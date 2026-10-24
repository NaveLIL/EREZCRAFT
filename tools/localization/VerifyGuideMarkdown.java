import guideme.libs.mdast.MdAst;
import guideme.libs.mdast.MdastOptions;
import guideme.libs.mdast.YamlFrontmatterExtension;
import guideme.libs.mdast.gfm.GfmTableMdastExtension;
import guideme.libs.mdast.gfmstrikethrough.GfmStrikethroughMdastExtension;
import guideme.libs.mdast.mdx.MdxMdastExtension;
import guideme.libs.micromark.extensions.gfm.GfmTableSyntax;
import guideme.libs.micromark.extensions.gfmstrikethrough.GfmStrikethroughSyntax;
import guideme.libs.mdx.MdxSyntax;
import guideme.libs.micromark.extensions.YamlFrontmatterSyntax;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipInputStream;

/** Parses the pack's guides with the installed GuideME parser, without starting Minecraft. */
public class VerifyGuideMarkdown {
    public static void main(String[] args) throws Exception {
        var options = new MdastOptions()
                .withSyntaxExtension(MdxSyntax.INSTANCE)
                .withSyntaxExtension(YamlFrontmatterSyntax.INSTANCE)
                .withSyntaxExtension(GfmTableSyntax.INSTANCE)
                .withSyntaxExtension(GfmStrikethroughSyntax.INSTANCE)
                .withMdastExtension(MdxMdastExtension.INSTANCE)
                .withMdastExtension(YamlFrontmatterExtension.INSTANCE)
                .withMdastExtension(GfmTableMdastExtension.INSTANCE)
                .withMdastExtension(GfmStrikethroughMdastExtension.INSTANCE);
        int count = 0;
        try (var paths = Files.walk(Path.of(args[0]))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
                // License/notice files aren't GuideME documents.
                if (!path.toString().contains("ae2guide") && !path.toString().contains("mi_guidebook") && !path.toString().replace('\\','/').contains("/guides/")) continue;
                try {
                    MdAst.fromMarkdown(Files.readString(path).replace("\r\n", "\n"), options);
                } catch (Exception error) {
                    throw new IllegalStateException("Invalid GuideME page: " + path, error);
                }
                count++;
            }
        }
        System.out.println("{\"guidemeParser\":\"PASS\",\"pages\":" + count + "}");
        if (args.length > 1) {
            var root = Path.of(args[0]).toAbsolutePath().normalize();
            int entries = 0;
            try (var zip = new ZipInputStream(Files.newInputStream(Path.of(args[1])))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    var source = root.resolve(entry.getName()).normalize();
                    if (!source.startsWith(root)) throw new IllegalStateException("Unsafe archive entry");
                    // ZipInputStream independently verifies ZIP headers, deflate streams and CRCs.
                    var content = zip.readAllBytes();
                    if (!Arrays.equals(content, Files.readAllBytes(source))) {
                        throw new IllegalStateException("Archive differs from source: " + entry.getName());
                    }
                    entries++;
                }
            }
            System.out.println("{\"javaZipValidation\":\"PASS\",\"entries\":" + entries + "}");
        }
    }
}
