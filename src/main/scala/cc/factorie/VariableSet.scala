/* Copyright (C) 2008-2010 Univ of Massachusetts Amherst, Computer Science Dept
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://code.google.com/p/factorie/
   This software is provided under the terms of the Eclipse Public License 1.0
   as published by http://www.opensource.org.  For further information,
   see the file `LICENSE.txt' included with this distribution. */

package cc.factorie

import scala.collection.mutable.{ArrayBuffer, HashMap, HashSet, ListBuffer, FlatHashTable}
import scala.reflect.Manifest
import scala.util.Random
import scala.Math
import scala.util.Sorting

/**A variable whose value is a set of other variables */
abstract class SetVariable[A]() extends Variable with TypedValues {
  type ValueType = A
  type VariableType <: SetVariable[A];
  private val _members = new HashSet[A];
  def members: scala.collection.Set[A] = _members
  def size = _members.size
  def contains(x:A) = _members.contains(x)
  def add(x:A)(implicit d: DiffList): Unit = if (!_members.contains(x)) {
    if (d != null) d += new SetVariableAddDiff(x)
    _members += x
  }
  def remove(x: A)(implicit d: DiffList): Unit = if (_members.contains(x)) {
    if (d != null) d += new SetVariableRemoveDiff(x)
    _members -= x
  }
  case class SetVariableAddDiff(added: A) extends Diff {
    // Console.println ("new SetVariableAddDiff added="+added)
    def variable: SetVariable[A] = SetVariable.this
    def redo = _members += added //if (_members.contains(added)) throw new Error else
    def undo = _members -= added
  }
  case class SetVariableRemoveDiff(removed: A) extends Diff {
    //        Console.println ("new SetVariableRemoveDiff removed="+removed)
    def variable: SetVariable[A] = SetVariable.this
    def redo = _members -= removed
    def undo = _members += removed //if (_members.contains(removed)) throw new Error else
    override def toString = "SetVariableRemoveDiff of " + removed + " from " + SetVariable.this
  }
}

abstract class WeakSetVariable[A<:{def present:Boolean}] extends Variable with TypedValues {
  type ValueType = A
  type VariableType <: WeakSetVariable[A];
  private val _members = new cc.factorie.util.WeakHashSet[A];
  //def members: scala.collection.Set[A] = _members
  def iterator = _members.iterator.filter(_.present)
  //def size = _members.size
  def contains(x: A) = _members.contains(x) && x.present
  def add(x: A)(implicit d: DiffList): Unit = if (!_members.contains(x)) {
    if (d != null) d += new WeakSetVariableAddDiff(x)
    _members += x
  }
  def remove(x: A)(implicit d: DiffList): Unit = if (_members.contains(x)) {
    if (d != null) d += new WeakSetVariableRemoveDiff(x)
    _members -= x
  }
  case class WeakSetVariableAddDiff(added: A) extends Diff {
    // Console.println ("new WeakSetVariableAddDiff added="+added)
    def variable: WeakSetVariable[A] = WeakSetVariable.this
    def redo = _members += added //if (_members.contains(added)) throw new Error else
    def undo = _members -= added
  }
  case class WeakSetVariableRemoveDiff(removed: A) extends Diff {
    //        Console.println ("new WeakSetVariableRemoveDiff removed="+removed)
    def variable: WeakSetVariable[A] = WeakSetVariable.this
    def redo = _members -= removed
    def undo = _members += removed //if (_members.contains(removed)) throw new Error else
    override def toString = "WeakSetVariableRemoveDiff of " + removed + " from " + WeakSetVariable.this
  }
}
