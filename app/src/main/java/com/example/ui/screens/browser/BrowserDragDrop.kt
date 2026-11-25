package com.example.ui.screens.browser

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.FileItem
import kotlin.math.roundToInt

@Stable
data class DropTargetEntry(
    val id: String,
    val label: String,
    val pathOrUri: String,
    val isSaf: Boolean,
    val coords: LayoutCoordinates
)

@Stable
sealed interface DragDropResult {
    data class MoveToFolder(
        val items: List<FileItem>,
        val target: DropTargetEntry
    ) : DragDropResult

    data class Reorder(
        val items: List<FileItem>,
        val orderedIds: List<String>
    ) : DragDropResult
}

@Stable
data class ActiveDragSession(
    val draggedItems: List<FileItem>,
    val pointerPosInRoot: Offset,
    val hoveredTarget: DropTargetEntry? = null,
    val previewOrderIds: List<String> = emptyList(),
    val reorderInsertPosition: Int? = null,
    val isReorderModified: Boolean = false
)

@Stable
class BrowserDragDropState {
    var rootCoords: LayoutCoordinates? = null
    var reorderEnabled: Boolean = true
    var isGridMode: Boolean = false
    private var baseOrderIds: List<String> = emptyList()

    var activeSession by mutableStateOf<ActiveDragSession?>(null)
        private set

    private var initialOrderIds: List<String> = emptyList()
    private var lastSwappedItemId: String? = null
    private var lastSwapUptimeMs: Long = 0L

    internal class MutableTarget(
        val id: String,
        var label: String,
        var pathOrUri: String,
        var isSaf: Boolean,
        var itemId: String? = null,
        var isDirectory: Boolean = true,
        var coords: LayoutCoordinates? = null
    ) {
        fun toEntryOrNull(): DropTargetEntry? {
            val c = coords ?: return null
            if (!c.isAttached) return null
            return DropTargetEntry(id = id, label = label, pathOrUri = pathOrUri, isSaf = isSaf, coords = c)
        }
    }

    // Plain HashMap (never read during composition) so onGloballyPositioned during scroll causes zero Snapshot writes
    private val targets = HashMap<String, MutableTarget>(48)

    fun syncBaseOrder(ids: List<String>) {
        baseOrderIds = ids
    }

    internal fun registerOrUpdateTarget(
        id: String,
        label: String,
        pathOrUri: String,
        isSaf: Boolean,
        itemId: String? = null,
        isDirectory: Boolean = true,
        coords: LayoutCoordinates
    ) {
        val existing = targets[id]
        if (existing != null) {
            existing.label = label
            existing.pathOrUri = pathOrUri
            existing.isSaf = isSaf
            existing.itemId = itemId
            existing.isDirectory = isDirectory
            existing.coords = coords
        } else {
            targets[id] = MutableTarget(
                id = id,
                label = label,
                pathOrUri = pathOrUri,
                isSaf = isSaf,
                itemId = itemId,
                isDirectory = isDirectory,
                coords = coords
            )
        }
    }

    fun registerTarget(entry: DropTargetEntry) {
        registerOrUpdateTarget(entry.id, entry.label, entry.pathOrUri, entry.isSaf, coords = entry.coords)
    }

    fun unregisterTarget(id: String) {
        targets.remove(id)
    }

    fun isDragged(itemId: String): Boolean {
        val session = activeSession ?: return false
        return session.draggedItems.any { it.id == itemId }
    }

    fun isDropHovered(targetId: String): Boolean {
        return activeSession?.hoveredTarget?.id == targetId
    }

    fun startDrag(
        items: List<FileItem>,
        pointerPosInRoot: Offset,
        uptimeMillis: Long = SystemClock.uptimeMillis()
    ) {
        if (items.isEmpty()) return
        initialOrderIds = if (baseOrderIds.isNotEmpty()) baseOrderIds else items.map { it.id }
        lastSwappedItemId = null
        lastSwapUptimeMs = uptimeMillis

        activeSession = ActiveDragSession(
            draggedItems = items,
            pointerPosInRoot = pointerPosInRoot,
            hoveredTarget = null,
            previewOrderIds = initialOrderIds,
            reorderInsertPosition = null,
            isReorderModified = false
        )
        updateDrag(pointerPosInRoot, uptimeMillis)
    }

    /**
     * Returns true when a meaningful drop-target engagement or reorder swap occurred.
     */
    fun updateDrag(
        pointerPosInRoot: Offset,
        uptimeMillis: Long = SystemClock.uptimeMillis()
    ): Boolean {
        val current = activeSession ?: return false
        val draggedIdsSet = current.draggedItems.mapTo(HashSet()) { it.id }
        val canReorder = reorderEnabled && initialOrderIds.size > 1
        val hit = findTargetAt(pointerPosInRoot)

        // 1. Breadcrumb drop target (itemId == null) -> always MoveToFolder
        if (hit != null && hit.target.itemId == null) {
            if (isValidDropTarget(current.draggedItems, hit.target.pathOrUri)) {
                val entry = hit.target.toEntryOrNull()
                val changedTarget = current.hoveredTarget?.id != entry?.id
                lastSwappedItemId = null
                activeSession = current.copy(
                    pointerPosInRoot = pointerPosInRoot,
                    hoveredTarget = entry,
                    previewOrderIds = initialOrderIds,
                    reorderInsertPosition = null,
                    isReorderModified = false
                )
                return changedTarget
            }
        }

        // 2. Folder or file row/card hit
        if (hit != null && hit.target.itemId != null) {
            val hoverTarget = hit.target
            val hoverItemId = hoverTarget.itemId!!
            if (hoverItemId in draggedIdsSet) {
                if (uptimeMillis - lastSwapUptimeMs >= REORDER_COOLDOWN_MS) {
                    lastSwappedItemId = null
                }
                activeSession = current.copy(
                    pointerPosInRoot = pointerPosInRoot,
                    hoveredTarget = null
                )
                return false
            }

            // Check folder center zone vs edge seam
            if (hoverTarget.isDirectory && isValidDropTarget(current.draggedItems, hoverTarget.pathOrUri)) {
                val edgeDirection = if (canReorder) detectFolderEdgeSeam(hit.relX, hit.relY) else null
                if (edgeDirection == null) {
                    val entry = hoverTarget.toEntryOrNull()
                    val engaged = current.hoveredTarget?.id != entry?.id
                    lastSwappedItemId = null
                    activeSession = current.copy(
                        pointerPosInRoot = pointerPosInRoot,
                        hoveredTarget = entry,
                        previewOrderIds = initialOrderIds,
                        reorderInsertPosition = null,
                        isReorderModified = false
                    )
                    return engaged
                } else if (canReorder) {
                    return performReorderStep(
                        current = current,
                        draggedIdsSet = draggedIdsSet,
                        hoverItemId = hoverItemId,
                        explicitInsertAfter = edgeDirection,
                        pointerPosInRoot = pointerPosInRoot,
                        uptimeMillis = uptimeMillis
                    )
                }
            }

            // Regular file (or non-movable folder): custom sort reorder
            if (canReorder) {
                return performReorderStep(
                    current = current,
                    draggedIdsSet = draggedIdsSet,
                    hoverItemId = hoverItemId,
                    explicitInsertAfter = null,
                    pointerPosInRoot = pointerPosInRoot,
                    uptimeMillis = uptimeMillis
                )
            }
        }

        // 3. Not hovering any actionable target
        if (uptimeMillis - lastSwapUptimeMs >= REORDER_COOLDOWN_MS) {
            lastSwappedItemId = null
        }
        activeSession = current.copy(
            pointerPosInRoot = pointerPosInRoot,
            hoveredTarget = null
        )
        return false
    }

    private fun performReorderStep(
        current: ActiveDragSession,
        draggedIdsSet: Set<String>,
        hoverItemId: String,
        explicitInsertAfter: Boolean?,
        pointerPosInRoot: Offset,
        uptimeMillis: Long
    ): Boolean {
        val elapsed = uptimeMillis - lastSwapUptimeMs
        if (hoverItemId == lastSwappedItemId && elapsed < REORDER_COOLDOWN_MS) {
            activeSession = current.copy(
                pointerPosInRoot = pointerPosInRoot,
                hoveredTarget = null
            )
            return false
        }

        val currentOrder = current.previewOrderIds.ifEmpty { initialOrderIds }
        val dragAnchorIdx = currentOrder.indexOfFirst { it in draggedIdsSet }
        val hoverIdx = currentOrder.indexOf(hoverItemId)
        if (dragAnchorIdx < 0 || hoverIdx < 0) {
            activeSession = current.copy(pointerPosInRoot = pointerPosInRoot, hoveredTarget = null)
            return false
        }

        val insertAfter = explicitInsertAfter ?: (dragAnchorIdx < hoverIdx)
        val (rebuilt, insertPos1Based) = computeReorderedIds(
            currentOrder = currentOrder,
            draggedIdsInOrder = current.draggedItems.map { it.id },
            targetId = hoverItemId,
            insertAfter = insertAfter
        )

        if (rebuilt == currentOrder) {
            activeSession = current.copy(
                pointerPosInRoot = pointerPosInRoot,
                hoveredTarget = null
            )
            return false
        }

        lastSwappedItemId = hoverItemId
        lastSwapUptimeMs = uptimeMillis
        val modified = rebuilt != initialOrderIds
        activeSession = current.copy(
            pointerPosInRoot = pointerPosInRoot,
            hoveredTarget = null,
            previewOrderIds = rebuilt,
            reorderInsertPosition = if (modified) insertPos1Based else null,
            isReorderModified = modified
        )
        return true
    }

    fun finishDrag(): DragDropResult? {
        val current = activeSession ?: return null
        val hovered = current.hoveredTarget
        val modified = current.isReorderModified && current.previewOrderIds.isNotEmpty()
        val result: DragDropResult? = when {
            hovered != null -> DragDropResult.MoveToFolder(current.draggedItems, hovered)
            modified -> DragDropResult.Reorder(current.draggedItems, current.previewOrderIds)
            else -> null
        }
        activeSession = null
        initialOrderIds = emptyList()
        lastSwappedItemId = null
        return result
    }

    fun cancelDrag() {
        activeSession = null
        initialOrderIds = emptyList()
        lastSwappedItemId = null
    }

    /**
     * Returns `null` when inside a folder's center drop-to-move zone;
     * returns `false` (insert before) or `true` (insert after) when over outer seam edges.
     */
    private fun detectFolderEdgeSeam(relX: Float, relY: Float): Boolean? {
        return if (isGridMode) {
            val inCenterX = relX in 0.16f..0.84f
            val inCenterY = relY in 0.16f..0.84f
            if (inCenterX && inCenterY) {
                null
            } else {
                relX > 0.84f || relY > 0.84f
            }
        } else {
            when {
                relY < 0.18f -> false
                relY > 0.82f -> true
                else -> null
            }
        }
    }

    private data class TargetHit(
        val target: MutableTarget,
        val relX: Float,
        val relY: Float
    )

    private fun findTargetAt(pointerInRoot: Offset): TargetHit? {
        val root = rootCoords ?: return null
        if (!root.isAttached) return null
        val hitPaddingPx = 6f
        var fallbackItemHit: TargetHit? = null
        for (target in targets.values) {
            val coords = target.coords ?: continue
            if (!coords.isAttached) continue
            val topLeft = runCatching { root.localPositionOf(coords, Offset.Zero) }.getOrNull() ?: continue
            val size = coords.size
            if (size.width <= 0 || size.height <= 0) continue
            val rect = Rect(
                left = topLeft.x - hitPaddingPx,
                top = topLeft.y - hitPaddingPx,
                right = topLeft.x + size.width + hitPaddingPx,
                bottom = topLeft.y + size.height + hitPaddingPx
            )
            if (rect.contains(pointerInRoot)) {
                val relX = ((pointerInRoot.x - topLeft.x) / size.width.toFloat()).coerceIn(0f, 1f)
                val relY = ((pointerInRoot.y - topLeft.y) / size.height.toFloat()).coerceIn(0f, 1f)
                val hit = TargetHit(target, relX, relY)
                if (target.itemId == null) {
                    // Breadcrumbs take top priority
                    return hit
                }
                if (fallbackItemHit == null) {
                    fallbackItemHit = hit
                }
            }
        }
        return fallbackItemHit
    }

    companion object {
        private const val REORDER_COOLDOWN_MS = 280L

        fun computeReorderedIds(
            currentOrder: List<String>,
            draggedIdsInOrder: List<String>,
            targetId: String,
            insertAfter: Boolean
        ): Pair<List<String>, Int> {
            val draggedSet = draggedIdsInOrder.toHashSet()
            val draggedOrdered = currentOrder.filter { it in draggedSet }
                .ifEmpty { draggedIdsInOrder.filter { currentOrder.contains(it) } }
            if (draggedOrdered.isEmpty() || targetId in draggedSet) {
                val fallbackPos = (currentOrder.indexOfFirst { it in draggedSet } + 1).coerceAtLeast(1)
                return currentOrder to fallbackPos
            }
            val remaining = currentOrder.filterNot { it in draggedSet }
            val targetIdxInRemaining = remaining.indexOf(targetId)
            if (targetIdxInRemaining < 0) {
                return currentOrder to 1
            }
            val insertIdx = (if (insertAfter) targetIdxInRemaining + 1 else targetIdxInRemaining)
                .coerceIn(0, remaining.size)
            val rebuilt = ArrayList<String>(currentOrder.size)
            rebuilt.addAll(remaining.subList(0, insertIdx))
            rebuilt.addAll(draggedOrdered)
            rebuilt.addAll(remaining.subList(insertIdx, remaining.size))
            return rebuilt to (insertIdx + 1)
        }

        fun isValidDropTarget(draggedItems: List<FileItem>, targetPathOrUri: String): Boolean {
            if (draggedItems.isEmpty() || targetPathOrUri.isBlank()) return false
            val normTarget = targetPathOrUri.trimEnd('/')
            // Cannot drop a folder into itself or inside one of its own subfolders
            val selfConflict = draggedItems.any { item ->
                if (!item.isDirectory) return@any false
                val normSrc = item.pathOrUri.trimEnd('/')
                normTarget == normSrc || (!item.isSafUri && normTarget.startsWith("$normSrc/"))
            }
            if (selfConflict) return false
            // No-op if all dragged items already reside directly inside targetPathOrUri
            val alreadyInsideTarget = draggedItems.all { item ->
                item.parentPathOrUri.trimEnd('/') == normTarget
            }
            return !alreadyInsideTarget
        }
    }
}

@Composable
fun rememberBrowserDragDropState(): BrowserDragDropState {
    return remember { BrowserDragDropState() }
}

/**
 * Registers any item row/card or breadcrumb node as an interactive drop/reorder target.
 */
fun Modifier.browserDropTarget(
    dragState: BrowserDragDropState?,
    targetId: String,
    label: String,
    pathOrUri: String,
    isSaf: Boolean,
    enabled: Boolean = true,
    itemId: String? = null,
    isDirectory: Boolean = true
): Modifier = if (dragState == null || !enabled || pathOrUri.isBlank()) {
    this
} else {
    this then BrowserDropTargetElement(
        dragState = dragState,
        targetId = targetId,
        label = label,
        pathOrUri = pathOrUri,
        isSaf = isSaf,
        itemId = itemId,
        isDirectory = isDirectory
    )
}

private data class BrowserDropTargetElement(
    val dragState: BrowserDragDropState,
    val targetId: String,
    val label: String,
    val pathOrUri: String,
    val isSaf: Boolean,
    val itemId: String?,
    val isDirectory: Boolean
) : ModifierNodeElement<BrowserDropTargetNode>() {
    override fun create() = BrowserDropTargetNode(dragState, targetId, label, pathOrUri, isSaf, itemId, isDirectory)

    override fun update(node: BrowserDropTargetNode) {
        node.updateTarget(dragState, targetId, label, pathOrUri, isSaf, itemId, isDirectory)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "browserDropTarget"
        properties["targetId"] = targetId
        properties["label"] = label
    }
}

private class BrowserDropTargetNode(
    private var dragState: BrowserDragDropState,
    private var targetId: String,
    private var label: String,
    private var pathOrUri: String,
    private var isSaf: Boolean,
    private var itemId: String?,
    private var isDirectory: Boolean
) : Modifier.Node(), GlobalPositionAwareModifierNode {

    private var lastCoords: LayoutCoordinates? = null

    fun updateTarget(
        dragState: BrowserDragDropState,
        targetId: String,
        label: String,
        pathOrUri: String,
        isSaf: Boolean,
        itemId: String?,
        isDirectory: Boolean
    ) {
        if (this.dragState !== dragState || this.targetId != targetId) {
            this.dragState.unregisterTarget(this.targetId)
        }
        this.dragState = dragState
        this.targetId = targetId
        this.label = label
        this.pathOrUri = pathOrUri
        this.isSaf = isSaf
        this.itemId = itemId
        this.isDirectory = isDirectory
        val coords = lastCoords
        if (isAttached && coords != null && coords.isAttached) {
            dragState.registerOrUpdateTarget(targetId, label, pathOrUri, isSaf, itemId, isDirectory, coords)
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        lastCoords = coordinates
        if (coordinates.isAttached) {
            dragState.registerOrUpdateTarget(
                id = targetId,
                label = label,
                pathOrUri = pathOrUri,
                isSaf = isSaf,
                itemId = itemId,
                isDirectory = isDirectory,
                coords = coordinates
            )
        }
    }

    override fun onDetach() {
        lastCoords = null
        dragState.unregisterTarget(targetId)
    }
}

/**
 * Unifies long-press selection, smooth drag-to-reorder (single & batch), and drag-to-folder move
 * without fighting scroll or tap.
 */
fun Modifier.browserDragSource(
    dragState: BrowserDragDropState?,
    item: FileItem,
    dragPayloadProvider: () -> List<FileItem> = { listOf(item) },
    dragPayloadForItem: ((FileItem) -> List<FileItem>)? = null,
    enabled: Boolean,
    onLongClick: () -> Unit = {},
    onLongClickItem: ((FileItem) -> Unit)? = null,
    onDropMove: (List<FileItem>, DropTargetEntry) -> Unit,
    onReorderCommit: (List<String>) -> Unit = {}
): Modifier = if (dragState == null || !enabled) {
    this
} else {
    this then BrowserDragSourceElement(
        dragState = dragState,
        item = item,
        dragPayloadProvider = dragPayloadProvider,
        dragPayloadForItem = dragPayloadForItem,
        onLongClick = onLongClick,
        onLongClickItem = onLongClickItem,
        onDropMove = onDropMove,
        onReorderCommit = onReorderCommit
    )
}

private data class BrowserDragSourceElement(
    val dragState: BrowserDragDropState,
    val item: FileItem,
    val dragPayloadProvider: () -> List<FileItem>,
    val dragPayloadForItem: ((FileItem) -> List<FileItem>)?,
    val onLongClick: () -> Unit,
    val onLongClickItem: ((FileItem) -> Unit)?,
    val onDropMove: (List<FileItem>, DropTargetEntry) -> Unit,
    val onReorderCommit: (List<String>) -> Unit
) : ModifierNodeElement<BrowserDragSourceNode>() {
    override fun create(): BrowserDragSourceNode = BrowserDragSourceNode(
        dragState = dragState,
        item = item,
        dragPayloadProvider = dragPayloadProvider,
        dragPayloadForItem = dragPayloadForItem,
        onLongClick = onLongClick,
        onLongClickItem = onLongClickItem,
        onDropMove = onDropMove,
        onReorderCommit = onReorderCommit
    )

    override fun update(node: BrowserDragSourceNode) {
        node.updateParams(
            dragState = dragState,
            item = item,
            dragPayloadProvider = dragPayloadProvider,
            dragPayloadForItem = dragPayloadForItem,
            onLongClick = onLongClick,
            onLongClickItem = onLongClickItem,
            onDropMove = onDropMove,
            onReorderCommit = onReorderCommit
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "browserDragSource"
        properties["itemId"] = item.id
    }
}

private class BrowserDragSourceNode(
    private var dragState: BrowserDragDropState,
    private var item: FileItem,
    private var dragPayloadProvider: () -> List<FileItem>,
    private var dragPayloadForItem: ((FileItem) -> List<FileItem>)?,
    private var onLongClick: () -> Unit,
    private var onLongClickItem: ((FileItem) -> Unit)?,
    private var onDropMove: (List<FileItem>, DropTargetEntry) -> Unit,
    private var onReorderCommit: (List<String>) -> Unit
) : DelegatingNode(),
    CompositionLocalConsumerModifierNode,
    GlobalPositionAwareModifierNode {

    private var itemCoords: LayoutCoordinates? = null

    private val pointerNode = delegate(
        SuspendingPointerInputModifierNode {
            val touchSlop = viewConfiguration.touchSlop
            val longPressTimeout = viewConfiguration.longPressTimeoutMillis

            awaitEachGesture {
                val firstDown = awaitFirstDown(requireUnconsumed = false)
                val startLocal = firstDown.position

                var endedBeforeLongPress = false
                val upOrMovedEarly = withTimeoutOrNull(longPressTimeout) {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Main)
                        val tracked = ev.changes.firstOrNull { it.id == firstDown.id }
                        if (tracked == null || !tracked.pressed || tracked.changedToUpIgnoreConsumed()) {
                            endedBeforeLongPress = true
                            return@withTimeoutOrNull true
                        }
                        if ((tracked.position - startLocal).getDistance() > touchSlop) {
                            endedBeforeLongPress = true
                            return@withTimeoutOrNull true
                        }
                    }
                }
                if (upOrMovedEarly == true || endedBeforeLongPress) {
                    return@awaitEachGesture
                }

                val haptic = currentValueOf(LocalHapticFeedback)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                fun toRootOffset(localOffset: Offset): Offset {
                    val root = dragState.rootCoords
                    val mine = itemCoords
                    if (root != null && root.isAttached && mine != null && mine.isAttached) {
                        val origin = runCatching { root.localPositionOf(mine, Offset.Zero) }.getOrNull()
                        if (origin != null) return origin + localOffset
                    }
                    return localOffset
                }

                var dragStarted = false
                var latestUpChange: PointerInputChange? = null

                try {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Main)
                        val tracked = ev.changes.firstOrNull { it.id == firstDown.id }
                        if (tracked == null || !tracked.pressed || tracked.changedToUpIgnoreConsumed()) {
                            latestUpChange = tracked
                            tracked?.consume()
                            break
                        }

                        if (!dragStarted) {
                            if ((tracked.position - startLocal).getDistance() > touchSlop) {
                                dragStarted = true
                                val currentItem = item
                                val payload = (dragPayloadForItem?.invoke(currentItem) ?: dragPayloadProvider())
                                    .ifEmpty { listOf(currentItem) }
                                dragState.startDrag(
                                    items = payload,
                                    pointerPosInRoot = toRootOffset(tracked.position),
                                    uptimeMillis = tracked.uptimeMillis
                                )
                                tracked.consume()
                            }
                        } else {
                            if (tracked.positionChanged()) {
                                val changed = dragState.updateDrag(
                                    pointerPosInRoot = toRootOffset(tracked.position),
                                    uptimeMillis = tracked.uptimeMillis
                                )
                                if (changed) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                tracked.consume()
                            }
                        }
                    }

                    if (dragStarted) {
                        latestUpChange?.consume()
                        when (val result = dragState.finishDrag()) {
                            is DragDropResult.MoveToFolder -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDropMove(result.items, result.target)
                            }
                            is DragDropResult.Reorder -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReorderCommit(result.orderedIds)
                            }
                            null -> Unit
                        }
                    } else {
                        latestUpChange?.consume()
                        val currentItem = item
                        val itemHandler = onLongClickItem
                        if (itemHandler != null) itemHandler(currentItem) else onLongClick()
                    }
                } catch (t: Throwable) {
                    if (dragStarted) {
                        dragState.cancelDrag()
                    }
                    throw t
                }
            }
        }
    )

    fun updateParams(
        dragState: BrowserDragDropState,
        item: FileItem,
        dragPayloadProvider: () -> List<FileItem>,
        dragPayloadForItem: ((FileItem) -> List<FileItem>)?,
        onLongClick: () -> Unit,
        onLongClickItem: ((FileItem) -> Unit)?,
        onDropMove: (List<FileItem>, DropTargetEntry) -> Unit,
        onReorderCommit: (List<String>) -> Unit
    ) {
        val identityChanged = this.dragState !== dragState || this.item.id != item.id
        this.dragState = dragState
        this.item = item
        this.dragPayloadProvider = dragPayloadProvider
        this.dragPayloadForItem = dragPayloadForItem
        this.onLongClick = onLongClick
        this.onLongClickItem = onLongClickItem
        this.onDropMove = onDropMove
        this.onReorderCommit = onReorderCommit
        if (identityChanged) {
            pointerNode.resetPointerInputHandler()
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        itemCoords = coordinates
    }

    override fun onDetach() {
        itemCoords = null
    }
}

@Composable
fun BrowserDragGhostOverlay(
    dragState: BrowserDragDropState,
    modifier: Modifier = Modifier
) {
    val session = dragState.activeSession ?: return
    val density = LocalDensity.current
    val yLiftPx = with(density) { 58.dp.toPx() }
    val xCenterOffsetPx = with(density) { 108.dp.toPx() }
    val isHoveringFolder = session.hoveredTarget != null
    val isReordering = !isHoveringFolder && session.isReorderModified && session.reorderInsertPosition != null
    val isActiveAccent = isHoveringFolder || isReordering

    val badgeScale by animateFloatAsState(
        targetValue = if (isHoveringFolder) 1.05f else if (isReordering) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "drag_ghost_scale"
    )
    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surface,
        label = "drag_ghost_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActiveAccent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        },
        label = "drag_ghost_border"
    )

    val count = session.draggedItems.size
    val first = session.draggedItems.firstOrNull() ?: return
    val title = if (count == 1) first.name else "$count items selected"
    val subtitle = when {
        session.hoveredTarget != null -> "Drop to move → ${session.hoveredTarget.label}"
        isReordering -> {
            val pos = session.reorderInsertPosition ?: 1
            if (count == 1) "Reorder → Position #$pos" else "Reorder $count items → #$pos"
        }
        dragState.reorderEnabled -> "Drag to reorder or drop in folder"
        else -> "Drag onto a folder"
    }

    Box(modifier = modifier.fillMaxSize().zIndex(50f)) {
        Surface(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (session.pointerPosInRoot.x - xCenterOffsetPx).roundToInt().coerceAtLeast(12),
                        y = (session.pointerPosInRoot.y - yLiftPx).roundToInt().coerceAtLeast(12)
                    )
                }
                .graphicsLayer(scaleX = badgeScale, scaleY = badgeScale)
                .widthIn(min = 188.dp, max = 272.dp)
                .testTag("drag_ghost_pill"),
            shape = RoundedCornerShape(18.dp),
            color = containerColor,
            tonalElevation = 0.dp,
            shadowElevation = 14.dp,
            border = BorderStroke(if (isActiveAccent) 1.5.dp else 0.7.dp, borderColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            color = if (isActiveAccent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isHoveringFolder -> Icons.AutoMirrored.Filled.DriveFileMove
                            isReordering -> Icons.Filled.SwapVert
                            dragState.reorderEnabled -> Icons.Filled.DragIndicator
                            first.isDirectory -> Icons.Filled.Folder
                            else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = if (isActiveAccent) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isActiveAccent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActiveAccent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (count > 1) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
