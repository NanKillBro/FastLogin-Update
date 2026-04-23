/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.bukkit.listener;

import com.github.games647.craftapi.model.skin.Textures;
import com.github.games647.fastlogin.bukkit.BukkitLoginSession;
import com.github.games647.fastlogin.bukkit.FastLoginBukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

public class PaperCacheListener implements Listener {

    private static final String[] PROFILE_PROPERTY_CLASSES = {
            "io.papermc.paper.profile.ProfileProperty",
            "com.destroystokyo.paper.profile.ProfileProperty"
    };

    private final FastLoginBukkit plugin;

    public PaperCacheListener(final FastLoginBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    //if paper is used - player skin must be set at pre login, otherwise user cache is used
    // user cache makes premium name change basically impossible
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != Result.ALLOWED) {
            return;
        }

        // event gives us only IP, not the port, so we need to loop through all the sessions
        for (BukkitLoginSession session : plugin.getLoginSessions().values()) {
            if (!event.getName().equals(session.getUsername())) {
                continue;
            }

            session.getSkin().ifPresent(skin -> setSkinProperty(event, skin));
            break;
        }
    }

    private void setSkinProperty(
            AsyncPlayerPreLoginEvent event,
            com.github.games647.craftapi.model.skin.SkinProperty skin
    ) {
        try {
            Object playerProfile = event.getPlayerProfile();
            for (String propertyClassName : PROFILE_PROPERTY_CLASSES) {
                Optional<Class<?>> propertyClass = findClass(propertyClassName);
                if (!propertyClass.isPresent()) {
                    continue;
                }

                Constructor<?> constructor = propertyClass.get()
                        .getConstructor(String.class, String.class, String.class);
                Object property = constructor.newInstance(Textures.KEY, skin.getValue(), skin.getSignature());

                Method setProperty = playerProfile.getClass().getMethod("setProperty", propertyClass.get());
                setProperty.invoke(playerProfile, property);
                return;
            }

            plugin.getLog().warn(
                    "Cannot apply skin property because no compatible Paper ProfileProperty type was found"
            );
        } catch (ReflectiveOperationException reflectiveOperationException) {
            plugin.getLog().debug("Failed to apply skin property during pre login", reflectiveOperationException);
        }
    }

    private Optional<Class<?>> findClass(String className) {
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException notFoundException) {
            return Optional.empty();
        }
    }

}
