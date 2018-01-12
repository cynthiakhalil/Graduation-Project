package com.vuforia.samples.VuforiaSamples.app.ImageTargets;

public class AugmentationShaders
{

    public static final String VERTEX_SHADER = "\n"
            + "attribute vec4 vertexPosition; \n"
            + "attribute vec4 vertexNormal; \n"
            + "attribute vec2 vertexTexCoord; \n"
            + "varying vec2 texCoord; \n"
            + "varying vec4 normal; \n"
            + "uniform mat4 modelViewProjectionMatrix; \n"

            + "void main() \n"
            + "{ \n"
            + "    gl_Position = modelViewProjectionMatrix * vertexPosition; \n"
            + "    normal = vertexNormal; \n"
            + "    texCoord = vertexTexCoord; \n"
            + "} \n";

    public static final String FRAGMENT_SHADER = "\n"
            + "precision mediump float; \n"
            + "varying vec2 texCoord; \n"
            + "varying vec4 normal; \n"
            + "uniform sampler2D texSampler2D; \n"

            + "void main() \n"
            + "{ \n"
            + "    gl_FragColor = texture2D(texSampler2D, texCoord); \n"
            + "} \n";
}