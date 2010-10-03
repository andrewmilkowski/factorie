/* Copyright (C) 2008-2010 Univ of Massachusetts Amherst, Computer Science Dept
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://code.google.com/p/factorie/
   This software is provided under the terms of the Eclipse Public License 1.0
   as published by http://www.opensource.org.  For further information,
   see the file `LICENSE.txt' included with this distribution. */

package cc.factorie.util;

import scala.util.Random
import scala.util.Sorting

/** New functionality on Traversable instances, available by implicit conversion in the cc.factorie package object in cc/factorie/package.scala. */
trait TraversableExtras[A] {
  val t: Traversable[A]
  implicit val defaultRandom = cc.factorie.Global.random

  def sumDoubles(extractor: A => Double): Double = t.foldLeft(0.0)((sum, x:A) => sum + extractor(x))
  def sumInts(extractor: A => Int): Int = t.foldLeft(0)((sum, x:A) => sum + extractor(x))

  def multiplyDoubles(extractor: A => Double): Double = t.foldLeft(1.0)((prod, x) => prod * extractor(x))
  def multiplyInts(extractor: A => Int): Int = t.foldLeft(1)((prod, x) => prod * extractor(x))

  def maxByDouble(extractor: A => Double): A = {
    val iter = t.toSeq.iterator
    if (!iter.hasNext) throw new Error("TraversableExtras.maxByDouble on empty Traversable")
    var result: A = iter.next
    var value = extractor(result)
    while (iter.hasNext) {
      val x = iter.next; val v = extractor(x)
      if (v > value) { result = x; value = v }
    }
    result
  }
  def maxByInt(extractor: A => Int): A = {
    val iter = t.toSeq.iterator
    if (!iter.hasNext) throw new Error("TraversableExtras.maxByInt on empty Traversable")
    var result: A = iter.next
    var value = extractor(result)
    while (iter.hasNext) {
      val x = iter.next; val v = extractor(x)
      if (v > value) { result = x; value = v }
    }
    result
  }

  def minByDouble(extractor: A => Double): A = {
    val iter = t.toSeq.iterator
    if (!iter.hasNext) throw new Error("TraversableExtras.minByDouble on empty Traversable")
    var result: A = iter.next
    var value = extractor(result)
    while (iter.hasNext) {
      val x = iter.next; val v = extractor(x)
      if (v < value) { result = x; value = v }
    }
    result
  }
  def minByInt(extractor: A => Int): A = {
    val iter = t.toSeq.iterator
    if (!iter.hasNext) throw new Error("TraversableExtras.minByInt on empty Traversable")
    var result: A = iter.next
    var value = extractor(result)
    while (iter.hasNext) {
      val x = iter.next; val v = extractor(x)
      if (v < value) { result = x; value = v }
    }
    result
  }

  /**Returns both the maximum element and the second-to-max element */
  // TODO reimplement this to make it more efficient; no need to sort the whole sequence
  def max2ByDouble(extractor: A => Double): (A, A) = {
    val s1 = t.toSeq
    assert(s1.length > 1)
    val s2: Seq[A] = s1.sortWith((x1:A, x2:A) => extractor(x1) > extractor(x2))
    (s2(0), s2(1))
  }

  /** Sorts with minimum first. */
  //@deprecated // use SeqLike sort instead?
  def sortForward(extractor: A => Double): Seq[A] =
    t.toSeq.sortWith((x1:A, x2:A) => extractor(x1) < extractor(x2))

  /** Sorts with maximum first.*/
  //@deprecated // use SeqLike sort instead?
  def sortReverse(extractor: A => Double): Seq[A] =
    t.toSeq.sortWith((x1:A, x2:A) => extractor(x1) > extractor(x2))

  def shuffle(implicit random: Random = defaultRandom) : Seq[A] = {
    val s2 = t.map(x => (x, random.nextInt)).toSeq
    Sorting.stableSort(s2, (t1: (A, Int), t2: (A, Int)) => t1._2 > t2._2).map(t => t._1)
  }

  def split(ratio: Double): (Seq[A], Seq[A]) = {
    val s2 = t.toSeq
    if (ratio <= 0 || ratio > 1.0) throw new Error
    val index = (ratio * s2.size).toInt
    if (index >= s2.size)
      (s2, Seq.empty)
    else
      (s2.slice(0, index), s2.drop(index))
  }

  // TODO Make these preserve their correct return types rather than backing off to Traversable.
  def filterByType[T<:AnyRef](implicit m: ClassManifest[T]): Traversable[T] = 
    t.filter(t1 => m.erasure.isAssignableFrom(t1.asInstanceOf[AnyRef].getClass)).asInstanceOf[Traversable[T]]
  def filterByClass[C](c: Class[C]): Traversable[C] =
    t.filter(t1 => c.isAssignableFrom(t1.asInstanceOf[AnyRef].getClass)).asInstanceOf[Traversable[C]]




  def sample(implicit random: Random = defaultRandom): A = {
    val s2 = t.toSeq
    if (s2.size == 1) s2.head
    else s2(random.nextInt(s2.size))
  }

  def sampleProportionally(extractor: A => Double)(implicit random:Random = defaultRandom): A = {
    var sum = t.foldLeft(0.0)((total, x) => total + extractor(x))
    val r = random.nextDouble * sum
    sum = 0
    for (choice <- t) {
      val e = extractor(choice)
      if (e < 0.0) throw new Error("TraversableExtras sample extractor value " + e + " less than zero.  Sum=" + sum)
      sum += e
      if (sum >= r)
        return choice;
    }
    throw new Error("TraversableExtras sample error: r=" + r + " sum=" + sum)
  }

  def sampleExpProportionally(extractor: A => Double)(implicit random:Random  = defaultRandom): A = {
    val maxValue : Double = t.foldLeft(Math.NEG_INF_DOUBLE)((max,t) => {val x = extractor(t); assert(x==x); if (x>max) x else max})
    if (maxValue == Math.NEG_INF_DOUBLE) throw new Error("Cannot sample from an empty list.")
    sampleProportionally(t1 => if (extractor(t1) == Math.NEG_INF_DOUBLE) Math.NEG_INF_DOUBLE else Math.exp(extractor(t1) - maxValue))(random)
  }


}
