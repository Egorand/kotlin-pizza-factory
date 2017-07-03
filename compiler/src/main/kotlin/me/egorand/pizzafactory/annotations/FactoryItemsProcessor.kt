/*
 * Copyright 2017 Egor Andreevici
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.egorand.pizzafactory.annotations

import com.google.auto.common.BasicAnnotationProcessor
import com.google.auto.service.AutoService
import com.google.common.collect.SetMultimap
import java.io.File
import java.io.IOException
import java.util.*
import javax.annotation.processing.Messager
import javax.annotation.processing.Processor
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.tools.Diagnostic

@AutoService(Processor::class)
class FactoryItemsProcessor : BasicAnnotationProcessor() {

  override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

  override fun getSupportedOptions(): Set<String> = setOf(KAPT_KOTLIN_GENERATED_OPTION_NAME)

  override fun initSteps(): Iterable<ProcessingStep> {
    val outputDirectory = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]?.let { File(it) }
        ?: throw IllegalArgumentException("No output directory given")

    return listOf(FactoryItemsProcessingStep(
        elements = processingEnv.elementUtils,
        messager = processingEnv.messager,
        outputDir = outputDirectory))
  }

  companion object {
    private const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
  }
}

class FactoryItemsProcessingStep(
    private val elements: Elements,
    private val messager: Messager,
    private val outputDir: File
) : BasicAnnotationProcessor.ProcessingStep {

  private val factoryClasses = LinkedHashMap<String, FactoryItemGroupedClasses>()

  override fun annotations() = setOf(FactoryItem::class.java)

  override fun process(elementsByAnnotation: SetMultimap<Class<out Annotation>, Element>): Set<Element> {
    try {
      for (annotatedElement in elementsByAnnotation[FactoryItem::class.java]) {
        if (annotatedElement.kind !== ElementKind.CLASS) {
          throw ProcessingException(annotatedElement,
              "Only classes can be annotated with @${FactoryItem::class.java.simpleName}")
        }
        val typeElement = annotatedElement as TypeElement
        val annotatedClass = FactoryItemAnnotatedClass(typeElement)
        annotatedClass.checkValidClass()
        var factoryClass = factoryClasses[annotatedClass.qualifiedGroupClassName]
        if (factoryClass == null) {
          val qualifiedGroupName = annotatedClass.qualifiedGroupClassName
          factoryClass = FactoryItemGroupedClasses(qualifiedGroupName)
          factoryClasses.put(qualifiedGroupName, factoryClass)
        }
        factoryClass += annotatedClass
      }
      for (factoryClass in factoryClasses.values) {
        factoryClass.generateCode(elements, outputDir)
      }
      factoryClasses.clear()
    } catch (e: ProcessingException) {
      error(e.element, e.message)
    } catch (e: IOException) {
      error(null, e.message)
    }
    return emptySet()
  }

  @Throws(ProcessingException::class)
  private fun FactoryItemAnnotatedClass.checkValidClass() {
    annotatedClassElement.checkPublic {
      throw ProcessingException(annotatedClassElement,
          "The class ${annotatedClassElement.qualifiedName} is not public.")
    }
    annotatedClassElement.checkNotAbstract {
      throw ProcessingException(annotatedClassElement, """
          |The class ${annotatedClassElement.qualifiedName} is abstract.
          |You can't annotate abstract classes with @${FactoryItem::class.java.simpleName}.
          |""".trimMargin())
    }
    annotatedClassElement.checkImplementsInterface(qualifiedGroupClassName) {
      throw ProcessingException(annotatedClassElement, """
          |The class ${annotatedClassElement.qualifiedName} annotated with
          |@${FactoryItem::class.java.simpleName} must implement the interface
          |$qualifiedGroupClassName.
          |""".trimMargin())
    }
    annotatedClassElement.checkHasPublicConstructor {
      throw ProcessingException(annotatedClassElement, """
          |The class ${annotatedClassElement.qualifiedName}
          |must provide an public empty default constructor.
          |""".trimMargin())
    }
  }

  private fun TypeElement.checkPublic(errorBlock: () -> Throwable) {
    if (Modifier.PUBLIC !in modifiers) {
      throw errorBlock()
    }
  }

  private fun TypeElement.checkNotAbstract(errorBlock: () -> Throwable) {
    if (Modifier.ABSTRACT in modifiers) {
      throw errorBlock()
    }
  }

  private fun TypeElement.checkImplementsInterface(
      interfaceName: String,
      errorBlock: () -> Throwable) {
    val superClassElement = elements.getTypeElement(interfaceName)
    if (superClassElement.kind == ElementKind.INTERFACE) {
      if (superClassElement.asType() !in interfaces) {
        throw errorBlock()
      }
    }
  }

  private fun TypeElement.checkHasPublicConstructor(errorBlock: () -> Throwable) {
    for (enclosed in enclosedElements) {
      if (enclosed.kind === ElementKind.CONSTRUCTOR) {
        val constructorElement = enclosed as ExecutableElement
        if (constructorElement.parameters.size == 0 &&
            Modifier.PUBLIC in constructorElement.modifiers) {
          return
        }
      }
    }
    throw errorBlock()
  }

  private fun error(e: Element?, msg: String?) {
    messager.printMessage(Diagnostic.Kind.ERROR, msg, e)
  }
}