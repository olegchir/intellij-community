// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.conversion.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.conversion.*;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.ide.impl.convert.JDomConvertingUtil;
import com.intellij.ide.impl.convert.ProjectFileVersionImpl;
import com.intellij.ide.impl.convert.ProjectFileVersionState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author nik
 */
public class ConversionContextImpl implements ConversionContext {
  private static final Logger LOG = Logger.getInstance("#com.intellij.conversion.impl.ConversionContextImpl");
  private final Map<Path, SettingsXmlFile> mySettingsFiles = new HashMap<>();
  private final StorageScheme myStorageScheme;
  private final Path myProjectBaseDir;
  private final Path myProjectFile;
  private final Path myWorkspaceFile;
  private final File[] myModuleFiles;
  private ProjectSettingsImpl myProjectSettings;
  private WorkspaceSettingsImpl myWorkspaceSettings;
  private final List<Path> myNonExistingModuleFiles = new ArrayList<>();
  private final Map<File, ModuleSettingsImpl> myFile2ModuleSettings = new HashMap<>();
  private final Map<String, ModuleSettingsImpl> myName2ModuleSettings = new HashMap<>();
  private RunManagerSettingsImpl myRunManagerSettings;
  private Path mySettingsBaseDir;
  private ComponentManagerSettings myCompilerManagerSettings;
  private ComponentManagerSettings myProjectRootManagerSettings;
  private ComponentManagerSettingsImpl myModulesSettings;
  private ProjectLibrariesSettingsImpl myProjectLibrariesSettings;
  private ArtifactsSettingsImpl myArtifactsSettings;
  private ComponentManagerSettings myProjectFileVersionSettings;
  private final Set<String> myPerformedConversionIds;

  public ConversionContextImpl(@NotNull Path projectPath) throws CannotConvertException {
    myProjectFile = projectPath;

    Path modulesFile;
    if (Files.isDirectory(myProjectFile)) {
      myStorageScheme = StorageScheme.DIRECTORY_BASED;
      myProjectBaseDir = myProjectFile;
      mySettingsBaseDir = myProjectBaseDir.toAbsolutePath().resolve(Project.DIRECTORY_STORE_FOLDER);
      modulesFile = mySettingsBaseDir.resolve("modules.xml");
      myWorkspaceFile = mySettingsBaseDir.resolve("workspace.xml");
    }
    else {
      myStorageScheme = StorageScheme.DEFAULT;
      myProjectBaseDir = myProjectFile.getParent();
      modulesFile = myProjectFile;
      myWorkspaceFile = Paths.get(StringUtil.trimEnd(projectPath.toString(), ProjectFileType.DOT_DEFAULT_EXTENSION) + WorkspaceFileType.DOT_DEFAULT_EXTENSION);
    }

    myModuleFiles = Files.exists(modulesFile) ? findModuleFiles(JDomConvertingUtil.load(modulesFile)) : new File[0];
    myPerformedConversionIds = loadPerformedConversionIds();
  }

  public Set<File> getAllProjectFiles() {
    Set<File> files = new HashSet<>(Arrays.asList(myModuleFiles));
    if (myStorageScheme == StorageScheme.DEFAULT) {
      files.add(myProjectFile.toFile());
      files.add(myWorkspaceFile.toFile());
    }
    else {
      File dotIdeaDirectory = mySettingsBaseDir.toFile();
      addXmlFilesFromDirectory(dotIdeaDirectory, files);
      for (String subdirectoryName : new String[]{"libraries", "artifacts", "runConfigurations"}) {
        addXmlFilesFromDirectory(new File(dotIdeaDirectory, subdirectoryName), files);
      }
    }
    return files;
  }

  private static void addXmlFilesFromDirectory(File dir, Set<? super File> result) {
    File[] children = dir.listFiles(
      child -> child.isFile() && FileUtilRt.extensionEquals(child.getPath(), "xml") && !dir.getName().startsWith(".")
    );
    if (children != null) {
      Collections.addAll(result, children);
    }
  }

  public boolean isConversionAlreadyPerformed(ConverterProvider provider) {
    return myPerformedConversionIds.contains(provider.getId());
  }

  @Override
  @NotNull
  public File getProjectBaseDir() {
    return myProjectBaseDir.toFile();
  }

  @Override
  public File[] getModuleFiles() {
    return myModuleFiles;
  }

  private File[] findModuleFiles(final Element root) {
    final Element modulesManager = JDomSerializationUtil.findComponent(root, ModuleManagerImpl.COMPONENT_NAME);
    if (modulesManager == null) return new File[0];

    final Element modules = modulesManager.getChild(ModuleManagerImpl.ELEMENT_MODULES);
    if (modules == null) return new File[0];

    final ExpandMacroToPathMap macros = createExpandMacroMap();

    List<File> files = new ArrayList<>();
    for (Element module : modules.getChildren(ModuleManagerImpl.ELEMENT_MODULE)) {
      String filePath = module.getAttributeValue(ModuleManagerImpl.ATTRIBUTE_FILEPATH);
      if (filePath != null) {
        filePath = macros.substitute(filePath, true);
        files.add(new File(FileUtil.toSystemDependentName(filePath)));
      }
    }
    return files.toArray(new File[0]);
  }

  @NotNull
  public String expandPath(@NotNull String path, @NotNull ModuleSettingsImpl moduleSettings) {
    return createExpandMacroMap(moduleSettings).substitute(path, true);
  }

  private ExpandMacroToPathMap createExpandMacroMap(@Nullable ModuleSettingsImpl moduleSettings) {
    final ExpandMacroToPathMap map = createExpandMacroMap();
    if (moduleSettings != null) {
      final String modulePath = FileUtil.toSystemIndependentName(moduleSettings.getModuleFile().getParentFile().getAbsolutePath());
      map.addMacroExpand(PathMacroUtil.MODULE_DIR_MACRO_NAME, modulePath);
    }
    return map;
  }

  @Override
  @NotNull
  public String expandPath(@NotNull String path) {
    ExpandMacroToPathMap map = createExpandMacroMap(null);
    return map.substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  @Override
  @NotNull
  public String collapsePath(@NotNull String path) {
    ReplacePathToMacroMap map = createCollapseMacroMap(PathMacroUtil.PROJECT_DIR_MACRO_NAME, myProjectBaseDir.toFile());
    return map.substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  public static String collapsePath(@NotNull String path, @NotNull ModuleSettingsImpl moduleSettings) {
    final ReplacePathToMacroMap map = createCollapseMacroMap(PathMacroUtil.MODULE_DIR_MACRO_NAME, moduleSettings.getModuleFile().getParentFile());
    return map.substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  private static ReplacePathToMacroMap createCollapseMacroMap(final String macroName, final File dir) {
    ReplacePathToMacroMap map = new ReplacePathToMacroMap();
    map.addMacroReplacement(FileUtil.toSystemIndependentName(dir.getAbsolutePath()), macroName);
    PathMacrosImpl.getInstanceEx().addMacroReplacements(map);
    return map;
  }

  @Override
  public Collection<File> getLibraryClassRoots(@NotNull String name, @NotNull String level) {
    try {
      Element libraryElement = null;
      if (LibraryTablesRegistrar.PROJECT_LEVEL.equals(level)) {
        libraryElement = findProjectLibraryElement(name);
      }
      else if (LibraryTablesRegistrar.APPLICATION_LEVEL.equals(level)) {
        libraryElement = findGlobalLibraryElement(name);
      }

      if (libraryElement != null) {
        return getClassRoots(libraryElement, null);
      }

      return Collections.emptyList();
    }
    catch (CannotConvertException e) {
      return Collections.emptyList();
    }
  }

  @NotNull
  public List<File> getClassRoots(Element libraryElement, @Nullable ModuleSettingsImpl moduleSettings) {
    List<File> files = new ArrayList<>();
    //todo[nik] support jar directories
    final Element classesChild = libraryElement.getChild("CLASSES");
    if (classesChild != null) {
      final ExpandMacroToPathMap pathMap = createExpandMacroMap(moduleSettings);
      for (Element root : classesChild.getChildren("root")) {
        final String url = root.getAttributeValue("url");
        final String path = VfsUtilCore.urlToPath(url);
        files.add(new File(PathUtil.getLocalPath(pathMap.substitute(path, true))));
      }
    }
    return files;
  }

  @Override
  public ComponentManagerSettings getCompilerSettings() {
    if (myCompilerManagerSettings == null) {
      myCompilerManagerSettings = createProjectSettings("compiler.xml");
    }
    return myCompilerManagerSettings;
  }

  @Override
  public ComponentManagerSettings getProjectRootManagerSettings() {
    if (myProjectRootManagerSettings == null) {
      myProjectRootManagerSettings = createProjectSettings("misc.xml");
    }
    return myProjectRootManagerSettings;
  }

  @Override
  public ComponentManagerSettings getModulesSettings() {
    if (myModulesSettings == null) {
      myModulesSettings = createProjectSettings("modules.xml");
    }
    return myModulesSettings;
  }

  @Nullable
  public ComponentManagerSettings getProjectFileVersionSettings() {
    if (myProjectFileVersionSettings == null) {
      myProjectFileVersionSettings = createProjectSettings("misc.xml");
    }
    return myProjectFileVersionSettings;
  }

  @Override
  @Nullable
  public ComponentManagerSettingsImpl createProjectSettings(@NotNull final String fileName) {
    try {
      Path file;
      if (myStorageScheme == StorageScheme.DEFAULT) {
        file = myProjectFile;
      }
      else {
        file = mySettingsBaseDir.resolve(fileName);
        if (!Files.exists(file)) {
          return null;
        }
      }
      return new ComponentManagerSettingsImpl(file, this);
    }
    catch (CannotConvertException e) {
      LOG.info(e);
      return null;
    }
  }

  @Nullable
  private static Element findGlobalLibraryElement(String name) throws CannotConvertException {
    final File file = PathManager.getOptionsFile("applicationLibraries");
    if (file.exists()) {
      final Element root = JDomConvertingUtil.load(file.toPath());
      final Element libraryTable = JDomSerializationUtil.findComponent(root, "libraryTable");
      if (libraryTable != null) {
        return findLibraryInTable(libraryTable, name);
      }
    }
    return null;
  }

  @Nullable
  private Element findProjectLibraryElement(String name) throws CannotConvertException {
    final Collection<? extends Element> libraries = getProjectLibrariesSettings().getProjectLibraries();
    final Condition<Element> filter = JDomConvertingUtil.createElementWithAttributeFilter(LibraryImpl.ELEMENT,
                                                                                          LibraryImpl.LIBRARY_NAME_ATTR, name);
    return ContainerUtil.find(libraries, filter);
  }

  @Nullable
  private static Element findLibraryInTable(Element tableElement, String name) {
    final Condition<Element> filter = JDomConvertingUtil.createElementWithAttributeFilter(LibraryImpl.ELEMENT,
                                                                                          LibraryImpl.LIBRARY_NAME_ATTR, name);
    return JDomConvertingUtil.findChild(tableElement, filter);
  }

  private ExpandMacroToPathMap createExpandMacroMap() {
    final ExpandMacroToPathMap macros = new ExpandMacroToPathMap();
    final String projectDir = FileUtil.toSystemIndependentName(myProjectBaseDir.toAbsolutePath().toString());
    macros.addMacroExpand(PathMacroUtil.PROJECT_DIR_MACRO_NAME, projectDir);
    PathMacrosImpl.getInstanceEx().addMacroExpands(macros);
    return macros;
  }

  @Override
  public File getSettingsBaseDir() {
    return mySettingsBaseDir.toFile();
  }

  @NotNull
  @Override
  public File getProjectFile() {
    return myProjectFile.toFile();
  }

  @Override
  public ProjectSettings getProjectSettings() throws CannotConvertException {
    if (myProjectSettings == null) {
      myProjectSettings = new ProjectSettingsImpl(myProjectFile, this);
    }
    return myProjectSettings;
  }

  @Override
  public RunManagerSettingsImpl getRunManagerSettings() throws CannotConvertException {
    if (myRunManagerSettings == null) {
      if (myStorageScheme == StorageScheme.DEFAULT) {
        myRunManagerSettings = new RunManagerSettingsImpl(myWorkspaceFile, myProjectFile, null, this);
      }
      else {
        File[] files = mySettingsBaseDir.resolve("runConfigurations").toFile().listFiles(FileFilters.filesWithExtension("xml"));
        myRunManagerSettings = new RunManagerSettingsImpl(myWorkspaceFile, null, files, this);
      }
    }
    return myRunManagerSettings;
  }

  @Override
  public WorkspaceSettings getWorkspaceSettings() throws CannotConvertException {
    if (myWorkspaceSettings == null) {
      myWorkspaceSettings = new WorkspaceSettingsImpl(myWorkspaceFile, this);
    }
    return myWorkspaceSettings;
  }


  @Override
  public ModuleSettings getModuleSettings(File moduleFile) throws CannotConvertException {
    ModuleSettingsImpl settings = myFile2ModuleSettings.get(moduleFile);
    if (settings == null) {
      settings = new ModuleSettingsImpl(moduleFile.toPath(), this);
      myFile2ModuleSettings.put(moduleFile, settings);
      myName2ModuleSettings.put(settings.getModuleName(), settings);
    }
    return settings;
  }

  @Override
  public ModuleSettings getModuleSettings(@NotNull String moduleName) {
    if (!myName2ModuleSettings.containsKey(moduleName)) {
      for (File moduleFile : myModuleFiles) {
        try {
          getModuleSettings(moduleFile);
        }
        catch (CannotConvertException ignored) {
        }
      }
    }
    return myName2ModuleSettings.get(moduleName);
  }

  public List<Path> getNonExistingModuleFiles() {
    return myNonExistingModuleFiles;
  }

  @NotNull
  @Override
  public StorageScheme getStorageScheme() {
    return myStorageScheme;
  }

  public Path getWorkspaceFile() {
    return myWorkspaceFile;
  }

  public void saveFiles(Collection<? extends Path> files, List<? extends ConversionRunner> usedRunners) throws IOException {
    Set<String> performedConversions = new HashSet<>();
    for (ConversionRunner runner : usedRunners) {
      final ConverterProvider provider = runner.getProvider();
      if (!provider.canDetermineIfConversionAlreadyPerformedByProjectFiles()) {
        performedConversions.add(provider.getId());
      }
    }
    if (!performedConversions.isEmpty()) {
      performedConversions.addAll(myPerformedConversionIds);
      final ProjectFileVersionState state = new ProjectFileVersionState();
      final List<String> performedConversionsList = new ArrayList<>(performedConversions);
      Collections.sort(performedConversionsList, String.CASE_INSENSITIVE_ORDER);
      state.setPerformedConversionIds(performedConversionsList);
      final ComponentManagerSettings settings = getProjectFileVersionSettings();
      if (settings != null) {
        final Element element =
          JDomSerializationUtil.findOrCreateComponentElement(settings.getRootElement(), ProjectFileVersionImpl.COMPONENT_NAME);
        XmlSerializer.serializeInto(state, element);
      }
    }

    for (Path file : files) {
      final SettingsXmlFile xmlFile = mySettingsFiles.get(file);
      if (xmlFile != null) {
        xmlFile.save();
      }
    }
  }

  private Set<String> loadPerformedConversionIds() {
    final ComponentManagerSettings component = getProjectFileVersionSettings();
    if (component != null) {
      final Element componentElement = component.getComponentElement(ProjectFileVersionImpl.COMPONENT_NAME);
      if (componentElement != null) {
        final ProjectFileVersionState state = XmlSerializer.deserialize(componentElement, ProjectFileVersionState.class);
        return new HashSet<>(state.getPerformedConversionIds());
      }
    }
    return Collections.emptySet();
  }

  public SettingsXmlFile getOrCreateFile(Path file) throws CannotConvertException {
    SettingsXmlFile settingsFile = mySettingsFiles.get(file);
    if (settingsFile == null) {
      settingsFile = new SettingsXmlFile(file);
      mySettingsFiles.put(file, settingsFile);
    }
    return settingsFile;
  }

  @Override
  public ProjectLibrariesSettingsImpl getProjectLibrariesSettings() throws CannotConvertException {
    if (myProjectLibrariesSettings == null) {
      myProjectLibrariesSettings = myStorageScheme == StorageScheme.DEFAULT
                                   ? new ProjectLibrariesSettingsImpl(myProjectFile, null, this)
                                   : new ProjectLibrariesSettingsImpl(null, getSettingsXmlFiles("libraries"), this);
    }
    return myProjectLibrariesSettings;
  }

  @Override
  public ArtifactsSettingsImpl getArtifactsSettings() throws CannotConvertException {
    if (myArtifactsSettings == null) {
      myArtifactsSettings = myStorageScheme == StorageScheme.DEFAULT
                            ? new ArtifactsSettingsImpl(myProjectFile, null, this)
                            : new ArtifactsSettingsImpl(null, getSettingsXmlFiles("artifacts"), this);
    }
    return myArtifactsSettings;
  }

  @NotNull
  private File[] getSettingsXmlFiles(@NotNull String dirName) {
    Path librariesDir = mySettingsBaseDir.resolve(dirName);
    return ObjectUtils.notNull(librariesDir.toFile().listFiles(FileFilters.filesWithExtension("xml")), ArrayUtilRt.EMPTY_FILE_ARRAY);
  }
}
