/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.core.codelabs.streetscapegeometry

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.Coordinates2d
import com.google.ar.core.StreetscapeGeometry
import com.google.ar.core.TrackingState
import com.google.ar.core.codelabs.streetscapegeometry.helpers.StreetscapeGeometryRenderer
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper
import com.google.ar.core.examples.java.common.samplerender.Framebuffer
import com.google.ar.core.examples.java.common.samplerender.SampleRender
import com.google.ar.core.examples.java.common.samplerender.arcore.BackgroundRenderer
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.io.IOException


class StreetscapeCodelabRenderer(val activity: StreetscapeGeometryActivity) :
  SampleRender.Renderer, DefaultLifecycleObserver {
  companion object {
    val TAG = "StreetscapeCodelabRenderer"

    private val Z_NEAR = 0.1f
    private val Z_FAR = 1000f
  }

  lateinit var backgroundRenderer: BackgroundRenderer
  lateinit var virtualSceneFramebuffer: Framebuffer
  var hasSetTextureNames = false

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  val modelMatrix = FloatArray(16)
  val viewMatrix = FloatArray(16)
  val projectionMatrix = FloatArray(16)
  val modelViewMatrix = FloatArray(16) // view x model
  val modelViewProjectionMatrix = FloatArray(16) // projection x view x model

  val center = floatArrayOf(0.5f, 0.5f)
  val centerCoords = floatArrayOf(0.0f, 0.0f)

  val session
    get() = activity.arCoreSessionHelper.session

  val displayRotationHelper = DisplayRotationHelper(activity)
  val trackingStateHelper = TrackingStateHelper(activity)

  val streetscapeGeometryRenderer = StreetscapeGeometryRenderer(activity)
  var showStreetscapeGeometry = false
  var fillStreetscapeGeometry = false

  override fun onResume(owner: LifecycleOwner) {
    displayRotationHelper.onResume()
    hasSetTextureNames = false
  }

  override fun onPause(owner: LifecycleOwner) {
    displayRotationHelper.onPause()
  }

  override fun onSurfaceCreated(render: SampleRender) {
    // Prepare the rendering objects.
    // This involves reading shaders and 3D model files, so may throw an IOException.
    try {
      backgroundRenderer = BackgroundRenderer(render)
      virtualSceneFramebuffer = Framebuffer(render, /*width=*/ 1, /*height=*/ 1)

      backgroundRenderer.setUseDepthVisualization(render, false)
      backgroundRenderer.setUseOcclusion(render, false)

      streetscapeGeometryRenderer.init(render)
    } catch (e: IOException) {
      Log.e(TAG, "Failed to read a required asset file", e)
      showError("Failed to read a required asset file: $e")
    }
  }

  override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
    displayRotationHelper.onSurfaceChanged(width, height)
    virtualSceneFramebuffer.resize(width, height)
  }
  override fun onDrawFrame(render: SampleRender) {
    val session = session ?: return

    if (!hasSetTextureNames) {
      session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))
      hasSetTextureNames = true
    }

    displayRotationHelper.updateSessionIfNeeded(session)

    val frame =
      try {
        session.update()
      } catch (e: CameraNotAvailableException) {
        Log.e(TAG, "Camera not available during onDrawFrame", e)
        showError("Camera not available. Try restarting the app.")
        return
      }

    val camera = frame.camera

    backgroundRenderer.updateDisplayGeometry(frame)
    trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

    if (frame.timestamp != 0L) {
      backgroundRenderer.drawBackground(render)
    }

    if (camera.trackingState == TrackingState.PAUSED) {
      return
    }
    camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR)
    camera.getViewMatrix(viewMatrix, 0)

    frame.transformCoordinates2d(
      Coordinates2d.VIEW_NORMALIZED,
      center,
      Coordinates2d.VIEW,
      centerCoords
    )
    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f)
    //</editor-fold>

    if (showStreetscapeGeometry && fillStreetscapeGeometry) {
       val streetscapeGeometry = session.getAllTrackables(StreetscapeGeometry::class.java)
       streetscapeGeometryRenderer.render(render, streetscapeGeometry)
    }

    val centerHits = frame.hitTest(centerCoords[0], centerCoords[1])
    val centerGeometryHit = centerHits.firstOrNull { it.trackable is StreetscapeGeometry }
    if (centerGeometryHit == null) {
      activity.view.updateStreetscapeGeometryStatusTextNone()
    } else {
      activity.view.updateStreetscapeGeometryStatusText(
        centerGeometryHit.trackable as StreetscapeGeometry,
        centerGeometryHit.distance
      )
    }

    val earth = session.earth
    if (earth?.trackingState == TrackingState.TRACKING) {
      val cameraGeospatialPose = earth.cameraGeospatialPose
      activity.view.updateEarthStatusText(earth, cameraGeospatialPose)
    }

    backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR)
  }

  private fun showError(errorMessage: String) =
    activity.view.snackbarHelper.showError(activity, errorMessage)
}
