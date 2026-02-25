package com.sammy.catskincRemake.forge;

import com.sammy.catskincRemake.CatskincRemake;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CatskincRemakeForge.FORGE_MOD_ID)
@SuppressWarnings("removal")
public final class CatskincRemakeForge {
    public static final String FORGE_MOD_ID = "catskinc_remake";

    public CatskincRemakeForge() {
        EventBuses.registerModEventBus(FORGE_MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        CatskincRemake.init();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> CatskincRemakeForgeClient::registerClientInit);
    }
}
