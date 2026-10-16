package net.perfectdreams.butterscotch.android.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import net.perfectdreams.butterscotch.android.Libraries
import net.perfectdreams.butterscotch.android.VirtualKeyState
import net.perfectdreams.butterscotch.android.layouts.GamepadElement
import net.perfectdreams.butterscotch.android.layouts.GamepadStick
import net.perfectdreams.butterscotch.android.layouts.InputBinding
import net.perfectdreams.butterscotch.android.layouts.KeyTrigger
import net.perfectdreams.butterscotch.android.settings.GamepadStyle
import net.perfectdreams.butterscotch.android.theme.ButterscotchPrimary
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun MenuButton(
    interactive: Boolean,
    onMenuOpen: () -> (Unit),
    sprite: String? = null,
    modifier: Modifier = Modifier
) {
    val spriteBitmap = rememberSpriteBitmap(sprite)
    val pixel = LocalGamepadStyle.current == GamepadStyle.PIXEL_ART
    Box(
        modifier = modifier
            .then(
                if (spriteBitmap == null && !pixel) {
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                } else Modifier
            )
            .pointerInput(Unit) {
                if (interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        onMenuOpen.invoke()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (spriteBitmap != null) {
            Image(
                bitmap = spriteBitmap,
                contentDescription = "Menüyü aç",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else if (pixel) {
            PixelButtonFace(PixelIcons.MENU, pressed = false)
        } else {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menüyü aç",
                tint = Color.White,
                // The Box is sized by placement (scale * shorter side), so size the glyph as a fraction of it
                modifier = Modifier.fillMaxSize(0.55f)
            )
        }
    }
}

@Composable
fun FastForwardButton(
    isActive: Boolean,
    interactive: Boolean,
    element: GamepadElement.FastForward,
    onFastForwardPress: (GamepadElement.FastForward) -> (Unit),
    onFastForwardRelease: (GamepadElement.FastForward) -> (Unit),
    modifier: Modifier = Modifier
) {
    val spriteBitmap = rememberSpriteBitmap(element.sprite)
    val spritePressedBitmap = rememberSpriteBitmap(element.spritePressed)
    val pixel = LocalGamepadStyle.current == GamepadStyle.PIXEL_ART
    Box(
        modifier = modifier
            .then(
                if (spriteBitmap == null && !pixel) {
                    Modifier
                        .clip(CircleShape)
                        .background(if (isActive) ButterscotchPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.22f))
                } else Modifier
            )
            .pointerInput(Unit) {
                if (interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        if (!element.toggle) {
                            onFastForwardPress.invoke(element)
                            waitForUpOrCancellation()
                            onFastForwardRelease.invoke(element)
                        } else {
                            onFastForwardPress.invoke(element)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (spriteBitmap != null) {
            Image(
                bitmap = if (isActive && spritePressedBitmap != null) spritePressedBitmap else spriteBitmap,
                contentDescription = "Hızlı ileriyi aç",
                contentScale = ContentScale.Fit,
                colorFilter = if (isActive && spritePressedBitmap == null) ColorFilter.tint(ButterscotchPrimary.copy(alpha = 0.45f), BlendMode.SrcAtop) else null,
                modifier = Modifier.fillMaxSize()
            )
        } else if (pixel) {
            PixelButtonFace(PixelIcons.FAST_FORWARD, pressed = isActive)
        } else {
            Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = "Hızlı ileriyi aç",
                tint = Color.White,
                // The Box is sized by placement (scale * shorter side), so size the glyph as a fraction of it
                modifier = Modifier.fillMaxSize(0.55f)
            )
        }
    }
}

@Composable
fun MouseButtonOverrideButton(
    isActive: Boolean,
    interactive: Boolean,
    element: GamepadElement.MouseButton,
    onMouseButtonPress: (GamepadElement.MouseButton) -> (Unit),
    onMouseButtonRelease: (GamepadElement.MouseButton) -> (Unit),
    modifier: Modifier = Modifier
) {
    val spriteBitmap = rememberSpriteBitmap(element.sprite)
    val spritePressedBitmap = rememberSpriteBitmap(element.spritePressed)
    val pixel = LocalGamepadStyle.current == GamepadStyle.PIXEL_ART
    Box(
        modifier = modifier
            .then(
                if (spriteBitmap == null && !pixel) {
                    Modifier
                        .clip(CircleShape)
                        .background(if (isActive) ButterscotchPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.22f))
                } else Modifier
            )
            .pointerInput(Unit) {
                if (interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        if (!element.toggle) {
                            onMouseButtonPress.invoke(element)
                            waitForUpOrCancellation()
                            onMouseButtonRelease.invoke(element)
                        } else {
                            onMouseButtonPress.invoke(element)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (spriteBitmap != null) {
            Image(
                bitmap = if (isActive && spritePressedBitmap != null) spritePressedBitmap else spriteBitmap,
                contentDescription = "Fare tuşunu aç",
                contentScale = ContentScale.Fit,
                colorFilter = if (isActive && spritePressedBitmap == null) ColorFilter.tint(ButterscotchPrimary.copy(alpha = 0.45f), BlendMode.SrcAtop) else null,
                modifier = Modifier.fillMaxSize()
            )
        } else if (pixel) {
            PixelButtonFace(PixelIcons.MOUSE, pressed = isActive)
        } else {
            Icon(
                imageVector = Icons.Default.Mouse,
                contentDescription = "Fare tuşunu aç",
                tint = Color.White,
                // The Box is sized by placement (scale * shorter side), so size the glyph as a fraction of it
                modifier = Modifier.fillMaxSize(0.55f)
            )
        }
    }
}

// ===[ Action Buttons ]===

// Loads a sprite from the layout sprite pool as an ImageBitmap, or null if unset/missing/corrupt
@Composable
fun rememberSpriteBitmap(fileName: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(fileName) {
        fileName?.let { BitmapFactory.decodeFile(Libraries.loadLayoutLibrary(context).spriteFile(it).path)?.asImageBitmap() }
    }
}

// A single round action button. The renderer sizes/positions it via [modifier] (which already
// carries the resolved size, offset, and opacity from the layout), so this only owns its look and
// press gesture.
//
// [type] is currently always Press: hold the binding while the pointer is down. RapidFire is part
// of the model but not wired yet - it needs a per-frame tick to emit repeat pulses, which the
// edge-only input pipeline does not have.
//
// With a [sprite] set the circle + label is replaced by the image. While pressed we swap to
// [spritePressed] when present, otherwise tint the sprite's pixels with the same press color flat
// buttons use.
@Composable
fun ActionButton(
    label: String,
    binding: InputBinding,
    trigger: KeyTrigger,
    keys: VirtualKeyState,
    interactive: Boolean,
    sprite: String? = null,
    spritePressed: String? = null,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val spriteBitmap = rememberSpriteBitmap(sprite)
    val spritePressedBitmap = rememberSpriteBitmap(spritePressed)
    val pixel = LocalGamepadStyle.current == GamepadStyle.PIXEL_ART
    val pixelLabel = remember(label) { pixelText(label) }
    Box(
        modifier = modifier
            .then(
                if (spriteBitmap == null && !pixel) {
                    Modifier
                        .clip(CircleShape)
                        .background(if (pressed) ButterscotchPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.22f))
                } else Modifier
            )
            .pointerInput(binding) {
                if (interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        keys.acquire(binding)
                        try {
                            // Hold the press as long as this specific pointer stays down. We don't care
                            // about position once it's grabbed - sliding off the button still keeps the
                            // key held (matches console gamepad UX better than "release on slide-off").
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                            }
                        } finally {
                            pressed = false
                            keys.release(binding)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (spriteBitmap != null) {
            Image(
                bitmap = if (pressed && spritePressedBitmap != null) spritePressedBitmap else spriteBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = if (pressed && spritePressedBitmap == null) ColorFilter.tint(ButterscotchPrimary.copy(alpha = 0.45f), BlendMode.SrcAtop) else null,
                modifier = Modifier.fillMaxSize()
            )
        } else if (pixel) {
            PixelButtonFace(pixelLabel, pressed = pressed)
        } else {
            Text(
                text = label,
                color = Color.White,
                // Text labels like "Shift"/"Enter" overflow a small round button at the glyph size, so step down for anything longer than 2 chars
                style = TextStyle(fontSize = if (label.length > 2) 13.sp else 22.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}


// ===[ Joystick ]===

/**
 * 8-way directional joystick. Mirrors the WebKT reference: map the finger position relative to the
 * joystick centre into an angle, snap that angle to one of 8 sectors (45 degrees each), and emit the
 * corresponding GameMaker arrow-key combo. A circular deadzone in the middle suppresses jitter.
 *
 * Visually, the thumb glyph follows the finger (clamped to the base radius) for tactile feedback.
 */
@Composable
fun JoystickBase(
    sprite: String? = null,
    spriteThumb: String? = null,
    modifier: Modifier = Modifier,
    modifierWithOffset: Modifier.(holdingState: MutableState<Boolean>, thumbOffset: MutableState<Offset>) -> Modifier
) {
    // The gesture writes the live finger target here, but the rendering is handled by thumb below
    val thumbOffset = remember { mutableStateOf(Offset.Zero) }
    val holding = remember { mutableStateOf(false) }
    val color = if (holding.value) ButterscotchPrimary else Color.White
    val baseBitmap = rememberSpriteBitmap(sprite)
    val thumbBitmap = rememberSpriteBitmap(spriteThumb)
    val pixel = LocalGamepadStyle.current == GamepadStyle.PIXEL_ART

    // This is the REAL position of the thumb
    val thumb = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(Unit) {
        snapshotFlow { holding.value to thumbOffset.value }.collectLatest { (isHolding, target) ->
            if (isHolding) {
                thumb.snapTo(target)
            } else {
                thumb.animateTo(Offset.Zero, tween(durationMillis = 40, easing = EaseOut))
            }
        }
    }

    // With a base sprite the circle clip/background goes away so non-circular art renders as authored.
    // The gesture math is unaffected, it derives the input zone from min(width, height) on its own
    Box(
        modifier = modifier
            .then(
                if (baseBitmap == null && !pixel) {
                    Modifier
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.18f))
                } else Modifier
            )
            .modifierWithOffset(holding, thumbOffset),
        contentAlignment = Alignment.Center
    ) {
        if (baseBitmap != null) {
            Image(
                bitmap = baseBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (pixel) {
                drawPixelJoystick(thumb.value, drawBase = baseBitmap == null, drawThumb = thumbBitmap == null, holding = holding.value)
                return@Canvas
            }
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            if (baseBitmap == null) {
                // Outer ring
                drawCircle(
                    color = color.copy(alpha = 0.35f),
                    radius = radius * 0.95f,
                    center = center,
                    style = Stroke(width = 4f)
                )
            }
            if (thumbBitmap == null) {
                // Thumb
                drawCircle(
                    color = color.copy(alpha = 0.6f),
                    radius = radius * 0.40f,
                    center = center + thumb.value
                )
            }
        }
        if (thumbBitmap != null) {
            // Same diameter as the drawn thumb (0.40 * minDimension). The lambda offset keeps the
            // per-frame finger tracking out of recomposition
            Image(
                bitmap = thumbBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize(0.4f)
                    .offset { IntOffset(thumb.value.x.roundToInt(), thumb.value.y.roundToInt()) }
            )
        }
    }
}

/**
 * 8-way directional joystick. Mirrors the WebKT reference: map the finger position relative to the
 * joystick centre into an angle, snap that angle to one of 8 sectors (45 degrees each), and emit the
 * corresponding GameMaker arrow-key combo. A circular deadzone in the middle suppresses jitter.
 *
 * Visually, the thumb glyph follows the finger (clamped to the base radius) for tactile feedback.
 */
@Composable
fun Joystick(
    up: InputBinding,
    down: InputBinding,
    left: InputBinding,
    right: InputBinding,
    keys: VirtualKeyState,
    interactive: Boolean,
    sprite: String? = null,
    spriteThumb: String? = null,
    modifier: Modifier = Modifier
) {
    JoystickBase(sprite, spriteThumb, modifier) { holding, thumbOffset ->
        this.pointerInput(up, down, left, right) {
            if (interactive) {
                awaitEachGesture {
                    val downPointer = awaitFirstDown(requireUnconsumed = false)
                    downPointer.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radiusPx = min(size.width, size.height) / 2f
                    val deadzonePx = radiusPx * 0.30f

                    var currentKeys = emptySet<InputBinding>()

                    fun update(position: Offset) {
                        val delta = position - center
                        val dist = sqrt(delta.x * delta.x + delta.y * delta.y)
                        // Visual thumb position: clamp to base radius so it stays inside the circle.
                        thumbOffset.value =
                            if (dist > radiusPx) delta * (radiusPx / dist) else delta

                        val newKeys = if (dist < deadzonePx) {
                            emptySet()
                        } else {
                            // Asymmetric 8-way sectors: cardinals are 60 degrees wide, diagonals are
                            // only 30 degrees wide. The WebKT reference uses equal 45/45 sectors,
                            // which makes "pure down" so narrow (only +-22.5 degrees from straight
                            // down) that natural thumb-pivot offset usually lands you in a diagonal.
                            // That breaks menus like Undertale's name entry, which dispatch each
                            // arrow as a discrete keyboard_check_pressed event - diagonal combos
                            // get mis-handled. Widening cardinals to 60 degrees gives much more
                            // forgiving "I'm pushing this way" detection while still leaving room
                            // for intentional diagonals (needed in DELTARUNE bullet boards, etc.).
                            //
                            // Angle is normalized to [0, 360) where 0=right, 90=down, 180=left, 270=up.
                            val angleDeg =
                                (Math.toDegrees(atan2(delta.y, delta.x).toDouble()) + 360.0) % 360.0
                            val cardinalHalfWidth = 30.0  // cardinal zone = +-30 around its axis
                            bindingsForAngle(angleDeg, cardinalHalfWidth, up, down, left, right)
                        }
                        keys.transition(currentKeys, newKeys)
                        currentKeys = newKeys
                    }

                    try {
                        update(downPointer.position)
                        holding.value = true
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == downPointer.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (change.positionChanged()) {
                                change.consume()
                                update(change.position)
                            }
                        }
                    } finally {
                        // Also runs when the joystick leaves composition mid-drag (e.g. swapped to a d-pad), so no direction gets stuck
                        holding.value = false
                        keys.transition(currentKeys, emptySet())
                    }
                }
            }
        }
    }
}

// ===[ D-Pad ]===

/**
 * Plus shaped directional pad. Touching (or sliding over) an arm holds that direction; the corner
 * zones between arms hold both neighbours, like a physical d-pad rocking diagonally. Uses the same
 * angle sectors as [Joystick] so menus get the same forgiving cardinals, but with a much smaller
 * deadzone since there is no thumb to drag out first.
 */
@Composable
fun DPad(
    up: InputBinding,
    down: InputBinding,
    left: InputBinding,
    right: InputBinding,
    keys: VirtualKeyState,
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    var held by remember { mutableStateOf(emptySet<InputBinding>()) }
    val pixel = LocalGamepadStyle.current == GamepadStyle.PIXEL_ART

    Canvas(
        modifier = modifier.pointerInput(up, down, left, right) {
            if (interactive) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    firstDown.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val deadzonePx = min(size.width, size.height) / 2f * 0.12f

                    fun directionsAt(position: Offset): Set<InputBinding> {
                        val delta = position - center
                        val dist = sqrt(delta.x * delta.x + delta.y * delta.y)
                        if (dist < deadzonePx) return emptySet()
                        val angleDeg = (Math.toDegrees(atan2(delta.y, delta.x).toDouble()) + 360.0) % 360.0
                        return bindingsForAngle(angleDeg, 30.0, up, down, left, right)
                    }

                    // Every finger on the pad presses its own direction(s) and the game gets the union, so
                    // opposite directions can be held at once (e.g. left with one thumb, tapping right with
                    // another), which a single angle can never produce
                    val perPointer = mutableMapOf<PointerId, Set<InputBinding>>()

                    fun sync() {
                        val newKeys = perPointer.values.flatten().toSet()
                        keys.transition(held, newKeys)
                        held = newKeys
                    }

                    try {
                        perPointer[firstDown.id] = directionsAt(firstDown.position)
                        sync()
                        while (perPointer.isNotEmpty()) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            for (change in event.changes) {
                                if (change.pressed) {
                                    // New fingers landing on the pad join in, moving ones re-aim
                                    if (change.id !in perPointer || change.positionChanged()) {
                                        perPointer[change.id] = directionsAt(change.position)
                                        change.consume()
                                    }
                                } else if (perPointer.remove(change.id) != null) {
                                    change.consume()
                                }
                            }
                            sync()
                        }
                    } finally {
                        // Also runs when the pad leaves composition mid-press (e.g. swapped to a joystick), so no direction gets stuck
                        keys.transition(held, emptySet())
                        held = emptySet()
                    }
                }
            }
        }
    ) {
        if (pixel) {
            drawPixelDPad(up in held, down in held, left in held, right in held)
            return@Canvas
        }
        val side = size.minDimension
        val arm = side * 0.34f
        val corner = CornerRadius(arm * 0.18f)
        val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        val idle = Color.White.copy(alpha = 0.22f)
        val pressed = ButterscotchPrimary.copy(alpha = 0.45f)
        val armLength = (side - arm) / 2f

        // Base cross, as one shape so the translucent fill doesn't double up where the bars cross
        fun bar(offset: Offset, barSize: Size) = Path().apply { addRoundRect(RoundRect(Rect(topLeft + offset, barSize), corner)) }
        val cross = Path().apply {
            op(bar(Offset(armLength, 0f), Size(arm, side)), bar(Offset(0f, armLength), Size(side, arm)), PathOperation.Union)
        }
        drawPath(cross, idle)

        // Pressed arms (the centre square stays as is, so diagonal presses don't overlap either)
        if (up in held) drawRoundRect(pressed, topLeft + Offset(armLength, 0f), Size(arm, armLength), corner)
        if (down in held) drawRoundRect(pressed, topLeft + Offset(armLength, armLength + arm), Size(arm, armLength), corner)
        if (left in held) drawRoundRect(pressed, topLeft + Offset(0f, armLength), Size(armLength, arm), corner)
        if (right in held) drawRoundRect(pressed, topLeft + Offset(armLength + arm, armLength), Size(armLength, arm), corner)

        // Arrow glyphs near the end of each arm
        val glyph = Color.White.copy(alpha = 0.7f)
        val c = topLeft + Offset(side / 2f, side / 2f)
        val tip = side / 2f - arm * 0.35f
        val half = arm * 0.22f
        val depth = arm * 0.26f
        fun arrow(dirX: Float, dirY: Float) {
            val apex = c + Offset(dirX * tip, dirY * tip)
            val base = c + Offset(dirX * (tip - depth), dirY * (tip - depth))
            val path = Path().apply {
                moveTo(apex.x, apex.y)
                lineTo(base.x + dirY * half, base.y + dirX * half)
                lineTo(base.x - dirY * half, base.y - dirX * half)
                close()
            }
            drawPath(path, glyph)
        }
        arrow(0f, -1f)
        arrow(0f, 1f)
        arrow(-1f, 0f)
        arrow(1f, 0f)
    }
}

// ===[ Directional Swap ]===

/**
 * Round button that flips joysticks into d-pads and back. The icon shows what a press switches to.
 */
@Composable
fun DirectionalSwapButton(
    showsDPad: Boolean,
    interactive: Boolean,
    onSwap: () -> (Unit),
    modifier: Modifier = Modifier
) {
    val pixel = LocalGamepadStyle.current == GamepadStyle.PIXEL_ART
    Box(
        modifier = modifier
            .then(if (pixel) Modifier else Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
            .pointerInput(Unit) {
                if (interactive) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        onSwap.invoke()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (pixel) {
            PixelButtonFace(if (showsDPad) PixelIcons.JOYSTICK else PixelIcons.DPAD, pressed = false)
            return@Box
        }
        Icon(
            imageVector = if (showsDPad) Icons.Default.RadioButtonChecked else Icons.Default.Games,
            contentDescription = if (showsDPad) "Joystick'e geç" else "D-pad'e geç",
            tint = Color.White,
            // The Box is sized by placement (scale * shorter side), so size the glyph as a fraction of it
            modifier = Modifier.fillMaxSize(0.55f)
        )
    }
}

// ===[ Analog Joystick ]===

/**
 * On-screen *analog* stick: unlike the digital [Joystick] (which snaps the finger angle to 4 arrow
 * keys), this reports the thumb's continuous position as two raw axis values in [-1, 1] straight to
 * the runner's gamepad feed (which deadzones on read). Pushing up is negative V, left is negative H,
 * matching GameMaker's gp_axis* convention and the physical-controller mapping.
 *
 * The thumb (and the reported magnitude) is clamped to the base radius so neither axis exceeds 1.0.
 * On release it re-centers to (0, 0). [VirtualKeyState.setAxis] also re-centers it if this composable
 * is torn down mid-drag (e.g. an Overlay/Stacked reflow).
 */
@Composable
fun AnalogJoystick(
    stick: GamepadStick,
    device: Int,
    keys: VirtualKeyState,
    interactive: Boolean,
    sprite: String? = null,
    spriteThumb: String? = null,
    modifier: Modifier = Modifier
) {
    JoystickBase(sprite, spriteThumb, modifier) { holding, thumbOffset ->
        this.pointerInput(stick) {
            if (interactive) {
                awaitEachGesture {
                    val downPointer = awaitFirstDown(requireUnconsumed = false)
                    downPointer.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radiusPx = min(size.width, size.height) / 2f

                    fun update(position: Offset) {
                        val delta = position - center
                        val dist = sqrt(delta.x * delta.x + delta.y * delta.y)
                        // Clamp to the base radius so the normalized value never exceeds 1.0.
                        val clamped = if (dist > radiusPx) delta * (radiusPx / dist) else delta
                        thumbOffset.value = clamped
                        val x = if (radiusPx > 0f) (clamped.x / radiusPx).coerceIn(-1f, 1f) else 0f
                        val y = if (radiusPx > 0f) (clamped.y / radiusPx).coerceIn(-1f, 1f) else 0f
                        keys.setAxis(device, stick.horizontalAxisIndex, x)
                        keys.setAxis(device, stick.verticalAxisIndex, y)
                    }

                    update(downPointer.position)
                    holding.value = true
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == downPointer.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        if (change.positionChanged()) {
                            change.consume()
                            update(change.position)
                        }
                    }
                    holding.value = false
                    keys.setAxis(device, stick.horizontalAxisIndex, 0f)
                    keys.setAxis(device, stick.verticalAxisIndex, 0f)
                }
            }
        }
    }
}

// Tiny helper to keep the gesture loop readable - Compose Change doesn't expose this directly.
private fun PointerInputChange.positionChanged(): Boolean =
    position != previousPosition

// Map a normalized polar angle (0..360, 0=right, 90=down) to the set of direction bindings for that
// direction, using asymmetric sectors: cardinals get 2 * cardinalHalfWidth degrees, diagonals get
// the rest.
//
// With cardinalHalfWidth=30, each cardinal covers 60 degrees and each diagonal covers 30 degrees -
// so as long as the finger is within 30 degrees of "straight down", we emit pure DOWN with no
// spurious LEFT/RIGHT companion press.
private fun bindingsForAngle(
    angleDeg: Double,
    cardinalHalfWidth: Double,
    up: InputBinding,
    down: InputBinding,
    left: InputBinding,
    right: InputBinding
): Set<InputBinding> = when {
    angleDeg < cardinalHalfWidth || angleDeg >= 360.0 - cardinalHalfWidth -> setOf(right)
    angleDeg < 90.0 - cardinalHalfWidth -> setOf(right, down)
    angleDeg < 90.0 + cardinalHalfWidth -> setOf(down)
    angleDeg < 180.0 - cardinalHalfWidth -> setOf(down, left)
    angleDeg < 180.0 + cardinalHalfWidth -> setOf(left)
    angleDeg < 270.0 - cardinalHalfWidth -> setOf(left, up)
    angleDeg < 270.0 + cardinalHalfWidth -> setOf(up)
    else -> setOf(up, right)
}
