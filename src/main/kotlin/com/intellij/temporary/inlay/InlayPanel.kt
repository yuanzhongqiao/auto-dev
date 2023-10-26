package com.intellij.temporary.inlay

import cc.unitmesh.devti.actions.quick.QuickPrompt
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.rd.paint2DLine
import com.intellij.temporary.inlay.InlayLayoutManager.Companion.getXOffsetPosition
import com.intellij.ui.JBColor
import com.intellij.ui.paint.LinePainter2D
import com.intellij.util.ui.JBPoint
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JComponent
import javax.swing.JPanel

open class InlayPanel<T : JComponent?>(var component: T) : JPanel() {
    val panel: JPanel
    var inlay: Inlay<*>? = null

    private val visibleAreaListener: VisibleAreaListener

    init {
        panel = object : JPanel() {
            init {
                setOpaque(false)
                setBorder(JBUI.Borders.empty())
            }

            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val create: Graphics2D? = g.create() as Graphics2D?
                try {
                    create!!.paint2DLine(
                        JBPoint(0, 0),
                        JBPoint(0, height),
                        LinePainter2D.StrokeType.INSIDE,
                        3.0,
                        JBColor(Color(0, 100, 89, 100), Color(0, 0, 0, 90))
                    )

                    create.dispose()
                } catch (th: Throwable) {
                    create!!.dispose()
                    throw th
                }
            }
        }

        visibleAreaListener =
            VisibleAreaListener { v1: VisibleAreaEvent? ->
                if (v1 != null) {
                    invalidate()
                }
            }
    }

    protected open fun setupPane(inlay: Inlay<*>) {
        this.inlay = inlay

        add(panel)
        add(component)
        setOpaque(true)

        inlay.editor.scrollingModel.addVisibleAreaListener(visibleAreaListener, (inlay as Disposable))

        setLayout(object : LayoutManager {
            override fun addLayoutComponent(name: String, comp: Component?) {}
            override fun removeLayoutComponent(comp: Component?) {}
            override fun preferredLayoutSize(parent: Container?): Dimension {
                if (!inlay.isValid || parent == null) return Dimension(0, 0)

                val it: Dimension = component!!.preferredSize
                val xOffsetPosition = it.width + getXOffsetPosition(inlay)
                val insets = component!!.getInsets()

                return Dimension(xOffsetPosition, it.height + insets.height)
            }

            override fun minimumLayoutSize(parent: Container?): Dimension {
                if (!inlay.isValid || parent == null) return Dimension(0, 0)

                val it: Dimension = component!!.getMinimumSize()
                val xOffsetPosition = it.width + getXOffsetPosition(inlay)
                val insets = component!!.getInsets()

                return Dimension(xOffsetPosition, it.height + insets.height)
            }

            override fun layoutContainer(parent: Container?) {
                if (inlay.isValid) {
                    var size = parent?.size
                    if (size == null) {
                        size = Dimension(0, 0)
                    }
                    val size2: Dimension = size
                    val x = getXOffsetPosition(inlay)
                    component!!.setBounds(x, 0, size2.width - x, size2.height)

                    val scrollPane = (inlay.editor as EditorEx).scrollPane
                    panel.setBounds(scrollPane.viewport.viewRect.x - 1, 0, 5, size2.height)
                }
            }
        })
    }

    companion object {
        fun add(editor: EditorEx, offset: Int, component: QuickPrompt): InlayPanel<QuickPrompt>? {
            val properties = InlayProperties().showAbove(false).showWhenFolded(true);

            val inlayPanel = InlayPanel(component)
            val inlayRenderer = InlayRenderer(inlayPanel)

            val inlayElement =
                editor.inlayModel.addBlockElement(offset, properties, inlayRenderer) ?: return null

            ComponentInlaysContainer.addInlay(inlayElement)

            inlayPanel.setupPane(inlayElement)

            return inlayPanel
        }
    }
}