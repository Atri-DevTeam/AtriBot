package top.yzljc.atribot.plugin;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName PluginClassLoader
 * @Created_at 2026/09/10
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugin
 */
final class PluginClassLoader extends URLClassLoader {
    static {
        registerAsParallelCapable();
    }

    private final List<PluginClassLoader> dependencies;

    PluginClassLoader(URL jar, List<PluginClassLoader> dependencies) {
        super(new URL[]{jar}, AtriPlugin.class.getClassLoader());
        this.dependencies = List.copyOf(dependencies);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException missing) {
            for (PluginClassLoader dependency : dependencies) {
                try {
                    return dependency.loadClass(name);
                } catch (ClassNotFoundException ignored) {
                }
            }
            throw missing;
        }
    }
}
