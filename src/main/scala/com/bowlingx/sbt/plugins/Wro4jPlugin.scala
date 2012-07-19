/**
 * A SBT Plugin for wro4j (http://code.google.com/p/wro4j/)
 *
 * Copyright 2012 David Heidrich
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
package com.bowlingx.sbt.plugins

import sbt._
import Keys._
import ro.isdc.wro.manager.factory.standalone.{InjectableContextAwareManagerFactory, StandaloneContext}
import ro.isdc.wro.extensions.manager.standalone.ExtensionsStandaloneManagerFactory
import ro.isdc.wro.config._
import org.mockito.Mockito
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import ro.isdc.wro.http.support.DelegatingServletOutputStream
import java.io.{FileInputStream, ByteArrayOutputStream}
import javax.servlet.FilterConfig
import ro.isdc.wro.model.resource.processor.factory.ConfigurableProcessorsFactory
import ro.isdc.wro.model.resource.ResourceType
import java.util.Properties
import wro4j.CommonProcessorProvider

/**
 * A Wro4j Plugin
 */

object Wro4jPlugin extends Plugin {

  object Wro4jKeys {
    // Tasks
    val generateResources = TaskKey[Array[File]]("wro4j", "Starts compiling all your definied Groups in wro.xml")
    // Settings
    val wroFile = SettingKey[File]("wro4j-file", "wro.xml File")
    val outputFolder = SettingKey[String]("wro4j-output-folder", "Where are all those groups written? Relative to contextFolder")
    val contextFolder = SettingKey[File]("wro4j-context-folder", "Context Folder (your static resources root Dir)")
    val propertiesFile = SettingKey[File]("wro4j-properties", "wro.properties File")
  }

  import Wro4jKeys._

  private[this] def managerFactory(contextFolder: File, wroFile: File, propertiesFile: File) = {
    val context = new StandaloneContext()
    context.setIgnoreMissingResources(true)
    context.setContextFolder(contextFolder)
    context.setWroFile(wroFile)
    context.setMinimize(true)

    val managerFactory = new InjectableContextAwareManagerFactory(
      new ExtensionsStandaloneManagerFactory())
    val configurable = new ConfigurableProcessorsFactory()
    val props = new Properties()
    props.load(new FileInputStream(propertiesFile))
    configurable.setProperties(props)

    val pc = new CommonProcessorProvider
    configurable.setPostProcessorsMap(pc.providePostProcessors())
    configurable.setPreProcessorsMap(pc.providePreProcessors())

    managerFactory.initialize(context)

    managerFactory.setProcessorsFactory(configurable)
    managerFactory.create()
  }

  private def lessCompilerTask =
    (streams, sourceDirectory in generateResources, outputFolder in generateResources,
      wroFile in generateResources, contextFolder in generateResources, propertiesFile in generateResources, target in Compile) map {
      (out, sourcesDir, outputFolder, wroFile, contextFolder, propertiesFile, targetFolder) =>
        out.log.info("Generating Web-Resources")

        Context.set(Context.standaloneContext())

        import scala.collection.JavaConversions._
        val factory = managerFactory(contextFolder, wroFile, propertiesFile)
        for {
          suffix <- ResourceType.values()
          groupName <- factory.getModelFactory.create().getGroupNames

          val relative = outputFolder
          val outFile = "%s.%s" format(groupName, suffix.toString.toLowerCase)
          val outputFileName = "/%s/%s.%s" format(relative, groupName, suffix.toString.toLowerCase)
          val stream = {
            out.log.info("Using relative Context: /%s" format relative)

            out.log.info("Processing Group: [%s] with type [%s]" format(groupName, suffix))
            // Mock request, return current GroupName + Suffix
            val request = Mockito.mock(classOf[HttpServletRequest])
            Mockito.when(request.getRequestURI).thenReturn(outputFileName)
            // Mock Response, write everything in ByteArray instead of delivering to Browser :)
            val response = Mockito.mock(classOf[HttpServletResponse])
            val createdOutputStream = new ByteArrayOutputStream()
            Mockito.when(response.getOutputStream).thenReturn(new DelegatingServletOutputStream(createdOutputStream))

            // Initilize WebContext
            val conf = Context.get().getConfig
            Context.set(Context.webContext(request, response, Mockito.mock(classOf[FilterConfig])), conf)

            factory.process()

            createdOutputStream.toByteArray
          }
          if stream.length > 0
        } yield {
          val t = targetFolder / "webapp" / outputFolder
          t mkdirs()
          val output = t / outFile
          out.log.info("Writing Group File: [%s] with type [%s] to: %s" format(groupName, suffix, output.getAbsolutePath))
          IO.write(output, stream)
          // Return Generated Files (for further processing)
          output
        }
    }


  lazy val wro4jSettings = inConfig(Compile)(Seq(
    // Default WroFile
    wroFile in generateResources <<= (sourceDirectory in Compile)(_ / "webapp" / "WEB-INF" / "wro.xml"),
    // Default ContextFolder
    contextFolder in generateResources <<= (sourceDirectory in Compile)(_ / "webapp"),
    // Default output Folder (relative Path)
    outputFolder in generateResources := "compiled/",
    // Properties
    propertiesFile in generateResources <<= (sourceDirectory in Compile)(_ / "webapp" / "WEB-INF" / "wro.properties"),

    // Generate Task
    generateResources <<= lessCompilerTask,
    // Generate Resource task is invoked if compile
    compile in Compile <<= (compile in Compile) dependsOn (generateResources in Compile)
  ))


}
