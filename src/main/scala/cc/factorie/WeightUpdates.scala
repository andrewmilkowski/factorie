/* Copyright (C) 2008-2010 Univ of Massachusetts Amherst, Computer Science Dept
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://code.google.com/p/factorie/
   This software is provided under the terms of the Eclipse Public License 1.0
   as published by http://www.opensource.org.  For further information,
   see the file `LICENSE.txt' included with this distribution. */

package cc.factorie
//import scalala.Scalala._
//import scalala.tensor.Vector
import cc.factorie.la._

/** For parameter estimation methods that use a gradient to update weight parameters. 
    @author Andrew McCallum */
trait WeightUpdates {
  type TemplatesToUpdate <: DotTemplate // TODO Was type TemplatesToUpdate <: DotTemplate, but this no longer works in Scala 2.8
  def templateClassToUpdate: Class[TemplatesToUpdate]
  /** The number of times 'updateWeights' has been called. */
  var updateCount : Int = 0
  /** Call this method to use the current gradient to change the weight parameters.  When you override it, you must call super.updateWeights. */
  def updateWeights : Unit = updateCount += 1
  /** Adds a gradient (calculated by the recipient) to the accumulator.  Abstract method to be provided elsewhere. */
  def addGradient(accumulator:TemplatesToUpdate=>Vector, rate:Double): Unit
}


