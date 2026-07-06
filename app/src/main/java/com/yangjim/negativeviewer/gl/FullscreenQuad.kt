package com.yangjim.negativeviewer.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class FullscreenQuad {
    private val vertexBuffer: FloatBuffer = floatBufferOf(FloatArray(VERTEX_COUNT * FLOATS_PER_VERTEX))

    init {
        setScale(1f, 1f)
    }

    fun setScale(scaleX: Float, scaleY: Float) {
        val values = floatArrayOf(
            -scaleX, -scaleY, 0f, 0f,
            scaleX, -scaleY, 1f, 0f,
            -scaleX, scaleY, 0f, 1f,
            scaleX, scaleY, 1f, 1f,
        )
        vertexBuffer.position(0)
        vertexBuffer.put(values)
        vertexBuffer.position(0)
    }

    fun draw(shaderProgram: ShaderProgram) {
        val positionLocation = shaderProgram.attribLocation("aPosition")
        val texCoordLocation = shaderProgram.attribLocation("aTexCoord")
        if (positionLocation < 0 || texCoordLocation < 0) {
            return
        }

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(
            positionLocation,
            POSITION_SIZE,
            GLES20.GL_FLOAT,
            false,
            STRIDE_BYTES,
            vertexBuffer,
        )

        vertexBuffer.position(POSITION_SIZE)
        GLES20.glEnableVertexAttribArray(texCoordLocation)
        GLES20.glVertexAttribPointer(
            texCoordLocation,
            TEX_COORD_SIZE,
            GLES20.GL_FLOAT,
            false,
            STRIDE_BYTES,
            vertexBuffer,
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(texCoordLocation)
    }

    private fun floatBufferOf(values: FloatArray): FloatBuffer {
        return ByteBuffer
            .allocateDirect(values.size * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
    }

    private companion object {
        const val POSITION_SIZE = 2
        const val TEX_COORD_SIZE = 2
        const val VERTEX_COUNT = 4
        const val FLOATS_PER_VERTEX = POSITION_SIZE + TEX_COORD_SIZE
        const val BYTES_PER_FLOAT = 4
        const val STRIDE_BYTES = FLOATS_PER_VERTEX * BYTES_PER_FLOAT
    }
}
