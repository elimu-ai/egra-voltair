/*
 * Copyright (C) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import QtQuick 2.0
import VoltAir 1.0
import "../graphics"

/**
 * @ingroup QQuickItem
 * @brief Simple dialog for confirming or canceling an action.
 */
UiComponent {
    id: root

    /**
     * @brief Question to ask the user to confirm whether they want to perform the action or not.
     */
    property string questionText
    property string questionIcon

    /**
     * @brief type:string Text that indicates to the user that the action should not be performed.
     */
    //property alias cancelText: cancelButton.buttonText//
    //property string cancelText//

    property alias cancelUnselectedIconSource: cancelButton.unselectedIconSource
    property alias cancelSelectedIconSource: cancelButton.selectedIconSource

    /**
     * @brief type:string Text that indicates to the user that the action should be performed.
     */
    //property alias confirmText: confirmButton.buttonText
    //property string confirmText

    property alias confirmUnselectedIconSource: confirmButton.unselectedIconSource
    property alias confirmSelectedIconSource: confirmButton.selectedIconSource

    /**
     * @brief Emitted when the cancel button is activated.
     */
    signal canceled()
    /**
     * @brief Emitted when the confirm button is activated.
     */
    signal confirmed()

    showState: "SHOWING"
    hideState: "HIDDEN"

    states: [
        State { name: "SHOWING" },
        State { name: "HIDDEN" }
    ]

    transitions: [
        Transition {
            from: "HIDDEN"
            to: "SHOWING"
            SequentialAnimation {
                NumberAnimation {
                    target: root
                    property: "opacity"
                    to: 1.0
                    duration: showDuration
                }
                ScriptAction { script: root.showCompleted() }
            }
        },
        Transition {
            from: "SHOWING"
            to: "HIDDEN"
            SequentialAnimation {
                NumberAnimation {
                    target: root
                    property: "opacity"
                    to: 0.0
                    duration: hideDuration
                }
                ScriptAction { script: root.hideCompleted() }
            }
        }
    ]

    captureBackNavigation: true
    backNavigationMode: QmlConstants.BackNavigation_GamepadOnlyMode

    SimpleFrame {
        id: frame
        anchors.fill: parent

       SimpleFrame {
            id: frame1

            anchors.horizontalCenter: parent.horizontalCenter
            anchors.top: parent.top
            anchors.topMargin: 0.15 * parent.height

            width: 0.8 * parent.width
            height: 0.3 * parent.height

            /*VoltairImage {
                id: questionImage

                //anchors. .horizontalCenter: parent.left
                anchors.top: parent.top
                anchors.leftMargin: 0.1 * parent.width
                anchors.topMargin: 0 /*.15 * parent.height *-/

                width: 0.3 * parent.width
                height: 0.9 * parent.height

                sourceImage: questionIcon
            }*/

            VoltAirText {
                id: question

                anchors.horizontalCenter: parent.horizontalCenter// + 0.1 * parent.width
                anchors.top: parent.top
                anchors.topMargin: /*0.15 * parent.height*/ 0

                width: 0.6 * parent.width
                height: /*0.3 * */parent.height

                textElement.text: questionText
            }

        }

        MenuIcon {
            id: cancelButton

            anchors.left: parent.left
            anchors.bottom: parent.bottom
            anchors.leftMargin: 0.1 * root.width
            anchors.bottomMargin: 0.1 * root.height

            width: 0.3 * parent.width
            height: 0.3 * parent.height

            focusRight: confirmButton

            focus: true

            onReleased: {
                root.canceled()
            }
        }

        MenuIcon {
            id: confirmButton

            anchors.right: parent.right
            anchors.bottom: parent.bottom
            anchors.rightMargin: 0.1 * parent.width
            anchors.bottomMargin: 0.1 * parent.height

            width: 0.3 * parent.width
            height: 0.3 * parent.height

            focusLeft: cancelButton

            unselectedIconSource: Util.getPathToImage("UI/back.png")
            selectedIconSource: Util.getPathToImage("UI/back_h.png")

            onReleased: {
                root.confirmed()
            }
        }

    }

    onEscaped: {
        root.canceled()
    }
}
