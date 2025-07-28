package net.camotoy.bedrockskinutility.client.pluginmessage;

import com.mojang.blaze3d.platform.NativeImage;
import net.camotoy.bedrockskinutility.client.*;
import net.camotoy.bedrockskinutility.client.interfaces.BedrockPlayerInfo;
import net.camotoy.bedrockskinutility.client.mixin.PlayerEntityRendererChangeModel;
import net.camotoy.bedrockskinutility.client.mixin.PlayerSkinFieldAccessor;
import net.camotoy.bedrockskinutility.client.pluginmessage.data.BaseSkinInfo;
import net.camotoy.bedrockskinutility.client.pluginmessage.data.CapeData;
import net.camotoy.bedrockskinutility.client.pluginmessage.data.SkinData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Logger;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class BedrockMessageHandler {
    private final Logger logger;
    private final SkinManager skinManager;

    public BedrockMessageHandler(Logger logger, SkinManager skinManager) {
        this.logger = logger;
        this.skinManager = skinManager;
    }

    public void handle(CapeData payload, ClientPlayNetworking.Context context) {
        NativeImage capeImage = toNativeImage(payload.capeData(), payload.width(), payload.height());

        context.client().submit(() -> {
            // As of 1.17.1, identical identifiers do not result in multiple objects of the same type being registered
            context.client().getTextureManager().register(payload.identifier(), new DynamicTexture(() -> {
                assert capeImage != null;
                return payload.identifier().toString() + capeImage.hashCode();
            }, capeImage));
            applyCapeTexture(context.client().getConnection(), payload.playerUuid(), payload.identifier());
        });
    }

    /**
     * Should be run from the main thread
     */
    private void applyCapeTexture(ClientPacketListener handler, UUID playerUuid, ResourceLocation identifier) {
        PlayerInfo entry = handler.getPlayerInfo(playerUuid);
        if (entry == null) {
            // Save in the cache for later
            BedrockCachedProperties properties = skinManager.getCachedPlayers().getIfPresent(playerUuid);
            if (properties == null) {
                properties = new BedrockCachedProperties();
                skinManager.getCachedPlayers().put(playerUuid, properties);
            }
            properties.cape = identifier;
        } else {
            final PlayerSkinBuilder builder = new PlayerSkinBuilder(entry.getSkin());
            builder.capeTexture = identifier;
            builder.bedrockCape = true;
            final PlayerSkin playerSkin = builder.build();
            ((PlayerSkinFieldAccessor) entry).setPlayerSkin(() -> playerSkin);
        }
    }

    public void handle(BaseSkinInfo payload) {
        if (payload == null) {
            return;
        }

        skinManager.getSkinInfo().put(payload.playerUuid(), new SkinInfo(payload.skinWidth(), payload.skinHeight(), payload.jsonGeometry(),
                payload.jsonGeometryName(), payload.chunkCount()));
    }

    public void handle(SkinData payload, ClientPlayNetworking.Context context) {
        SkinInfo info = skinManager.getSkinInfo().get(payload.playerUuid());
        if (info == null) {
            this.logger.error("Skin info was null!!!");
            return;
        }
        info.setData(payload.skinData(), payload.chunkPosition());
        this.logger.info("Skin chunk {} received for {}", payload.chunkPosition(), payload.playerUuid());

        if (info.isComplete()) {
            // All skin data has been received
            skinManager.getSkinInfo().remove(payload.playerUuid());
        } else {
            return;
        }

        NativeImage skinImage = toNativeImage(info.getData(), info.getWidth(), info.getHeight());
        PlayerRenderer renderer;
        boolean setModel = info.getGeometry() != null && !info.getGeometry().isEmpty();

        ResourceLocation identifier = ResourceLocation.fromNamespaceAndPath("geyserskinmanager", payload.playerUuid().toString());

        Minecraft client = context.client();

        if (setModel) {
            // Ex: skinResourcePatch={"geometry":{"default":"geometry.humanoid.custom.1742391406.1704"}}
            String requiredGeometry = null;
            try {
                if (info.getGeometryName() != null) {
                    requiredGeometry = info.getGeometryName().getAsJsonObject()
                            .getAsJsonObject("geometry").get("default").getAsString();
                }
            } catch (Exception ignored) {}

            BedrockPlayerEntityModel<AbstractClientPlayer> model = null;

            final List<BedrockGeometryModel> geometries;
            try {
                geometries = BedrockGeometryModel.fromJson(info.getGeometry());

                if (!geometries.isEmpty()) {
                    BedrockGeometryModel geometry = geometries.getFirst();
                    if (requiredGeometry != null) {
                        for (final BedrockGeometryModel geometryModel : geometries) {
                            if (geometryModel.getIdentifier().equals(requiredGeometry)) {
                                geometry = geometryModel;
                                break;
                            }
                        }
                    }

                    // Convert Bedrock JSON geometry into a class format that Java understands
                    model = GeometryUtil.bedrockGeoToJava(geometry);
                }
            } catch (final Exception ignored) {
            }

            if (model != null) {
                EntityRendererProvider.Context entityContext = new EntityRendererProvider.Context(client.getEntityRenderDispatcher(),
                        client.getItemModelResolver(), client.getMapRenderer(), client.getBlockRenderer(),
                        client.getResourceManager(), client.getEntityModels(), new EquipmentAssetManager(), client.font);
                renderer = new BedrockPlayerRenderer(entityContext, false, identifier);
                ((PlayerEntityRendererChangeModel) renderer).bedrockskinutility$setModel(model);
            } else {
                renderer = null;
            }
        } else {
            renderer = null;
        }

        client.submit(() -> {
            client.getTextureManager().register(identifier, new DynamicTexture(() -> identifier.toString() + skinImage.hashCode(), skinImage));
            applySkinTexture(client.getConnection(), payload.playerUuid(), identifier, renderer);
        });
    }

    /**
     * Should be run from the main thread
     */
    private void applySkinTexture(ClientPacketListener handler, UUID playerUuid, ResourceLocation identifier, PlayerRenderer renderer) {
        PlayerInfo entry = handler.getPlayerInfo(playerUuid);
        if (entry == null) {
            // Save in the cache for later
            BedrockCachedProperties properties = skinManager.getCachedPlayers().getIfPresent(playerUuid);
            if (properties == null) {
                properties = new BedrockCachedProperties();
                skinManager.getCachedPlayers().put(playerUuid, properties);
            }
            properties.model = renderer;
            properties.skin = identifier;
        } else {
            try {
                ((BedrockPlayerInfo) entry).bedrockskinutility$setModel(renderer);

                final PlayerSkinBuilder builder = new PlayerSkinBuilder(entry.getSkin());
                builder.texture = identifier;
                builder.bedrockSkin = true;
                final PlayerSkin playerSkin = builder.build();
                ((PlayerSkinFieldAccessor) entry).setPlayerSkin(() -> playerSkin);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private NativeImage toNativeImage(byte[] data, int width, int height) {
        BufferedImage bufferedImage = SkinUtils.toBufferedImage(data, width, height);

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ImageIO.write(bufferedImage, "png", outputStream);
            return NativeImage.read(outputStream.toByteArray());
        } catch (IOException ignored) {
            return null;
        }
    }

    private static int getARGB(int index, byte[] data) {
        return (data[index + 3] & 0xFF) << 24 | (data[index] & 0xFF) << 16 |
                (data[index + 1] & 0xFF) << 8 | (data[index + 2] & 0xFF);
    }
}
