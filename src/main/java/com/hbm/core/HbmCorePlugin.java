package com.hbm.core;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions({"com.hbm.core"})
@IFMLLoadingPlugin.SortingIndex(2077) // >1000 after SRG
public class HbmCorePlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    static final Logger coreLogger = LogManager.getLogger("HBM CoreMod");
    private static final Brand brand;
    private static boolean runtimeDeobfEnabled = false;
    private static boolean hardCrash = true;

    static {
        if (Launch.classLoader.getResource("catserver/server/CatServer.class") != null) {
            brand = Brand.CAT_SERVER;
        } else if (Launch.classLoader.getResource("com/mohistmc/MohistMC.class") != null) {
            brand = Brand.MOHIST;
        } else if (Launch.classLoader.getResource("org/magmafoundation/magma/Magma.class") != null) {
            brand = Brand.MAGMA;
        } else if (Launch.classLoader.getResource("com/cleanroommc/boot/Main.class") != null) {
            brand = Brand.CLEANROOM;
        } else {
            brand = Brand.FORGE;
        }
    }

    // ================= CoreMod 原有逻辑 =================

    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
                HbmCoreTransformer.class.getName()
        };
    }

    @Override
    public String getModContainerClass() {
        return HbmCoreModContainer.class.getName();
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        runtimeDeobfEnabled = (Boolean) data.get("runtimeDeobfuscationEnabled");

        if (System.getProperty("hbm.core.disablecrash") != null) {
            hardCrash = false;
            coreLogger.info("Crash suppressed with -Dhbm.core.disablecrash");
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    // ================= Mixin Booter =================

    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList(
                "mixins.living.json",
                "mixins.render.json"
        );
    }

    // ================= 工具方法 =================

    public static boolean runtimeDeobfEnabled() {
        return runtimeDeobfEnabled;
    }

    public static String chooseName(String mcp, String srg) {
        return runtimeDeobfEnabled ? srg : mcp;
    }

    public static Brand getBrand() {
        return brand;
    }

    static void fail(String className, Throwable t) {
        coreLogger.fatal("Error transforming class {}", className, t);
        if (hardCrash) {
            throw new IllegalStateException("HBM CoreMod transformation failure: " + className, t);
        }
    }

    public enum Brand {
        FORGE, CAT_SERVER, MOHIST, MAGMA, CLEANROOM;

        public boolean isHybrid() {
            return this == CAT_SERVER || this == MOHIST || this == MAGMA;
        }
    }
}
