/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.core.codelabs.streetscapegeometry.helpers

import android.opengl.Matrix
import com.google.ar.core.StreetscapeGeometry
import com.google.ar.core.TrackingState
import com.google.ar.core.codelabs.streetscapegeometry.StreetscapeGeometryActivity
import com.google.ar.core.examples.java.common.samplerender.IndexBuffer
import com.google.ar.core.examples.java.common.samplerender.Mesh
import com.google.ar.core.examples.java.common.samplerender.SampleRender
import com.google.ar.core.examples.java.common.samplerender.Shader
import com.google.ar.core.examples.java.common.samplerender.Shader.BlendFactor
import com.google.ar.core.examples.java.common.samplerender.VertexBuffer


class StreetscapeGeometryRenderer(val activity: StreetscapeGeometryActivity) {
  private val streetscapeGeometryToMeshes = hashMapOf<StreetscapeGeometry, Mesh>()

  private val wallsColor  = floatArrayOf(0.0f, 0.0f, 0.7f, 0.8f)
  private val landColor = floatArrayOf(0.5f, 0.5f, 0.0f, 0.8f)

  private lateinit var streetscapeGeometryTerrainShader: Shader
  private lateinit var streetscapeGeometryBuildingShader: Shader

  fun init(render: SampleRender) {
    streetscapeGeometryBuildingShader = Shader.createFromAssets(
      render,
      "shaders/streetscape_geometry.vert",
      "shaders/streetscape_geometry.frag",
      null
    )
      .setBlend(
        BlendFactor.DST_ALPHA,
        BlendFactor.ONE
      )


    streetscapeGeometryTerrainShader = Shader.createFromAssets(
      render,
      "shaders/streetscape_geometry.vert",
      "shaders/streetscape_geometry.frag",
      null
    )
      .setBlend(
        BlendFactor.DST_ALPHA,  // RGB (src)
        BlendFactor.ONE
      ) // ALPHA (dest)
  }

  fun render(render: SampleRender, streetscapeGeometries: Collection<StreetscapeGeometry>) {
    val modelMatrix = activity.renderer.modelMatrix;
    val viewMatrix = activity.renderer.viewMatrix;
    val modelViewProjectionMatrix = activity.renderer.modelViewProjectionMatrix
    val projectionMatrix = activity.renderer.projectionMatrix
    val modelViewMatrix = activity.renderer.modelViewMatrix

    updateStreetscapeGeometries(render, streetscapeGeometries)
    for ((streetscapeGeometry, mesh) in streetscapeGeometryToMeshes) {
      if (streetscapeGeometry.trackingState != TrackingState.TRACKING) {
        continue
      }
      val pose = streetscapeGeometry.meshPose
      pose.toMatrix(modelMatrix, 0)

      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

      // Fills buildings with the color

//      if (streetscapeGeometry.type == StreetscapeGeometry.Type.BUILDING) {
//        streetscapeGeometryBuildingShader
//          .setVec4(
//            "u_Color", wallsColor)
//          .setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
//        render.draw(mesh, streetscapeGeometryBuildingShader)
//      } else

      // Fills terrain with the color
      if (streetscapeGeometry.type == StreetscapeGeometry.Type.TERRAIN) {
        streetscapeGeometryTerrainShader
          .setVec4("u_Color", landColor)
          .setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
        render.draw(mesh, streetscapeGeometryTerrainShader)
      }
    }
  }

  private fun getSampleRenderMesh(
    render: SampleRender,
    streetscapeGeometry: StreetscapeGeometry,
  ): Mesh {
    val streetscapeGeometryBuffer = streetscapeGeometry.mesh.vertexList
    streetscapeGeometryBuffer.rewind()
    val meshVertexBuffer = VertexBuffer(
      render, 3, streetscapeGeometryBuffer
    )
    val meshIndexBuffer = IndexBuffer(render, streetscapeGeometry.mesh.indexList)
    val meshVertexBuffers = arrayOf(meshVertexBuffer)
    return Mesh(
      render,
      Mesh.PrimitiveMode.TRIANGLES,
      meshIndexBuffer,
      meshVertexBuffers
    )
  }

  private fun updateStreetscapeGeometries(
    render: SampleRender,
    streetscapeGeometries: Collection<StreetscapeGeometry>,
  ) {
    for (streetscapeGeometry in streetscapeGeometries) {
      if (!streetscapeGeometryToMeshes.containsKey(streetscapeGeometry)) {
        val mesh = getSampleRenderMesh(render, streetscapeGeometry)
        streetscapeGeometryToMeshes[streetscapeGeometry] = mesh
      }
    }
  }
}
