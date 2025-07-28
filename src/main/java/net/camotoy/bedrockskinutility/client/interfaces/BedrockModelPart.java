package net.camotoy.bedrockskinutility.client.interfaces;

import org.joml.Vector3f;

public interface BedrockModelPart {
    boolean bedrockskinutility$isBedrockModel();
    void bedrockskinutility$setBedrockModel();
    void bedrockskinutility$setNeededOffset(boolean needed);
    void bedrockskinutility$setOffset(Vector3f vec3);
    void bedrockskinutility$setPivot(Vector3f vec3);
    void bedrockskinutility$setAngles(Vector3f vec3);
}