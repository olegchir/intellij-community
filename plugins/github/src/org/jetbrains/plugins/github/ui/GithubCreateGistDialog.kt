/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.*
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.ui.GithubAccountCombobox
import javax.swing.JComponent
import javax.swing.JTextArea

class GithubCreateGistDialog(project: Project,
                             accounts: Set<GithubAccount>,
                             defaultAccount: GithubAccount?,
                             fileName: String?,
                             secret: Boolean,
                             openInBrowser: Boolean,
                             copyLink: Boolean) : DialogWrapper(project, true) {
  private val myFileNameField: JBTextField? = if (fileName != null) JBTextField(fileName) else null
  private val myDescriptionField: JTextArea = JTextArea()
  private val mySecretCheckBox: JBCheckBox = JBCheckBox("Secret", secret)
  private val myOpenInBrowserCheckBox: JBCheckBox = JBCheckBox("Open in browser", openInBrowser)
  private val myCopyLinkCheckBox: JBCheckBox = JBCheckBox("Copy URL", copyLink)
  private val myAccountSelector: GithubAccountCombobox = GithubAccountCombobox(accounts, defaultAccount, null)

  val fileName: String?
    get() = myFileNameField?.text

  val description: String
    get() = myDescriptionField.text

  val isSecret: Boolean
    get() = mySecretCheckBox.isSelected

  val isOpenInBrowser: Boolean
    get() = myOpenInBrowserCheckBox.isSelected

  val isCopyURL: Boolean
    get() = myCopyLinkCheckBox.isSelected

  val account: GithubAccount
    get() = myAccountSelector.selectedItem as GithubAccount

  init {
    title = "Create Gist"
    init()
  }

  override fun createCenterPanel() = panel {
    myFileNameField?.let {
      row("Filename:") {
        it(pushX, growX)
      }
    }
    row("Description:") {
      scrollPane(myDescriptionField)
    }
    row("") {
      cell {
        mySecretCheckBox()
        myOpenInBrowserCheckBox()
        myCopyLinkCheckBox()
      }
    }
    if (myAccountSelector.isEnabled) {
      row("Create for:") {
        myAccountSelector(pushX, growX)
      }
    }
  }

  override fun getHelpId(): String? {
    return "github.create.gist.dialog"
  }

  override fun getDimensionServiceKey(): String? {
    return "Github.CreateGistDialog"
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return myDescriptionField
  }
}
