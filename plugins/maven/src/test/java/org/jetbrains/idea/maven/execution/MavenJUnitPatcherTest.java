/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.project.MavenProjectSettings;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

public class MavenJUnitPatcherTest extends MavenImportingTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MavenProjectSettings.getInstance(myProject).getTestRunningSettings().setPassArgLine(true);
    MavenProjectSettings.getInstance(myProject).getTestRunningSettings().setPassEnvironmentVariables(true);
    MavenProjectSettings.getInstance(myProject).getTestRunningSettings().setPassSystemProperties(true);
  }

  public void testExcludeClassPathElement() throws CantRunException {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>\n" +
                                           "<artifactId>m1</artifactId>\n" +
                                           "<version>1</version>\n" +
                                           "<dependencies>\n" +
                                           "  <dependency>\n" +
                                           "    <groupId>org.jetbrains</groupId>\n" +
                                           "    <artifactId>annotations</artifactId>\n" +
                                           "    <version>17.0.0</version>\n" +
                                           "  </dependency>\n" +
                                           "  <dependency>\n" +
                                           "    <groupId>org.jetbrains</groupId>\n" +
                                           "    <artifactId>annotations-java5</artifactId>\n" +
                                           "    <version>17.0.0</version>\n" +
                                           "  </dependency>\n" +
                                           "</dependencies>\n" +
                                           "<build>\n" +
                                           "  <plugins>\n" +
                                           "    <plugin>\n" +
                                           "      <groupId>org.apache.maven.plugins</groupId>\n" +
                                           "      <artifactId>maven-surefire-plugin</artifactId>\n" +
                                           "      <version>2.16</version>\n" +
                                           "      <configuration>\n" +
                                           "        <classpathDependencyExcludes>\n" +
                                           "          <classpathDependencyExclude>org.jetbrains:annotations</classpathDependencyExclude>\n" +
                                           "        </classpathDependencyExcludes>\n" +
                                           "      </configuration>\n" +
                                           "    </plugin>\n" +
                                           "  </plugins>\n" +
                                           "</build>\n");

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.configureByModule(module, JavaParameters.CLASSES_AND_TESTS, IdeaTestUtil.getMockJdk18());
    assertEquals(asList("annotations-17.0.0.jar", "annotations-java5-17.0.0.jar"),
                 ContainerUtil.map(javaParameters.getClassPath().getPathList(), path -> new File(path).getName()));
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    List<String> classPath = javaParameters.getClassPath().getPathList();
    assertEquals(Collections.singletonList("annotations-java5-17.0.0.jar"),
                 ContainerUtil.map(javaParameters.getClassPath().getPathList(), path -> new File(path).getName()));
  }

  public void testExcludeScope() throws CantRunException {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>\n" +
                                           "<artifactId>m1</artifactId>\n" +
                                           "<version>1</version>\n" +
                                           "<dependencies>\n" +
                                           "  <dependency>\n" +
                                           "    <groupId>org.jetbrains</groupId>\n" +
                                           "    <artifactId>annotations</artifactId>\n" +
                                           "    <version>17.0.0</version>\n" +
                                           "    <scope>runtime</scope>\n" +
                                           "  </dependency>\n" +
                                           "  <dependency>\n" +
                                           "    <groupId>org.jetbrains</groupId>\n" +
                                           "    <artifactId>annotations-java5</artifactId>\n" +
                                           "    <version>17.0.0</version>\n" +
                                           "  </dependency>\n" +
                                           "</dependencies>\n" +
                                           "<build>\n" +
                                           "  <plugins>\n" +
                                           "    <plugin>\n" +
                                           "      <groupId>org.apache.maven.plugins</groupId>\n" +
                                           "      <artifactId>maven-surefire-plugin</artifactId>\n" +
                                           "      <version>2.16</version>\n" +
                                           "      <configuration>\n" +
                                           "        <classpathDependencyScopeExclude>runtime</classpathDependencyScopeExclude>\n" +
                                           "      </configuration>\n" +
                                           "    </plugin>\n" +
                                           "  </plugins>\n" +
                                           "</build>\n");

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.configureByModule(module, JavaParameters.CLASSES_AND_TESTS, IdeaTestUtil.getMockJdk18());
    assertEquals(asList("annotations-17.0.0.jar", "annotations-java5-17.0.0.jar"),
                 ContainerUtil.map(javaParameters.getClassPath().getPathList(), path -> new File(path).getName()));
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    List<String> classPath = javaParameters.getClassPath().getPathList();
    assertEquals(Collections.singletonList("annotations-java5-17.0.0.jar"),
                 ContainerUtil.map(javaParameters.getClassPath().getPathList(), path -> new File(path).getName()));
  }

  public void testAddClassPath() {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>\n" +
                                           "<artifactId>m1</artifactId>\n" +
                                           "<version>1</version>\n" +
                                           "<build>\n" +
                                           "  <plugins>\n" +
                                           "    <plugin>\n" +
                                           "      <groupId>org.apache.maven.plugins</groupId>\n" +
                                           "      <artifactId>maven-surefire-plugin</artifactId>\n" +
                                           "      <version>2.16</version>\n" +
                                           "      <configuration>\n" +
                                           "        <additionalClasspathElements>\n" +
                                           "          <additionalClasspathElement>path/to/additional/resources</additionalClasspathElement>\n" +
                                           "          <additionalClasspathElement>path/to/additional/jar</additionalClasspathElement>\n" +
                                           "          <additionalClasspathElement>path/to/csv/jar1, path/to/csv/jar2</additionalClasspathElement>\n" +
                                           "        </additionalClasspathElements>\n" +
                                           "      </configuration>\n" +
                                           "    </plugin>\n" +
                                           "  </plugins>\n" +
                                           "</build>\n");

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    List<String> classPath = javaParameters.getClassPath().getPathList();
    assertEquals(asList("path/to/additional/resources", "path/to/additional/jar", "path/to/csv/jar1", "path/to/csv/jar2"), classPath);
  }

  public void testArgList() {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>" +
                                           "<dependencies>" +
                                           "  <dependency>" +
                                           "    <groupId>test</groupId>" +
                                           "    <artifactId>m2</artifactId>" +
                                           "    <version>1</version>" +
                                           "  </dependency>" +
                                           "</dependencies>" +
                                           "<build><plugins>" +
                                           "  <plugin>" +
                                           "    <groupId>org.apache.maven.plugins</groupId>" +
                                           "    <artifactId>maven-surefire-plugin</artifactId>" +
                                           "    <version>2.16</version>" +
                                           "    <configuration>" +
                                           "      <argLine>-Xmx2048M -XX:MaxPermSize=512M \"-Dargs=can have spaces\"</argLine>" +
                                           "    </configuration>" +
                                           "  </plugin>" +
                                           "</plugins></build>");

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(asList("-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces"),
                 javaParameters.getVMParametersList().getList());
  }

  public void testVmPropertiesResolve() {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>" +
                                           "<dependencies>" +
                                           "  <dependency>" +
                                           "    <groupId>test</groupId>" +
                                           "    <artifactId>m2</artifactId>" +
                                           "    <version>1</version>" +
                                           "  </dependency>" +
                                           "</dependencies>" +
                                           "<build><plugins>" +
                                           "  <plugin>" +
                                           "    <groupId>org.apache.maven.plugins</groupId>" +
                                           "    <artifactId>maven-surefire-plugin</artifactId>" +
                                           "    <version>2.16</version>" +
                                           "    <configuration>" +
                                           "      <argLine>-Xmx2048M -XX:MaxPermSize=512M \"-Dargs=can have spaces\" ${argLineApx}</argLine>" +
                                           "    </configuration>" +
                                           "  </plugin>" +
                                           "</plugins></build>");

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.getVMParametersList().addProperty("argLineApx", "-DsomeKey=someValue");
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(asList("-DargLineApx=-DsomeKey=someValue", "-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces", "-DsomeKey=someValue"),
                 javaParameters.getVMParametersList().getList());
  }

  public void testArgLineLateReplacement() {
    VirtualFile m1 = createModulePom("m1", "<groupId>test</groupId>" +
                                           "<artifactId>m1</artifactId>" +
                                           "<version>1</version>" +
                                           "<build><plugins>" +
                                           "  <plugin>" +
                                           "    <groupId>org.apache.maven.plugins</groupId>" +
                                           "    <artifactId>maven-surefire-plugin</artifactId>" +
                                           "    <version>2.16</version>" +
                                           "    <configuration>" +
                                           "      <argLine>@{argLine} -Xmx2048M -XX:MaxPermSize=512M \"-Dargs=can have spaces\"</argLine>" +
                                           "    </configuration>" +
                                           "  </plugin>" +
                                           "</plugins></build>");

    importProjects(m1);
    Module module = getModule("m1");

    MavenJUnitPatcher mavenJUnitPatcher = new MavenJUnitPatcher();
    JavaParameters javaParameters = new JavaParameters();
    javaParameters.getVMParametersList().add("-ea");
    mavenJUnitPatcher.patchJavaParameters(module, javaParameters);
    assertEquals(asList("-ea", "-Xmx2048M", "-XX:MaxPermSize=512M", "-Dargs=can have spaces"),
                 javaParameters.getVMParametersList().getList());
  }
}
