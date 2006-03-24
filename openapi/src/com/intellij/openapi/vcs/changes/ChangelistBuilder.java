package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

/**
 * @author max
 */
public interface ChangelistBuilder {
  void processChange(Change change);
  void processUnversionedFile(VirtualFile file);
  void processLocallyDeletedFile(File file);
  void processModifiedWithoutEditing(VirtualFile file);
}
