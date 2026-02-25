package com.sammy.catskincRemake.fabric.client;

import com.sammy.catskincRemake.client.CatskincRemakeClient;
import com.sammy.catskincRemake.client.ModSounds;
import net.fabricmc.api.ClientModInitializer;

public final class CatskincRemakeFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModSounds.register();
        CatskincRemakeClient.init();
    }
}
