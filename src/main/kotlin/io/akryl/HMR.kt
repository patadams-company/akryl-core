package io.akryl

import js.JsObject
import js.JsSet
import js.isPlainObject
import js.module
import org.w3c.dom.Element

@Suppress("UnsafeCastFromDynamic")
fun updatePrototypeInner(obj: dynamic, resolver: (String) -> dynamic, classes: dynamic) {
  val oldProto = JsObject.getPrototypeOf(obj) ?: return
  val className = oldProto.constructor?.name ?: return

  val newProto = try {
    val clazz = classes[className]
    if (clazz === undefined) {
      resolver(className)?.prototype
    } else {
      clazz
    }
  } catch (e: Throwable) {
    null
  }
  classes[className] = newProto

  if (newProto === null) return
  if (oldProto === newProto) return

  JsObject.setPrototypeOf(obj, newProto)
}

@Suppress("UnsafeCastFromDynamic")
fun updatePrototype(
  obj: dynamic,
  resolver: (String) -> dynamic,
  visited: JsSet<dynamic> = JsSet(),
  classes: dynamic = JsObject.create(null)
) {
  if (!isPlainObject(obj) || visited.has(obj)) return

  visited.add(obj)
  updatePrototypeInner(obj, resolver, classes)

  for (k in JsObject.keys(obj)) {
    updatePrototype(obj[k], resolver, visited, classes)
  }
}

@Suppress("UnsafeCastFromDynamic", "NOTHING_TO_INLINE")
inline fun hotMount(node: Element, widget: Widget, key: String = "io.akryl.hmr.root") {
  val hot = module.hot
  val data = hot?.data
  var ref: MountRef? = if (data != null) data[key] else null
  hot?.accept()

  if (hot != null) {
    StyleRegistry.clear()
  }

  if (ref == null) {
    println("Initial render of '$key' mount")
    ref = mount(node, widget)
  } else {
    println("Hot reloading '$key' mount")
    val resolver = js("function(name) { return eval(name); }")
    updatePrototype(ref, resolver)
    ref.rebuild(widget)
  }

  hot?.dispose { disposeData ->
    println("Disposing '$key' mount")
    disposeData[key] = ref
  }
}