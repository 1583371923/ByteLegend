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

package com.bytelegend.client.app.ui.noticeboard

import com.bytelegend.app.client.api.EventListener
import com.bytelegend.app.client.ui.bootstrap.BootstrapModalBody
import com.bytelegend.app.client.ui.bootstrap.BootstrapSpinner
import com.bytelegend.app.shared.GridCoordinate
import com.bytelegend.app.shared.protocol.ChallengeUpdateEventData
import com.bytelegend.app.shared.util.currentTimeMillis
import com.bytelegend.client.app.engine.MISSION_REPAINT_EVENT
import com.bytelegend.client.app.ui.GameProps
import com.bytelegend.client.app.ui.unsafeSpan
import com.bytelegend.client.utils.jsObjectBackedSetOf
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.html.classes
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onLoadFunction
import kotlinx.html.js.onMouseMoveFunction
import kotlinx.html.js.onMouseOutFunction
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import react.RBuilder
import react.RComponent
import react.State
import react.dom.b
import react.dom.div
import react.dom.h2
import react.dom.img
import react.dom.jsStyle
import react.dom.p
import react.dom.span
import react.setState

fun bravePeopleJsonUrl(timestamp: Long) = "/proxy/brave-people-all.json?timestamp=$timestamp"
fun bravePeopleImgUrl(timestamp: Long) = "/proxy/brave-people.png?timestamp=$timestamp"

// Don't change these values. They are defined elsewhere:
// https://github.com/ByteLegendQuest/remember-brave-people/blob/master/src/main/java/com/bytelegend/game/Constants.java#L26
const val AVATAR_TILE_SIZE = 30

// https://github.com/ByteLegendQuest/remember-brave-people/blob/master/src/main/java/com/bytelegend/game/SimpleTile.java
// https://github.com/ByteLegendQuest/remember-brave-people/blob/master/src/main/java/com/bytelegend/game/AllInfoTile.java
data class AvatarTile(
    val x: Int,
    val y: Int,
    val color: String,
    val username: String,
    val createdAt: String,
    val changedAt: String
)

interface JavaIslandNewbieVillageNoticeboardState : State {
    var hoveredTile: AvatarTile?
    var hoveredTileCoordinate: GridCoordinate?
    var avatarTiles: Array<AvatarTile>?
    var imageDisplay: String
    var timestamp: Long
}

class JavaIslandNewbieVillageNoticeboard :
    RComponent<GameProps, JavaIslandNewbieVillageNoticeboardState>() {
    private var loading = false
    private val onChallengeRepaintListener: EventListener<ChallengeUpdateEventData> = this::onMissionRepaint

    private fun onMissionRepaint(eventData: ChallengeUpdateEventData) {
        // Refresh upon mission finished event
        if (eventData.change.accomplished && eventData.newValue.missionId == "remember-brave-people") {
            setState {
                init()
            }
        }
    }

    override fun JavaIslandNewbieVillageNoticeboardState.init() {
        loading = false
        avatarTiles = undefined
        imageDisplay = "none"
        timestamp = currentTimeMillis()
    }

    private fun imgAndJsonLoaded(): Boolean {
        return state.avatarTiles != undefined && state.imageDisplay == "block"
    }

    override fun RBuilder.render() {
        BootstrapModalBody {
            h2 {
                attrs.jsStyle.textAlign = "center"
                b {
                    +props.game.i("BravePeopleBoard")
                }
            }
            p {
                attrs.jsStyle.textAlign = "center"
                unsafeSpan(props.game.i("BravePeopleDedication"))
            }
            div {
                attrs.classes = jsObjectBackedSetOf("noticeboard-avatars-div")

                if (!imgAndJsonLoaded()) {
                    if (loading) {
                        div {
                            attrs.classes = jsObjectBackedSetOf("flex-center")
                            BootstrapSpinner {
                                attrs.animation = "border"
                            }
                        }
                    } else {
                        GlobalScope.launch {
                            val json = window.fetch(bravePeopleJsonUrl(state.timestamp))
                                .await()
                                .apply {
                                    if (status < 200 || status > 400) {
                                        throw Exception("Got response status code $status")
                                    }
                                }.text().await()
                            setState({
                                it.avatarTiles = JSON.parse(json)
                                it
                            }, { loading = false })
                        }
                        loading = true
                    }
                }
                avatarImg()

                if (state.hoveredTile != undefined) {
                    avatarTooltip()
                }
            }
            span {
                attrs.jsStyle {
                    margin = "0 auto"
                    display = "table"
                }
                if (state.hoveredTileCoordinate != null) {
                    +"(${state.hoveredTileCoordinate!!.x}, ${state.hoveredTileCoordinate!!.y})"
                } else {
                    attrs.classes = jsObjectBackedSetOf("transparent-text")
                    +"Yay! You found an easter egg!"
                }
            }
        }
    }

    private fun findTileByMouseCoordinate(event: Event): AvatarTile? {
        val e = event.asDynamic().nativeEvent as MouseEvent
        val hoveredTileX = e.offsetX.toInt() / AVATAR_TILE_SIZE
        val hoveredTileY = e.offsetY.toInt() / AVATAR_TILE_SIZE
        for (tile in state.avatarTiles!!) {
            if (tile.x == hoveredTileX && tile.y == hoveredTileY) {
                return tile
            }
        }
        return null
    }

    private fun toCoordinate(event: Event): GridCoordinate {
        val e = event.asDynamic().nativeEvent as MouseEvent
        return GridCoordinate(
            e.offsetX.toInt() / AVATAR_TILE_SIZE,
            e.offsetY.toInt() / AVATAR_TILE_SIZE
        )
    }

    private fun RBuilder.avatarImg() {
        img {
            attrs.classes = jsObjectBackedSetOf("noticeboard-avatars-img")
            attrs.src = bravePeopleImgUrl(state.timestamp)
            attrs.jsStyle {
                display = state.imageDisplay
            }
            attrs.onLoadFunction = {
                setState {
                    imageDisplay = "block"
                }
            }
            if (imgAndJsonLoaded()) {
                attrs.onClickFunction = {
                    findTileByMouseCoordinate(it)?.apply {
                        window.open("https://github.com/$username", "_blank")
                    }
                }
                attrs.onMouseMoveFunction = {
                    val coordinate: GridCoordinate = toCoordinate(it)
                    val hoveredTile: AvatarTile? = findTileByMouseCoordinate(it)

                    val updateCoordinate = (state.hoveredTileCoordinate != coordinate)
                    val updateTile = (state.hoveredTile?.x != hoveredTile?.x || state.hoveredTile?.y != hoveredTile?.y)
                    setState {
                        if (updateTile) {
                            this.hoveredTile = hoveredTile
                        }
                        if (updateCoordinate) {
                            this.hoveredTileCoordinate = coordinate
                        }
                    }
                }
                attrs.onMouseOutFunction = {
                    setState {
                        hoveredTile = null
                        hoveredTileCoordinate = null
                    }
                }
            }
        }
    }

    private fun RBuilder.avatarTooltip() {
        child(AvatarTooltip::class) {
            attrs.game = props.game
            attrs.joinedAtI18n = props.game.i("JoinedAt")
            attrs.tile = state.hoveredTile!!
        }
    }

    override fun componentDidMount() {
        props.game.eventBus.on(MISSION_REPAINT_EVENT, onChallengeRepaintListener)
    }

    override fun componentWillUnmount() {
        props.game.eventBus.remove(MISSION_REPAINT_EVENT, onChallengeRepaintListener)
    }
}
