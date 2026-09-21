package taboolib.common.platform;

import java.io.File;

/**
 * TabooLib bundled Plugin 类的幂等补丁版。
 * 与原版唯一差异：setInstance 对「同一实例的重复注册」直接忽略（原版无条件抛异常）。
 * 背景：Indra 的构建方式（跳过 ASM 后处理 + isolated classloader）会使
 * PlatformFactory 的扫描在模块加载期与 BukkitPlugin.<clinit> 的 CONST 期各执行一次，
 * 两次传入的都是 Kotlin object 单例，原版会在第二次抛
 * IllegalStateException: Plugin instance already set. 导致 <clinit> 失败、插件加载被 Paper 中止。
 */
public abstract class Plugin {

    private static Plugin instance = null;

    public void onLoad() {
    }

    public void onEnable() {
    }

    public void onActive() {
    }

    public void onDisable() {
    }

    public File nativeJarFile() {
        return null;
    }

    public File nativeDataFolder() {
        return null;
    }

    public static Plugin getInstance() {
        return Plugin.instance;
    }

    public static void setInstance(Plugin instance) {
        if (Plugin.instance != null) {
            // 幂等：同一实例重复注册直接忽略
            if (Plugin.instance == instance) {
                return;
            }
            throw new IllegalStateException("Plugin instance already set.");
        }
        Plugin.instance = instance;
    }
}
