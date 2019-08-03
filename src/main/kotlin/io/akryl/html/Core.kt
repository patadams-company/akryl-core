@file:Suppress("FunctionName")

package io.akryl.html

import io.akryl.*
import org.w3c.dom.events.Event

class HtmlWidget(
  val tag: String,
  val ns: String?,
  val cssPrefix: String?,
  val attributes: Map<String, String?> = emptyMap(),
  val style: Map<String, String?> = emptyMap(),
  val listeners: Map<String, (event: Event) -> Unit> = emptyMap(),
  val children: List<Widget> = emptyList(),
  val innerHtml: String? = null,
  key: Key? = null
) : Widget(key) {
  init {
    check(children.isEmpty() || innerHtml == null) { "Only one property of [children, innerHtml] can be set" }
  }

  override fun createElement(parent: RenderElement) = HtmlRenderElement(parent, this)
}

class HtmlRenderElement(
  override val parent: RenderElement,
  widget: HtmlWidget
) : RenderElement() {
  private val children = ArrayList<RenderElement>()
  override val prefix = ""

  override val factory = parent.factory
  override val node = factory.createNode(widget.tag, widget.ns)

  init {
    widget.cssPrefix?.let { node.setAttribute(STYLE_ATTRIBUTE_NAME, it) }

    for ((k, v) in widget.attributes) {
      if (v != null) {
        node.setAttribute(k, v)
      }
    }

    for ((k, v) in widget.style) {
      if (v != null) {
        node.setStyle(k, v)
      }
    }

    for ((k, v) in widget.listeners) {
      node.addEventListener(k, v)
    }

    for (child in widget.children) {
      val childElement = child.createElement(this)
      children.add(childElement)
      node.appendChild(childElement.node)
    }
    widget.innerHtml?.let { node.innerHTML = it }
  }

  override var widget: HtmlWidget = widget
    private set

  override fun mounted() {
    super.mounted()
    children.forEach { it.mounted() }
  }

  override fun update(newWidget: Widget, force: Boolean): Boolean {
    if (newWidget !is HtmlWidget) return false
    if (newWidget.tag != this.widget.tag) return false

    updateCssPrefix(newWidget)
    updateAttributes(newWidget.attributes)
    updateStyle(newWidget.style)
    updateListeners(newWidget.listeners)
    updateChildren(newWidget.children, newWidget.innerHtml, force)
    widget = newWidget

    return true
  }

  override fun unmounted() {
    super.unmounted()
    children.forEach { it.unmounted() }
  }

  private fun updateCssPrefix(newWidget: HtmlWidget) {
    if (newWidget.cssPrefix != widget.cssPrefix) {
      val prefix = newWidget.cssPrefix
      if (prefix != null) {
        node.setAttribute(STYLE_ATTRIBUTE_NAME, prefix)
      } else {
        node.removeAttribute(STYLE_ATTRIBUTE_NAME)
      }
    }
  }

  private fun updateChildren(newChildren: List<Widget>, newInnerHtml: String?, force: Boolean) {
    if (widget.innerHtml != newInnerHtml) {
      if (newInnerHtml != null) {
        node.innerHTML = newInnerHtml
      } else {
        node.clear()
      }
    }

    val currentKeys = HashMap<IndexedKey, RenderElement>()
    var indexCounter = 0

    for (child in children) {
      val key = child.widget.key
      if (key != null) {
        currentKeys[IndexedKey(key, 0)] = child
      } else {
        indexCounter += 1
        currentKeys[IndexedKey(null, indexCounter)] = child
      }
    }

    indexCounter = 0

    for ((index, newWidget) in newChildren.withIndex()) {
      val key = newWidget.key
      val indexedKey = if (key != null) {
        IndexedKey(key, 0)
      } else {
        indexCounter += 1
        IndexedKey(null, indexCounter)
      }
      var element = currentKeys[indexedKey]
      if (element != null) {
        val oldIndex = children.indexOf(element)
        if (oldIndex != index) {
          val insertRef = children[index].node
          node.insertBefore(element.node, insertRef)
          children.removeAt(oldIndex)
          children.add(index, element)
        }
        children[index] = update(this, element, newWidget, force)
      } else {
        element = newWidget.createElement(this)
        if (index < children.size) {
          val insertRef = children[index].node
          node.insertBefore(element.node, insertRef)
        } else {
          node.appendChild(element.node)
        }
        children.add(index, element)
        element.mounted()
      }
    }

    for (i in (children.size - 1) downTo newChildren.size) {
      val oldChild = children.removeAt(i)
      oldChild.node.remove()
      oldChild.unmounted()
    }
  }

  private fun updateStyle(newStyle: Map<String, String?>) {
    for ((k, oldValue) in widget.style) {
      val newValue = newStyle[k]
      if (oldValue != newValue) {
        updateStyle(k, newValue)
      }
    }

    for ((k, newValue) in newStyle) {
      val oldValue = widget.style[k]
      if (oldValue == null && newValue != null) {
        updateStyle(k, newValue)
      }
    }
  }

  private fun updateAttributes(newAttributes: Map<String, String?>) {
    for ((k, oldValue) in widget.attributes) {
      val newValue = newAttributes[k]
      if (oldValue != newValue) {
        updateAttribute(k, newValue)
      }
      updateSpecialAttribute(node, k, newValue)
    }

    for ((k, newValue) in newAttributes) {
      val oldValue = widget.attributes[k]
      if (oldValue == null && newValue != null) {
        updateAttribute(k, newValue)
      }
      if (newValue != null) {
        updateSpecialAttribute(node, k, newValue)
      }
    }
  }

  private fun updateListeners(newListeners: Map<String, (Event) -> Unit>) {
    for ((k, oldValue) in widget.listeners) {
      val newValue = newListeners[k]
      if (oldValue != newValue) {
        node.removeEventListener(k, oldValue)
        if (newValue != null) {
          node.addEventListener(k, newValue)
        }
      }
    }

    for ((k, newValue) in newListeners) {
      val oldValue = widget.style[k]
      if (oldValue == null) {
        node.addEventListener(k, newValue)
      }
    }
  }

  private fun updateStyle(k: String, v: String?) {
    if (v != null) {
      node.setStyle(k, v)
    } else {
      node.removeStyle(k)
    }
  }

  private fun updateAttribute(k: String, v: String?) {
    if (v != null) {
      node.setAttribute(k, v)
    } else {
      node.removeAttribute(k)
    }
  }
}

class Text(
  val value: String
) : Widget() {
  override fun createElement(parent: RenderElement) = TextRenderElement(parent, this)
}

class TextRenderElement(
  override val parent: RenderElement,
  widget: Text
) : RenderElement() {
  override val prefix = ""
  override val factory = parent.factory
  override val node = factory.createText(widget.value)

  override var widget: Text = widget
    private set

  override fun update(newWidget: Widget, force: Boolean): Boolean {
    if (newWidget !is Text) return false
    if (newWidget.value != widget.value) {
      node.textContent = newWidget.value
    }
    widget = newWidget
    return true
  }
}

fun classMap(vararg items: Pair<String, Boolean>) = items
  .filter { it.second }
  .map { it.first }

private fun updateSpecialAttribute(node: dynamic, k: String, v: String?) {
  when (k) {
    "checked" -> node.checked = v != null
    "value" -> node.value = v
  }
}

private data class IndexedKey(val key: Key?, val index: Int)