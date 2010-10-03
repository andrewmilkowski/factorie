/* Copyright (C) 2008-2010 Univ of Massachusetts Amherst, Computer Science Dept
   This file is part of "FACTORIE" (Factor graphs, Imperative, Extensible)
   http://factorie.cs.umass.edu, http://code.google.com/p/factorie/
   This software is provided under the terms of the Eclipse Public License 1.0
   as published by http://www.opensource.org.  For further information,
   see the file `LICENSE.txt' included with this distribution. */

package cc.factorie
import scala.reflect.Manifest
import scala.collection.mutable.{ListBuffer,ArrayBuffer,HashMap,PriorityQueue}
//import scalala.tensor.Vector
import cc.factorie.util.{Hooks0,Hooks1}

/** Samplers that key off of particular contexts.  Subclasses implement "process1(context:C)" */
trait Sampler[C] {
  type ContextType = C
  //if (contextClass == classOf[Nothing]) throw new Error("Constructor for class "+this.getClass.getName+" must be given type argument");
  /** The number of calls to process(numIterations:Int) or process(contexts:C,numIterations:Int). */
  var iterationCount = 0
  /** The number of calls to process(context:C) */
  var processCount = 0
  /** The number of calls to process that resulted in a change (a non-empty DiffList) */
  var changeCount = 0
  /** Do one step of sampling.  This is a method intended to be called by users.  It manages hooks and processCount. */
  final def process(context:C): DiffList = {
    val c = preProcessHook(context)
    if (c == null && !processingWithoutContext) return null // TODO should we return newDiffList here instead?
    val d = process1(c)
    processCount += 1
    postProcessHook(c, d)
    diffHook(d)
    if (null != d && d.size > 0) changeCount += 1
    d
  }
  /** If true, calls to "newDiffList" will create a new DiffList to describe the changes they made, otherwise "newDiffList" will return null. */
  var makeNewDiffList = true
  /** Convenient method for setting makeNewDiffList to false, and returning this. */
  def noDiffList: this.type = { makeNewDiffList = false; this }
  /** In your implementation of "process1" use this method to optionally create a new DiffList, obeying "makeNewDiffList". */
  def newDiffList = if (makeNewDiffList) new DiffList else null
  /** The underlying protected method that actually does the work.  Use this.newDiffList to optionally create returned DiffList.
      Needs to be defined in subclasses. */
  def process1(context:C): DiffList // TODO Why isn't this 'protected'?  It should be.  -akm.
  protected final def processN(contexts:Iterable[C]): Unit = { 
    contexts.foreach(process(_))
    iterationCount += 1
    postIterationHooks
    if (!postIterationHook) return 
  }
  def process(contexts:Iterable[C], numIterations:Int): Unit = for (i <- 0 to numIterations) processN(contexts)
  private var processingWithoutContext = false
  def process(count:Int): Unit = {
    processingWithoutContext = true // examined in process()
    for (i <- 0 to count) process(null.asInstanceOf[C]) // TODO Why is this cast necessary?;
    processingWithoutContext = false
  }
 
  // Hooks
  /** Called just before each step of sampling.  Return an alternative variable if you want that one sampled instead.  
      Return null if you want to abort sampling of this context. */
  def preProcessHook(context:C): C = context 
  /** Call just after each step of sampling. */
  def postProcessHook(context:C, difflist:DiffList): Unit = {}
  /** An alternative to postProcessHook that does not require the type C. */ // TODO Really have both?  Remove 'context:C' from postProcessHook?
  def diffHook(difflist:DiffList): Unit = {}
  /** Called after each iteration of sampling the full list of variables.  Return false if you want sampling to stop early. */
  def postIterationHook: Boolean = true
  def postIterationHooks = new Hooks0
}


// So we can call super in traits that override these methods
// TODO Is there a better way to do this?
// TODO Remove this and put ProposalSampler instead?  But then problems with SampleRank trait re-overriding methods it shouldn't?  Look into this. 
trait ProposalSampler0 {
  def proposalsHook(proposals:Seq[Proposal]): Unit
  def proposalHook(proposal:Proposal): Unit
}

/** Samplers that generate a list of Proposal objects, and select one log-proportionally to their modelScore.
    Proposal objects come from abstract method "proposals". 
    @author Andrew McCallum */
trait ProposalSampler[C] extends Sampler[C] with ProposalSampler0 {
  var temperature = 1.0 // Not used here, but used in subclasses; here for uniformity // TODO Consider moving use from SettingsSampler to this.process1
  def proposals(context:C): Seq[Proposal]
  def skipEmptyProposals = true
  def process1(context:C): DiffList = {
    val props = proposals(context)
    if (props.size == 0 && skipEmptyProposals) return new DiffList
    proposalsHook(props)
    val proposal = props.size match {
      case 0 => throw new Error("No proposals created.")
      case 1 => props.first 
      case _ => props.sampleExpProportionally((p:Proposal) => p.acceptanceScore)
    }
    proposal.diff.redo
    proposalHook(proposal)
    proposal.diff
  }
  val proposalsHooks = new Hooks1[Seq[Proposal]] // Allows non-overriders to add hooks
  def proposalsHook(proposals:Seq[Proposal]): Unit = proposalsHooks(proposals)
  val proposalHooks = new Hooks1[Proposal]
  def proposalHook(proposal:Proposal): Unit = proposalHooks(proposal)
}

// Not intended for users.  Here just so that SampleRank can override it.
// TODO is there a better way to do this?
trait SettingsSampler0 {
  def objective: Model
}

/** Tries each one of the settings in the Iterator provided by the abstract method "settings(C), 
    scores each, builds a distribution from the scores, and samples from it.
    @author Andrew McCallum */
abstract class SettingsSampler[C](theModel:Model, theObjective:Model) extends ProposalSampler[C] with SettingsSampler0 {
  def this(m:Model) = this(m, null)
  def model = theModel
  def objective = theObjective 
  /** Abstract method must be implemented in sub-classes.  
      Provides access to all different possible worlds we will evaluate for each call to 'process' */ 
  def settings(context:C) : SettingIterator
  def proposals(context:C): Seq[Proposal] = {
    // 'map's call to 'next' is actually what causes the change in state to happen
    // TODO some more efficient alternative to 'toList'?  But we have to be careful to make the collection 'strict'
    val s = settings(context).map(d => {val (m,o) = d.scoreAndUndo(model,objective); new Proposal(d, m, o, m/temperature)}).toList
    //if (s.exists(p=>p.modelScore > 0.0)) { s.foreach(p => println(p.modelScore+" "+model)); println("SettingsSampler^") }
    s
  } 
}

/** Tries each one of the settings of the given variable, 
    scores each, builds a distribution from the scores, and samples from it.
    This is exactly Gibbs sampling over a finite number of possible values of the variable.
    Note:  This differs from cc.factorie.GibbsSampler in that GibbsSampler may not iterate over settings, but instead sample from a closed-form distribution.
    Because SampleRank requires Proposal objects, we use this intsead of GibbsSampler.
    @see GibbsSampling
    @author Andrew McCallum */
class VariableSettingsSampler[V<:Variable with IterableSettings](model:Model = Global.defaultModel, objective:Model = null) extends SettingsSampler[V](model, objective) {
  def settings(v:V): SettingIterator = v.settings
}


/** Manage and use a queue to more often revisit low-scoring factors and re-sample their variables. */
// Consider FactorQueue { this: Sampler[_] => ... abstract override postProcessHook(C,DiffList).  But what to do about C?  
trait FactorQueue[C] extends Sampler[C] {
  var useQueue = true
  var maxQueueSize = 1000
  lazy val queue = new PriorityQueue[Factor]
  /** The proportion of sampling process steps to take from the queue, versus from the standard source of contexts. */
  var queueProportion = 0.5
  
  /** Override to provide the generic sampler that can potentially deal with arbitrary variables coming from Factors */
  def process0(x:AnyRef): DiffList
  def model: Model
  
  override def postProcessHook(context:C, diff:DiffList): Unit = {
    super.postProcessHook(context, diff)
    if (useQueue) {
      var queueDiff: DiffList = new DiffList
      if (queueProportion > 1.0 && !queue.isEmpty) {
        for (i <- 0 until (queueProportion.toInt)) if (!queue.isEmpty) {
          val qd = sampleFromQueue
          if (qd != null) queueDiff ++= qd
        }
      } else if (!queue.isEmpty && Global.random.nextDouble < queueProportion) {
        val qd = sampleFromQueue
        if (qd != null) queueDiff ++= qd
      }
      if (maxQueueSize > 0) {
        queue ++= model.factors(diff)
        if (queue.size > maxQueueSize) throw new Error // TODO find alternative, reduceToSize is missing from Scala 2.8; queue.reduceToSize(maxQueueSize)
      }
      diff appendAll queueDiff
    }
  }
  def sampleFromQueue : DiffList = {
    val factor = queue.dequeue // TODO consider proportionally sampling from the queue instead
    for (variable <- factor.variables.toSeq.shuffle; if (!variable.isConstant)) {
      val difflist = process0(variable)
      if (difflist != null && difflist.size > 0) return difflist
    }
    null
  }
}

// TODO But I haven't been able to make this existential typing work in practice yet.
/*
trait AlternativeFactorQueue extends Sampler[C forSome {type C <: Variable}] {
  override def diffHook(diff:DiffList): Unit = {
   println("foo") 
  }
}
*/


