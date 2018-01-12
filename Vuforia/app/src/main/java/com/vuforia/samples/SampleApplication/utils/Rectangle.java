/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.samples.SampleApplication.utils;

import java.nio.Buffer;


public class Rectangle extends MeshObject {

    private Buffer mVertBuff;
    private Buffer mTexCoordBuff;
    private Buffer mNormBuff;
    private Buffer mIndBuff;

    private int indicesNumber = 0;
    private int verticesNumber = 0;

    private float width;
    private float height;


    public Rectangle(float width, float height) {
        this.width = width;
        this.height = height;

        setVerts();
        setTexCoords();
        setNorms();
        setIndices();
    }


    private void setVerts() {
        double[] RECTANGLE_VERTS = {-width / 2, height / 2, 0, width / 2, height / 2, 0, width / 2, -height / 2, 0, -width / 2, -height / 2, 0};
        mVertBuff = fillBuffer(RECTANGLE_VERTS);
        verticesNumber = RECTANGLE_VERTS.length / 3;
    }


    private void setTexCoords() {
        double[] RECTANGLE_TEX_COORDS = {0, 1, 1, 1, 1, 0, 0, 0};
        mTexCoordBuff = fillBuffer(RECTANGLE_TEX_COORDS);

    }


    private void setNorms() {
        double[] RECTANGLE_NORMS = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        mNormBuff = fillBuffer(RECTANGLE_NORMS);
    }


    private void setIndices() {
        short[] RECTANGLE_INDICES = {0, 2, 1, 0, 3, 2};
        mIndBuff = fillBuffer(RECTANGLE_INDICES);
        indicesNumber = RECTANGLE_INDICES.length;
    }


    public int getNumObjectIndex() {
        return indicesNumber;
    }


    @Override
    public int getNumObjectVertex() {
        return verticesNumber;
    }


    @Override
    public Buffer getBuffer(BUFFER_TYPE bufferType) {
        Buffer result = null;
        switch (bufferType) {
            case BUFFER_TYPE_VERTEX:
                result = mVertBuff;
                break;
            case BUFFER_TYPE_TEXTURE_COORD:
                result = mTexCoordBuff;
                break;
            case BUFFER_TYPE_NORMALS:
                result = mNormBuff;
                break;
            case BUFFER_TYPE_INDICES:
                result = mIndBuff;
            default:
                break;

        }

        return result;
    }

}
