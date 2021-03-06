// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue

/**
 * Contains a lot of UI related constants for clone dialog that can be helpful for external implementations.
 */
object VcsCloneDialogUiSpec {
  object ExtensionsList {
    val iconSize = JBValue.UIInteger("VcsCloneDialog.iconSize", 22)
    const val iconTitleGap = 6
    val insets = JBUI.insets(8, 10)
  }

  object Components {
    val avatarSize = JBValue.UIInteger("VcsCloneDialog.Accounts.AvatarSize", 24)
    val popupMenuAvatarSize = JBValue.UIInteger("VcsCloneDialog.Accounts.Popup.AvatarSize", 40)
  }
}
