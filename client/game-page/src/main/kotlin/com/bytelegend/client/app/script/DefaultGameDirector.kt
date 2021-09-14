/*
 * Copyright 2021 ByteLegend Technologies and the original author or authors.
 *
 * Licensed under the GNU Affero General Public License v3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://github.com/ByteLegend/ByteLegend/blob/master/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.bytelegend.client.app.script

import com.bytelegend.app.client.api.EventBus
import com.bytelegend.app.client.api.GameRuntime
import com.bytelegend.app.client.api.ScriptsBuilder
import com.bytelegend.app.client.api.SpeechBuilder
import com.bytelegend.app.client.api.dsl.SuspendUnitFunction
import com.bytelegend.app.client.api.dsl.UnitFunction
import com.bytelegend.app.client.misc.playAudio
import com.bytelegend.app.shared.GridCoordinate
import com.bytelegend.app.shared.PixelCoordinate
import com.bytelegend.client.app.engine.DefaultGameScene
import com.bytelegend.client.app.engine.GAME_SCRIPT_NEXT
import com.bytelegend.client.app.engine.GAME_UI_UPDATE_EVENT
import com.bytelegend.client.app.engine.Game
import com.bytelegend.client.app.engine.GameControl
import com.bytelegend.client.app.engine.GameMouseEvent
import com.bytelegend.client.app.engine.calculateCoordinateInGameContainer
import com.bytelegend.client.app.engine.logger
import com.bytelegend.client.app.obj.CharacterSprite
import com.bytelegend.client.app.script.effect.itemPopupEffect
import com.bytelegend.client.app.script.effect.showArrowGif
import com.bytelegend.client.app.ui.COORDINATE_BORDER_FLICKER
import com.bytelegend.client.app.ui.GameProps
import com.bytelegend.client.app.ui.GameUIComponent
import com.bytelegend.client.app.ui.determineRightSideBarTopLeftCornerCoordinateInGameContainer
import com.bytelegend.client.app.ui.mission.HIGHTLIGHT_TITLES_EVENT
import com.bytelegend.client.app.ui.script.SpeechBubbleWidget
import com.bytelegend.client.app.ui.script.Widget
import com.bytelegend.client.app.web.WebSocketClient
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import org.w3c.dom.HTMLElement
import react.RHandler
import kotlin.reflect.KClass

interface GameScript {
    /**
     * Start an execution of script. A script can be:
     *
     * 1. Display a speech bubble.
     * 2. Display an animation, such as star, fireworks, etc.
     * 3. Move viewport to other part of map.
     * 4. Move NPC to another location.
     * ...
     *
     */
    fun start()
    fun stop() {}
}

const val STAR_BYTELEGEND_MISSION_ID = "star-bytelegend"
const val MAIN_CHANNEL = "MainChannel"
const val ASYNC_ANIMATION_CHANNEL = "AsyncAnimation"

/**
 * A director directs the scripts running on the scene, in a specific channel.
 */
class DefaultGameDirector(
    di: DI,
    private val channel: String,
    private val gameScene: DefaultGameScene
) : ScriptsBuilder {
    private val gameControl: GameControl by di.instance()
    private val game: GameRuntime by di.instance()
    private val eventBus: EventBus by di.instance()

    /**
     * Main channel means that it can respond to user click or other events.
     * Also, user mouse will be disabled during scripts running.
     *
     * This channel is usually used to display main story, like NPC speech.
     */
    private val isMainChannel: Boolean = channel == MAIN_CHANNEL

    /**
     * When it is true, the user mouse click can trigger next script to run,
     * like speech bubbles.
     */
    private var isRespondToClick: Boolean = false
    private val webSocketClient: WebSocketClient by lazy {
        gameScene.gameRuntime.unsafeCast<Game>().webSocketClient
    }

    private val scripts: MutableList<GameScript> = mutableListOf()

    // Point to next script to run
    private var index = -1

    /**
     * A counter providing unique id for widgets in DOM
     */
    private var counter = 0

    val isRunning: Boolean
        get() = index != -1

    init {
        eventBus.on<String>(GAME_SCRIPT_NEXT) { channel ->
            if (channel == this.channel && gameScene.isActive) {
                if (scripts.isNotEmpty()) {
                    next()
                }
            }
        }
    }

    private fun respondToClick(enabled: Boolean) {
        if (isMainChannel) {
            isRespondToClick = enabled
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onMouseClickOnCanvas(event: GameMouseEvent) {
        if (isRunning && isRespondToClick) {
            next()
        }
    }

    /**
     * Trigger next script to run.
     */
    private fun next() {
        if (scripts.isEmpty()) {
            throw IllegalStateException("Scripts should not be empty!")
        }
        if (channel == ASYNC_ANIMATION_CHANNEL && (!gameControl.isWindowVisible || game.modalController.visible)) {
            return
        }

        if (index == -1) {
            index = 0
        }

        if (index != 0) {
            scripts[index - 1].stop()
        }

        if (index == scripts.size) {
            reset()
            return
        }

        val script = scripts[index++]
        logger.debug("Running script $channel:${index - 1}: $script")

        script.start()
    }

    private fun reset() {
        scripts.clear()
        index = -1
    }

    fun scripts(block: ScriptsBuilder.() -> Unit) {
        scripts(true, block)
    }

    fun scripts(runImmediately: Boolean, block: ScriptsBuilder.() -> Unit) {
        block()
        if (runImmediately) {
            next()
        }
    }

    @Suppress("UnsafeCastFromDynamic")
    override fun speech(action: SpeechBuilder.() -> Unit) {
        val builder = SpeechBuilder()
        builder.action()

        if (builder.speakerId == null && builder.speakerCoordinate == null) {
            throw IllegalArgumentException("Either speakerId or speakerCoordinate need to be set for speech!")
        }

        scripts.add(
            DisplayWidgetScript(
                SpeechBubbleWidget::class,
                {
                    attrs.game = gameScene.gameRuntime.asDynamic()
                    attrs.speakerId = builder.speakerId
                    attrs.speakerCoordinate = builder.speakerCoordinate
                    attrs.contentHtml = gameScene.gameRuntime.i(builder.contentHtmlId!!, *builder.args)
                    attrs.arrow = builder.arrow
                },
                builder.contentHtmlId,
                builder.dismissMs
            )
        )
    }

    fun suspendAnimation(fn: SuspendUnitFunction) {
        scripts.add(RunSuspendFunctionScript(fn))
    }

    override fun playAnimate(objectId: String, frames: List<Int>, intervalMs: Int) {
        TODO("Not yet implemented")
    }

    override fun characterMove(characterId: String, destMapCoordinate: GridCoordinate, callback: UnitFunction) {
        scripts.add(CharacterMoveScript(gameScene.objects.getById(characterId), destMapCoordinate, callback))
    }

    override fun startBeginnerGuide() {
        scripts.add(BeginnerGuideScript())
    }

    override fun putState(key: String, value: String) {
        scripts.add(
            RunSuspendFunctionScript {
                webSocketClient.putState(key, value)
                game.heroPlayer.states[key] = value
            }
        )
    }

    override fun removeState(key: String) {
        scripts.add(
            RunSuspendFunctionScript {
                webSocketClient.removeState(key)
                game.heroPlayer.states.remove(key)
            }
        )
    }

    override fun removeItem(item: String, targetCoordinate: GridCoordinate?) {
        scripts.add(RemoveItemScript(item, targetCoordinate))
    }

    inner class BeginnerGuideScript : GameScript {
        lateinit var arrowGif: HTMLElement
        override fun start() {
            // show gif arrow pointing to the coordinate
            // highlight the first mission
            respondToClick(true)
            arrowGif = showArrowGif(gameScene.canvasState.getUICoordinateInGameContainer(), game.i("ThisIsCoordinate"))
            eventBus.emit(COORDINATE_BORDER_FLICKER, true)
            eventBus.emit(HIGHTLIGHT_TITLES_EVENT, listOf(STAR_BYTELEGEND_MISSION_ID))
        }

        override fun stop() {
            respondToClick(false)
            eventBus.emit(COORDINATE_BORDER_FLICKER, false)
            eventBus.emit(HIGHTLIGHT_TITLES_EVENT, null)
            document.body?.removeChild(arrowGif)
        }
    }

    fun getAndIncrement(): Int {
        return counter++
    }

    inner class DisplayWidgetScript<P : GameProps>(
        private val klass: KClass<out GameUIComponent<P, *>>,
        private val handler: RHandler<P>,
        private val stringRepresentation: String?,
        private val dismissMs: Int = 0
    ) : GameScript {
        private val id = "${gameScene.map.id}-ScriptWidget-$channel-${getAndIncrement()}"
        override fun start() {
            respondToClick(true)
            gameScene.scriptWidgets[id] = Widget(klass, handler)
            eventBus.emit(GAME_UI_UPDATE_EVENT, null)

            if (dismissMs != 0) {
                window.setTimeout({
                    next()
                }, dismissMs)
            }
        }

        override fun stop() {
            respondToClick(false)
            gameScene.scriptWidgets.remove(id)
            eventBus.emit(GAME_UI_UPDATE_EVENT, null)
        }

        override fun toString(): String {
            return stringRepresentation ?: id
        }
    }

    inner class CharacterMoveScript(
        val character: CharacterSprite,
        val destMapCoordinate: GridCoordinate,
        val callback: UnitFunction
    ) : GameScript {
        override fun start() {
            character.moveTo(destMapCoordinate) {
                callback()
                next()
            }
        }
    }

    inner class RunNativeJsScript(
        val pixelCoordinate: PixelCoordinate
    ) : GameScript {
        override fun start() {
            next()
//            window.asDynamic().starFly(0, 0, 500, 500, 2).then
//            , {
//                next()
//            })
        }
    }

    inner class RemoveItemScript(
        private val item: String,
        private val destination: GridCoordinate?
    ) : GameScript {
        override fun start() {
            if (destination != null) {
                itemDisappearAnimation()
                GlobalScope.launch {
                    playAudio("popup")
                    val canvasState = gameScene.canvasState
                    itemPopupEffect(
                        item,
                        canvasState.gameContainerSize,
                        canvasState.determineRightSideBarTopLeftCornerCoordinateInGameContainer() + PixelCoordinate(0, 200), /* items box offset */
                        canvasState.calculateCoordinateInGameContainer(destination),
                        3.0
                    )
                    delay(3000)
                    next()
                }
            } else {
                itemDisappearAnimation()
            }
        }

        fun itemDisappearAnimation() {
            GlobalScope.launch {
                game.heroPlayer.items.remove(item)
                game.eventBus.emit(GAME_UI_UPDATE_EVENT, null)
                webSocketClient.removeItem(item)
            }
        }
    }

    inner class RunSuspendFunctionScript(
        private val fn: suspend () -> Unit
    ) : GameScript {
        override fun start() {
            GlobalScope.launch {
                fn()
                next()
            }
        }
    }
}
