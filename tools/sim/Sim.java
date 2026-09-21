import java.util.*;

/**
 * Indra 引导类修复 —— 运行时行为仿真
 *
 * 目的：
 *   1) 复现「禁用 ASM 后 ProjectInfoKt.getGroupId() 返回 taboolib」的失败场景
 *   2) 复现「Java 静态块注入 taboolib.group=com.indra 后」的成功场景
 *   3) 验证 ClassVisitorHandler 阶段一过滤的通过数量
 *
 * 说明：沙箱内无 Paper API，无法真正 Class.forName 加载 IndraBootstrap
 *      （父类链 BukkitPlugin -> JavaPlugin 不存在），因此这里只做
 *      「逻辑等价仿真」，同时直接读取 jar 内真实 class 字节码来证明静态块内容。
 */
public class Sim {

    /** 等价于 ProjectInfoKt 的静态字段初值 */
    private static final String HARDCODED_GROUP_ID = "taboolib";

    /** 等价于 ProjectInfoKt.getGroupId() */
    static String getGroupId() {
        if (HARDCODED_GROUP_ID.equals("taboolib")) {
            return System.getProperty("taboolib.group", HARDCODED_GROUP_ID);
        }
        return HARDCODED_GROUP_ID;
    }

    /** 等价于 ClassVisitorHandler.isProjectClass(String) */
    static boolean isProjectClass(String dottedName) {
        return dottedName.startsWith(getGroupId()) || dottedName.startsWith("taboolib");
    }

    private static final String[] PROJECT_CLASSES = {
        "com.indra.rpg.Indra",
        "com.indra.rpg.IndraBootstrap",
        "com.indra.rpg.config.IndraConfig",
        "com.indra.rpg.command.CommandModule",
        "com.indra.rpg.ban.BanConfig",
        "com.indra.rpg.ban.BanManager",
        "com.indra.rpg.data.DataManager",
        "com.indra.rpg.economy.VaultBridge",
        "com.indra.rpg.module.ModuleManager",
    };

    static void scenario(String label, String systemProp) {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  场景: " + label);
        System.out.println("═══════════════════════════════════════════════════════════");
        if (systemProp == null) {
            System.clearProperty("taboolib.group");
        } else {
            System.setProperty("taboolib.group", systemProp);
        }

        System.out.println("  ProjectInfoKt 静态字段初值   = \"" + HARDCODED_GROUP_ID + "\"");
        System.out.println("  System.getProperty(taboolib.group) = " + System.getProperty("taboolib.group"));
        System.out.println("  → getGroupId() 返回值        = \"" + getGroupId() + "\"");
        System.out.println();

        int passed = 0;
        for (String c : PROJECT_CLASSES) {
            boolean ok = isProjectClass(c);
            if (ok) passed++;
        }
        System.out.println("  阶段一过滤通过数 = " + passed + " / " + PROJECT_CLASSES.length);
        for (String c : PROJECT_CLASSES) {
            System.out.printf("      %-42s %s%n", c, isProjectClass(c) ? "PASS" : "DROP");
        }
        System.out.println();
        if (passed > 0) {
            System.out.println("  >>> 类集合非空");
            System.out.println("  >>> PlatformFactory.inject() 会遍历到 Indra");
            System.out.println("  >>> superName == taboolib.common.platform.Plugin → Plugin.setInstance(Indra)");
            System.out.println("  >>> BukkitPlugin.onEnable(): pluginInstance != null → Indra.onEnable() 执行");
            System.out.println("  >>> IndraConfig.load() / DataManager.init() / ModuleManager.enableAll() 全部生效");
        } else {
            System.out.println("  >>> 类集合为【空集】");
            System.out.println("  >>> PlatformFactory.inject() 遍历 0 个类");
            System.out.println("  >>> Plugin.setInstance() 从不调用 → pluginInstance == null");
            System.out.println("  >>> BukkitPlugin.onEnable(): pluginInstance == null → 静默跳过");
            System.out.println("  >>> 症状：插件加载成功、零报错、@Awake 不触发、@Config 不生成、命令不注册");
        }
        System.out.println();
    }

    /** 从真实 jar 里读 IndraBootstrap.class，提取常量池中的字符串，证明静态块内容 */
    static void inspectJar(String jarPath) {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  产物字节码核验: " + jarPath);
        System.out.println("═══════════════════════════════════════════════════════════");
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath)) {
            java.util.jar.JarEntry e = jar.getJarEntry("com/indra/rpg/IndraBootstrap.class");
            if (e == null) {
                System.out.println("  ✗ 未找到 com/indra/rpg/IndraBootstrap.class");
                return;
            }
            byte[] bytes = jar.getInputStream(e).readAllBytes();
            System.out.println("  com/indra/rpg/IndraBootstrap.class 大小 = " + bytes.length + " B");

            // 扫描 class 文件字节流中的字符串常量
            boolean hasProp = indexOf(bytes, "taboolib.group") >= 0;
            boolean hasValue = indexOf(bytes, "com.indra") >= 0;
            boolean hasSetProp = indexOf(bytes, "setProperty") >= 0;

            System.out.println("    包含 \"taboolib.group\" 字符串常量 = " + hasProp);
            System.out.println("    包含 \"com.indra\" 字符串常量       = " + hasValue);
            System.out.println("    包含 \"setProperty\" 方法引用       = " + hasSetProp);

            java.util.jar.JarEntry me = jar.getJarEntry("META-INF/MANIFEST.MF");
            if (me != null) {
                System.out.println("  --- plugin.yml ---");
                java.util.jar.JarEntry py = jar.getJarEntry("plugin.yml");
                if (py != null) {
                    String s = new String(jar.getInputStream(py).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    for (String line : s.split("\n")) {
                        if (line.startsWith("main:") || line.startsWith("version:") || line.startsWith("api-version:")
                            || line.startsWith("name:") || line.startsWith("authors:")) {
                            System.out.println("    " + line.trim());
                        }
                    }
                }
            }

            int kotlinCnt = 0;
            java.util.Enumeration<java.util.jar.JarEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.startsWith("kotlin/") && n.endsWith(".class")) kotlinCnt++;
            }
            System.out.println("  kotlin/*.class 数量 = " + kotlinCnt + "  (方案 B 不变式: 应为 0)");
            System.out.println("  BukkitPlugin 父类入包 = " + (jar.getJarEntry("taboolib/platform/BukkitPlugin.class") != null));
        } catch (Exception ex) {
            System.out.println("  ✗ " + ex);
        }
        System.out.println();
    }

    static int indexOf(byte[] hay, String needle) {
        byte[] n = needle.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= hay.length - n.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (hay[i + j] != n[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    public static void main(String[] args) throws Exception {
        System.out.println();
        System.out.println("╔═════════════════════════════════════════════════════════╗");
        System.out.println("║   Indra 静默失活根因 —— 修复前后行为对照仿真            ║");
        System.out.println("╚═════════════════════════════════════════════════════════╝");
        System.out.println();

        scenario("修复前（禁用 ASM，无引导类）", null);
        scenario("修复后（Java 静态块注入 taboolib.group=com.indra）", "com.indra");

        String jar = args.length > 0 ? args[0] : "/workspace/Indra/dist/Indra-1.5.0.jar";
        inspectJar(jar);
    }
}
