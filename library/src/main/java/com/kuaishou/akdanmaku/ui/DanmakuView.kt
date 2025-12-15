/*
 * The MIT License (MIT)
 *
 * Copyright 2021 Kwai, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.kuaishou.akdanmaku.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.os.Looper
import android.util.AttributeSet
import android.view.Choreographer
import android.view.TextureView

/**
 * 用于显示弹幕的 UI View，与 DanmakuPlayer 绑定并联合实现弹幕的具体展现逻辑。
 * 起关系类似于视频播放场景 ViewView & MediaPlayer 的关系
 */
class DanmakuView : TextureView, TextureView.SurfaceTextureListener {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    var danmakuPlayer: DanmakuPlayer? = null
    internal val displayer: ViewDisplayer = ViewDisplayer()

    private var drawingThread: DrawingThread? = null

    init {
        context.resources.displayMetrics?.let { metrics ->
            displayer.density = metrics.density
            displayer.scaleDensity = metrics.scaledDensity
            displayer.densityDpi = metrics.densityDpi
        }
        surfaceTextureListener = this;
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        danmakuPlayer?.notifyDisplayerSizeChanged(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        danmakuPlayer?.notifyDisplayerSizeChanged(right - left, bottom - top)
    }

    fun doDraw()
    {
        Choreographer.getInstance().postFrameCallback(drawingThread)
    }

    private fun drawCustomContent(canvas: Canvas?) {
        if (canvas == null) return
        val width = measuredWidth
        val height = measuredHeight
        // 部分机型存在长按时大小为零的问题（Flyme）
        if (width == 0 || height == 0) return
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        danmakuPlayer?.notifyDisplayerSizeChanged(width, height)
        danmakuPlayer?.draw(canvas)
    }

    override fun onSurfaceTextureAvailable(
        p0: SurfaceTexture,
        p1: Int,
        p2: Int
    ) {
        drawingThread = DrawingThread(this)
        drawingThread?.start()
    }

    override fun onSurfaceTextureSizeChanged(
        p0: SurfaceTexture,
        p1: Int,
        p2: Int
    ) {
        val width = measuredWidth
        val height = measuredHeight
        if (width == 0 || height == 0) return
        danmakuPlayer?.notifyDisplayerSizeChanged(width, height)
    }

    override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
        var retry = true
        drawingThread?.stopDrawing()
        while (retry) {
            try {
                drawingThread?.join() // 等待线程执行完毕
                retry = false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        drawingThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {

    }

    class ViewDisplayer : DanmakuDisplayer {
        override var height: Int = 0
        override var width: Int = 0
        override var margin: Int = 4
        override var allMarginTop: Float = 0f
        override var density: Float = 1f
        override var scaleDensity: Float = 1f
        override var densityDpi: Int = 160
    }

    inner class DrawingThread(private val surfaceHolder: TextureView) : Thread(),
        Choreographer.FrameCallback {
        @Volatile
        var looper: Looper? = null

        override fun run() {
            Looper.prepare()
            looper = Looper.myLooper()
            Choreographer.getInstance().postFrameCallback(this)
            Looper.loop()
        }

        override fun doFrame(p0: Long) {
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()

                synchronized(surfaceHolder) {
                    drawCustomContent(canvas)
                }
            } finally {
                if (canvas != null) {
                    surfaceHolder.unlockCanvasAndPost(canvas)
                }
            }
        }

        fun stopDrawing() {
            Choreographer.getInstance().removeFrameCallback(this)
            looper?.quit();
        }
    }
}
