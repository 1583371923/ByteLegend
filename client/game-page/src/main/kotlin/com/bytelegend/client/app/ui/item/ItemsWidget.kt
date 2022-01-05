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
package com.bytelegend.client.app.ui.item

import com.bytelegend.app.client.ui.bootstrap.BootstrapListGroupItem
import com.bytelegend.client.app.engine.getIconUrl
import com.bytelegend.client.app.ui.GameProps
import com.bytelegend.client.app.ui.GameUIComponent
import com.bytelegend.client.app.ui.HistoryModal
import kotlinext.js.jso
import react.ChildrenBuilder
import react.Fragment
import react.State
import react.create
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.react

interface ItemWidgetProps : GameProps

class ItemsWidget : GameUIComponent<ItemWidgetProps, State>() {
    override fun render() = Fragment.create {
        BootstrapListGroupItem {
            val items = game.heroPlayer.items
            if (items.isNotEmpty()) {
                renderOne(items[0])
                renderText("...")
            } else {
                renderText(i("Items"))
            }
        }
    }

    private fun ChildrenBuilder.renderOne(item: String) {
        img {
            src = game.getIconUrl(item)
            className = "inline-icon-16 item-$item"
        }
    }

    private fun ChildrenBuilder.renderText(text: String) {
        div {
            className = "map-title-text items-widget"
            onClick = {
                game.modalController.show {
                    child(HistoryModal::class.react, jso {
                        this.game = props.game
                    })
                }
            }
            +text
        }
    }
}
