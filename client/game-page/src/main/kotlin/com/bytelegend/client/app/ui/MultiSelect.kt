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
@file:Suppress("UnsafeCastFromDynamic")

package com.bytelegend.client.app.ui

import com.bytelegend.client.app.external.ReactSelect
import com.bytelegend.client.app.external.ReactSelectProps
import kotlinext.js.clone
import kotlinext.js.jso
import react.Component
import react.Fragment
import react.Props
import react.State
import react.create

data class Option(
    /**
     * The value for machine
     */
    val value: String,
    /**
     * The value name displayed for human beings
     */
    val label: String
) {
    constructor(jsObj: dynamic) : this(jsObj.value, jsObj.label)

    fun toJsObject(): dynamic = jso {
        this.value = this@Option.value
        this.label = this@Option.label
    }
}

interface MultiSelectProps : Props {
    var className: String?
    var initOptions: List<Option>
    var id: String
    var allOptions: List<Option>

    /**
     * User select multiple values and close the dropdown
     */
    var onSelectComplete: (List<Option>) -> List<Option>
    var configuration: ReactSelectProps.() -> Unit
}

interface MultiSelectState : State {
    var selectedOptions: List<Option>
}

class MultiSelect(props: MultiSelectProps) : Component<MultiSelectProps, MultiSelectState>(props) {
    init {
        state = jso { selectedOptions = props.initOptions }
    }

    override fun render() = Fragment.create {
        ReactSelect {
            if (props.configuration != undefined) {
                props.configuration.invoke(this)
            }
            if (props.id != undefined) {
                id = props.id
            }
            className = "${props.className ?: ""} mission-modal-tutorial-filter"
            options = props.allOptions.map { it.toJsObject() }.toTypedArray()
            value = state.selectedOptions.map { it.toJsObject() }.toTypedArray()

            onChange = { it: Array<dynamic> ->
                setState { selectedOptions = it.map { Option(it) }.distinct() }
            }
            isMulti = true
            closeMenuOnSelect = false
            onMenuClose = {
                setState {
                    selectedOptions = props.onSelectComplete(state.selectedOptions.map { Option(it) })
                }
            }
            styles = jso<dynamic> {
                container = { provided: dynamic, _: dynamic ->
                    val ret: dynamic = clone(provided)
                    ret.width = "${(this@MultiSelect.state.selectedOptions.size + 1) * 100}px"
                    ret
                }
            }
        }
    }
}
