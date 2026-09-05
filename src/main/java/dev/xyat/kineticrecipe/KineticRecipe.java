package dev.xyat.kineticrecipe;

import com.mojang.logging.LogUtils;
import dev.xyat.kineticrecipe.recipehud.RecipeConfigStore;
import dev.xyat.kineticrecipe.recipehud.RecipeRegistry;
import dev.xyat.kineticrecipe.recipehud.config.RecipeConfigGui;
import dev.xyat.kineticrecipe.recipehud.network.RecipeNetwork;
import dev.xyat.kineticcore.config.server.KTServerConfigApi;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(KineticRecipe.MODID)
public final class KineticRecipe {
    public static final String MODID = "kineticrecipe";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KineticRecipe(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        RecipeConfigStore.ensureDatapackFile();
        KTServerConfigApi.registerActionPage("kineticrecipe:recipehud");
        RecipeRegistry.register(modEventBus);
        RecipeNetwork.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> RecipeConfigGui::load);
    }
}
