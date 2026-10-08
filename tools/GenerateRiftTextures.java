import java.nio.file.Path;

/** Compatibility entry point for the project's original, reproducible texture generator. */
public final class GenerateRiftTextures {
    public static void main(String[] args) throws Exception {
        String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        int result=new ProcessBuilder(executable,"tools/GenerateProjectTextures.java").inheritIO().start().waitFor();
        if(result!=0)throw new IllegalStateException("Project texture generation failed: "+result);
    }
}
