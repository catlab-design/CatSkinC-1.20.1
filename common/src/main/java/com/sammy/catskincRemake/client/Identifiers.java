package com.sammy.catskincRemake.client;

import com.sammy.catskincRemake.CatskincRemake;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;

public final class Identifiers {
    private static final Method IDENTIFIER_OF = findOf();

    private Identifiers() {
    }

    public static Identifier mod(String path) {
        return of(CatskincRemake.MOD_ID, path);
    }

    public static Identifier of(String namespace, String path) {
        if (IDENTIFIER_OF != null) {
            try {
                return (Identifier) IDENTIFIER_OF.invoke(null, namespace, path);
            } catch (Exception ignored) {
            }
        }
        return new Identifier(namespace, path);
    }

    private static Method findOf() {
        try {
            return Identifier.class.getMethod("of", String.class, String.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}

