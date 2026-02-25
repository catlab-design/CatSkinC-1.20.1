package com.sammy.catskincRemake.forge;

import com.sammy.catskincRemake.client.CatskincRemakeClient;
import com.sammy.catskincRemake.client.ModSounds;

public final class CatskincRemakeForgeClient {
    private CatskincRemakeForgeClient() {
    }

    @SuppressWarnings("removal")
    public static void registerClientInit() {
        ModSounds.register();
        CatskincRemakeClient.init();
    }
}
