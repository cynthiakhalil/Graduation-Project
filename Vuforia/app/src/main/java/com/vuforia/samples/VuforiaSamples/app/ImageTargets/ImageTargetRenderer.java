/*==============================================================================
Copyright (c) 2015-2016 PTC Inc. All Rights Reserved.


Copyright (c) 2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Confidential and Proprietary - Qualcomm Connected Experiences, Inc.
Vuforia is a trademark of PTC Inc., registered in the United States and other
countries.

@file
    StereoRenderingRenderer.java

@brief
    Sample usage of rendering primitives to draw augmented/virtual content

==============================================================================*/


package com.vuforia.samples.VuforiaSamples.app.ImageTargets;

import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.widget.Toast;

import com.vuforia.COORDINATE_SYSTEM_TYPE;
import com.vuforia.CameraDevice;
import com.vuforia.Device;
import com.vuforia.EyewearDevice;
import com.vuforia.GLTextureUnit;
import com.vuforia.Mesh;
import com.vuforia.Renderer;
import com.vuforia.RenderingPrimitives;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.Vec4F;
import com.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.vuforia.VIEW;
import com.vuforia.ViewList;
import com.vuforia.Vuforia;
import com.vuforia.samples.SampleApplication.SampleApplicationSession;
import com.vuforia.samples.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.samples.SampleApplication.utils.Rectangle;
import com.vuforia.samples.SampleApplication.utils.SampleUtils;
import com.vuforia.samples.SampleApplication.utils.Teapot;
import com.vuforia.samples.SampleApplication.utils.Texture;

import java.util.HashMap;
import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


public class ImageTargetRenderer implements GLSurfaceView.Renderer
{
    private static final String LOGTAG = "StereoRendering";

    private SampleApplicationSession mVuforiaAppSession;
    private ImageTargets mActivity;

    private Teapot mTeapot = new Teapot();
    private Vector<Texture> mTextures;

    // True if we're rendering
    private boolean mIsActive = false;

    // Rendering objects used by this class
    private Device mDevice = null;
    private Renderer mRenderer = null;
    private RenderingPrimitives mRenderingPrimitives = null;

    // Default scale for the augmentation
    private static final float OBJECT_SCALE_FLOAT = 0.003f;

    // Default values for near and far OpenGL clip-planes
    public static final float NEAR_PLANE = 0.05f;
    public static final float FAR_PLANE =  5f;

    // Use texture unit 0 for the video background; this will hold the camera frame and we want to reuse for all views
    // and we will need to use a different texture unit for the augmentation
    private static final int VB_VIDEO_TEXTURE_UNIT = 0;

    // Default scene-scale factor, will be updated later
    private float mSceneScaleFactors[] = new float [VIEW.VIEW_COUNT];

    // Handles for the video background shaders
    private int mVBShaderProgramID;
    private int mVBVertexHandle;
    private int mVBTextureCoordinateHandle;
    private int mVBTextureSampler2DHandle;
    private int mVBProjectionHandle;

    // Handles for the augmentation shaders
    private int mARShaderProgramID;
    private int mARVertexHandle;
    private int mARNormalHandle;
    private int mARTextureCoordinateHandle;
    private int mARModelViewProjectionHandle;
    private int mARTextureSampler2DHandle;

    // Handles for the distortion shaders
    private int mDistShaderProgramID = 0;
    private int mDistVertexHandle = 0;
    private int mDistTextureCoordinateHandle = 0;
    private int mDistTextureSampler2DHandle = 0;

    // Handles for the distortion framebuffer
    private int[] mDistFrameBufferID = { 0 };
    private int[] mDistTextureColorID = { 0 };
    private int[] mDistTextureDepthID = { 0 };

    private boolean showedToast = false;

    private HashMap<String, Rectangle> mRectangles;

    private static MediaPlayer mediaPlayer;

    public ImageTargetRenderer(ImageTargets activity, SampleApplicationSession session)
    {
        mActivity = activity;
        mVuforiaAppSession = session;

        mRectangles = new HashMap<>();

        float inverseScale = 1/OBJECT_SCALE_FLOAT;

        mRectangles.put("Nicely_Noon_Normal", new Rectangle(.247f * inverseScale, .18525f * inverseScale));
        mRectangles.put("Fisk_Noon_Normal", new Rectangle(.247f * inverseScale, .18525f * inverseScale));
        mRectangles.put("IssamFares_Noon_Normal", new Rectangle(.247f * inverseScale, .18525f * inverseScale));
        mRectangles.put("Bliss_Noon_Normal", new Rectangle(.247f * inverseScale, .18525f * inverseScale));

        mRectangles.put("Nicely_Noon_Cropped", new Rectangle(.247f * inverseScale, .108365f * inverseScale));
        mRectangles.put("Fisk_Noon_Cropped", new Rectangle(.247f * inverseScale, .093230f * inverseScale));
        mRectangles.put("IssamFares_Noon_Cropped", new Rectangle(.247f * inverseScale, .135676f * inverseScale));
        mRectangles.put("Bliss_Noon_Cropped", new Rectangle(.247f * inverseScale, .094558f * inverseScale));

        mRectangles.put("Nicely_4PM_Normal", new Rectangle(.247f * inverseScale, .18525f * inverseScale));
        mRectangles.put("Fisk_4PM_Normal", new Rectangle(.247f * inverseScale, .18525f * inverseScale));
        mRectangles.put("IssamFares_4PM_Normal", new Rectangle(.247f * inverseScale, .18525f * inverseScale));
        mRectangles.put("Bliss_4PM_Normal", new Rectangle(.247f * inverseScale, .18525f * inverseScale));

        mRectangles.put("Nicely_4PM_Cropped", new Rectangle(.247f * inverseScale, .105899f * inverseScale));
        mRectangles.put("Fisk_4PM_Cropped", new Rectangle(.247f * inverseScale, .093230f * inverseScale));
        mRectangles.put("IssamFares_4PM_Cropped", new Rectangle(.247f * inverseScale, .130544f * inverseScale));
        mRectangles.put("Bliss_4PM_Cropped", new Rectangle(.247f * inverseScale, .096166f * inverseScale));

        mediaPlayer = new MediaPlayer();
    }

    private void playAudio(String buildingName){
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }

            mediaPlayer = new MediaPlayer();

            AssetFileDescriptor descriptor = mActivity.getAssets().openFd(buildingName + ".mp3");
            mediaPlayer.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            descriptor.close();

            mediaPlayer.prepare();
            mediaPlayer.setVolume(1f, 1f);
            mediaPlayer.setLooping(false);
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setActive(boolean isActive)
    {
        // Enable or disable the renderer
        mIsActive = isActive;
    }

    public synchronized void updateRenderingPrimitives()
    {
        // Update the rendering primitives used for drawing; this method is synchronized with renderScene
        mDevice = Device.getInstance();
        mRenderingPrimitives = mDevice.getRenderingPrimitives();
    }

    @Override
    public void onDrawFrame(GL10 gl)
    {
        // Draw the current frame; perform some sanity checks before we attempt to render
        // 1) The renderer should be active
        // 2) The device should have been created
        // 3) The rendering primitives should have been created
        if (mIsActive && mDevice != null && mRenderingPrimitives != null)
        {
            renderFrame();
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        // Called when the rendering surface is created or recreated
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");

        // Call Vuforia function to (re)initialize rendering after first use or after OpenGL ES context was lost
        // (for example, after onPause/onResume)
        mVuforiaAppSession.onSurfaceCreated();

        // Hide the loading dialog
        mActivity.loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        // Called when the surface changes size
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged width=" + width + " height=" + height);

        // Call Vuforia function to handle render surface size changes
        mVuforiaAppSession.onSurfaceChanged(width, height);

        // Update the rendering primitives used to draw on the display according to the new surface size
        updateRenderingPrimitives();

        // Initialize the app rendering pipeline (shaders and distortion framebuffer)
        setupRendering();
    }

    private boolean setupAugmentationShaders()
    {
        boolean result = false;     // Unless proven otherwise

        // Load the textures for the augmentation
        for (Texture texture : mTextures)
        {
            GLES20.glGenTextures(1, texture.mTextureID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.mTextureID[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, texture.mWidth, texture.mHeight, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, texture.mData);
        }

        // Create the augmentation rendering shaders
        mARShaderProgramID = SampleUtils.createProgramFromShaderSrc(AugmentationShaders.VERTEX_SHADER,
                AugmentationShaders.FRAGMENT_SHADER);

        // Setup the shader variable handles only if we successfully created the shader program
        if (mARShaderProgramID > 0)
        {
            // Activate the shader program
            GLES20.glUseProgram(mARShaderProgramID);

            // Retrieve a handle for the vertex position, vertex normal, texture coordinate, texture sampler and MVP
            // matrix variables from the shader program
            mARVertexHandle = GLES20.glGetAttribLocation(mARShaderProgramID, "vertexPosition");
            mARNormalHandle = GLES20.glGetAttribLocation(mARShaderProgramID, "vertexNormal");
            mARTextureCoordinateHandle = GLES20.glGetAttribLocation(mARShaderProgramID, "vertexTexCoord");
            mARTextureSampler2DHandle = GLES20.glGetUniformLocation(mARShaderProgramID, "texSampler2D");
            mARModelViewProjectionHandle = GLES20.glGetUniformLocation(mARShaderProgramID, "modelViewProjectionMatrix");

            // Stop using the shader program
            GLES20.glUseProgram(0);

            result = true;
        }

        return result;
    }

    private boolean setupVideoBackgroundShaders()
    {
        boolean result = false;     // Unless proven otherwise

        // Create the video background rendering shaders
        mVBShaderProgramID = SampleUtils.createProgramFromShaderSrc(VideoBackgroundShaders.VERTEX_SHADER,
                VideoBackgroundShaders.FRAGMENT_SHADER);

        // Setup the shader variable handles only if we successfully created the shader program
        if (mVBShaderProgramID > 0)
        {
            // Activate the shader program
            GLES20.glUseProgram(mVBShaderProgramID);

            // Retrieve a handle for the vertex position, texture coordinate, texture sampler and projection matrix
            // variables from the shader program
            mVBVertexHandle = GLES20.glGetAttribLocation(mVBShaderProgramID, "vertexPosition");
            mVBTextureCoordinateHandle = GLES20.glGetAttribLocation(mVBShaderProgramID, "vertexTexCoord");
            mVBTextureSampler2DHandle = GLES20.glGetUniformLocation(mVBShaderProgramID, "texSampler2D");
            mVBProjectionHandle = GLES20.glGetUniformLocation(mVBShaderProgramID, "projectionMatrix");

            // Stop using the shader program
            GLES20.glUseProgram(0);

            result = true;
        }

        return result;
    }

    private boolean setupDistortionShaders()
    {
        boolean result = false;     // Unless proven otherwise

        // Create the distortion rendering shaders
        mDistShaderProgramID = SampleUtils.createProgramFromShaderSrc(DistortionShaders.VERTEX_SHADER,
                DistortionShaders.FRAGMENT_SHADER);

        if (mDistShaderProgramID > 0)
        {
            // Create the textures for viewer distortion
            GLES20.glGenTextures(1, mDistTextureColorID, 0);
            GLES20.glGenTextures(1, mDistTextureDepthID, 0);

            // Create the framebuffer for viewer distortion
            GLES20.glGenFramebuffers(1, mDistFrameBufferID, 0);

            // Activate the shader program
            GLES20.glUseProgram(mDistShaderProgramID);

            // Retrieve a handle for the vertex position, texture coordinate and texture sampler variables from the
            // shader program
            mDistVertexHandle = GLES20.glGetAttribLocation(mDistShaderProgramID, "vertexPosition");
            mDistTextureCoordinateHandle = GLES20.glGetAttribLocation(mDistShaderProgramID, "vertexTexCoord");
            mDistTextureSampler2DHandle = GLES20.glGetUniformLocation(mDistShaderProgramID, "texSampler2D");

            // Stop using the program
            GLES20.glUseProgram(0);

            result = true;
        }

        return result;
    }

    private boolean setupDistortionBuffer()
    {
        boolean result = true;     // Unless proven otherwise

        // The 'postprocess' view is a special one that indicates that a distortion post-processing step is required.
        // If this is present, then we need to prepare an off-screen buffer to support the distortion.
        final int[] textureSize = mRenderingPrimitives.getDistortionTextureSize(VIEW.VIEW_POSTPROCESS).getData();

        // Check if the texture size is valid; if not, the device/configuration doesn't support distortion
        if (textureSize[0] == 0 || textureSize[1] == 0)
        {
            result = false;
            Log.w(LOGTAG, "Viewer distortion is not supported in this configuration");
        }
        else
        {
            // Bind the color texture
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mDistTextureColorID[0]);
            result &= SampleUtils.checkGLError("Distortion framebuffer color texture bind failed");

            // Initialize texture size, format, pixel size
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, textureSize[0], textureSize[1],
                    0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, null);

            // Configure the texture parameters
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            // Bind the depth texture
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mDistTextureDepthID[0]);
            result &= SampleUtils.checkGLError("Distortion framebuffer depth texture bind failed");

            // Initialize texture size, format, pixel size
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_DEPTH_COMPONENT, textureSize[0], textureSize[1],
                    0, GLES20.GL_DEPTH_COMPONENT, GLES20.GL_UNSIGNED_INT, null);
        }

        return result;
    }

    private void setupRendering()
    {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f : 1.0f);

        // Retrieve the renderer for later use
        mRenderer = Renderer.getInstance();

        // Compute the scene-scale factor, which will be used to scale the video background and augmentation on occluded
        // eyewear devices and viewer devices
        mSceneScaleFactors[VIEW.VIEW_LEFTEYE] = getSceneScaleFactor(VIEW.VIEW_LEFTEYE);
        mSceneScaleFactors[VIEW.VIEW_RIGHTEYE] = getSceneScaleFactor(VIEW.VIEW_RIGHTEYE);

        // Initializes the OpenGL shaders for the video background, the augmentation rendering and the viewer distortion
        setupVideoBackgroundShaders();
        setupAugmentationShaders();
        setupDistortionShaders();
        setupDistortionBuffer();
    }

    private float getSceneScaleFactor(int viewId)
    {
        // Get the y-dimension of the physical camera field of view
        float[] fovVector = CameraDevice.getInstance().getCameraCalibration().getFieldOfViewRads().getData();
        float cameraFovYRads = fovVector[1];

        // Get the y-dimension of the virtual camera field of view
        Vec4F virtualFovVector = mRenderingPrimitives.getEffectiveFov(viewId); // {left, right, bottom, top}
        float virtualFovYRads = virtualFovVector.getData()[2] + virtualFovVector.getData()[3];

        // The scene-scale factor represents the proportion of the viewport that is filled by the video background when
        // projected onto the same plane. In order to calculate this, let 'd' be the distance between the cameras and
        // the plane. The height of the projected image 'h' on this plane can then be calculated:
        //   tan(fov/2) = h/2d
        // which rearranges to:
        //   2d = h/tan(fov/2)
        // Since 'd' is the same for both cameras, we can combine the equations for the two cameras:
        //   hPhysical/tan(fovPhysical/2) = hVirtual/tan(fovVirtual/2)
        // Which rearranges to:
        //   hPhysical/hVirtual = tan(fovPhysical/2)/tan(fovVirtual/2)
        // ... which is the scene-scale factor.
        return (float) (Math.tan(cameraFovYRads / 2) / Math.tan(virtualFovYRads / 2));
    }

    private boolean setupEyewearDevice()
    {
        boolean result = true;      // Unless proven otherwise

        // We only attempt the next steps if this is an eyewear device; viewer devices do not need to be switched to
        // extended-display (stereo) mode
        if (mDevice instanceof EyewearDevice)
        {
            EyewearDevice eyewearDevice = (EyewearDevice) mDevice;

            // Attempt to set the device in extended-display (stereo) mode if it's not using that mode already; log an
            // error if the switch fails
            if (eyewearDevice.isDualDisplay() && !eyewearDevice.isDisplayExtended())
            {
                result = eyewearDevice.setDisplayExtended(true);

                if (!result)
                {
                    Log.e(LOGTAG, "Eyewear device display setup failed");
                }
            }
        }

        return result;
    }

    private void setupVertexCulling()
    {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // We must detect if background reflection is active and adjust the culling direction; if reflection is active,
        // this means the final model-view matrix has been reflected as well, therefore the default counter clockwise
        // face culling would result in "inside out" (incorrect) models
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        if (mRenderer.getVideoBackgroundConfig().getReflection() ==
                VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
        {
            GLES20.glFrontFace(GLES20.GL_CW);   // Front camera
        }
        else
        {
            GLES20.glFrontFace(GLES20.GL_CCW);  // Back camera
        }
    }

    private boolean setupVideoBackground()
    {
        boolean result = false;     // Unless proven otherwise
        boolean isEyewearDevice = mDevice instanceof EyewearDevice;

        // There are two cases in which we want to enable rendering of the video background
        // 1) We're using a viewer device (for example Cardboard, not an eyewear device)
        // 2) We're using an occluded eyewear device (for example GearVR, not see-through)
        if (!isEyewearDevice || !((EyewearDevice) mDevice).isSeeThru())
        {
            // Bind the video background texture. Log an error if the bind fails
            result = mRenderer.updateVideoBackgroundTexture(new GLTextureUnit(VB_VIDEO_TEXTURE_UNIT));

            if (!result)
            {
                Log.e(LOGTAG, "Unable to bind video background texture!");
            }
        }

        return result;
    }

    private boolean prepareForDistortion()
    {
        boolean result = true;     // Unless proven otherwise

        // Route all drawing commands to the distortion framebuffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mDistFrameBufferID[0]);
        result &= SampleUtils.checkGLError("Distortion framebuffer bind failed");

        // Attach the distortion textures to the distortion framebuffer
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                mDistTextureColorID[0], 0);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_TEXTURE_2D,
                mDistTextureDepthID[0], 0);

        result &= SampleUtils.checkGLError("Distortion texture attach failed");

        return result;
    }

    private int[] setupViewportForDistortion(int viewID)
    {
        // We're drawing on the distortion framebuffer, so the viewport is relative to that buffer
        final int[] viewport = mRenderingPrimitives.getDistortionTextureViewport(viewID).getData();
        GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        // We're drawing to the distortion framebuffer, so need to clear part of that buffer
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(viewport[0], viewport[1], viewport[2], viewport[3]);

        // Clear the buffer
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        return viewport;
    }

    private int[] setupViewport(final int viewID)
    {
        // We're drawing on the display, so the viewport is relative to the view
        final int[] viewport = mRenderingPrimitives.getViewport(viewID).getData();
        GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        return viewport;
    }

    private boolean renderVideoBackground(int viewID, final int[] viewport, boolean applySceneScale)
    {
        // Retrieve the video background mesh
        final Mesh vbMesh = mRenderingPrimitives.getVideoBackgroundMesh(viewID);

        // Retrieve the projection matrix required to draw the video background and convert it for use with OpenGL
        final float[] vbProjectionMatrix =
                Tool.convert2GLMatrix(mRenderingPrimitives.getVideoBackgroundProjectionMatrix(viewID,
                        COORDINATE_SYSTEM_TYPE.COORDINATE_SYSTEM_CAMERA)).getData();

        // Apply the scene-scale factor on occluded eyewear, to scale the video background so that it lines up with the
        // real world
        if (applySceneScale)
        {
            float sceneScaleFactor = mSceneScaleFactors[viewID];
            Matrix.scaleM(vbProjectionMatrix, 0, sceneScaleFactor, sceneScaleFactor, 1.0f);

            // Apply a scissor around the video background, so that the augmentation doesn't 'bleed' outside it
            int[] scissorRect = SampleUtils.getScissorRect(vbProjectionMatrix, viewport);

            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glScissor(scissorRect[0], scissorRect[1], scissorRect[2], scissorRect[3]);
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        // Load the shader and upload the vertex/texcoord/index data
        GLES20.glUseProgram(mVBShaderProgramID);
        GLES20.glVertexAttribPointer(mVBVertexHandle, 3, GLES20.GL_FLOAT, false, 0, vbMesh.getPositions());
        GLES20.glVertexAttribPointer(mVBTextureCoordinateHandle, 2, GLES20.GL_FLOAT, false, 0, vbMesh.getUVs());

        GLES20.glUniform1i(mVBTextureSampler2DHandle, VB_VIDEO_TEXTURE_UNIT);

        // Render the video background with the custom shader
        // First, we enable the vertex arrays
        GLES20.glEnableVertexAttribArray(mVBVertexHandle);
        GLES20.glEnableVertexAttribArray(mVBTextureCoordinateHandle);

        // Pass the projection matrix to OpenGL
        GLES20.glUniformMatrix4fv(mVBProjectionHandle, 1, false, vbProjectionMatrix, 0);

        // Then, issue the render call
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, vbMesh.getNumTriangles() * 3, GLES20.GL_UNSIGNED_SHORT,
                vbMesh.getTriangles());

        // Finally, disable the vertex arrays
        GLES20.glDisableVertexAttribArray(mVBVertexHandle);
        GLES20.glDisableVertexAttribArray(mVBTextureCoordinateHandle);

        return SampleUtils.checkGLError("Video background rendering failed");
    }

    private boolean renderTrackable(final TrackableResult result, final float[] projectionGL)
    {
        final float[] modelViewProjection = new float[projectionGL.length];
        final Trackable trackable = result.getTrackable();

        final String name = trackable.getName().split("_")[0];

        if (!showedToast){
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    playAudio(name);
                    Toast.makeText(mActivity, "Detected " + name, Toast.LENGTH_SHORT).show();
                }
            });
            showedToast = true;
        }

        int textureIndex = ImageTargets.GetTextureIndex(name);

        // Retrieve the trackable pose
        final float[] modelViewGL = Tool.convertPose2GLMatrix(result.getPose()).getData();

        // Translate and scale the augmentation to the right position and size
        Matrix.translateM(modelViewGL, 0, 0.0f, 0.0f, OBJECT_SCALE_FLOAT);
        Matrix.scaleM(modelViewGL, 0, OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT);

        // Compute the final model-view-projection matrix
        Matrix.multiplyMM(modelViewProjection, 0, projectionGL, 0, modelViewGL, 0);

        // Activate the shader program and bind the vertex/normal/texture variables
        GLES20.glUseProgram(mARShaderProgramID);

        Rectangle rectangle = mRectangles.get(trackable.getName());

        GLES20.glVertexAttribPointer(mARVertexHandle, 3, GLES20.GL_FLOAT, false, 0, rectangle.getVertices());
        GLES20.glVertexAttribPointer(mARNormalHandle, 3, GLES20.GL_FLOAT, false, 0, rectangle.getNormals());
        GLES20.glVertexAttribPointer(mARTextureCoordinateHandle, 2, GLES20.GL_FLOAT, false, 0, rectangle.getTexCoords());

        GLES20.glEnableVertexAttribArray(mARVertexHandle);
        GLES20.glEnableVertexAttribArray(mARNormalHandle);
        GLES20.glEnableVertexAttribArray(mARTextureCoordinateHandle);

        // Activate texture 0, bind it, and pass to shader
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures.get(textureIndex).mTextureID[0]);
        GLES20.glUniform1i(mARTextureSampler2DHandle, 0);

        // Pass the model view matrix to the shader
        GLES20.glUniformMatrix4fv(mARModelViewProjectionHandle, 1, false, modelViewProjection, 0);

        // Finally draw the teapot
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, rectangle.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT,
                rectangle.getIndices());

        // Disable the arrays
        GLES20.glDisableVertexAttribArray(mARVertexHandle);
        GLES20.glDisableVertexAttribArray(mARNormalHandle);
        GLES20.glDisableVertexAttribArray(mARTextureCoordinateHandle);

        printUserData(trackable);

        return SampleUtils.checkGLError("Trackable rendering failed");
    }

    private boolean renderAugmentation(int viewID, final State state, boolean applySceneScale)
    {
        boolean result = true;      // Unless proven otherwise

        // Retrieve the projection matrix required to draw the augmentation, add in the near and far clip-planes and
        // convert the matrix for use with OpenGL
        final float[] projectionGL =
                Tool.convertPerspectiveProjection2GLMatrix(mRenderingPrimitives.getProjectionMatrix(viewID,
                        COORDINATE_SYSTEM_TYPE.COORDINATE_SYSTEM_CAMERA), NEAR_PLANE, FAR_PLANE).getData();

        // Retrieve the pose adjustment matrix to apply to the current view; this matrix is used to adjust the
        // augmentation pose so that it looks correct for the user's eye (the current view)
        final float[] eyeAdjustmentGL =
                Tool.convert2GLMatrix(mRenderingPrimitives.getEyeDisplayAdjustmentMatrix(viewID)).getData();

        final float[] adjustedProjectionGL = new float[projectionGL.length];

        // Apply the projection matrix to the augmentation pose
        Matrix.multiplyMM(adjustedProjectionGL, 0, projectionGL, 0, eyeAdjustmentGL, 0);

        // Apply the scene-scale factor on occluded eyewear, to scale the augmentation so that it lines up with the real
        // world
        if (applySceneScale)
        {
            float sceneScaleFactor = mSceneScaleFactors[viewID];
            Matrix.scaleM(adjustedProjectionGL, 0, sceneScaleFactor, sceneScaleFactor, 1.0f);
        }

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        if (state.getNumTrackableResults() == 0) {
            showedToast = false;
            if (mediaPlayer.isPlaying()){
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = new MediaPlayer();
            }
        }

        // Loop through all trackables detected in this frame
        for (int trackableID = 0; trackableID < state.getNumTrackableResults(); trackableID++)
        {
            result &= renderTrackable(state.getTrackableResult(trackableID), adjustedProjectionGL);
        }

        if (applySceneScale)
        {
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        }

        result &= SampleUtils.checkGLError("Augmentation rendering failed");

        return result;
    }

    private boolean applyDistortion()
    {
        final int[] screenViewport = mRenderingPrimitives.getViewport(VIEW.VIEW_POSTPROCESS).getData();
        final Mesh distortionMesh = mRenderingPrimitives.getDistortionTextureMesh(VIEW.VIEW_POSTPROCESS);

        // Render the framebuffer to the screen using the texture
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(screenViewport[0], screenViewport[1], screenViewport[2], screenViewport[3]);

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        // Disable depth testing
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        // Setup the shaders
        GLES20.glUseProgram(mDistShaderProgramID);

        // Pass our FBO texture to the shader
        GLES20.glUniform1i(mDistTextureSampler2DHandle, 0);

        // Activate texture unit
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // Bind the texture with the stereo rendering
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mDistTextureColorID[0]);

        // Enable vertex and texture coordinate vertex attribute arrays:
        GLES20.glEnableVertexAttribArray(mDistVertexHandle);
        GLES20.glEnableVertexAttribArray(mDistTextureCoordinateHandle);

        // Draw geometry
        GLES20.glVertexAttribPointer(mDistVertexHandle, 3, GLES20.GL_FLOAT, false, 0, distortionMesh.getPositions());
        GLES20.glVertexAttribPointer(mDistTextureCoordinateHandle, 2, GLES20.GL_FLOAT, false, 0, distortionMesh.getUVs());
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, distortionMesh.getNumTriangles() * 3, GLES20.GL_UNSIGNED_SHORT,
                distortionMesh.getTriangles());

        // Disable vertex and texture coordinate vertex attribute arrays again:
        GLES20.glDisableVertexAttribArray(mDistVertexHandle);
        GLES20.glDisableVertexAttribArray(mDistTextureCoordinateHandle);

        return SampleUtils.checkGLError("Distortion post-processing failed");
    }

    private synchronized void renderFrame()
    {
        // Setup an eyewear device if required (viewer devices don't need setup); return immediately if the setup fails
        if (!setupEyewearDevice())
        {
            return;
        }

        // Retrieve the Vuforia Renderer state (for example, the trackables in view); this must be called before any
        // other rendering operations (for example, setupVideoBackground)
        final State state = mRenderer.begin();

        // Retrieve the views we need to loop over for rendering
        final ViewList renderingViews = mRenderingPrimitives.getRenderingViews();

        // Perform some initial setup
        setupVertexCulling();
        boolean applyDistortion = renderingViews.contains(VIEW.VIEW_POSTPROCESS);
        boolean applySceneScale = mDevice.isViewerActive();

        // If we're applying a distortion post-processing step we need some additional setup first
        if (applyDistortion)
        {
            prepareForDistortion();
        }

        for (int i = 0; i < renderingViews.getNumViews(); i++)
        {
            int viewID = renderingViews.getView(i);

            // Any post-processing step is a special case that will be completed after the render loop
            if (viewID != VIEW.VIEW_POSTPROCESS)
            {
                // Setup the video background rendering according to the device in use
                boolean renderVideoBackground = setupVideoBackground();

                // Setup the viewport according to whether we're performing distortion or not
                final int[] viewport = applyDistortion ? setupViewportForDistortion(viewID) : setupViewport(viewID);

                // Render the video background on viewer devices and on occluded eyewear devices
                if (renderVideoBackground)
                {
                    // Note: the scene-scale factor is only  applied on occluded eyewear devices, to make sure that
                    // the video background lines up with the real world; the scene-scale factor should not be applied
                    // on see-through eyewear devices, as those don't show the video background
                    renderVideoBackground(viewID, viewport, applySceneScale);
                }

                // Render the augmentation
                // Note: the scene-scale factor is only applied on occluded eyewear devices, to make sure that the
                // augmentation lines up with the video background; the scene-scale factor should not be applied on
                // see-through eyewear devices, as those don't show the video background, and the user calibration
                // ensures that the augmentation matches the real world
                renderAugmentation(viewID, state, renderVideoBackground && applySceneScale);
            }
        }

        // Apply the distortion post-processing step if required
        if (applyDistortion)
        {
            applyDistortion();
        }

        // We've finished rendering
        mRenderer.end();
    }

    private void printUserData(Trackable trackable)
    {
        String userData = (String) trackable.getUserData();
        Log.d(LOGTAG, "UserData: Retrieved User Data \"" + userData + "\"");
    }

    public void setTextures(Vector<Texture> textures)
    {
        mTextures = textures;
    }
}
