package com.bowlingx.sbt.plugins.wro4j

import ro.isdc.wro.util.provider.ConfigurableProviderSupport
import collection.immutable.HashMap
import ro.isdc.wro.model.resource.processor.impl.css._
import ro.isdc.wro.model.resource.processor.impl.js.{JSMinProcessor, SemicolonAppenderPreProcessor}
import ro.isdc.wro.extensions.processor.css.{YUICssCompressorProcessor, LessCssProcessor}
import ro.isdc.wro.model.resource.processor.decorator.{ProcessorDecorator, CopyrightKeeperProcessorDecorator}
import scala.collection.JavaConverters._
import ro.isdc.wro.model.resource.processor.{ResourcePostProcessor, ResourcePreProcessor}

class CommonProcessorProvider extends ConfigurableProviderSupport {

  object A {
    val COPYRIGHT_KEEPER = "copyrightMin"
  }

  import A._

  override def providePreProcessors() = mapAsJavaMapConverter(processors).asJava

  override def providePostProcessors() = mapAsJavaMapConverter(
    processors.map(v => v._1 -> new ProcessorDecorator(v._2).asInstanceOf[ResourcePostProcessor])).asJava

  protected def processors: HashMap[String, ResourcePreProcessor] = {
    HashMap(
      CssUrlRewritingProcessor.ALIAS -> new CssUrlRewritingProcessor(),
      CssImportPreProcessor.ALIAS -> new CssImportPreProcessor(),
      SemicolonAppenderPreProcessor.ALIAS -> new SemicolonAppenderPreProcessor(),
      LessCssProcessor.ALIAS -> new LessCssProcessor(),
      CssDataUriPreProcessor.ALIAS -> new CssDataUriPreProcessor(),
      COPYRIGHT_KEEPER -> CopyrightKeeperProcessorDecorator.decorate(new JSMinProcessor()),
      YUICssCompressorProcessor.ALIAS -> new YUICssCompressorProcessor()
    )
  }
}
