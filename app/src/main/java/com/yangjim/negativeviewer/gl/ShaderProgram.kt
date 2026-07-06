package com.yangjim.negativeviewer.gl

import android.opengl.GLES20
import android.util.Log

class ShaderProgram(
    vertexShaderSource: String,
    fragmentShaderSource: String,
) {
    val programId: Int = createProgram(vertexShaderSource, fragmentShaderSource)

    fun use(block: ShaderProgram.() -> Unit) {
        GLES20.glUseProgram(programId)
        block()
    }

    fun attribLocation(name: String): Int = GLES20.glGetAttribLocation(programId, name)

    private fun createProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
        val program = GLES20.glCreateProgram()

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val infoLog = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            error("OpenGL shader program link failed: $infoLog")
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val infoLog = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            Log.e(TAG, "Shader compile failed: $infoLog")
            error("OpenGL shader compile failed: $infoLog")
        }

        return shader
    }

    private companion object {
        const val TAG = "NegativeViewerGL"
    }
}
